package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.AnimalFamiliarRules.Action;
import com.kadamitas.warlockery.entity.AnimalFamiliarRules.Decision;
import com.kadamitas.warlockery.entity.AnimalFamiliarRules.Facts;
import com.kadamitas.warlockery.entity.AnimalFamiliarRules.HomeCandidate;
import com.kadamitas.warlockery.entity.AnimalFamiliarRules.PreyCandidate;
import com.kadamitas.warlockery.entity.AnimalFamiliarRules.Profile;
import com.kadamitas.warlockery.entity.AnimalFamiliarState.Phase;
import com.kadamitas.warlockery.entity.AnimalFamiliarState.Signature;
import com.kadamitas.warlockery.entity.behavior.ReadBudget;
import com.kadamitas.warlockery.entity.behavior.ScanEnvelope;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The single server-side controller for all three bound animal familiars.
 *
 * <p>One controller, not three. Ownership resolution, the owner aura pulse, the tether, the
 * defensive lease, the home claim, the bounded searches, the navigation lease, the signature phase
 * machine, the save seam and the persistence reason are written once and every species runs the same
 * code. The species reaches in at exactly five places, each of them a small exhaustive switch: the
 * aura it grants, the block predicate that makes a position a home, the entity tag that makes an
 * entity prey, the extra precondition its signature action requires, and how it moves.</p>
 *
 * <h2>Shared primitives</h2>
 *
 * <p>Adopted: {@link ScanEnvelope} for the home search, because the read cap is far smaller than the
 * envelope volume and this is exactly the search that otherwise never leaves the innermost ring; and
 * {@link ReadBudget}, because it charges before the value can be judged, which is the discipline
 * this family needs and the one that is easiest to get wrong by hand.</p>
 *
 * <p>Declined, deliberately: {@code Cadence} and {@code RouteRequest} are countdown based and this
 * family stores absolute deadlines against game time, matching every neighbouring file in the
 * package; the package's own guidance says converting between the two dialects is a behaviour
 * change rather than an extraction. {@code PhaseTimer} models exactly the pair this family already
 * keeps as {@code phase} plus {@code phaseEndsAt}, and adopting it would mean a second
 * representation of one fact. {@code PriorityLadder.select} allocates a {@code List<Rung>} of
 * lambdas per call and this ladder runs every tick on every loaded familiar, which is the
 * per-tick allocation the house style forbids; the ordering it would state is stated instead by
 * {@code AnimalFamiliarRules.decide}, which is pure, allocation free and directly unit tested.</p>
 */
public final class AnimalFamiliarRuntime {

    /** Ordinary melee reach, squared. One value for all three; a cat is not longer armed than a toad. */
    static final double MELEE_REACH_SQUARED = 4.0;
    /** How close counts as standing at home. */
    static final double AT_HOME_DISTANCE_SQUARED = 4.0;
    /** The bounded prey envelope, in blocks, around the familiar. */
    static final double PREY_RADIUS = 10.0;
    /** The toad's herb landmark must be this close to a candidate insect. */
    static final double LANDMARK_PROXIMITY_SQUARED = 36.0;
    /** The owl launches only from a perch with this much clear air beneath it. */
    static final int PERCH_MINIMUM_CLEARANCE = 1;
    /** How often a claimed home is re-validated against the world. Not every tick. */
    static final int HOME_VALIDATION_INTERVAL_TICKS = 40;
    /** How far an idle familiar drifts from where it stands. Deliberately short. */
    static final double IDLE_DRIFT_RADIUS = 4.0;
    /** How far above or below itself an idle owl drifts. A flyer that only drifts flat is a plate. */
    static final double IDLE_DRIFT_VERTICAL = 2.0;
    /** Idle is a saunter, not a commute. */
    static final double IDLE_DRIFT_SPEED = 0.7;

    private AnimalFamiliarRuntime() {
    }

    /**
     * Exact structural work counters, all source derived rather than measured.
     *
     * <p>Asserted by the live fixtures: {@code decisions}, {@code homeSearches},
     * {@code homeCandidatesInspected}, {@code homeBlockReads}, {@code homeClaims},
     * {@code homeReleases}, {@code meleeOpportunities}, {@code defenceLeases},
     * {@code navigationRequests}, {@code auraPulses} and {@code genericLayersDeclined}. The
     * remainder are recorded for the stress pass and for a reviewer reading a live run, and are
     * honestly not yet pinned by an assertion.</p>
     *
     * <p>There was a {@code worldEdits} counter here, incremented nowhere and asserted equal to
     * zero six times across four fixtures, on the reasoning that "its zero is the assertion". It
     * was not an assertion, it was a tautology: no edit anywhere in the family could have made it
     * non-zero, so the six assertions would have passed against a familiar that paved the arena.
     * The claim it was meant to make is now made against the arena itself, by snapshotting every
     * block state the fixture can reach and comparing it after the run.</p>
     */
    public static final class Counters {
        long decisions;
        long homeSearches;
        long homeCandidatesInspected;
        long homeBlockReads;
        long homeClaims;
        long homeReleases;
        long preySearches;
        long preyCandidatesInspected;
        long preyLineOfSightChecks;
        long telegraphsBegun;
        long commitsBegun;
        long meleeOpportunities;
        long defenceLeases;
        long navigationRequests;
        long navigationAccepts;
        long navigationFailures;
        long routeBackoffs;
        long recalls;
        long auraPulses;
        long hops;
        long genericLayersDeclined;

        public long decisions() { return decisions; }
        public long homeSearches() { return homeSearches; }
        public long homeCandidatesInspected() { return homeCandidatesInspected; }
        public long homeBlockReads() { return homeBlockReads; }
        public long homeClaims() { return homeClaims; }
        public long homeReleases() { return homeReleases; }
        public long preySearches() { return preySearches; }
        public long preyCandidatesInspected() { return preyCandidatesInspected; }
        public long preyLineOfSightChecks() { return preyLineOfSightChecks; }
        public long telegraphsBegun() { return telegraphsBegun; }
        public long commitsBegun() { return commitsBegun; }
        public long meleeOpportunities() { return meleeOpportunities; }
        public long defenceLeases() { return defenceLeases; }
        public long navigationRequests() { return navigationRequests; }
        public long navigationAccepts() { return navigationAccepts; }
        public long navigationFailures() { return navigationFailures; }
        public long routeBackoffs() { return routeBackoffs; }
        public long recalls() { return recalls; }
        public long auraPulses() { return auraPulses; }
        public long hops() { return hops; }
        /** Generic profiled, tactical and ambient layers reached and declined. */
        public long genericLayersDeclined() { return genericLayersDeclined; }
    }

    // ---- the one tick ----

    public static void tick(final AnimalFamiliarMob body, final ServerLevel level) {
        final long now = level.getGameTime();
        advanceLoadedPhases(body, level, now);
        final Optional<LivingEntity> owner = resolveOwner(body, level);
        pulseOwnerAura(body, owner, now);
        final Decision decision = AnimalFamiliarRules.decide(observe(body, level, owner, now));
        body.familiarCounters().decisions++;
        execute(body, level, owner, decision, now);
    }

    /**
     * The single exit for every timer this family keeps.
     *
     * <p>Nothing else ends a phase, releases a lease or arms a cooldown. The state record reports
     * expiry and this method acts on the report, which is why {@code AnimalFamiliarState}'s compact
     * constructor is forbidden from reconciling a phase away when its deadline passes: ending a
     * phase also arms the cooldown, releases the target, records the epoch and, for the owl,
     * updates the miss counter, and a constructor would have skipped all four.</p>
     */
    private static void advanceLoadedPhases(
        final AnimalFamiliarMob body,
        final ServerLevel level,
        final long now
    ) {
        AnimalFamiliarState state = body.familiarState();
        if (state.defenceElapsed(now)) {
            // The single exit for the lease. Ending it also arms the window, so a failed intercept
            // costs exactly what a successful one costs and the same event cannot re-lease forever.
            state = state
                .withDefence(Optional.empty(), 0L)
                .withDefenceCooldown(AnimalFamiliarRules.saturatingAdd(
                    now, AnimalFamiliarRules.DEFENSE_LEASE_TICKS));
            body.setTarget(null);
        }
        if (state.phaseElapsed(now)) {
            final Profile profile = AnimalFamiliarRules.profile(state.species());
            state = switch (state.phase()) {
                case TELEGRAPH -> {
                    body.familiarCounters().commitsBegun++;
                    yield state.withPhase(
                        Phase.COMMIT,
                        state.phaseTargetId(),
                        AnimalFamiliarRules.saturatingAdd(now, profile.telegraphTicks() * 2L)
                    );
                }
                case COMMIT -> endSignature(body, state, now, false);
                case NONE -> state;
            };
        }
        if (state.phase() == Phase.COMMIT && !targetStillValid(level, state.phaseTargetId())) {
            state = endSignature(body, state, now, false);
        }
        body.setFamiliarState(state);
    }

    /**
     * Closes a signature action, whatever the outcome. The cooldown is armed on every path, so a
     * miss, a timeout, an invalidation and a hit all cost the same wait; only the owl's durable miss
     * counter distinguishes them, and only because an owl that keeps missing stops launching.
     */
    private static AnimalFamiliarState endSignature(
        final AnimalFamiliarMob body,
        final AnimalFamiliarState state,
        final long now,
        final boolean connected
    ) {
        final Profile profile = AnimalFamiliarRules.profile(state.species());
        body.setTarget(null);
        final Signature signature = switch (state.signature()) {
            case Signature.Hunt hunt -> connected ? hunt.connected() : hunt.missed();
            case Signature.Territory territory -> territory.advanced();
            case Signature.Forage forage -> forage;
        };
        return state
            .withPhase(Phase.NONE, Optional.empty(), 0L)
            .withSignature(signature)
            .withSignatureCooldown(
                AnimalFamiliarRules.saturatingAdd(now, profile.signatureCooldownTicks()))
            .withActionEpoch(now);
    }

    private static boolean targetStillValid(final ServerLevel level, final Optional<UUID> target) {
        if (target.isEmpty()) {
            return false;
        }
        return level.getEntity(target.orElseThrow()) instanceof LivingEntity living && living.isAlive();
    }

    // ---- ownership, the aura, and the tether ----

    private static Optional<LivingEntity> resolveOwner(
        final AnimalFamiliarMob body,
        final ServerLevel level
    ) {
        final Optional<UUID> owner = CreatureBehaviorState.owner(body);
        if (owner.isEmpty()) {
            return Optional.empty();
        }
        final Entity resolved = level.getEntity(owner.orElseThrow());
        return resolved instanceof LivingEntity living && living.isAlive()
            ? Optional.of(living)
            : Optional.empty();
    }

    /**
     * The frozen owner aura. Effect, duration, amplifier, ambient and visibility flags and the
     * twenty-tick period are byte identical to {@code CreatureBehaviorRuntime.applyOwnerAura}; this
     * family took over the pulse because it took over the tick, not because it wanted to change it.
     */
    private static void pulseOwnerAura(
        final AnimalFamiliarMob body,
        final Optional<LivingEntity> owner,
        final long now
    ) {
        if (owner.isEmpty() || body.tickCount % AnimalFamiliarRules.AURA_PULSE_INTERVAL_TICKS != 0) {
            return;
        }
        final LivingEntity target = owner.orElseThrow();
        switch (body.species()) {
            case CAT -> target.addEffect(new MobEffectInstance(MobEffects.LUCK, 60, 0, true, false));
            case OWL -> {
                if (target instanceof Player player
                    && inventoryContains(player, CreatureBehaviorTags.Items.BROOMS)) {
                    target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, true, false));
                    target.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, true, false));
                }
            }
            case TOAD -> {
                target.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, true, false));
                target.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 60, 0, true, false));
            }
        }
        body.familiarCounters().auraPulses++;
    }

    private static boolean inventoryContains(
        final Player player,
        final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag
    ) {
        final var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(tag)) {
                return true;
            }
        }
        return false;
    }

    // ---- perception ----

    private static Facts observe(
        final AnimalFamiliarMob body,
        final ServerLevel level,
        final Optional<LivingEntity> owner,
        final long now
    ) {
        final AnimalFamiliarState entry = body.familiarState();
        final boolean ownerLoaded = owner.isPresent();
        final double ownerDistanceSquared = ownerLoaded ? body.distanceToSqr(owner.orElseThrow()) : 0.0;
        final boolean defending = holdOrAcquireDefence(body, level, threat(body, owner), now);
        final boolean homeClaimed = entry.home().isPresent();
        final boolean homeValid = homeClaimed && cachedHomeValidity(body, level, now);
        final boolean signatureRunning = entry.phase() != Phase.NONE;
        final boolean preyQualified = !signatureRunning
            && entry.signatureOffCooldown(now)
            && entry.preySearchDue(now)
            && searchPrey(body, level, owner, now);
        return new Facts(
            body.species(),
            !body.isAlive() || body.isRemoved(),
            ownerLoaded,
            ownerDistanceSquared,
            defending,
            signatureRunning,
            entry.signatureOffCooldown(now),
            preyQualified,
            homeClaimed,
            homeValid,
            homeClaimed && body.distanceToSqr(Vec3.atCenterOf(entry.home().orElseThrow()))
                <= AT_HOME_DISTANCE_SQUARED,
            body.familiarState().homeSearchDue(now),
            AnimalFamiliarRules.awake(body.species(), level.getDefaultClockTime(), level.isRaining())
        );
    }

    /**
     * The one candidate this decision may open a defensive lease against.
     *
     * <p>Two sources, in order. An attributed attacker of the loaded owner, or of this body when it
     * has no loaded owner, is the frozen 1.4 rule and comes first. A target somebody else assigned
     * to this body comes second, and reading it is the fix for a real regression rather than a new
     * feature: {@code ArcaneMob}'s constructor installed a {@code MeleeAttackGoal} for CAT, OWL and
     * TOAD, this family removes it because the runtime is the sole target authority, and nothing
     * here read {@code getTarget()}. {@code BrewRuntime.summonOwls} sets a target on every owl it
     * spawns, so at HEAD those three owls attacked and after this family they held a target forever
     * and acted on none of it. It now routes through exactly the same one-bounded-intercept lease
     * every other threat takes: never a per-tick chain, never a standing target.</p>
     *
     * <p>Deliberate and visible consequence, recorded rather than hidden: the lease is consumed by
     * one intercept, so a brew-summoned owl makes one attack run at its assigned target instead of
     * pursuing it indefinitely. That is a weaker summon than HEAD's and a working one, which is the
     * trade this family's single-writer discipline is worth.</p>
     */
    private static LivingEntity threat(
        final AnimalFamiliarMob body,
        final Optional<LivingEntity> owner
    ) {
        final LivingEntity attacker = owner.isPresent()
            ? owner.orElseThrow().getLastHurtByMob()
            : body.getLastHurtByMob();
        return attacker != null ? attacker : body.getTarget();
    }

    /**
     * Home validity is a real world read, so it runs on its own cadence rather than every tick and
     * the answer is cached between checks. Forty ticks is well inside the interval at which any
     * legitimate loss of support could matter and far outside the per-tick budget.
     */
    private static boolean cachedHomeValidity(
        final AnimalFamiliarMob body,
        final ServerLevel level,
        final long now
    ) {
        if (now - body.homeValidityCheckedAt() >= HOME_VALIDATION_INTERVAL_TICKS
            || body.homeValidityCheckedAt() == 0L) {
            final BlockPos home = body.familiarState().home().orElseThrow();
            body.setHomeValidity(homeStillValid(body, level, home), now);
        }
        return body.homeValidity();
    }

    /**
     * Acquires or holds the one defensive lease. Only a direct, attributed attacker of this
     * familiar or of its loaded owner qualifies, and every rejection is the shared rule in
     * {@code AnimalFamiliarRules.mayDefendAgainst}.
     */
    private static boolean holdOrAcquireDefence(
        final AnimalFamiliarMob body,
        final ServerLevel level,
        final LivingEntity candidate,
        final long now
    ) {
        AnimalFamiliarState state = body.familiarState();
        if (state.defenceTargetId().isPresent()) {
            return targetStillValid(level, state.defenceTargetId());
        }
        if (candidate == null || !state.defenceReady(now)) {
            // One bounded intercept per window. Without this gate a familiar standing next to its
            // owner's attacker would take a fresh lease, and therefore a fresh melee opportunity,
            // on every single tick: a chain, which is exactly what a lease is meant to prevent.
            return false;
        }
        final boolean invulnerable = candidate instanceof Player player
            && (player.isCreative() || player.isSpectator());
        if (!AnimalFamiliarRules.mayDefendAgainst(
            CreatureBehaviorState.owner(body),
            candidate.getUUID(),
            CreatureBehaviorState.owner(candidate),
            candidate.isAlive(),
            candidate == body,
            invulnerable,
            true
        )) {
            return false;
        }
        state = state.withDefence(
            Optional.of(candidate.getUUID()),
            AnimalFamiliarRules.saturatingAdd(now, AnimalFamiliarRules.DEFENSE_LEASE_TICKS)
        );
        body.setFamiliarState(state);
        body.familiarCounters().defenceLeases++;
        return true;
    }

    // ---- the two bounded searches ----

    /**
     * The one home search, for all three species.
     *
     * <p>The envelope is a {@link ScanEnvelope}, so the fixed anchor guarantees the familiar's own
     * neighbourhood is evaluated on every scan while the rotating page walks the far tail; a naive
     * raster with this read cap would spend every read inside the bottom layer and never reach the
     * familiar's own level.</p>
     *
     * <p>The window is requested in <em>positions</em> and the {@link ReadBudget} is sized in
     * <em>reads</em>, and the two are different numbers. Passing the read cap as the window length
     * is what made the delivered search stop at squared distance two for the cat and one for the
     * toad: a position costs {@link AnimalFamiliarRules#homeReadsPerPosition} reads, not one, so the
     * budget was exhausted inside the first ring and the rotating page walked over ground the loop
     * had already stopped reaching. {@link Profile#homeReadCap()} is now that product, so the window
     * is walkable to its end even in the worst case and the cursor's advertised coverage is real.</p>
     *
     * <p>Every read is still charged before its value can be judged. The expensive species
     * predicate is asked only where it can change the answer -- a position that is neither clear nor
     * supported can never be selected however it is tagged -- so a scene of solid stone costs three
     * reads a position instead of up to twenty, and the cap is a true bound rather than a limit the
     * ordinary case runs into.</p>
     */
    private static boolean searchHome(
        final AnimalFamiliarMob body,
        final ServerLevel level,
        final long now
    ) {
        final AnimalFamiliarState state = body.familiarState();
        final Profile profile = AnimalFamiliarRules.profile(state.species());
        final ScanEnvelope envelope =
            ScanEnvelope.of(profile.homeRadiusHorizontal(), profile.homeRadiusVertical());
        final int positions = profile.homePositionsPerScan();
        if (body.homeScanCursor() == AnimalFamiliarMob.UNSEEDED_CURSOR) {
            body.setHomeScanCursor(envelope.seedCursor(body.getUUID(), positions));
        }
        final ReadBudget budget = ReadBudget.of(profile.homeReadCap());
        final List<BlockPos> window = envelope.window(positions, body.homeScanCursor());
        final BlockPos origin = body.blockPosition();
        final List<HomeCandidate> candidates = new ArrayList<>(window.size());
        for (int index = 0; index < window.size(); index++) {
            if (!budget.charge()) {
                break;
            }
            body.familiarCounters().homeBlockReads++;
            final BlockPos position = origin.offset(window.get(index));
            if (!level.isLoaded(position)) {
                continue;
            }
            final BlockState here = level.getBlockState(position);
            final boolean clear = here.isAir() || here.getCollisionShape(level, position).isEmpty();
            final boolean supported = footing(body.species(), level, position, budget, body);
            candidates.add(new HomeCandidate(
                position.asLong(),
                body.distanceToSqr(Vec3.atCenterOf(position)),
                supported,
                clear,
                clear && supported
                    && qualifiesAsHome(body.species(), level, position, budget, body)
            ));
        }
        body.setHomeScanCursor(envelope.advanceCursor(positions, body.homeScanCursor()));
        final var selection = AnimalFamiliarRules.selectHome(candidates, positions);
        body.familiarCounters().homeSearches++;
        body.familiarCounters().homeCandidatesInspected += selection.inspected();
        final boolean qualified = selection.home().isPresent();
        AnimalFamiliarState next = body.familiarState().withHomeSearch(
            AnimalFamiliarRules.recordSearch(
                now,
                profile.homeSearchIntervalTicks(),
                qualified,
                body.familiarState().homeSearch().consecutiveFailures()
            )
        );
        if (qualified) {
            next = next.withHome(
                Optional.of(BlockPos.of(selection.home().orElseThrow())),
                Optional.of(level.dimension().identifier().toString())
            );
            body.familiarCounters().homeClaims++;
        }
        body.setFamiliarState(next);
        return qualified;
    }

    /**
     * Where the species' weight rests. A cat and a toad stand on something; an owl hangs from
     * something. Asking the same question of all three would have made the perch a floor tile.
     *
     * <p>Package-private rather than private, and deliberately: this is one of the five points at
     * which a species reaches into the shared controller, and a distinctness proof has to be able
     * to call it. The proof that shipped never did -- it re-implemented the species preconditions
     * inside the test -- which is why an auditor could make all three species byte identical here
     * and still watch all six of its cases pass.</p>
     */
    static boolean footing(
        final AnimalFamiliarSpecies species,
        final ServerLevel level,
        final BlockPos position,
        final ReadBudget budget,
        final AnimalFamiliarMob body
    ) {
        if (!budget.charge()) {
            return false;
        }
        body.familiarCounters().homeBlockReads++;
        final BlockPos support = switch (species) {
            case CAT, TOAD -> position.below();
            case OWL -> position.above();
        };
        return level.isLoaded(support) && level.getBlockState(support).isSolidRender();
    }

    /**
     * The species predicate, and the first of the five places a species reaches into this
     * controller. Three genuinely different questions about the same position.
     */
    static boolean qualifiesAsHome(
        final AnimalFamiliarSpecies species,
        final ServerLevel level,
        final BlockPos position,
        final ReadBudget budget,
        final AnimalFamiliarMob body
    ) {
        if (!budget.charge()) {
            return false;
        }
        body.familiarCounters().homeBlockReads++;
        return switch (species) {
            // A household: a bed, or any block the pack marks as a familiar's place, directly
            // beside the claim. A cat claims a room, not a ledge.
            case CAT -> adjacentTo(level, position, budget, body, BlockTags.BEDS,
                WarlockeryTags.Blocks.FAMILIAR_CAT_HOMES);
            // A perch: tagged support ABOVE the claim, and real clearance beneath it, because an
            // owl launches downward and a perch at ground level is not a perch.
            case OWL -> taggedAt(level, position.above(), budget, body,
                WarlockeryTags.Blocks.OWL_PERCHES)
                && clearBelow(level, position, budget, body);
            // A shelter: still water within reach, and something overhead. A toad under open sky
            // beside a lake has not found a shelter.
            case TOAD -> waterWithinReach(level, position, budget, body)
                && taggedAt(level, position.above(), budget, body,
                    WarlockeryTags.Blocks.TOAD_SHELTERS);
        };
    }

    private static boolean adjacentTo(
        final ServerLevel level,
        final BlockPos position,
        final ReadBudget budget,
        final AnimalFamiliarMob body,
        final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> first,
        final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> second
    ) {
        for (final var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            if (!budget.charge()) {
                return false;
            }
            body.familiarCounters().homeBlockReads++;
            final BlockPos neighbour = position.relative(direction);
            if (!level.isLoaded(neighbour)) {
                continue;
            }
            final BlockState state = level.getBlockState(neighbour);
            if (state.is(first) || state.is(second)) {
                return true;
            }
        }
        return false;
    }

    private static boolean taggedAt(
        final ServerLevel level,
        final BlockPos position,
        final ReadBudget budget,
        final AnimalFamiliarMob body,
        final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tag
    ) {
        if (!budget.charge()) {
            return false;
        }
        body.familiarCounters().homeBlockReads++;
        return level.isLoaded(position) && level.getBlockState(position).is(tag);
    }

    private static boolean clearBelow(
        final ServerLevel level,
        final BlockPos position,
        final ReadBudget budget,
        final AnimalFamiliarMob body
    ) {
        for (int drop = 1; drop <= PERCH_MINIMUM_CLEARANCE; drop++) {
            if (!budget.charge()) {
                return false;
            }
            body.familiarCounters().homeBlockReads++;
            final BlockPos below = position.below(drop);
            if (!level.isLoaded(below) || !level.getBlockState(below).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static boolean waterWithinReach(
        final ServerLevel level,
        final BlockPos position,
        final ReadBudget budget,
        final AnimalFamiliarMob body
    ) {
        for (final var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            for (int distance = 1; distance <= 4; distance++) {
                if (!budget.charge()) {
                    return false;
                }
                body.familiarCounters().homeBlockReads++;
                final BlockPos probe = position.relative(direction, distance);
                if (level.isLoaded(probe) && level.getFluidState(probe).is(net.minecraft.tags.FluidTags.WATER)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean homeStillValid(
        final AnimalFamiliarMob body,
        final ServerLevel level,
        final BlockPos home
    ) {
        final Optional<String> dimension = body.familiarState().homeDimension();
        if (dimension.isEmpty()
            || !dimension.orElseThrow().equals(level.dimension().identifier().toString())
            || !level.isLoaded(home)) {
            return false;
        }
        // One position's worth of allowance, from the same table the scan is sized against, so a
        // re-validation can never be truncated by a budget that a later predicate change outgrew.
        final ReadBudget budget =
            ReadBudget.of(AnimalFamiliarRules.homeReadsPerPosition(body.species()));
        return footing(body.species(), level, home, budget, body)
            && qualifiesAsHome(body.species(), level, home, budget, body);
    }

    /**
     * The one prey search. The species contributes the entity tag and one extra precondition; the
     * traversal, the charging and the tie breaking are shared.
     */
    private static boolean searchPrey(
        final AnimalFamiliarMob body,
        final ServerLevel level,
        final Optional<LivingEntity> owner,
        final long now
    ) {
        final AnimalFamiliarState state = body.familiarState();
        final Profile profile = AnimalFamiliarRules.profile(state.species());
        if (state.species() == AnimalFamiliarSpecies.OWL
            && state.signature() instanceof Signature.Hunt hunt && hunt.discouraged()) {
            body.setFamiliarState(state.withPreySearch(AnimalFamiliarRules.recordSearch(
                now, profile.signatureCooldownTicks(), false,
                state.preySearch().consecutiveFailures())));
            return false;
        }
        if (state.species() == AnimalFamiliarSpecies.OWL && state.home().isEmpty()) {
            // An owl pounces from a perch or not at all.
            body.setFamiliarState(state.withPreySearch(AnimalFamiliarRules.recordSearch(
                now, profile.signatureCooldownTicks(), false,
                state.preySearch().consecutiveFailures())));
            return false;
        }
        final AABB envelope = body.getBoundingBox().inflate(PREY_RADIUS);
        final List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, envelope, _ -> true);
        found.sort((left, right) -> {
            final int byDistance = Double.compare(body.distanceToSqr(left), body.distanceToSqr(right));
            return byDistance != 0 ? byDistance : left.getUUID().compareTo(right.getUUID());
        });
        final Optional<UUID> ownerId = CreatureBehaviorState.owner(body);
        // Description is bounded by the same cap the selection charges against, so the expensive
        // per-candidate work can never exceed the budget by describing entities the selection would
        // never have reached. Nothing here rejects anything; every described candidate is charged.
        final int described = Math.min(found.size(), profile.preyCandidateCap());
        final List<PreyCandidate> candidates = new ArrayList<>(described);
        int traces = 0;
        for (int index = 0; index < described; index++) {
            final LivingEntity entity = found.get(index);
            final boolean protectedFromFamiliars = entity == body
                || entity instanceof Player
                || ownerId.isPresent() && CreatureBehaviorState.owner(entity).equals(ownerId)
                || entity instanceof ArcaneCreature;
            final boolean cheapPass = entity.typeHolder().is(preyTag(state.species()))
                && entity.isAlive()
                && !protectedFromFamiliars;
            // A line of sight trace is the one genuinely expensive read here, so it is spent only
            // on candidates that survived the cheap description and only while the trace budget
            // holds. A species with a zero trace budget -- the Cat, which stalks by scent -- never
            // spends one and never rejects on visibility.
            boolean visible = true;
            if (profile.preyLineOfSightCap() > 0 && cheapPass) {
                if (traces < profile.preyLineOfSightCap()) {
                    traces++;
                    visible = body.getSensing().hasLineOfSight(entity);
                } else {
                    visible = false;
                }
            }
            candidates.add(new PreyCandidate(
                entity.getUUID(),
                body.distanceToSqr(entity),
                entity.typeHolder().is(preyTag(state.species())),
                entity.isAlive(),
                protectedFromFamiliars,
                insideSignatureEnvelope(body, level, entity, owner),
                visible
            ));
        }
        final var selection = AnimalFamiliarRules.selectPrey(
            candidates, profile.preyCandidateCap(), profile.preyLineOfSightCap());
        body.familiarCounters().preySearches++;
        body.familiarCounters().preyCandidatesInspected += selection.inspected();
        body.familiarCounters().preyLineOfSightChecks += traces;
        final boolean qualified = selection.prey().isPresent();
        AnimalFamiliarState next = body.familiarState().withPreySearch(
            AnimalFamiliarRules.recordSearch(
                now,
                profile.signatureCooldownTicks(),
                qualified,
                body.familiarState().preySearch().consecutiveFailures()
            )
        );
        if (qualified) {
            body.familiarCounters().telegraphsBegun++;
            next = next.withPhase(
                Phase.TELEGRAPH,
                selection.prey(),
                AnimalFamiliarRules.saturatingAdd(now, profile.telegraphTicks())
            );
            next = retainLandmark(body, next, level, selection.prey().map(level::getEntity));
        }
        body.setFamiliarState(next);
        return qualified;
    }

    /**
     * The one durable write a prey search makes, and the only place it is made.
     *
     * <p>A toad that has just committed to an insect anchors its forage to the landmark beside
     * <em>that</em> insect, unless it already holds one the world still agrees with, in which case
     * the held one is what qualified the prey and it stays. A cat and an owl carry no landmark, so
     * the other arm returns the state untouched; the switch is exhaustive so a fourth species would
     * be a compile error rather than a silently skipped write.</p>
     */
    private static AnimalFamiliarState retainLandmark(
        final AnimalFamiliarMob body,
        final AnimalFamiliarState state,
        final ServerLevel level,
        final Optional<Entity> prey
    ) {
        return switch (state.species()) {
            case CAT, OWL -> state;
            case TOAD -> retainedLandmark(body, level).isPresent()
                ? state
                : prey
                    .filter(LivingEntity.class::isInstance)
                    .map(LivingEntity.class::cast)
                    .flatMap(living -> discoverLandmark(level, living))
                    .map(landmark -> state.withSignature(new Signature.Forage(
                        Optional.of(landmark),
                        Optional.of(level.dimension().identifier().toString())
                    )))
                    .orElse(state);
        };
    }

    static net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> preyTag(
        final AnimalFamiliarSpecies species
    ) {
        return switch (species) {
            case CAT -> WarlockeryTags.EntityTypes.FAMILIAR_CAT_VERMIN;
            case OWL -> WarlockeryTags.EntityTypes.OWL_HUNT_TARGETS;
            case TOAD -> WarlockeryTags.EntityTypes.TOAD_INSECT_TARGETS;
        };
    }

    /**
     * The second species reach-in: the extra precondition each signature action carries. A cat
     * needs the prey inside its household territory, an owl needs it below its perch, and a toad
     * needs it beside a retained herb landmark.
     */
    static boolean insideSignatureEnvelope(
        final AnimalFamiliarMob body,
        final ServerLevel level,
        final LivingEntity candidate,
        final Optional<LivingEntity> owner
    ) {
        final AnimalFamiliarState state = body.familiarState();
        if (owner.isPresent()
            && candidate.distanceToSqr(owner.orElseThrow())
                > AnimalFamiliarRules.profile(state.species()).tetherRadiusSquared()) {
            return false;
        }
        return switch (state.species()) {
            case CAT -> state.home().isPresent()
                && candidate.distanceToSqr(Vec3.atCenterOf(state.home().orElseThrow())) <= 144.0;
            case OWL -> state.home().isPresent()
                && candidate.getY() < state.home().orElseThrow().getY();
            case TOAD -> landmarkNear(body, level, candidate);
        };
    }

    /**
     * Whether a herb landmark anchors this candidate. Pure: it reads the world and reports.
     *
     * <p>It used to write. The retention below was performed from inside here, which meant a
     * perception path persisted state once per inspected candidate, and the toad's durable landmark
     * was decided by whichever candidate the traversal happened to reach first rather than by the
     * one that was actually chosen. Discovery still happens here; the single write it earns is made
     * once, in {@link #searchPrey}'s own commit block, for the prey that won.</p>
     */
    private static boolean landmarkNear(
        final AnimalFamiliarMob body,
        final ServerLevel level,
        final LivingEntity candidate
    ) {
        final Optional<BlockPos> retained = retainedLandmark(body, level);
        if (retained.isPresent()) {
            return candidate.distanceToSqr(Vec3.atCenterOf(retained.orElseThrow()))
                <= LANDMARK_PROXIMITY_SQUARED;
        }
        return discoverLandmark(level, candidate).isPresent();
    }

    /** The persisted landmark, but only while the world still agrees it is one. Pure. */
    private static Optional<BlockPos> retainedLandmark(
        final AnimalFamiliarMob body,
        final ServerLevel level
    ) {
        if (!(body.familiarState().signature() instanceof Signature.Forage forage)) {
            return Optional.empty();
        }
        return forage.landmark().filter(position -> level.isLoaded(position)
            && level.getBlockState(position).is(WarlockeryTags.Blocks.TOAD_HERB_LANDMARKS));
    }

    /** The nearest herb landmark on the candidate's own level, inside a 5x5 footprint. Pure. */
    private static Optional<BlockPos> discoverLandmark(
        final ServerLevel level,
        final LivingEntity candidate
    ) {
        final BlockPos near = candidate.blockPosition();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                final BlockPos probe = near.offset(dx, 0, dz);
                if (level.isLoaded(probe)
                    && level.getBlockState(probe).is(WarlockeryTags.Blocks.TOAD_HERB_LANDMARKS)) {
                    return Optional.of(probe.immutable());
                }
            }
        }
        return Optional.empty();
    }

    // ---- execution ----

    private static void execute(
        final AnimalFamiliarMob body,
        final ServerLevel level,
        final Optional<LivingEntity> owner,
        final Decision decision,
        final long now
    ) {
        final AnimalFamiliarState state = body.familiarState();
        body.recordFamiliarAction(decision);
        switch (decision.action()) {
            case IDLE -> drift(body, decision, now);
            case DEFEND_OWNER -> defend(body, level, now);
            case TETHER_RETURN -> returnToOwner(body, owner, now);
            case HOME_SEARCH -> {
                if (state.homeSearchDue(now)) {
                    searchHome(body, level, now);
                } else if (state.home().isPresent()) {
                    // The claim failed its own predicate. Release it here, in the tick branch, so
                    // the release is one decision with a counter and not a constructor side effect.
                    body.setFamiliarState(state.withHome(Optional.empty(), Optional.empty()));
                    body.setHomeValidity(false, 0L);
                    body.familiarCounters().homeReleases++;
                }
            }
            case HOME_RETURN -> state.home().ifPresent(home ->
                requestNavigation(body, Vec3.atCenterOf(home), 1.0, now));
            case PATROL_TERRITORY -> patrol(body, now);
            case STALK_VERMIN, POUNCE_PREY, FORAGE_INSECT -> pursue(body, level, now);
            case CURL_AT_HOME, ROOST_WATCH, SHELTER_REST -> rest(body, owner);
            case GLIDE_SURVEY -> survey(body, now);
            case HOP_TO_LANDMARK -> hopToLandmark(body, now);
        }
    }

    /**
     * The idle rung, which is not the same thing as standing still.
     *
     * <p>An unbound familiar with no home and nothing to hunt used to reach {@code IDLE} and get
     * {@code navigation.stop()}. That was survivable while such a familiar despawned; it is not
     * survivable now that it never does, and it is exactly what a brew-summoned poison toad got.
     * {@code BrewRuntime.summonPoisonToads} spawns four unbound toads, this family had already
     * removed the {@code WaterAvoidingRandomStrollGoal} that used to move them, and an unbound
     * toad's ladder resolves to {@code IDLE}, so the brew's visible effect was four statues.</p>
     *
     * <p>The drift is a movement request like any other: it goes through
     * {@link #requestNavigation}, so it obeys the same one-request-per-interval pacing, the same
     * three-failure backoff and the same single-writer rule, and it never fires on a dead or
     * removed body. Nothing is allocated on a tick that is not allowed to issue a request, which is
     * why the interval is checked here rather than left to the request itself.</p>
     */
    private static void drift(
        final AnimalFamiliarMob body,
        final Decision decision,
        final long now
    ) {
        if (decision.reason() == AnimalFamiliarRules.Reason.BODY_INVALID) {
            body.getNavigation().stop();
            return;
        }
        final AnimalFamiliarState state = body.familiarState();
        if (!AnimalFamiliarRules.mayRoute(now, state.nextNavigationAt(), state.routeBackoffUntil())) {
            // A drift already under way is left alone. Stopping here every tick is what would make
            // this rung a statue with extra steps.
            return;
        }
        final var random = body.getRandom();
        final double dx = (random.nextDouble() - 0.5) * 2.0 * IDLE_DRIFT_RADIUS;
        final double dz = (random.nextDouble() - 0.5) * 2.0 * IDLE_DRIFT_RADIUS;
        final double dy = switch (body.species()) {
            case CAT, TOAD -> 0.0;
            case OWL -> (random.nextDouble() - 0.5) * 2.0 * IDLE_DRIFT_VERTICAL;
        };
        requestNavigation(body, body.position().add(dx, dy, dz), IDLE_DRIFT_SPEED, now);
        if (body.species() == AnimalFamiliarSpecies.TOAD && body.onGround()) {
            impulseHop(body);
        }
    }

    private static void defend(
        final AnimalFamiliarMob body,
        final ServerLevel level,
        final long now
    ) {
        final Optional<UUID> target = body.familiarState().defenceTargetId();
        if (target.isEmpty()
            || !(level.getEntity(target.orElseThrow()) instanceof LivingEntity attacker)) {
            return;
        }
        body.setTarget(attacker);
        if (body.distanceToSqr(attacker) <= MELEE_REACH_SQUARED) {
            strike(body, level, attacker);
            body.setFamiliarState(body.familiarState()
                .withDefence(Optional.empty(), 0L)
                .withDefenceCooldown(AnimalFamiliarRules.saturatingAdd(
                    now, AnimalFamiliarRules.DEFENSE_LEASE_TICKS))
                .withActionEpoch(now));
            body.setTarget(null);
            return;
        }
        requestNavigation(body, attacker.position(), 1.2, now);
    }

    private static void returnToOwner(
        final AnimalFamiliarMob body,
        final Optional<LivingEntity> owner,
        final long now
    ) {
        if (owner.isEmpty()) {
            return;
        }
        final LivingEntity target = owner.orElseThrow();
        if (AnimalFamiliarRules.recallRequired(true, body.distanceToSqr(target))) {
            // The frozen 1.4 emergency recall, at its existing distance and with no new cadence.
            body.teleportTo(target.getX() + 1.0, target.getY(), target.getZ() + 1.0);
            body.familiarCounters().recalls++;
            return;
        }
        requestNavigation(body, target.position(), 1.1, now);
    }

    private static void patrol(final AnimalFamiliarMob body, final long now) {
        final AnimalFamiliarState state = body.familiarState();
        if (!(state.signature() instanceof Signature.Territory territory) || state.home().isEmpty()) {
            body.getNavigation().stop();
            return;
        }
        final BlockPos home = state.home().orElseThrow();
        final int index = territory.patrolIndex();
        final int dx = switch (index) {
            case 0 -> 4;
            case 1 -> 0;
            case 2 -> -4;
            default -> 0;
        };
        final int dz = switch (index) {
            case 0 -> 0;
            case 1 -> 4;
            case 2 -> 0;
            default -> -4;
        };
        final Vec3 point = Vec3.atCenterOf(home.offset(dx, 0, dz));
        if (body.distanceToSqr(point) <= AT_HOME_DISTANCE_SQUARED) {
            body.setFamiliarState(state.withSignature(territory.advanced()).withActionEpoch(now));
            return;
        }
        requestNavigation(body, point, 0.9, now);
    }

    private static void survey(final AnimalFamiliarMob body, final long now) {
        final AnimalFamiliarState state = body.familiarState();
        if (state.home().isEmpty()) {
            return;
        }
        // An owl surveys in the air, above its own perch. It never lands to do this.
        final Vec3 point = Vec3.atCenterOf(state.home().orElseThrow()).add(0.0, 2.0, 0.0);
        requestNavigation(body, point, 1.0, now);
    }

    private static void hopToLandmark(final AnimalFamiliarMob body, final long now) {
        final AnimalFamiliarState state = body.familiarState();
        if (!(state.signature() instanceof Signature.Forage forage) || forage.landmark().isEmpty()) {
            body.getNavigation().stop();
            return;
        }
        final Vec3 point = Vec3.atCenterOf(forage.landmark().orElseThrow());
        if (requestNavigation(body, point, 0.8, now)) {
            impulseHop(body);
        }
    }

    private static void rest(final AnimalFamiliarMob body, final Optional<LivingEntity> owner) {
        body.getNavigation().stop();
        owner.ifPresent(target -> body.getLookControl().setLookAt(target, 10.0F, 10.0F));
    }

    private static void pursue(
        final AnimalFamiliarMob body,
        final ServerLevel level,
        final long now
    ) {
        final AnimalFamiliarState state = body.familiarState();
        if (state.phaseTargetId().isEmpty()
            || !(level.getEntity(state.phaseTargetId().orElseThrow()) instanceof LivingEntity prey)) {
            return;
        }
        body.getLookControl().setLookAt(prey, 30.0F, 30.0F);
        if (state.phase() == Phase.TELEGRAPH) {
            // The visible wind-up. Nothing has committed and nothing may move.
            body.getNavigation().stop();
            return;
        }
        if (body.distanceToSqr(prey) <= MELEE_REACH_SQUARED) {
            strike(body, level, prey);
            body.setFamiliarState(endSignature(body, body.familiarState(), now, true));
            return;
        }
        if (body.species() == AnimalFamiliarSpecies.OWL && body.onGround()) {
            impulseHop(body);
        }
        requestNavigation(body, prey.position(), 1.15, now);
    }

    /** The one ordinary melee opportunity. Never a chain, never a bonus, never a status effect. */
    private static void strike(
        final AnimalFamiliarMob body,
        final ServerLevel level,
        final LivingEntity target
    ) {
        body.swing(InteractionHand.MAIN_HAND);
        body.familiarCounters().meleeOpportunities++;
        body.doHurtTarget(level, target);
    }

    private static void impulseHop(final AnimalFamiliarMob body) {
        final Vec3 movement = body.getDeltaMovement();
        body.setDeltaMovement(movement.x, 0.42, movement.z);
        body.familiarCounters().hops++;
    }

    /**
     * The sole movement writer. One request per navigation interval at most, three consecutive
     * failures release the destination for a fixed window, and both outcomes re-arm the interval so
     * a familiar that cannot reach anything does not retry every tick forever.
     */
    private static boolean requestNavigation(
        final AnimalFamiliarMob body,
        final Vec3 destination,
        final double speed,
        final long now
    ) {
        final AnimalFamiliarState state = body.familiarState();
        if (!AnimalFamiliarRules.mayRoute(now, state.nextNavigationAt(), state.routeBackoffUntil())) {
            return false;
        }
        body.familiarCounters().navigationRequests++;
        final boolean accepted = body.getNavigation()
            .moveTo(destination.x, destination.y, destination.z, speed);
        final int failures = accepted ? 0 : state.routeFailures() + 1;
        final int backoff = AnimalFamiliarRules.backoffTicks(failures);
        if (accepted) {
            body.familiarCounters().navigationAccepts++;
        } else {
            body.familiarCounters().navigationFailures++;
        }
        if (backoff > 0) {
            body.familiarCounters().routeBackoffs++;
        }
        body.setFamiliarState(state.withRoute(
            AnimalFamiliarRules.saturatingAdd(now, AnimalFamiliarRules.NAVIGATION_INTERVAL_TICKS),
            backoff > 0 ? AnimalFamiliarRules.saturatingAdd(now, backoff) : state.routeBackoffUntil(),
            backoff > 0 ? 0 : failures
        ));
        return accepted;
    }

    // ---- seams the bodies call ----

    /**
     * Shared target legality. A familiar never attacks its owner, a creature bound to its owner, or
     * anything it has not been given a lease against.
     */
    public static boolean canAttack(final AnimalFamiliarMob body, final LivingEntity target) {
        final Optional<UUID> owner = CreatureBehaviorState.owner(body);
        if (owner.isEmpty()) {
            return true;
        }
        if (owner.orElseThrow().equals(target.getUUID())) {
            return false;
        }
        return !CreatureBehaviorState.owner(target).equals(owner);
    }

    /** A direct attributed attacker of the familiar itself opens the same one defensive lease. */
    public static void onAcceptedDamage(
        final AnimalFamiliarMob body,
        final ServerLevel level,
        final DamageSource source
    ) {
        if (!(source.getEntity() instanceof LivingEntity attacker)) {
            // Unattributed irritation never becomes a target. This is the frozen rule, not a new one.
            return;
        }
        holdOrAcquireDefence(body, level, attacker, level.getGameTime());
    }
}
