package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ParasyticLouseTenancyRules.DeliveryRoute;
import com.kadamitas.warlockery.entity.ParasyticLouseTenancyRules.EvictReason;
import com.kadamitas.warlockery.entity.ParasyticLouseTenancyRules.HostFacts;
import com.kadamitas.warlockery.entity.ParasyticLouseTenancyRules.Phase;
import com.kadamitas.warlockery.entity.ParasyticLouseTenancyRules.RedirectFacts;
import com.kadamitas.warlockery.entity.ParasyticLouseTenancyRules.RedirectRejection;
import com.kadamitas.warlockery.entity.ParasyticLouseTenancyRules.TenancyFacts;
import com.kadamitas.warlockery.entity.behavior.Cadence;
import com.kadamitas.warlockery.entity.behavior.Candidates;
import com.kadamitas.warlockery.entity.behavior.PhaseTimer;
import com.kadamitas.warlockery.entity.behavior.ReadBudget;
import com.kadamitas.warlockery.entity.behavior.RouteRequest;
import com.kadamitas.warlockery.entity.behavior.ScanEnvelope;
import com.kadamitas.warlockery.entity.behavior.Ticks;
import com.kadamitas.warlockery.item.ParasyticLouseItem;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The only server-side Parasytic Louse controller and the sole navigation writer for this kind.
 *
 * <p>A louse owns at most one <em>tenancy</em> at a time: it selects one host, telegraphs a mark
 * that commits no movement and writes nothing to the candidate, attaches, feeds on a capped ladder,
 * delivers its one payload at satiation and then unwinds. Both endings, satiation and the expiry of
 * the residence term, leave through the same single unwind, so nothing can land after a tenancy has
 * been cancelled.</p>
 *
 * <p>Nothing here edits, breaks or places a block, opens a container, reads a player inventory
 * beyond the hand and armor items the preserved interactions already involve, picks up an item,
 * touches farmland or a crop, creates an entity, teleports, forces a chunk, calls {@code getChunk},
 * looks across a dimension, or iterates entities globally. The only writes this family ever makes to
 * another entity are ordinary attributed melee damage and one {@code MobEffectInstance}, both
 * preserved 1.4.0 contracts.</p>
 *
 * <h2>Shared primitives used</h2>
 *
 * <p>{@code ReadBudget} charges every raw entity visit, sight trace and block read before any filter
 * may reject what it fetched. {@code Cadence} arms on the fact that work <em>ran</em>, so a scan
 * that qualifies nothing still pays its full period. {@code RouteRequest} carries the failure run
 * and the backoff. {@code PhaseTimer} holds the mark telegraph, so the pair
 * {@code (MARK, 0 remaining)} that a reconciling constructor would have tidied away cannot be built
 * and the branch that owns committing the attach always runs. {@code ScanEnvelope} supplies the
 * centre-out anchor plus rotating page for the hazard escape search, which is the only block search
 * this family performs. {@code Candidates} supplies the distance-then-identity ordering.
 * {@code Ticks} supplies the loaded countdown arithmetic and the per-entity stagger.</p>
 *
 * <p>Per-tick paths allocate nothing: the cadences and the route request are small records
 * reassigned in place, and every list, comparator and budget below is built only inside the scan,
 * the occupancy probe or the escape search, none of which runs on an ordinary tick.</p>
 */
public final class ParasyticLouseRuntime {

    static final TagKey<Block> CONTACT_HAZARDS = TagKey.create(
        Registries.BLOCK,
        Identifier.fromNamespaceAndPath("warlockery", "ai/contact_hazards")
    );
    private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    );
    private static final ScanEnvelope ESCAPE_ENVELOPE = ScanEnvelope.of(
        ParasyticLouseTenancyRules.ESCAPE_HORIZONTAL_RADIUS,
        ParasyticLouseTenancyRules.ESCAPE_VERTICAL_RADIUS
    );

    private ParasyticLouseRuntime() {
    }

    // ---------------------------------------------------------------- counters

    /**
     * Structural work counters. Pass-local diagnostics: never saved, never synced, and losing them
     * can never change behavior.
     *
     * <p>Every counter here has at least one real increment site in this file. Counters for the
     * things this family must never do, block edits, item pickups, container access, entity
     * creation, teleports, item-entity scans and retaliation, are deliberately absent rather than
     * present and always zero: a counter with no increment site asserts nothing that could fail, so
     * the fixtures prove those prohibitions against the world instead, by hashing the arena block
     * states before and after and by leaving an item entity in the arena and checking it survives
     * untouched.</p>
     */
    public static final class Counters {
        private final long[] evicts = new long[EvictReason.values().length];
        private final long[] redirectRejections = new long[RedirectRejection.values().length];
        private final long[] deliveries = new long[DeliveryRoute.values().length];

        long aiTicks;
        long hostScans;
        long hostRawVisits;
        long hostSightRays;
        long hostAcquisitions;
        long scanFailures;
        long denyGateRejections;
        long graceRejections;
        long marksStarted;
        long markAborts;
        long attachCommits;
        long occupancyProbes;
        long occupancyRawVisits;
        long feedAttempts;
        long feedAccepted;
        long feedRejected;
        long nourishmentIncrements;
        long nourishmentDecays;
        long satiations;
        long payloadCeilingClamps;
        long payloadsCleared;
        long redirectEvaluations;
        long redirectSightRays;
        long attackerAttributions;
        long withdrawals;
        long evictEvaluations;
        long detaches;
        long pathRequests;
        long pathFailures;
        long pathBackoffs;
        long hazardObservations;
        long hazardBlockReads;
        long escapeSearches;
        long escapeCandidateVisits;
        long capturesByHand;
        long capturesByPotion;
        long sounds;
        long particles;

        public long aiTicks() { return aiTicks; }
        public long hostScans() { return hostScans; }
        public long hostRawVisits() { return hostRawVisits; }
        public long hostSightRays() { return hostSightRays; }
        public long hostAcquisitions() { return hostAcquisitions; }
        public long scanFailures() { return scanFailures; }
        public long denyGateRejections() { return denyGateRejections; }
        public long graceRejections() { return graceRejections; }
        public long marksStarted() { return marksStarted; }
        public long markAborts() { return markAborts; }
        public long attachCommits() { return attachCommits; }
        public long occupancyProbes() { return occupancyProbes; }
        public long occupancyRawVisits() { return occupancyRawVisits; }
        public long feedAttempts() { return feedAttempts; }
        public long feedAccepted() { return feedAccepted; }
        public long feedRejected() { return feedRejected; }
        public long nourishmentIncrements() { return nourishmentIncrements; }
        public long nourishmentDecays() { return nourishmentDecays; }
        public long satiations() { return satiations; }
        public long payloadCeilingClamps() { return payloadCeilingClamps; }
        public long payloadsCleared() { return payloadsCleared; }
        public long redirectEvaluations() { return redirectEvaluations; }
        public long redirectSightRays() { return redirectSightRays; }
        public long attackerAttributions() { return attackerAttributions; }
        public long withdrawals() { return withdrawals; }
        public long evictEvaluations() { return evictEvaluations; }
        public long detaches() { return detaches; }
        public long pathRequests() { return pathRequests; }
        public long pathFailures() { return pathFailures; }
        public long pathBackoffs() { return pathBackoffs; }
        public long hazardObservations() { return hazardObservations; }
        public long hazardBlockReads() { return hazardBlockReads; }
        public long escapeSearches() { return escapeSearches; }
        public long escapeCandidateVisits() { return escapeCandidateVisits; }
        public long capturesByHand() { return capturesByHand; }
        public long capturesByPotion() { return capturesByPotion; }
        public long sounds() { return sounds; }
        public long particles() { return particles; }

        public long evicts(final EvictReason reason) {
            return evicts[reason.ordinal()];
        }

        public long evictsTotal() {
            long total = 0L;
            for (final long count : evicts) {
                total += count;
            }
            return total;
        }

        public long redirectRejections(final RedirectRejection rejection) {
            return redirectRejections[rejection.ordinal()];
        }

        public long deliveries(final DeliveryRoute route) {
            return deliveries[route.ordinal()];
        }

        public long deliveriesTotal() {
            long total = 0L;
            for (final long count : deliveries) {
                total += count;
            }
            return total;
        }
    }

    // ---------------------------------------------------------------- transient tenancy

    /**
     * Everything the runtime owns and nothing that survives a save. Cleared on load, on removal and
     * on every cancellation trigger, so a reload can delay work by one cadence but can never replay
     * a mark, a delivery, a feed, a sound or a path.
     */
    public static final class Tenancy {

        /**
         * The one host or candidate, or none.
         *
         * <p>The compact constructor collapses a half-written host to none. This is the
         * <em>identity</em> shape of reconciliation, not the timer shape: it asserts that the two
         * halves of one identity cannot disagree, which is exactly the invariant a type should
         * enforce, and it touches no duration, so no tick branch loses its ending. For a parasite the
         * host reference is the identity that every host-dependent field hangs from, and a host
         * whose dimension is unknown is not a host the louse could ever legally reach.</p>
         */
        public record Host(Optional<UUID> id, Optional<String> dimension) {
            public Host {
                id = Objects.requireNonNull(id, "id");
                dimension = Objects.requireNonNull(dimension, "dimension").filter(key -> !key.isBlank());
                if (id.isEmpty() || dimension.isEmpty()) {
                    id = Optional.empty();
                    dimension = Optional.empty();
                }
            }

            public static Host none() {
                return new Host(Optional.empty(), Optional.empty());
            }

            public static Host of(final UUID id, final String dimension) {
                return new Host(Optional.of(id), Optional.of(dimension));
            }

            public boolean present() {
                return id.isPresent() && dimension.isPresent();
            }
        }

        Phase phase = Phase.FREE;
        Host host = Host.none();
        Host releasedHost = Host.none();
        int releasedGraceTicks;
        PhaseTimer<Phase> mark = PhaseTimer.none();
        int residenceRemainingTicks;
        int continuousSightLossTicks;
        boolean hostSighted;
        Optional<UUID> attacker = Optional.empty();
        int attackerFreshnessTicks;
        int withdrawalTicks;
        boolean hazardActive;
        int escapeCursor;
        boolean escapeCursorSeeded;
        boolean reconciled;

        Cadence scan = Cadence.every(ParasyticLouseTenancyRules.SCAN_CADENCE_TICKS);
        Cadence feed = Cadence.every(ParasyticLouseTenancyRules.FEED_CADENCE_TICKS);
        Cadence redirect = Cadence.every(ParasyticLouseTenancyRules.REDIRECT_CADENCE_TICKS);
        Cadence evict = Cadence.every(ParasyticLouseTenancyRules.EVICT_CADENCE_TICKS);
        Cadence sight = Cadence.every(ParasyticLouseTenancyRules.SIGHT_CHECK_CADENCE_TICKS);
        Cadence hazard = Cadence.every(ParasyticLouseTenancyRules.HAZARD_CADENCE_TICKS);
        RouteRequest route = RouteRequest.every(ParasyticLouseTenancyRules.PATH_CADENCE_TICKS);

        public Phase phase() {
            return phase;
        }

        public Host host() {
            return host;
        }

        public Host releasedHost() {
            return releasedHost;
        }

        public int residenceRemainingTicks() {
            return residenceRemainingTicks;
        }

        public int markRemainingTicks() {
            return mark.remaining();
        }

        public int withdrawalTicks() {
            return withdrawalTicks;
        }

        public int consecutiveRouteFailures() {
            return route.consecutiveFailures();
        }

        public int routeBackoffRemaining() {
            return route.backoffRemaining();
        }

        public boolean hazardActive() {
            return hazardActive;
        }

        /** Full teardown: nothing host-dependent, nothing telegraphed and nothing routed survives. */
        public void clearAll() {
            phase = Phase.FREE;
            host = Host.none();
            releasedHost = Host.none();
            releasedGraceTicks = 0;
            mark = PhaseTimer.none();
            residenceRemainingTicks = 0;
            continuousSightLossTicks = 0;
            hostSighted = false;
            attacker = Optional.empty();
            attackerFreshnessTicks = 0;
            withdrawalTicks = 0;
            hazardActive = false;
            escapeCursor = 0;
            escapeCursorSeeded = false;
            reconciled = false;
            route = RouteRequest.every(ParasyticLouseTenancyRules.PATH_CADENCE_TICKS);
        }
    }

    // ---------------------------------------------------------------- the live tick

    /**
     * The single entry point, called every loaded server AI tick from
     * {@link ParasyticLouseEntity#customServerAiStep}. Hazard outranks the accepted-damage
     * withdrawal, which outranks the tenancy, which outranks the routine; the redirect route is
     * resolved outside the ladder because it writes no navigation and changes no state.
     */
    public static void tick(final ParasyticLouseEntity louse, final ServerLevel level) {
        final Tenancy tenancy = louse.tenancy();
        louse.louseCounters().aiTicks++;
        reconcileOnLoad(louse, tenancy);
        advanceLoadedTimers(louse, tenancy);
        if (tickHazardBand(louse, level, tenancy)) {
            return;
        }
        if (tickWithdrawalBand(louse, level, tenancy)) {
            tickRedirect(louse, level, tenancy);
            return;
        }
        switch (tenancy.phase) {
            case FREE -> tickFree(louse, level, tenancy);
            case SEEK -> tickSeek(louse, level, tenancy);
            case MARK -> tickMark(louse, level, tenancy);
            case FEED -> tickFeed(louse, level, tenancy);
            case ESCAPE -> tenancy.phase = Phase.FREE;
        }
        tickRedirect(louse, level, tenancy);
    }

    private static void reconcileOnLoad(final ParasyticLouseEntity louse, final Tenancy tenancy) {
        if (tenancy.reconciled) {
            return;
        }
        tenancy.reconciled = true;
        final UUID identity = louse.getUUID();
        tenancy.scan = staggered(identity, ParasyticLouseTenancyRules.SCAN_CADENCE_TICKS);
        tenancy.redirect = staggered(identity, ParasyticLouseTenancyRules.REDIRECT_CADENCE_TICKS);
        tenancy.evict = staggered(identity, ParasyticLouseTenancyRules.EVICT_CADENCE_TICKS);
        tenancy.hazard = staggered(identity, ParasyticLouseTenancyRules.HAZARD_CADENCE_TICKS);
    }

    /**
     * A per-entity phase offset inside the period, so a crowd of lice never synchronizes its scans.
     * Derived from identity alone and never from absolute world time.
     */
    private static Cadence staggered(final UUID identity, final int period) {
        return new Cadence(period, Math.max(0, period - Ticks.stableOffset(identity, period)));
    }

    /**
     * One loaded tick of arithmetic and nothing else. Every countdown here is a remaining-tick
     * value, so an unloaded gap advances none of them and no catch-up is ever performed.
     */
    private static void advanceLoadedTimers(
        final ParasyticLouseEntity louse,
        final Tenancy tenancy
    ) {
        tenancy.scan = tenancy.scan.step();
        tenancy.feed = tenancy.feed.step();
        tenancy.redirect = tenancy.redirect.step();
        tenancy.evict = tenancy.evict.step();
        tenancy.sight = tenancy.sight.step();
        tenancy.hazard = tenancy.hazard.step();
        tenancy.route = tenancy.route.step();
        tenancy.releasedGraceTicks = Ticks.decrementLoaded(tenancy.releasedGraceTicks);
        if (tenancy.releasedGraceTicks == 0) {
            tenancy.releasedHost = Tenancy.Host.none();
        }
        tenancy.attackerFreshnessTicks = Ticks.decrementLoaded(tenancy.attackerFreshnessTicks);
        if (tenancy.attackerFreshnessTicks == 0) {
            tenancy.attacker = Optional.empty();
        }
        tenancy.withdrawalTicks = Ticks.decrementLoaded(tenancy.withdrawalTicks);
        if (tenancy.phase == Phase.MARK) {
            tenancy.mark = tenancy.mark.step();
        }
        if (tenancy.phase == Phase.FEED) {
            tenancy.residenceRemainingTicks =
                Ticks.decrementLoaded(tenancy.residenceRemainingTicks);
        }
        louse.setLouseState(
            louse.louseState().withSeekCooldown(
                Ticks.decrementLoaded(louse.louseState().seekCooldownRemainingTicks())
            )
        );
        advanceNourishmentDecay(louse, tenancy);
    }

    /**
     * Nourishment decays by exactly one per four hundred loaded ticks while the louse is not
     * feeding, through a persisted remainder, so an unload can neither lose nor invent progress.
     */
    private static void advanceNourishmentDecay(
        final ParasyticLouseEntity louse,
        final Tenancy tenancy
    ) {
        if (tenancy.phase == Phase.FEED || louse.louseState().nourishment() <= 0) {
            return;
        }
        final ParasyticLouseState state = louse.louseState();
        if (state.decayRemainingTicks() > 0) {
            louse.setLouseState(state.withDecayRemaining(state.decayRemainingTicks() - 1));
            return;
        }
        louse.louseCounters().nourishmentDecays++;
        louse.setLouseState(state
            .withNourishment(state.nourishment() - 1)
            .withDecayRemaining(ParasyticLouseState.MAX_DECAY_REMAINDER));
    }

    // ---------------------------------------------------------------- band one: hazard

    /**
     * Constant flags are read every tick and can stop a lower band at once; the charged block
     * observation runs at most every twenty ticks. A hazard tears the tenancy down first and only
     * then writes navigation, because a torn-down tenancy is the only kind that cannot deliver
     * anything afterwards.
     */
    private static boolean tickHazardBand(
        final ParasyticLouseEntity louse,
        final ServerLevel level,
        final Tenancy tenancy
    ) {
        final boolean immersed = louse.isInWater();
        final boolean urgent = louse.isOnFire() || louse.isInLava() || immersed;
        if (!urgent && !tenancy.hazard.due()) {
            return tenancy.hazardActive && tenancy.phase == Phase.ESCAPE;
        }
        if (tenancy.hazard.due()) {
            tenancy.hazard = tenancy.hazard.arm();
            tenancy.hazardActive = urgent || observeHazard(louse, level);
        } else {
            tenancy.hazardActive = true;
        }
        if (!ParasyticLouseTenancyRules.hazardPreempts(tenancy.hazardActive)) {
            return false;
        }
        if (tenancy.phase != Phase.ESCAPE) {
            endTenancy(louse, tenancy, immersed ? EvictReason.IMMERSED : EvictReason.HAZARD);
            tenancy.phase = Phase.ESCAPE;
        }
        routeEscape(louse, level, tenancy);
        return true;
    }

    /**
     * Bounded contact-neighbourhood observation. Every read is charged before the state it fetched
     * can be judged, so the declared ceiling bounds the reads actually performed rather than the
     * accepted minority, and an unloaded footprint is never forced: it simply reports no hazard.
     */
    private static boolean observeHazard(final ParasyticLouseEntity louse, final ServerLevel level) {
        louse.louseCounters().hazardObservations++;
        final BlockPos center = louse.blockPosition();
        if (!level.hasChunkAt(center)) {
            return false;
        }
        final ReadBudget budget = ReadBudget.of(ParasyticLouseTenancyRules.MAX_HAZARD_READS);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (budget.exhausted()) {
                        louse.louseCounters().hazardBlockReads += budget.spent();
                        return false;
                    }
                    final BlockPos probe = center.offset(dx, dy, dz);
                    final boolean hazardous = budget.accepts(
                        () -> level.getBlockState(probe), ParasyticLouseRuntime::isHazardBlock
                    );
                    if (hazardous) {
                        louse.louseCounters().hazardBlockReads += budget.spent();
                        return true;
                    }
                }
            }
        }
        louse.louseCounters().hazardBlockReads += budget.spent();
        return false;
    }

    static boolean isHazardBlock(final BlockState state) {
        return state.is(Blocks.FIRE)
            || state.is(Blocks.SOUL_FIRE)
            || state.is(Blocks.CAMPFIRE)
            || state.is(Blocks.SOUL_CAMPFIRE)
            || state.is(Blocks.LAVA)
            || state.is(CONTACT_HAZARDS);
    }

    /**
     * The one block search this family performs, and the only place {@code ScanEnvelope} is used.
     * The window is a fixed near anchor containing the louse's own neighbourhood plus a page over
     * the far tail that rotates by exactly one page per search, so successive searches cover the
     * whole envelope instead of spending the entire budget on the innermost ring.
     *
     * <p>A search that qualifies nothing has spent the same real reads as one that succeeded, so it
     * arms the same cadence and counts the same failure. That is what makes the three-failure
     * backoff able to bind at all.</p>
     */
    private static void routeEscape(
        final ParasyticLouseEntity louse,
        final ServerLevel level,
        final Tenancy tenancy
    ) {
        if (!tenancy.route.mayRequest()) {
            return;
        }
        if (!tenancy.escapeCursorSeeded) {
            tenancy.escapeCursorSeeded = true;
            tenancy.escapeCursor = ESCAPE_ENVELOPE.seedCursor(
                louse.getUUID(), ParasyticLouseTenancyRules.MAX_ESCAPE_CANDIDATES
            );
        }
        louse.louseCounters().escapeSearches++;
        final Optional<BlockPos> destination = findEscape(louse, level, tenancy);
        tenancy.escapeCursor = ESCAPE_ENVELOPE.advanceCursor(
            ParasyticLouseTenancyRules.MAX_ESCAPE_CANDIDATES, tenancy.escapeCursor
        );
        if (destination.isEmpty()) {
            failRoute(louse, tenancy);
            return;
        }
        requestPath(louse, tenancy, destination.orElseThrow(),
            ParasyticLouseTenancyRules.ESCAPE_SPEED);
    }

    private static Optional<BlockPos> findEscape(
        final ParasyticLouseEntity louse,
        final ServerLevel level,
        final Tenancy tenancy
    ) {
        final BlockPos origin = louse.blockPosition();
        final ReadBudget budget = ReadBudget.of(ParasyticLouseTenancyRules.MAX_ESCAPE_CANDIDATES);
        BlockPos best = null;
        double bestDistance = -1.0D;
        for (final BlockPos offset : ESCAPE_ENVELOPE.window(
            ParasyticLouseTenancyRules.MAX_ESCAPE_CANDIDATES, tenancy.escapeCursor
        )) {
            if (budget.exhausted()) {
                break;
            }
            final BlockPos candidate = origin.offset(offset);
            if (candidate.equals(origin)) {
                continue;
            }
            louse.louseCounters().escapeCandidateVisits++;
            final boolean safe = budget.accepts(
                () -> candidate, probe -> safeDestination(louse, level, probe)
            );
            if (!safe) {
                continue;
            }
            final double distance = candidate.distSqr(origin);
            if (distance > bestDistance) {
                bestDistance = distance;
                best = candidate.immutable();
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean safeDestination(
        final ParasyticLouseEntity louse,
        final ServerLevel level,
        final BlockPos candidate
    ) {
        final AABB box = louse.getType().getDimensions()
            .makeBoundingBox(Vec3.atBottomCenterOf(candidate));
        if (!level.getWorldBorder().isWithinBounds(box) || !level.hasChunkAt(candidate)) {
            return false;
        }
        if (isHazardBlock(level.getBlockState(candidate))
            || !level.getFluidState(candidate).isEmpty()) {
            return false;
        }
        return level.noCollision(louse, box);
    }

    // ---------------------------------------------------------------- band two: withdrawal

    /**
     * The bounded withdrawal after an accepted hit. A louse never retaliates, never acquires its
     * attacker, never applies an effect to it and never begins a tenancy while withdrawing.
     */
    private static boolean tickWithdrawalBand(
        final ParasyticLouseEntity louse,
        final ServerLevel level,
        final Tenancy tenancy
    ) {
        if (tenancy.withdrawalTicks <= 0) {
            return false;
        }
        final Optional<LivingEntity> attacker = tenancy.attacker
            .map(level::getEntity)
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast);
        if (attacker.isEmpty() || !tenancy.route.mayRequest()) {
            return true;
        }
        final Vec3 away = louse.position().subtract(attacker.orElseThrow().position());
        final Vec3 target = louse.position().add(away.horizontal().normalize().scale(4.0D));
        requestPath(louse, tenancy, BlockPos.containing(target),
            ParasyticLouseTenancyRules.ESCAPE_SPEED);
        return true;
    }

    // ---------------------------------------------------------------- band three: routine

    /**
     * Standing still and, when the scan is due and no cooldown is running, one bounded host scan.
     * No navigation is written here at all.
     */
    private static void tickFree(
        final ParasyticLouseEntity louse,
        final ServerLevel level,
        final Tenancy tenancy
    ) {
        if (!ParasyticLouseTenancyRules.tenancyMayStart(
            tenancy.phase, louse.louseState().seekCooldownRemainingTicks(), tenancy.withdrawalTicks
        ) || !tenancy.scan.due()) {
            return;
        }
        // Armed before the scan runs, not inside its success branch: a scan that qualifies nothing
        // has spent the same six raw visits and must pay the same full period.
        tenancy.scan = tenancy.scan.arm();
        louse.louseCounters().hostScans++;
        final Optional<LivingEntity> selected = scanForHost(louse, level, tenancy);
        if (selected.isEmpty()) {
            louse.louseCounters().scanFailures++;
            return;
        }
        final LivingEntity host = selected.orElseThrow();
        louse.louseCounters().hostAcquisitions++;
        tenancy.host = Tenancy.Host.of(host.getUUID(), dimensionOf(level));
        tenancy.continuousSightLossTicks = 0;
        tenancy.hostSighted = true;
        // A tenancy starts with its own route ledger. Failures accumulated while idle belong to the
        // idling, and a fresh tenancy that inherited three of them would be released on ROUTE_FAILED
        // before it ever closed on its host. An open backoff window is deliberately preserved, so a
        // new tenancy still cannot spam path requests out of terrain that just refused three.
        tenancy.route = new RouteRequest(
            Cadence.every(ParasyticLouseTenancyRules.PATH_CADENCE_TICKS),
            0,
            tenancy.route.backoffRemaining()
        );
        tenancy.phase = Phase.SEEK;
        routeToHost(louse, tenancy, host);
    }

    /**
     * One bounded scan. Discovery uses the abortable getter with an always-accept raw consumer and a
     * hard visit cap, so eligibility is decided only on that bounded snapshot: an eligible-only
     * predicate could traverse an unbounded number of ineligible entities before collecting six
     * matches, which is the shape that makes a declared cap unable to bind.
     *
     * <p>Every raw visit and every sight trace is charged before the filter that could reject it.
     * At most two sight traces are spent, so if neither of the two nearest eligible candidates is
     * visible the scan yields nothing this cadence and deliberately does not widen, re-query or fall
     * through to a third trace.</p>
     */
    private static Optional<LivingEntity> scanForHost(
        final ParasyticLouseEntity louse,
        final ServerLevel level,
        final Tenancy tenancy
    ) {
        final List<LivingEntity> visited = new ArrayList<>(
            ParasyticLouseTenancyRules.MAX_SCAN_VISITS
        );
        final ReadBudget visits = ReadBudget.of(ParasyticLouseTenancyRules.MAX_SCAN_VISITS);
        level.getEntities().get(
            EntityTypeTest.forClass(LivingEntity.class),
            louse.getBoundingBox().inflate(ParasyticLouseTenancyRules.SCAN_RADIUS),
            candidate -> {
                if (!visits.charge()) {
                    return AbortableIterationConsumer.Continuation.ABORT;
                }
                louse.louseCounters().hostRawVisits++;
                visited.add(candidate);
                return visits.exhausted()
                    ? AbortableIterationConsumer.Continuation.ABORT
                    : AbortableIterationConsumer.Continuation.CONTINUE;
            }
        );
        final List<LivingEntity> eligible = new ArrayList<>(visited.size());
        for (final LivingEntity candidate : visited) {
            final HostFacts facts = observeHost(louse, level, tenancy, candidate);
            if (ParasyticLouseTenancyRules.eligibleHost(facts)) {
                eligible.add(candidate);
                continue;
            }
            if (facts.diseaseImmune()) {
                louse.louseCounters().denyGateRejections++;
            }
            if (facts.inGrace()) {
                louse.louseCounters().graceRejections++;
            }
        }
        final Comparator<LivingEntity> order = Candidates.byDistanceThenIdentity(
            louse::distanceToSqr, LivingEntity::getUUID
        );
        eligible.sort(order);
        final ReadBudget rays = ReadBudget.of(ParasyticLouseTenancyRules.MAX_SCAN_SIGHT_RAYS);
        for (final LivingEntity candidate : eligible) {
            if (!rays.charge()) {
                return Optional.empty();
            }
            louse.louseCounters().hostSightRays++;
            if (louse.getSensing().hasLineOfSight(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /** Closing on the bound candidate, and the only place a mark may be opened. */
    private static void tickSeek(
        final ParasyticLouseEntity louse,
        final ServerLevel level,
        final Tenancy tenancy
    ) {
        final Optional<LivingEntity> resolved = resolveHost(louse, level, tenancy);
        if (releaseIfRequired(louse, level, tenancy, resolved, true)) {
            return;
        }
        final LivingEntity host = resolved.orElseThrow();
        louse.getLookControl().setLookAt(host, 30.0F, 30.0F);
        final double distanceSquared = louse.distanceToSqr(host);
        if (ParasyticLouseTenancyRules.markOpens(distanceSquared, observeSight(louse, tenancy, host))) {
            openMark(louse, level, tenancy);
            return;
        }
        if (louse.getNavigation().isDone()) {
            routeToHost(louse, tenancy, host);
        }
    }

    private static void openMark(
        final ParasyticLouseEntity louse,
        final ServerLevel level,
        final Tenancy tenancy
    ) {
        louse.getNavigation().stop();
        tenancy.phase = Phase.MARK;
        tenancy.mark = PhaseTimer.start(Phase.MARK, ParasyticLouseTenancyRules.MARK_TICKS);
        louse.louseCounters().marksStarted++;
        louse.louseCounters().sounds++;
        louse.louseCounters().particles++;
        level.playSound(null, louse.getX(), louse.getY(), louse.getZ(),
            SoundEvents.SPIDER_AMBIENT, louse.getSoundSource(), 0.5F, 1.6F);
        level.sendParticles(ParticleTypes.CRIT, louse.getX(), louse.getY() + 0.2D, louse.getZ(),
            ParasyticLouseTenancyRules.MAX_MARK_PARTICLES, 0.15D, 0.1D, 0.15D, 0.01D);
    }

    /**
     * The telegraph. Deliberately the only tenancy state that performs no perception at all and
     * writes nothing whatsoever: no navigation call, no path request, no target, no damage and
     * nothing at all onto the candidate. The two counters that must stay zero are incremented
     * nowhere, which is a stronger statement than asserting they are zero.
     *
     * <p>The timer is a {@code PhaseTimer}, so the pair this branch tests for cannot have been
     * destroyed by a constructor before the branch ran: reaching zero produces an explicitly expired
     * value that only this branch may end.</p>
     */
    private static void tickMark(
        final ParasyticLouseEntity louse,
        final ServerLevel level,
        final Tenancy tenancy
    ) {
        if (!tenancy.mark.expired()) {
            return;
        }
        final Optional<LivingEntity> resolved = resolveHost(louse, level, tenancy);
        if (releaseIfRequired(louse, level, tenancy, resolved, true)) {
            tenancy.mark = tenancy.mark.cancel();
            return;
        }
        final LivingEntity candidate = resolved.orElseThrow();
        final double distanceSquared = louse.distanceToSqr(candidate);
        louse.louseCounters().hostSightRays++;
        final boolean sighted = louse.getSensing().hasLineOfSight(candidate);
        if (ParasyticLouseTenancyRules.markLapses(distanceSquared, sighted)) {
            // A lapsed telegraph is not an ending. Nothing is applied, no cooldown starts and the
            // louse simply resumes closing, which is why this path does not route through the unwind.
            louse.louseCounters().markAborts++;
            tenancy.mark = tenancy.mark.cancel();
            tenancy.phase = Phase.SEEK;
            return;
        }
        if (!ParasyticLouseTenancyRules.attachCommits(distanceSquared, sighted, tenancy.hazardActive)) {
            louse.louseCounters().markAborts++;
            tenancy.mark = tenancy.mark.cancel();
            tenancy.phase = Phase.SEEK;
            return;
        }
        if (occupied(louse, level, candidate)) {
            tenancy.mark = tenancy.mark.cancel();
            endTenancy(louse, tenancy, EvictReason.OCCUPIED);
            return;
        }
        tenancy.mark = tenancy.mark.endExpired();
        tenancy.residenceRemainingTicks = ParasyticLouseTenancyRules.RESIDENCE_TERM_TICKS;
        tenancy.phase = Phase.FEED;
        tenancy.feed = tenancy.feed.trigger();
        louse.louseCounters().attachCommits++;
    }

    /**
     * At most one louse may be attached to a given host. The probe is one bounded abortable visit
     * over the host's own box, charged per raw entity before any filter, and a denied attach never
     * leaves a partial attachment behind.
     */
    private static boolean occupied(
        final ParasyticLouseEntity louse,
        final ServerLevel level,
        final LivingEntity candidate
    ) {
        louse.louseCounters().occupancyProbes++;
        final ReadBudget visits = ReadBudget.of(ParasyticLouseTenancyRules.MAX_OCCUPANCY_VISITS);
        final UUID hostId = candidate.getUUID();
        final boolean[] taken = {false};
        level.getEntities().get(
            EntityTypeTest.forClass(ParasyticLouseEntity.class),
            candidate.getBoundingBox().inflate(ParasyticLouseTenancyRules.OCCUPANCY_PROBE_INFLATION),
            other -> {
                if (!visits.charge()) {
                    return AbortableIterationConsumer.Continuation.ABORT;
                }
                louse.louseCounters().occupancyRawVisits++;
                if (other != louse
                    && other.tenancy().phase == Phase.FEED
                    && other.tenancy().host.id().filter(hostId::equals).isPresent()) {
                    taken[0] = true;
                    return AbortableIterationConsumer.Continuation.ABORT;
                }
                return visits.exhausted()
                    ? AbortableIterationConsumer.Continuation.ABORT
                    : AbortableIterationConsumer.Continuation.CONTINUE;
            }
        );
        return taken[0];
    }

    /**
     * The feed. At most one ordinary attributed melee attempt per forty loaded ticks, only in
     * contact, and the ladder rises only on a hit whose effective health plus absorption strictly
     * decreased. The cadence rather than a new damage number is what separates a feeding parasite
     * from a brawler.
     */
    private static void tickFeed(
        final ParasyticLouseEntity louse,
        final ServerLevel level,
        final Tenancy tenancy
    ) {
        final Optional<LivingEntity> resolved = resolveHost(louse, level, tenancy);
        if (releaseIfRequired(louse, level, tenancy, resolved, false)) {
            return;
        }
        final LivingEntity host = resolved.orElseThrow();
        louse.getLookControl().setLookAt(host, 30.0F, 30.0F);
        observeSight(louse, tenancy, host);
        final double distanceSquared = louse.distanceToSqr(host);
        if (!ParasyticLouseTenancyRules.feedAllowed(distanceSquared, tenancy.hazardActive)) {
            if (louse.getNavigation().isDone()) {
                routeToHost(louse, tenancy, host);
            }
            return;
        }
        if (!tenancy.feed.due()) {
            return;
        }
        tenancy.feed = tenancy.feed.arm();
        louse.louseCounters().feedAttempts++;
        final float before = host.getHealth() + host.getAbsorptionAmount();
        final boolean hurt = louse.doHurtTarget(level, host);
        final float after = host.getHealth() + host.getAbsorptionAmount();
        if (!ParasyticLouseTenancyRules.effectiveFeed(hurt, before, after)) {
            louse.louseCounters().feedRejected++;
            return;
        }
        louse.louseCounters().feedAccepted++;
        louse.louseCounters().nourishmentIncrements++;
        final int nourishment = ParasyticLouseTenancyRules.nourishmentAfter(
            louse.louseState().nourishment(), true
        );
        louse.setLouseState(louse.louseState().withNourishment(nourishment));
        if (!ParasyticLouseTenancyRules.satiated(nourishment)) {
            return;
        }
        louse.louseCounters().satiations++;
        deliverPayload(louse, level, host, DeliveryRoute.SATIATION);
        endTenancy(louse, tenancy, EvictReason.SATED);
    }

    // ---------------------------------------------------------------- retention and unwind

    /**
     * The one retention decision, evaluated at most every twenty loaded ticks and never per tick.
     * A resolved host that has become illegal, dead, distant, unsighted, asleep, trading, breeding,
     * raiding or panicking leaves through the same unwind as satiation.
     */
    private static boolean releaseIfRequired(
        final ParasyticLouseEntity louse,
        final ServerLevel level,
        final Tenancy tenancy,
        final Optional<LivingEntity> resolved,
        final boolean candidateOnly
    ) {
        if (resolved.isEmpty()) {
            endTenancy(louse, tenancy, EvictReason.HOST_UNLOADED);
            return true;
        }
        if (!tenancy.evict.due()) {
            return false;
        }
        tenancy.evict = tenancy.evict.arm();
        louse.louseCounters().evictEvaluations++;
        final HostFacts facts = observeHost(louse, level, tenancy, resolved.orElseThrow());
        final TenancyFacts tenancyFacts = new TenancyFacts(
            true,
            tenancy.residenceRemainingTicks,
            tenancy.continuousSightLossTicks,
            tenancy.route.consecutiveFailures()
        );
        final Optional<EvictReason> reason = candidateOnly
            ? ParasyticLouseTenancyRules.candidateReleaseReason(facts, tenancyFacts)
            : ParasyticLouseTenancyRules.evictReason(facts, tenancyFacts);
        if (reason.isEmpty()) {
            return false;
        }
        endTenancy(louse, tenancy, reason.orElseThrow());
        return true;
    }

    /**
     * The single unwind, and the only exit from a tenancy. Both endings pass through it, so nothing
     * pending can land after a cancellation: the unwind is the last step of every path.
     *
     * <p>Durable nourishment and the decay remainder are preserved and never replayed. The seek
     * cooldown is armed here rather than by any caller, and the released host is remembered for a
     * bounded grace so the same host cannot be re-taken on the following tick.</p>
     */
    static void endTenancy(
        final ParasyticLouseEntity louse,
        final Tenancy tenancy,
        final EvictReason reason
    ) {
        louse.louseCounters().evicts[reason.ordinal()]++;
        louse.louseCounters().detaches++;
        if (tenancy.host.present()) {
            tenancy.releasedHost = tenancy.host;
            tenancy.releasedGraceTicks = ParasyticLouseTenancyRules.RELEASED_HOST_GRACE_TICKS;
        }
        tenancy.host = Tenancy.Host.none();
        tenancy.mark = tenancy.mark.cancel();
        tenancy.residenceRemainingTicks = 0;
        tenancy.continuousSightLossTicks = 0;
        tenancy.hostSighted = false;
        tenancy.phase = Phase.FREE;
        // An open backoff survives the unwind for the same reason it survives a tenancy start: it
        // describes the terrain, not the tenancy.
        tenancy.route = new RouteRequest(
            Cadence.every(ParasyticLouseTenancyRules.PATH_CADENCE_TICKS),
            0,
            tenancy.route.backoffRemaining()
        );
        louse.setLouseState(louse.louseState().withSeekCooldown(
            ParasyticLouseTenancyRules.SEEK_COOLDOWN_TICKS
        ));
        louse.getNavigation().stop();
        louse.setTarget(null);
        louse.getMoveControl().setWait();
        final Vec3 velocity = louse.getDeltaMovement();
        louse.setDeltaMovement(0.0D, velocity.y, 0.0D);
    }

    // ---------------------------------------------------------------- the redirect route

    /**
     * The preserved owner-facing delivery, now bounded on every axis it was unbounded on: sixteen
     * blocks from the louse to its owner, sixteen from the owner to the attacker, one unobstructed
     * sight trace, the mod's own forty-tick attribution freshness, one delivery and the shared
     * ceiling. It writes no navigation, changes no state and never ends a tenancy.
     */
    private static void tickRedirect(
        final ParasyticLouseEntity louse,
        final ServerLevel level,
        final Tenancy tenancy
    ) {
        if (tenancy.phase == Phase.ESCAPE || !tenancy.redirect.due()) {
            return;
        }
        tenancy.redirect = tenancy.redirect.arm();
        louse.louseCounters().redirectEvaluations++;
        final Optional<CreatureBehaviorState.StoredEffect> payload =
            CreatureBehaviorState.storedEffect(louse);
        final Optional<Player> owner = CreatureBehaviorState.owner(louse)
            .map(level::getEntity)
            .filter(Player.class::isInstance)
            .map(Player.class::cast)
            .filter(LivingEntity::isAlive);
        final Optional<LivingEntity> attacker = owner
            .map(LivingEntity::getLastHurtByMob)
            .filter(LivingEntity::isAlive)
            .filter(candidate -> candidate.level() == level);
        final boolean sighted = payload.isPresent() && attacker.isPresent()
            && chargeRedirectSight(louse, attacker.orElseThrow());
        final RedirectFacts facts = new RedirectFacts(
            payload.isPresent(),
            owner.isPresent(),
            owner.map(louse::distanceToSqr).orElse(Double.MAX_VALUE),
            owner.map(player -> armorContains(player, CreatureBehaviorTags.Items.LOUSE_REDIRECTING_ARMOR))
                .orElse(false),
            attacker.isPresent(),
            owner.flatMap(player -> attacker.map(player::distanceToSqr)).orElse(Double.MAX_VALUE),
            owner.map(player -> player.tickCount - player.getLastHurtByMobTimestamp())
                .orElse(Integer.MAX_VALUE),
            sighted
        );
        final Optional<RedirectRejection> rejection =
            ParasyticLouseTenancyRules.redirectRejection(facts);
        if (rejection.isPresent()) {
            louse.louseCounters().redirectRejections[rejection.orElseThrow().ordinal()]++;
            return;
        }
        deliverPayload(louse, level, attacker.orElseThrow(), DeliveryRoute.REDIRECT);
    }

    private static boolean chargeRedirectSight(
        final ParasyticLouseEntity louse,
        final LivingEntity attacker
    ) {
        louse.louseCounters().redirectSightRays++;
        return louse.getSensing().hasLineOfSight(attacker);
    }

    /**
     * The one delivery, shared by both routes and clamped by the one ceiling. Recorded and cleared
     * before the effect is applied, so no reentrant handler can produce a second delivery of the
     * same payload.
     */
    private static void deliverPayload(
        final ParasyticLouseEntity louse,
        final ServerLevel level,
        final LivingEntity subject,
        final DeliveryRoute route
    ) {
        final Optional<CreatureBehaviorState.StoredEffect> stored =
            CreatureBehaviorState.storedEffect(louse);
        if (stored.isEmpty()) {
            return;
        }
        final CreatureBehaviorState.StoredEffect payload = stored.orElseThrow();
        louse.louseCounters().deliveries[route.ordinal()]++;
        louse.louseCounters().payloadsCleared++;
        if (ParasyticLouseTenancyRules.payloadClamped(payload.durationTicks())) {
            louse.louseCounters().payloadCeilingClamps++;
        }
        CreatureBehaviorState.clearStoredEffect(louse);
        BuiltInRegistries.MOB_EFFECT.get(payload.effectId()).ifPresent(effect ->
            subject.addEffect(new MobEffectInstance(
                effect,
                ParasyticLouseTenancyRules.payloadDuration(payload.durationTicks()),
                payload.amplifier()
            ))
        );
        level.playSound(null, louse.getX(), louse.getY(), louse.getZ(),
            SoundEvents.SPIDER_HURT, louse.getSoundSource(), 0.5F, 1.4F);
        louse.louseCounters().sounds++;
    }

    // ---------------------------------------------------------------- perception helpers

    static String dimensionOf(final ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    private static Optional<LivingEntity> resolveHost(
        final ParasyticLouseEntity louse,
        final ServerLevel level,
        final Tenancy tenancy
    ) {
        if (!tenancy.host.present()
            || !tenancy.host.dimension().filter(dimensionOf(level)::equals).isPresent()) {
            return Optional.empty();
        }
        return tenancy.host.id()
            .map(level::getEntity)
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast)
            .filter(host -> host != louse);
    }

    /**
     * One sight trace at most every ten loaded ticks while a host is bound, accumulating continuous
     * loss rather than reacting to a single obstructed tick.
     */
    private static boolean observeSight(
        final ParasyticLouseEntity louse,
        final Tenancy tenancy,
        final LivingEntity host
    ) {
        if (!tenancy.sight.due()) {
            return tenancy.hostSighted;
        }
        tenancy.sight = tenancy.sight.arm();
        louse.louseCounters().hostSightRays++;
        tenancy.hostSighted = louse.getSensing().hasLineOfSight(host);
        tenancy.continuousSightLossTicks = tenancy.hostSighted
            ? 0
            : tenancy.continuousSightLossTicks + ParasyticLouseTenancyRules.SIGHT_CHECK_CADENCE_TICKS;
        return tenancy.hostSighted;
    }

    /** Every world fact the pure eligibility and retention rules are allowed to see, and no more. */
    private static HostFacts observeHost(
        final ParasyticLouseEntity louse,
        final ServerLevel level,
        final Tenancy tenancy,
        final LivingEntity candidate
    ) {
        final boolean owner = CreatureBehaviorState.owner(louse)
            .filter(candidate.getUUID()::equals)
            .isPresent();
        final boolean inGrace = tenancy.releasedHost.id()
            .filter(candidate.getUUID()::equals)
            .isPresent();
        return new HostFacts(
            candidate.isAlive() && !candidate.isRemoved(),
            candidate.level() == level,
            tenancy.host.dimension().map(dimensionOf(level)::equals).orElse(true),
            candidate == louse,
            candidate instanceof ParasyticLouseEntity,
            owner,
            inGrace,
            candidate.getType().builtInRegistryHolder().is(WarlockeryTags.EntityTypes.DISEASE_IMMUNE),
            candidate instanceof Player player && (player.isCreative() || player.isSpectator()),
            candidate.isSleeping(),
            candidate instanceof AbstractVillager villager && villager.isTrading(),
            candidate instanceof Animal animal && animal.isInLove(),
            candidate instanceof Raider raider && raider.getCurrentRaid() != null,
            candidate instanceof Mob mob
                && mob.getBrain().hasMemoryValue(MemoryModuleType.IS_PANICKING),
            louse.distanceToSqr(candidate)
        );
    }

    private static boolean armorContains(
        final LivingEntity entity,
        final TagKey<net.minecraft.world.item.Item> tag
    ) {
        for (final EquipmentSlot slot : ARMOR_SLOTS) {
            if (entity.getItemBySlot(slot).is(tag)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- movement plumbing

    private static void routeToHost(
        final ParasyticLouseEntity louse,
        final Tenancy tenancy,
        final LivingEntity host
    ) {
        if (!tenancy.route.mayRequest()) {
            return;
        }
        louse.louseCounters().pathRequests++;
        final Path path = louse.getNavigation().createPath(host, 0);
        final boolean reachable = path != null && path.canReach();
        final boolean accepted = reachable
            && louse.getNavigation().moveTo(path, ParasyticLouseTenancyRules.SEEK_SPEED);
        recordRoute(louse, tenancy, RouteRequest.outcomeOf(path != null, reachable, accepted));
    }

    private static void requestPath(
        final ParasyticLouseEntity louse,
        final Tenancy tenancy,
        final BlockPos destination,
        final double speed
    ) {
        louse.louseCounters().pathRequests++;
        final Path path = louse.getNavigation().createPath(destination, 0);
        final boolean reachable = path != null && path.canReach();
        final boolean accepted = reachable && louse.getNavigation().moveTo(path, speed);
        recordRoute(louse, tenancy, RouteRequest.outcomeOf(path != null, reachable, accepted));
    }

    private static void failRoute(final ParasyticLouseEntity louse, final Tenancy tenancy) {
        recordRoute(louse, tenancy, RouteRequest.Outcome.NO_CANDIDATE);
    }

    private static void recordRoute(
        final ParasyticLouseEntity louse,
        final Tenancy tenancy,
        final RouteRequest.Outcome outcome
    ) {
        if (outcome.accepted()) {
            tenancy.route = tenancy.route.succeeded();
            return;
        }
        louse.louseCounters().pathFailures++;
        final int backoffBefore = tenancy.route.backoffRemaining();
        tenancy.route = tenancy.route.failed(ParasyticLouseTenancyRules.ROUTE_BACKOFF);
        if (backoffBefore == 0 && tenancy.route.backoffRemaining() > 0) {
            louse.louseCounters().pathBackoffs++;
            louse.getNavigation().stop();
        }
    }

    // ---------------------------------------------------------------- entity-facing hooks

    /**
     * Called from {@link ParasyticLouseEntity#hurtServer} only when the damage was accepted and
     * actually reduced health plus absorption. A truthy call with no effective positive loss mints
     * no attribution and no withdrawal.
     *
     * <p>A louse never retaliates. The whole response is to let go and move away, which is why the
     * only thing this method starts is a withdrawal and the {@code retaliations} counter has no
     * increment site anywhere in this file.</p>
     */
    public static void recordAcceptedDamage(
        final ParasyticLouseEntity louse,
        final ServerLevel level,
        final DamageSource source
    ) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == louse) {
            return;
        }
        final Tenancy tenancy = louse.tenancy();
        louse.louseCounters().attackerAttributions++;
        tenancy.attacker = Optional.of(attacker.getUUID());
        tenancy.attackerFreshnessTicks = ParasyticLouseTenancyRules.ATTRIBUTION_FRESHNESS_TICKS;
        if (tenancy.phase != Phase.FREE && tenancy.phase != Phase.ESCAPE) {
            endTenancy(louse, tenancy, EvictReason.ATTACKED);
        }
        tenancy.withdrawalTicks = ParasyticLouseTenancyRules.WITHDRAWAL_TICKS;
        louse.louseCounters().withdrawals++;
        if (level.isClientSide()) {
            return;
        }
        louse.setTarget(null);
    }

    /**
     * The preserved interaction, moved onto the dedicated body unchanged in every player-facing
     * particular: an empty hand converts the louse into the {@code warlockery:louse} item carrying
     * its payload and sends the exact message key, and a potion binds the owner, stores the first
     * effect with the same floors, consumes the potion and returns a glass bottle.
     *
     * <p>The empty-hand capture is deliberately available in every state, including mid-feed, and to
     * any player rather than only the host or the owner. It is the cheap, repeatable, no-combat
     * counter, and it costs nothing and consumes nothing.</p>
     */
    public static InteractionResult interact(
        final ParasyticLouseEntity louse,
        final Player player,
        final InteractionHand hand
    ) {
        if (!(louse.level() instanceof ServerLevel)) {
            return InteractionResult.PASS;
        }
        final ItemStack held = player.getItemInHand(hand);
        final PotionContents potion =
            held.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        final var effects = potion.getAllEffects().iterator();
        if (!effects.hasNext()) {
            if (!held.isEmpty()) {
                return InteractionResult.PASS;
            }
            return captureByHand(louse, player);
        }
        final MobEffectInstance captured = effects.next();
        CreatureBehaviorState.bind(louse, player.getUUID());
        CreatureBehaviorState.storeEffect(louse, new CreatureBehaviorState.StoredEffect(
            BuiltInRegistries.MOB_EFFECT.getKey(captured.getEffect().value()),
            Math.max(20, captured.getDuration()),
            Math.max(0, captured.getAmplifier())
        ));
        if (!player.hasInfiniteMaterials()) {
            held.shrink(1);
            player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
        }
        louse.louseCounters().capturesByPotion++;
        player.sendSystemMessage(Component.translatable(
            "message.warlockery.creature.effect_stored", louse.getDisplayName()
        ));
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult captureByHand(
        final ParasyticLouseEntity louse,
        final Player player
    ) {
        final ItemStack captured = new ItemStack(ModItems.ALL.get("louse").get());
        ParasyticLouseItem.writeFromCreature(captured, louse);
        if (!player.getInventory().add(captured)) {
            player.drop(captured, false);
        }
        louse.louseCounters().capturesByHand++;
        // Full teardown before the entity leaves, so nothing is left written on the former host and
        // no pending mark, delivery, sound or particle can survive the capture.
        louse.tenancy().clearAll();
        louse.discard();
        player.sendSystemMessage(Component.translatable("message.warlockery.louse.captured"));
        return InteractionResult.SUCCESS;
    }

    /**
     * The absolute relation gate, called from {@link ParasyticLouseEntity#canAttack}. A louse is only
     * ever in a position to damage the one host its own runtime bound, so every other entity in the
     * world, including a player standing on top of it, is not attackable by any path.
     */
    public static boolean legalHost(final ParasyticLouseEntity louse, final LivingEntity target) {
        return louse.tenancy().phase == Phase.FEED
            && louse.tenancy().host.id().filter(target.getUUID()::equals).isPresent()
            && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target);
    }
}
