package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.MimicryRules.Act;
import com.kadamitas.warlockery.entity.MimicryRules.Candidate;
import com.kadamitas.warlockery.entity.MimicryRules.Decision;
import com.kadamitas.warlockery.entity.MimicryRules.Facts;
import com.kadamitas.warlockery.entity.MimicryRules.Phase;
import com.kadamitas.warlockery.entity.MimicryRules.Quota;
import com.kadamitas.warlockery.entity.MimicryRules.Species;
import com.kadamitas.warlockery.entity.behavior.Cadence;
import com.kadamitas.warlockery.entity.behavior.ReadBudget;
import com.kadamitas.warlockery.entity.behavior.RouteRequest;
import com.kadamitas.warlockery.entity.behavior.ScanEnvelope;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The single server-side tick authority and the sole navigation writer for all four mimics.
 *
 * <p>One runtime rather than four is the whole anti-duplication device. The species-specific work
 * is confined to {@link #execute} arms that {@link MimicryRules#permits} already partitions, and
 * every mechanism that four copies would have drifted apart on lives here exactly once: the load
 * reconcile, the loaded-tick clocks, the priority ladder, the charged bounded perception, the
 * route gate and its failure policy, the per-level quota, the teardown, and the episode ledger
 * reset that preserves an open route backoff.</p>
 *
 * <p>Reached from {@code customServerAiStep} on each of the four dedicated bodies and from nowhere
 * else. No mimic delegates any seam to {@link CreatureBehaviorRuntime}.</p>
 */
public final class MimicryRuntime {

    static final TagKey<Block> CONTACT_HAZARDS = TagKey.create(
        Registries.BLOCK,
        Identifier.fromNamespaceAndPath("warlockery", "ai/contact_hazards")
    );

    /** The escape destination envelope, evaluated centre out so the far shell is never skipped. */
    private static final ScanEnvelope ESCAPE_ENVELOPE = ScanEnvelope.of(
        MimicryRules.ESCAPE_HORIZONTAL_RADIUS, MimicryRules.ESCAPE_VERTICAL_RADIUS
    );

    private static final Map<ServerLevel, Quota> QUOTAS = new WeakHashMap<>();

    private MimicryRuntime() {
    }

    /** What a dedicated mimic body must expose. Deliberately not a base class. */
    public interface MimicBody {
        Mob body();

        Core mimicCore();

        Species mimicSpecies();

        Optional<UUID> reflectedTargetIdentity();
    }

    // ---------------------------------------------------------------- counters

    /** Pass-local structural counters. Never saved, never synced, never gameplay affecting. */
    public static final class Counters {
        public long aiTicks;
        public long checks;
        public long checksWithoutCandidate;
        public long rawVisits;
        public long sightRays;
        public long bindings;
        public long episodeStarts;
        public long episodeEnds;
        public long pathRequests;
        public long pathsAccepted;
        public long pathFailures;
        public long telegraphs;
        public long collapses;
        public long snareApplications;
        public long snareRemovals;
        public long snareRemovalGuardMisses;
        public long stations;
        public long draws;
        public long absorbs;
        public long unmasks;
        public long withdrawals;
        public long recognitions;
        public long confrontations;
        public long meleeAttempts;
        public long attributions;
        public long attributionRejections;
        public long hazardEscapes;
        public long hazardReads;
        public long hazardContactReads;
        public long feedbackEmitted;
        public long feedbackSuppressed;
        public long tokensDeferred;
        public long foreignEntityWrites;
        public long cancellations;

    }

    // ---------------------------------------------------------------- transient state

    /**
     * Everything the runtime owns and never writes to disk.
     *
     * <p>The bound-subject reconcile in {@link #reconcileBinding()} is the <em>identity</em> shape:
     * when the bound subject is gone, everything derived from it is meaningless and is cleared with
     * it. It is deliberately not the timer shape. Nothing here says "if a timer reached zero, zero
     * its dependents", because that is the reconcile that decides a phase ended in the wrong place
     * and leaves the tick branch that owned the ending, and its cooldown, unreached.</p>
     */
    public static final class TransientState {
        Phase phase = Phase.ESCAPE;
        int phaseTicks;
        int episodeTicks;
        @Nullable UUID bound;
        int unseenTicks;
        boolean boundVisible;
        int sightTestTicks;
        @Nullable UUID attacker;
        int attributionTicks;
        int acceptedHits;
        int facingDwellTicks;
        int recognition;
        boolean recognitionReached;
        boolean snareApplied;
        boolean presentationDerived;
        MimicryPresentation.Stance stance = MimicryPresentation.Stance.STILL;
        int attackRecoveryTicks;
        boolean hazardActive;
        int escapeCursor;
        boolean staggered;
        @Nullable ResourceKey<Level> lastDimension;
        @Nullable Vec3 lastPosition;
        Cadence check = MimicryRules.checkCadence();
        RouteRequest route = MimicryRules.routeRequest();

        void resetForLoad(final Species species) {
            phase = species.routine();
            phaseTicks = 0;
            episodeTicks = 0;
            bound = null;
            unseenTicks = 0;
            boundVisible = false;
            sightTestTicks = 0;
            attacker = null;
            attributionTicks = 0;
            acceptedHits = 0;
            facingDwellTicks = 0;
            recognition = 0;
            recognitionReached = false;
            snareApplied = false;
            presentationDerived = false;
            stance = MimicryPresentation.Stance.STILL;
            attackRecoveryTicks = 0;
            hazardActive = false;
            escapeCursor = 0;
            staggered = false;
            lastDimension = null;
            lastPosition = null;
            check = MimicryRules.checkCadence();
            route = MimicryRules.routeRequest();
        }

        /**
         * The episode ledger reset. Every accumulator that belongs to one episode is cleared at the
         * episode boundary, so nothing accumulated while idle can be inherited and released by the
         * next episode before it has done anything. The open route backoff is deliberately
         * preserved: a fresh episode does not earn the right to re-request a route that has just
         * failed three times.
         */
        void beginEpisode() {
            episodeTicks = 0;
            unseenTicks = 0;
            sightTestTicks = 0;
            acceptedHits = 0;
            facingDwellTicks = 0;
            recognition = 0;
            recognitionReached = false;
            snareApplied = false;
            presentationDerived = false;
            attackRecoveryTicks = 0;
            route = new RouteRequest(
                route.cadence(), 0, route.backoffRemaining()
            );
        }

        /** The identity reconcile: no subject means nothing derived from a subject survives. */
        void reconcileBinding() {
            if (bound != null) {
                return;
            }
            unseenTicks = 0;
            boundVisible = false;
            facingDwellTicks = 0;
            recognition = 0;
            recognitionReached = false;
            stance = MimicryPresentation.Stance.STILL;
            presentationDerived = false;
        }

        void clearBinding() {
            bound = null;
            reconcileBinding();
        }

        public Phase phase() {
            return phase;
        }

        public int recognition() {
            return recognition;
        }

        public int acceptedHits() {
            return acceptedHits;
        }

        public MimicryPresentation.Stance stance() {
            return stance;
        }

        public Optional<UUID> boundSubject() {
            return Optional.ofNullable(bound);
        }

        public int routeFailures() {
            return route.consecutiveFailures();
        }

        public int routeBackoff() {
            return route.backoffRemaining();
        }

        /** Fixture seam: makes every cadence due at once so a live test need not idle for a period. */
        public void makeEveryCadenceDue() {
            check = check.trigger();
            route = new RouteRequest(route.cadence().trigger(), route.consecutiveFailures(), 0);
        }
    }

    /** The one mutable holder a mimic body owns. Constructed with the body's own species. */
    public static final class Core {
        private final Counters counters = new Counters();
        private final TransientState scratch = new TransientState();
        private MimicryState state;

        public Core(final Species species) {
            this.state = MimicryState.empty(Objects.requireNonNull(species, "species"));
            this.scratch.resetForLoad(species);
        }

        public Counters counters() {
            return counters;
        }

        public TransientState scratch() {
            return scratch;
        }

        public MimicryState state() {
            return state;
        }

        public void setState(final MimicryState updated) {
            state = updated == null ? MimicryState.empty(scratchSpecies()) : updated;
        }

        private Species scratchSpecies() {
            return state == null ? Species.HOLLOW_FUSE : state.species();
        }
    }

    // ---------------------------------------------------------------- entry point

    /**
     * The one per-tick entry, reached from every dedicated mimic body's {@code customServerAiStep}.
     */
    public static void tick(final MimicBody mimic, final ServerLevel level) {
        Objects.requireNonNull(mimic, "mimic");
        Objects.requireNonNull(level, "level");
        final Mob body = mimic.body();
        final Species species = mimic.mimicSpecies();
        if (body == null || species == null) {
            return;
        }
        final Core core = mimic.mimicCore();
        final Counters counters = core.counters();
        final TransientState scratch = core.scratch();

        if (scratch.lastDimension != null && scratch.lastDimension != level.dimension()) {
            cancel(mimic);
        } else if (scratch.lastPosition != null
            && (MimicryRules.inEpisode(species, scratch.phase)
                || scratch.bound != null || scratch.attacker != null)
            && scratch.lastPosition.distanceToSqr(body.position()) > 64.0D) {
            cancel(mimic);
        }
        scratch.lastDimension = level.dimension();
        scratch.lastPosition = body.position();
        if (!body.isAlive() || body.isNoAi()) {
            return;
        }
        counters.aiTicks++;

        if (!scratch.staggered) {
            // DC6. Seeded once from the body's own identity so a group of mimics placed together
            // never checks on the same tick, and never from absolute world time.
            scratch.staggered = true;
            scratch.check = new Cadence(
                MimicryRules.CHECK_CADENCE_TICKS,
                MimicryRules.stagger(body.getUUID(), MimicryRules.CHECK_CADENCE_TICKS)
            );
        }
        advanceLoadedClocks(core);
        scratch.hazardActive = hazardous(body, level, core.counters());

        final boolean checkDue = scratch.check.due()
            && core.state().episodeAllowed()
            && !scratch.hazardActive
            && (species != Species.PRESENTED_LIKENESS
                || mimic.reflectedTargetIdentity().isPresent())
            && (scratch.phase == species.routine() || scratch.phase == Phase.STATION);
        boolean candidateFound = false;
        if (checkDue) {
            candidateFound = runBoundedCheck(mimic, level, species, core);
        }

        final Facts facts = observe(mimic, level, species, core, candidateFound);
        final Decision decision = MimicryRules.next(facts);
        applyPhase(mimic, level, species, core, decision.phase());
        decision.act().ifPresent(act -> {
            if (MimicryRules.permits(species, act)) {
                execute(mimic, level, species, core, act);
            }
        });
    }

    private static void advanceLoadedClocks(final Core core) {
        core.setState(core.state().tickLoaded());
        final TransientState scratch = core.scratch();
        scratch.phaseTicks++;
        scratch.episodeTicks++;
        scratch.check = scratch.check.step();
        scratch.route = scratch.route.step();
        scratch.attributionTicks = MimicryRules.decrementLoaded(scratch.attributionTicks);
        scratch.attackRecoveryTicks = MimicryRules.decrementLoaded(scratch.attackRecoveryTicks);
        scratch.sightTestTicks = MimicryRules.decrementLoaded(scratch.sightTestTicks);
        if (scratch.attributionTicks <= 0) {
            scratch.attacker = null;
        }
    }

    private static boolean hazardous(final Mob body, final ServerLevel level, final Counters counters) {
        counters.hazardContactReads++;
        final boolean contact = level.getBlockState(body.blockPosition()).is(CONTACT_HAZARDS);
        return body.isOnFire() || body.isInLava() || contact;
    }

    // ---------------------------------------------------------------- perception

    /**
     * One bounded, charged check. Every raw entity visit spends the read allowance <em>before</em>
     * any eligibility or visibility filter may reject it, so a rejected candidate can never cost a
     * real world read and be charged nothing.
     *
     * <p>The cadence is armed whatever the check found, including nothing at all, so a check that
     * qualifies no candidate cannot repeat on the very next tick forever.</p>
     */
    private static boolean runBoundedCheck(
        final MimicBody mimic,
        final ServerLevel level,
        final Species species,
        final Core core
    ) {
        final Mob body = mimic.body();
        final Counters counters = core.counters();
        final TransientState scratch = core.scratch();
        counters.checks++;
        scratch.check = scratch.check.arm();

        if (!grantToken(level, counters)) {
            counters.checksWithoutCandidate++;
            return false;
        }

        final AABB bounds = body.getBoundingBox().inflate(species.bindRadius());
        if (species == Species.PRESENTED_LIKENESS) {
            final Entity preferred = mimic.reflectedTargetIdentity().map(level::getEntity).orElse(null);
            if (preferred instanceof LivingEntity living && bounds.contains(living.position())
                && eligible(body, living, false)) {
                counters.rawVisits++;
                if (sight(mimic, core, living)) {
                    scratch.bound = living.getUUID();
                    scratch.boundVisible = true;
                    scratch.unseenTicks = 0;
                    counters.bindings++;
                    return true;
                }
            }
            counters.checksWithoutCandidate++;
            return false;
        }

        if (!reserveVisits(level, counters)) {
            counters.checksWithoutCandidate++;
            return false;
        }
        final ReadBudget budget = ReadBudget.of(MimicryRules.MAX_RAW_VISITS_PER_CHECK);
        final List<Candidate> inspected = new ArrayList<>(MimicryRules.MAX_RAW_VISITS_PER_CHECK);
        final boolean anchorSearch = species == Species.HOLLOW_DECOY && scratch.bound == null;
        com.kadamitas.warlockery.entity.BoundedEntityQuery.visit(level, EntityTypeTest.forClass(LivingEntity.class), bounds, found -> {
            if (!budget.charge()) {
                return AbortableIterationConsumer.Continuation.ABORT;
            }
            counters.rawVisits++;
            final boolean eligible = eligible(body, found, anchorSearch);
            final boolean visible = eligible && sight(mimic, core, found);
            inspected.add(new Candidate(
                found.getUUID(), eligible, visible, body.distanceToSqr(found), true
            ));
            return budget.exhausted()
                ? AbortableIterationConsumer.Continuation.ABORT
                : AbortableIterationConsumer.Continuation.CONTINUE;
        });

        final Optional<Candidate> bound = MimicryRules.bind(inspected, species.bindRadiusSquared());
        if (bound.isEmpty()) {
            counters.checksWithoutCandidate++;
            return false;
        }
        scratch.bound = bound.get().identity();
        scratch.boundVisible = true;
        scratch.unseenTicks = 0;
        counters.bindings++;
        return true;
    }

    /**
     * The shared exclusion list. No mimic ever binds another mimic, a creative or spectator player,
     * a sleeping player, a trading villager, a mob in love, or an entity on another level.
     */
    private static boolean eligible(final Mob body, final LivingEntity found, final boolean hostileAnchor) {
        if (found == body || !found.isAlive() || found.isRemoved() || found.level() != body.level()) {
            return false;
        }
        if (found instanceof MimicBody) {
            return false;
        }
        if (found instanceof Player player
            && (player.isCreative() || player.isSpectator() || player.isSleeping())) {
            return false;
        }
        if (found instanceof AbstractVillager villager && villager.getTradingPlayer() != null) {
            return false;
        }
        if (found instanceof Villager villager
            && (villager.getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET)
                || villager.getBrain().hasMemoryValue(MemoryModuleType.IS_PANICKING)
                || villager.getBrain().isActive(Activity.PANIC))) {
            return false;
        }
        if (found instanceof Raider raider && raider.getCurrentRaid() != null) {
            return false;
        }
        if (found instanceof Animal animal && animal.isInLove()) {
            return false;
        }
        if (hostileAnchor) {
            // The Hollow Decoy stations beside an existing hostile mob and reads nothing else from
            // it. A player is never an anchor, and the anchor is never written to.
            return found instanceof Mob mob && mob.getType().getCategory() == MobCategory.MONSTER;
        }
        return true;
    }

    private static boolean sight(final MimicBody mimic, final Core core, final LivingEntity target) {
        final Counters counters = core.counters();
        if (counters.sightRays % MimicryRules.MAX_SIGHT_RAYS == 0
            && !spendRay(mimic.body().level(), counters)) {
            return false;
        }
        counters.sightRays++;
        return mimic.body().getSensing().hasLineOfSight(target);
    }

    private static Facts observe(
        final MimicBody mimic,
        final ServerLevel level,
        final Species species,
        final Core core,
        final boolean candidateFound
    ) {
        final Mob body = mimic.body();
        final TransientState scratch = core.scratch();
        LivingEntity subject = resolveBound(level, scratch);
        if (subject != null && !eligible(body, subject, species == Species.HOLLOW_DECOY)) {
            // Keep the bound identity intact until the central cancellation has released any
            // guarded foreign effect owned by this episode. The teardown then clears every phase,
            // route, navigation and attribution field in one idempotent operation.
            cancel(mimic);
            subject = null;
        }
        boolean present = subject != null;
        double distanceSquared = Double.MAX_VALUE;
        if (present) {
            distanceSquared = body.distanceToSqr(subject);
            if (MimicryRules.due(scratch.sightTestTicks)) {
                scratch.boundVisible = sight(mimic, core, subject);
                scratch.sightTestTicks = MimicryRules.SIGHT_TEST_INTERVAL_TICKS;
            }
            scratch.unseenTicks = scratch.boundVisible ? 0 : scratch.unseenTicks + 1;
            if (scratch.unseenTicks >= species.sightGraceTicks()) {
                cancel(mimic);
                subject = null;
                present = false;
                distanceSquared = Double.MAX_VALUE;
            }
            if (present) {
                scratch.facingDwellTicks = facingUs(body, subject)
                    ? scratch.facingDwellTicks + 1
                    : 0;
                if (species == Species.PRESENTED_LIKENESS && scratch.phase == Phase.SHADOWING) {
                    scratch.recognition = MimicryRules.recognitionAfter(
                        scratch.recognition,
                        scratch.boundVisible && scratch.facingDwellTicks > 0,
                        false,
                        false
                    );
                }
            }
        } else {
            scratch.clearBinding();
        }
        // DC3 read on the subject's own clock. A deliberate act by the snared subject breaks the
        // snare, and this is a read of the subject's own field that mutates nothing.
        final boolean subjectActed = subject != null
            && subject.getLastHurtMob() != null
            && subject.getLastHurtMobTimestamp() > 0
            && MimicryRules.attributionFresh(
                Math.max(0, subject.tickCount - subject.getLastHurtMobTimestamp())
            );
        return new Facts(
            species,
            scratch.phase,
            scratch.phaseTicks,
            scratch.episodeTicks,
            scratch.hazardActive,
            present,
            scratch.boundVisible,
            present ? distanceSquared : 0.0D,
            scratch.attacker != null && scratch.attributionTicks > 0,
            scratch.acceptedHits,
            scratch.facingDwellTicks,
            scratch.recognition,
            core.state().primaryCooldown(),
            candidateFound,
            scratch.route.consecutiveFailures(),
            subjectActed
        );
    }

    private static @Nullable LivingEntity resolveBound(final ServerLevel level, final TransientState scratch) {
        if (scratch.bound == null) {
            return null;
        }
        final Entity found = level.getEntity(scratch.bound);
        return found instanceof LivingEntity living && living.isAlive() && !living.isRemoved()
            ? living
            : null;
    }

    private static boolean facingUs(final Mob body, final LivingEntity observer) {
        final Vec3 toward = body.getEyePosition().subtract(observer.getEyePosition());
        if (toward.lengthSqr() < 1.0E-6D) {
            return false;
        }
        return MimicryRules.facing(observer.getLookAngle().dot(toward.normalize()));
    }

    // ---------------------------------------------------------------- phase transitions

    private static void applyPhase(
        final MimicBody mimic,
        final ServerLevel level,
        final Species species,
        final Core core,
        final Phase next
    ) {
        final TransientState scratch = core.scratch();
        if (scratch.phase == next) {
            return;
        }
        final Phase previous = scratch.phase;
        final boolean wasEpisode = MimicryRules.inEpisode(species, previous);
        final boolean isEpisode = MimicryRules.inEpisode(species, next);

        if (wasEpisode && !isEpisode) {
            endEpisode(mimic, level, species, core, previous);
        }
        scratch.phase = next;
        scratch.phaseTicks = 0;
        if (!wasEpisode && isEpisode) {
            final int openingAcceptedHits = scratch.acceptedHits;
            scratch.beginEpisode();
            if (species == Species.HOLLOW_DECOY && next == Phase.ABSORB) {
                scratch.acceptedHits = openingAcceptedHits;
            }
            core.counters().episodeStarts++;
        }
        if (next == species.routine() || next == species.spent() || next == Phase.ESCAPE) {
            teardown(mimic, core, next != Phase.ESCAPE);
        }
    }

    /**
     * The one place an episode ends. Every ending arms the species cooldown here, so no constructor
     * and no timer clamp anywhere can end an episode without the cooldown that ending implies.
     */
    private static void endEpisode(
        final MimicBody mimic,
        final ServerLevel level,
        final Species species,
        final Core core,
        final Phase from
    ) {
        final Counters counters = core.counters();
        counters.episodeEnds++;
        if (species == Species.THRESHOLD_WEAVER && core.scratch().snareApplied) {
            releaseSnare(mimic, level, core);
        }
        final boolean committed = switch (species) {
            case HOLLOW_FUSE -> from == Phase.COLLAPSE;
            case THRESHOLD_WEAVER -> from == Phase.RESOLVE || from == Phase.SNARE || from == Phase.BREAK;
            case HOLLOW_DECOY -> from == Phase.UNMASK;
            case PRESENTED_LIKENESS -> from == Phase.WITHDRAWING || from == Phase.RECOGNISED
                || from == Phase.CONFRONT;
        };
        core.setState(committed
            ? core.state().withPrimaryCooldown(species.primaryCooldownTicks())
            : core.state().withEpisodeCooldown(MimicryState.EPISODE_COOLDOWN_TICKS));
    }

    /** The single teardown. Navigation stops, velocity zeroes preserving Y, and nothing replays. */
    private static void teardown(final MimicBody mimic, final Core core, final boolean clearBinding) {
        final Mob body = mimic.body();
        body.getNavigation().stop();
        body.getMoveControl().setWait();
        final Vec3 movement = body.getDeltaMovement();
        body.setDeltaMovement(new Vec3(0.0D, movement.y, 0.0D));
        body.setTarget(null);
        final TransientState scratch = core.scratch();
        if (clearBinding) {
            scratch.clearBinding();
        }
    }

    /** Full idempotent teardown for removal and external-lifecycle cancellation. */
    public static void cancel(final MimicBody mimic) {
        final Core core = mimic.mimicCore();
        final Mob body = mimic.body();
        if (core.scratch().snareApplied && body.level() instanceof ServerLevel serverLevel) {
            releaseSnare(mimic, serverLevel, core);
        }
        teardown(mimic, core, true);
        core.counters().cancellations++;
        core.scratch().resetForLoad(mimic.mimicSpecies());
    }

    // ---------------------------------------------------------------- execution

    private static void execute(
        final MimicBody mimic,
        final ServerLevel level,
        final Species species,
        final Core core,
        final Act act
    ) {
        final Mob body = mimic.body();
        final TransientState scratch = core.scratch();
        final Counters counters = core.counters();
        final LivingEntity subject = resolveBound(level, scratch);
        switch (act) {
            case IDLE -> standStill(body);
            case ESCAPE_HAZARD -> escape(mimic, level, core);
            case OBSERVE, THRESHOLD_WATCH, COMPANION_SCAN, LURE_STILL, HOLD_STILL, ABSORB_HIT -> {
                standStill(body);
                if (subject != null) {
                    body.getLookControl().setLookAt(subject, 30.0F, 30.0F);
                }
                if (act == Act.ABSORB_HIT) {
                    counters.absorbs++;
                }
            }
            case APPROACH_OBSERVER -> {
                if (subject != null) {
                    route(mimic, level, core, subject.blockPosition(), MimicryRules.ROUTE_SPEED);
                }
            }
            case TELEGRAPH -> {
                standStill(body);
                if (scratch.phaseTicks == 0) {
                    counters.telegraphs++;
                    feedback(level, core, body, SoundEvents.CREEPER_PRIMED, ParticleTypes.SMOKE);
                }
            }
            case COLLAPSE_QUIETLY -> {
                standStill(body);
                if (scratch.phaseTicks == 0) {
                    counters.collapses++;
                    if (subject != null && scratch.boundVisible) {
                        feedback(level, core, body, SoundEvents.FIRE_EXTINGUISH, ParticleTypes.SMOKE);
                    }
                }
            }
            case RESOLVE_COMMIT -> {
                standStill(body);
                if (scratch.phaseTicks == 0) {
                    feedback(level, core, body, SoundEvents.SPIDER_AMBIENT, ParticleTypes.CRIT);
                }
            }
            case SNARE_HOLD -> {
                standStill(body);
                if (!scratch.snareApplied && subject != null) {
                    applySnare(core, subject);
                }
            }
            case BREAK_SNARE -> {
                standStill(body);
                if (scratch.snareApplied) {
                    releaseSnare(mimic, level, core);
                }
            }
            case TAKE_STATION -> {
                if (subject != null) {
                    final BlockPos station = stationPoint(body, subject);
                    if (body.blockPosition().distSqr(station) <= MimicryRules.DECOY_STATION_ARRIVAL_SQUARED) {
                        standStill(body);
                        counters.stations++;
                    } else {
                        route(mimic, level, core, station, MimicryRules.ROUTE_SPEED);
                    }
                }
            }
            case DRAW_ATTENTION -> {
                standStill(body);
                if (subject != null) {
                    body.getLookControl().setLookAt(subject, 30.0F, 30.0F);
                }
                if (scratch.phaseTicks == 0) {
                    counters.draws++;
                }
            }
            case UNMASK_SELF -> {
                standStill(body);
                if (scratch.phaseTicks == 0) {
                    counters.unmasks++;
                    feedback(level, core, body, SoundEvents.ZOMBIE_AMBIENT, ParticleTypes.SMOKE);
                }
            }
            case BIND_SUBJECT -> {
                standStill(body);
                if (subject != null) {
                    body.getLookControl().setLookAt(subject, 30.0F, 30.0F);
                }
            }
            case SETTLE_PRESENTATION -> {
                standStill(body);
                if (!scratch.presentationDerived) {
                    derivePresentation(body, scratch, subject);
                }
            }
            case HOLD_BAND -> holdBand(mimic, level, core, subject);
            case WITHDRAW -> {
                if (scratch.phase == Phase.RECOGNISED && scratch.phaseTicks == 0) {
                    counters.withdrawals++;
                    if (!scratch.recognitionReached && scratch.recognition >= MimicryRules.RECOGNITION_CERTAIN) {
                        scratch.recognitionReached = true;
                        counters.recognitions++;
                        feedback(level, core, body, SoundEvents.GLASS_BREAK, ParticleTypes.CRIT);
                    }
                }
                withdraw(mimic, level, core, subject);
            }
            case CONFRONT_ATTACKER -> confront(mimic, level, core, subject);
        }
    }

    private static void standStill(final Mob body) {
        body.getNavigation().stop();
        body.getMoveControl().setWait();
        final Vec3 movement = body.getDeltaMovement();
        body.setDeltaMovement(new Vec3(0.0D, movement.y, 0.0D));
    }

    /**
     * The entire copied surface, derived through the closed allow-list and nowhere else. An
     * unresolvable subject yields the coarse fallback, which is never derived from any entity.
     */
    private static void derivePresentation(
        final Mob body,
        final TransientState scratch,
        final @Nullable LivingEntity subject
    ) {
        scratch.presentationDerived = true;
        final MimicryPresentation presentation;
        if (subject == null) {
            presentation = MimicryPresentation.fallback();
        } else {
            final Vec3 movement = subject.getDeltaMovement();
            presentation = new MimicryPresentation(
                MimicryPresentation.presentedNameFor(subject),
                MimicryPresentation.stanceOf(
                    subject.isCrouching(),
                    Math.sqrt(movement.x * movement.x + movement.z * movement.z)
                )
            );
        }
        scratch.stance = presentation.stance();
        if (!body.hasCustomName()) {
            presentation.presentedName().ifPresent(body::setCustomName);
        }
    }

    private static void holdBand(
        final MimicBody mimic,
        final ServerLevel level,
        final Core core,
        final @Nullable LivingEntity subject
    ) {
        final Mob body = mimic.body();
        if (subject == null) {
            standStill(body);
            return;
        }
        body.getLookControl().setLookAt(subject, 30.0F, 30.0F);
        final double distanceSquared = body.distanceToSqr(subject);
        final double distance = Math.sqrt(distanceSquared);
        final MimicryRules.LikenessBand band = MimicryRules.likenessBand(distance);
        if (band == MimicryRules.LikenessBand.HOLD) {
            standStill(body);
            return;
        }
        final Vec3 displacement = body.position().subtract(subject.position());
        final Vec3 away = displacement.lengthSqr() < 1.0E-6D
            ? new Vec3(1.0D, 0.0D, 0.0D) : displacement.normalize();
        final boolean tooFar = band == MimicryRules.LikenessBand.APPROACH
            || band == MimicryRules.LikenessBand.OUTER;
        final Vec3 direction = away;
        final double target = tooFar ? MimicryRules.LIKENESS_BAND_OUTER - 1.0D
            : MimicryRules.LIKENESS_BAND_INNER + 1.0D;
        final Vec3 point = subject.position().add(direction.scale(target));
        route(mimic, level, core, BlockPos.containing(point), MimicryRules.ROUTE_SPEED);
    }

    private static void withdraw(
        final MimicBody mimic,
        final ServerLevel level,
        final Core core,
        final @Nullable LivingEntity subject
    ) {
        final Mob body = mimic.body();
        if (subject == null) {
            standStill(body);
            return;
        }
        final Vec3 away = body.position().subtract(subject.position());
        final Vec3 direction = away.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : away.normalize();
        route(
            mimic, level, core,
            BlockPos.containing(body.position().add(direction.scale(6.0D))),
            MimicryRules.ESCAPE_SPEED
        );
    }

    /**
     * The only place any mimic ever deals damage, and only the Presented Likeness reaches it. The
     * three Illusion Copies never call this and never mint a target, which is why their zero-damage
     * invariant is a structural counter rather than a promise.
     */
    private static void confront(
        final MimicBody mimic,
        final ServerLevel level,
        final Core core,
        final @Nullable LivingEntity subject
    ) {
        final Mob body = mimic.body();
        final Counters counters = core.counters();
        final TransientState scratch = core.scratch();
        final Entity attacker = scratch.attacker == null ? null : level.getEntity(scratch.attacker);
        final LivingEntity target = attacker instanceof LivingEntity living && living.isAlive()
            ? living
            : subject;
        if (target == null) {
            standStill(body);
            return;
        }
        if (scratch.phaseTicks == 0) {
            counters.confrontations++;
        }
        body.getLookControl().setLookAt(target, 30.0F, 30.0F);
        final double reach = body.getBbWidth() * 2.0D + target.getBbWidth();
        if (body.distanceToSqr(target) > reach * reach) {
            route(mimic, level, core, target.blockPosition(), MimicryRules.ROUTE_SPEED);
            return;
        }
        standStill(body);
        if (MimicryRules.due(scratch.attackRecoveryTicks)) {
            scratch.attackRecoveryTicks = MimicryRules.LIKENESS_ATTACK_RECOVERY_TICKS;
            counters.meleeAttempts++;
            body.setTarget(target);
            body.doHurtTarget(level, target);
            body.setTarget(null);
        }
    }

    // ---------------------------------------------------------------- the one foreign mutation

    private static void applySnare(final Core core, final LivingEntity subject) {
        if (subject.hasEffect(MobEffects.SLOWNESS)) {
            return;
        }
        core.scratch().snareApplied = true;
        core.counters().snareApplications++;
        core.counters().foreignEntityWrites++;
        subject.addEffect(new MobEffectInstance(
            MobEffects.SLOWNESS,
            MimicryRules.WEAVER_SNARE_DURATION_TICKS,
            MimicryRules.WEAVER_SNARE_AMPLIFIER
        ));
    }

    /**
     * The guarded removal. The weaver removes only an instance it can prove is the one it applied
     * or a weaker one: amplifier zero and at most the duration it wrote. Anything stronger or
     * longer belongs to someone else and is left completely untouched, with the miss counted.
     */
    private static void releaseSnare(final MimicBody mimic, final ServerLevel level, final Core core) {
        final TransientState scratch = core.scratch();
        scratch.snareApplied = false;
        final LivingEntity subject = resolveBound(level, scratch);
        if (subject == null) {
            return;
        }
        final MobEffectInstance instance = subject.getEffect(MobEffects.SLOWNESS);
        if (instance == null
            || instance.getAmplifier() != MimicryRules.WEAVER_SNARE_AMPLIFIER
            || instance.getDuration() > MimicryRules.WEAVER_SNARE_DURATION_TICKS) {
            core.counters().snareRemovalGuardMisses++;
            return;
        }
        core.counters().snareRemovals++;
        core.counters().foreignEntityWrites++;
        subject.removeEffect(MobEffects.SLOWNESS);
    }

    /**
     * The anti-stacking station slot: one of four cardinal offsets exactly
     * {@link MimicryRules#DECOY_STATION_OFFSET} blocks out from the anchor, selected by the decoy's
     * own entity id so two decoys beside one anchor never claim the same block.
     *
     * <p>Split out from {@link #stationPoint} because the geometry is the part worth asserting and
     * the arrival counter it feeds cannot be reached inside a sealed three-by-three GameTest cell:
     * every one of these offsets lands outside a cell whose anchor is inside it.</p>
     */
    static Vec3 stationOffset(final int entityId) {
        final int slot = Math.floorMod(entityId, MimicryRules.DECOY_STATION_SLOTS);
        final double angle = slot * (Math.PI / 2.0D);
        return new Vec3(
            Math.cos(angle) * MimicryRules.DECOY_STATION_OFFSET,
            0.0D,
            Math.sin(angle) * MimicryRules.DECOY_STATION_OFFSET
        );
    }

    private static BlockPos stationPoint(final Mob body, final LivingEntity anchor) {
        final Vec3 offset = stationOffset(body.getId());
        return BlockPos.containing(
            anchor.getX() + offset.x, anchor.getY(), anchor.getZ() + offset.z
        );
    }

    // ---------------------------------------------------------------- routing and hazard

    private static void route(
        final MimicBody mimic,
        final ServerLevel level,
        final Core core,
        final BlockPos destination,
        final double speed
    ) {
        final Mob body = mimic.body();
        final TransientState scratch = core.scratch();
        final Counters counters = core.counters();
        if (!scratch.route.mayRequest()) {
            return;
        }
        if (!spendPath(level, counters)) {
            counters.tokensDeferred++;
            scratch.route = scratch.route.failed(MimicryRules.ROUTE_BACKOFF);
            return;
        }
        counters.pathRequests++;
        final var path = body.getNavigation().createPath(destination, 0);
        final boolean accepted = path != null && path.canReach()
            && body.getNavigation().moveTo(path, speed);
        if (accepted) {
            counters.pathsAccepted++;
            scratch.route = scratch.route.succeeded();
        } else {
            counters.pathFailures++;
            body.getNavigation().stop();
            scratch.route = scratch.route.failed(MimicryRules.ROUTE_BACKOFF);
        }
    }

    /**
     * Hazard escape. The destination envelope is evaluated centre out with a fixed near anchor and
     * a rotating page, so the far shell is reached within a bounded number of successive sweeps and
     * the mimic's own block is never skipped. Every candidate is charged before it can be rejected.
     */
    private static void escape(final MimicBody mimic, final ServerLevel level, final Core core) {
        final Mob body = mimic.body();
        final Counters counters = core.counters();
        final TransientState scratch = core.scratch();
        counters.hazardEscapes++;
        if (!scratch.route.mayRequest()) {
            return;
        }
        final int candidateCap = MimicryRules.destinationCandidateCap();
        final ReadBudget budget = ReadBudget.of(MimicryRules.MAX_DESTINATION_READS);
        final List<BlockPos> window = ESCAPE_ENVELOPE.window(candidateCap, scratch.escapeCursor);
        scratch.escapeCursor = ESCAPE_ENVELOPE.advanceCursor(candidateCap, scratch.escapeCursor);
        final Optional<BlockPos> best = chooseEscapeDestination(
            new LevelProbe(level, body), budget, body.blockPosition(), window
        );
        // The counter is the budget's own spend, so it is the real number of world reads the sweep
        // performed rather than a per candidate tally that under reports four reads out of five.
        counters.hazardReads += budget.spent();
        best.ifPresentOrElse(
            destination -> route(mimic, level, core, destination, MimicryRules.ESCAPE_SPEED),
            () -> scratch.route = scratch.route.failed(MimicryRules.ROUTE_BACKOFF)
        );
    }

    /**
     * Every world read one destination candidate needs, and nothing else.
     *
     * <p>A seam rather than a direct level call, because the charge discipline is the thing that
     * has to be provable. A test can count the probe calls and compare them against
     * {@link ReadBudget#spent()}, which is the only way to show the budget is the real cost instead
     * of a decoration: a sweep that charges once per candidate while each candidate performs five
     * reads has a guard that can never bind, however loudly the constants say otherwise.</p>
     */
    interface DestinationProbe {
        boolean loaded(BlockPos position);

        default boolean withinBorder(BlockPos position) { return true; }

        boolean air(BlockPos position);

        boolean fluidFree(BlockPos position);

        default boolean occupied(BlockPos position) { return false; }
    }

    private record LevelProbe(ServerLevel level, Mob body) implements DestinationProbe {
        @Override
        public boolean loaded(final BlockPos position) {
            return level.hasChunkAt(position);
        }

        @Override
        public boolean air(final BlockPos position) {
            return level.getBlockState(position).isAir();
        }

        @Override
        public boolean withinBorder(final BlockPos position) {
            return level.getWorldBorder().isWithinBounds(position);
        }

        @Override
        public boolean occupied(final BlockPos position) {
            return BoundedEntityQuery.any(
                level,
                Entity.class,
                new AABB(position),
                candidate -> candidate != body && candidate.isAlive()
            );
        }

        @Override
        public boolean fluidFree(final BlockPos position) {
            return level.getFluidState(position).isEmpty();
        }
    }

    /**
     * The charged destination sweep. The first candidate that qualifies wins and the sweep stops
     * there; an exhausted budget stops it too.
     *
     * <p>The whole worst case of a candidate must be affordable <em>before</em> any of that
     * candidate's reads run, so no candidate is ever half evaluated against a budget that ran out
     * underneath it and then reported as unsafe on the strength of reads that never happened.</p>
     */
    static Optional<BlockPos> chooseEscapeDestination(
        final DestinationProbe probe,
        final ReadBudget budget,
        final BlockPos origin,
        final List<BlockPos> window
    ) {
        for (int index = 0; index < window.size(); index++) {
            if (budget.remaining() < MimicryRules.READS_PER_DESTINATION_CANDIDATE) {
                // Break, never continue. An exhausted budget is not replenished by the next
                // candidate, so continuing only spins the rest of the window doing nothing.
                break;
            }
            final BlockPos candidate = origin.offset(window.get(index));
            if (safe(probe, budget, candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * The destination test, and the filter it is. Every one of its reads is charged before its
     * value may reject the candidate, which is the property the sweep previously lacked: it charged
     * once per candidate and then performed four more reads for free, so its declared cap of one
     * hundred and twenty eight bounded a real cost it never saw.
     */
    private static boolean safe(
        final DestinationProbe probe,
        final ReadBudget budget,
        final BlockPos candidate
    ) {
        if (!budget.charge() || !probe.loaded(candidate)) {
            return false;
        }
        if (!budget.charge() || !probe.withinBorder(candidate)) {
            return false;
        }
        if (!budget.charge() || !probe.air(candidate)) {
            return false;
        }
        if (!budget.charge() || !probe.air(candidate.above())) {
            return false;
        }
        if (!budget.charge() || !probe.fluidFree(candidate)) {
            return false;
        }
        final BlockPos below = candidate.below();
        if (!budget.charge() || probe.air(below)) {
            return false;
        }
        if (!budget.charge() || !probe.fluidFree(below)) {
            return false;
        }
        return budget.charge() && !probe.occupied(candidate);
    }

    // ---------------------------------------------------------------- quota

    private static Quota quota(final ServerLevel level) {
        final var server = level.getServer();
        if (server == null || !server.isSameThread()) {
            return null;
        }
        final Quota current = QUOTAS.getOrDefault(level, Quota.fresh(server.getTickCount()))
            .forServerTick(server.getTickCount());
        QUOTAS.put(level, current);
        return current;
    }

    private static boolean grantToken(final ServerLevel level, final Counters counters) {
        final Quota current = quota(level);
        if (current == null || !current.tokenAvailable()) {
            counters.tokensDeferred++;
            return false;
        }
        QUOTAS.put(level, current.spendToken());
        return true;
    }

    /**
     * The whole raw-visit allowance is reserved before the scan, not billed after it.
     *
     * <p>This is the arm that bounds real world reads across a whole level, and it is deliberately
     * tighter than the token arm above it: see {@link Quota#MAX_RAW_VISITS_PER_TICK}. A crowded
     * level therefore runs out of visits before it runs out of check tokens, which is the only
     * arrangement in which this member can ever be the one that denies.</p>
     */
    private static boolean reserveVisits(final ServerLevel level, final Counters counters) {
        final Quota current = quota(level);
        if (current == null || !current.visitsAvailable(MimicryRules.MAX_RAW_VISITS_PER_CHECK)) {
            counters.tokensDeferred++;
            return false;
        }
        QUOTAS.put(level, current.spendVisits(MimicryRules.MAX_RAW_VISITS_PER_CHECK));
        return true;
    }

    private static boolean spendPath(final ServerLevel level, final Counters counters) {
        final Quota current = quota(level);
        if (current == null || !current.pathAvailable()) {
            return false;
        }
        QUOTAS.put(level, current.spendPath());
        return true;
    }

    private static boolean spendRay(final net.minecraft.world.level.Level level, final Counters counters) {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        final Quota current = quota(server);
        if (current == null || !current.rayAvailable()) {
            counters.tokensDeferred++;
            return false;
        }
        QUOTAS.put(server, current.spendRay());
        return true;
    }

    /**
     * Bounded semantic feedback: at most one existing registered sound and at most eight existing
     * particles, and only while the per-level quota allows it. Suppressed feedback is counted and
     * never replayed later.
     */
    private static void feedback(
        final ServerLevel level,
        final Core core,
        final Mob body,
        final net.minecraft.sounds.SoundEvent sound,
        final net.minecraft.core.particles.SimpleParticleType particle
    ) {
        final Quota current = quota(level);
        if (current == null || !current.feedbackAvailable()) {
            core.counters().feedbackSuppressed++;
            return;
        }
        QUOTAS.put(level, current.spendFeedback());
        core.counters().feedbackEmitted++;
        level.playSound(null, body.getX(), body.getY(), body.getZ(), sound, SoundSource.HOSTILE, 0.6F, 1.0F);
        level.sendParticles(
            particle, body.getX(), body.getY() + 0.8D, body.getZ(), 8, 0.25D, 0.25D, 0.25D, 0.0D
        );
    }

    // ---------------------------------------------------------------- damage attribution

    /**
     * DC3. Only a causing {@link LivingEntity} mints an attribution, and only for the next forty
     * loaded ticks. A projectile object, a dispenser, anonymous magic, fall, fire and every other
     * environmental source mint nothing and change no phase.
     */
    public static void onAcceptedDamage(final MimicBody mimic, final @Nullable LivingEntity attacker) {
        Objects.requireNonNull(mimic, "mimic");
        final Core core = mimic.mimicCore();
        final Counters counters = core.counters();
        if (attacker == null || attacker == mimic.body() || attacker instanceof MimicBody
            || attacker instanceof ServerPlayer player && (player.isCreative() || player.isSpectator())) {
            counters.attributionRejections++;
            return;
        }
        final TransientState scratch = core.scratch();
        counters.attributions++;
        scratch.attacker = attacker.getUUID();
        scratch.attributionTicks = MimicryRules.ATTRIBUTION_FRESHNESS_TICKS;
        if (mimic.mimicSpecies() == Species.HOLLOW_DECOY
            && scratch.acceptedHits < MimicryRules.DECOY_DECISIVE_HITS) {
            scratch.acceptedHits++;
        }
        if (mimic.mimicSpecies() == Species.PRESENTED_LIKENESS) {
            scratch.recognition = MimicryRules.recognitionAfter(scratch.recognition, false, false, true);
        }
    }

    // ---------------------------------------------------------------- persistence

    public static void writeSaveData(final MimicBody mimic, final ValueOutput output, final String key) {
        output.store(key, CompoundTag.CODEC, mimic.mimicCore().state().write());
    }

    public static void readSaveData(final MimicBody mimic, final ValueInput input, final String key) {
        final Species species = mimic.mimicSpecies();
        final Core core = mimic.mimicCore();
        core.setState(input.read(key, CompoundTag.CODEC)
            .map(tag -> MimicryState.read(tag, species))
            .orElseGet(() -> MimicryState.empty(species)));
        core.scratch().resetForLoad(species);
    }
}
