package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.StormSimianRules.Concern;
import com.kadamitas.warlockery.entity.StormSimianRules.Weather;
import com.kadamitas.warlockery.entity.behavior.Candidates;
import com.kadamitas.warlockery.entity.behavior.PhaseTimer;
import com.kadamitas.warlockery.entity.behavior.ReadBudget;
import com.kadamitas.warlockery.entity.behavior.RouteRequest;
import com.kadamitas.warlockery.entity.behavior.ScanEnvelope;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

/**
 * The server side Storm Simian arbiter. It runs last in the entity's own tick, after the frozen
 * companion, tactical and ambient writers have had theirs, so exactly one species intent owns the
 * final navigation write of the tick and everything the species does is reached from one place.
 *
 * <p>Nothing here creates or redirects lightning, changes weather, energizes a rod, writes a block,
 * picks an item up, ignites anything, forces a chunk or reaches outside the loaded local space. The
 * charge is a read of the weather the simian is already standing in and nothing else.</p>
 */
public final class StormSimianRuntime {

    private static final int UNSEEDED_CURSOR = Integer.MIN_VALUE;

    private StormSimianRuntime() {
    }

    /** Structural work counters proving the exact caps. Pass local, never persisted. */
    public static final class Counters {
        long decisions;
        long gripSearches;
        long emptyGripSearches;
        long gripCandidateVisits;
        long gripBlockReads;
        long gripsTaken;
        long routeRequests;
        long routeFailures;
        long alarmsRaised;
        long alarmCandidateVisits;
        long alarmLineOfSightChecks;
        long alarmRecipients;
        long alarmsReceived;
        long curiosityScans;
        long curiosityCandidateVisits;
        long inspectionsStarted;
        long inspectionsCompleted;
        long observationsStarted;
        long observationsCompleted;
        long chargeGained;
        long chargeSpent;
        long chargedGusts;
        long plainGusts;
        long windowsCancelled;
        long routineStretchResets;
        long weatherWrites;
        long blockWrites;

        public long decisions() { return decisions; }
        public long gripSearches() { return gripSearches; }
        public long emptyGripSearches() { return emptyGripSearches; }
        public long gripCandidateVisits() { return gripCandidateVisits; }
        public long gripBlockReads() { return gripBlockReads; }
        public long gripsTaken() { return gripsTaken; }
        public long routeRequests() { return routeRequests; }
        public long routeFailures() { return routeFailures; }
        public long alarmsRaised() { return alarmsRaised; }
        public long alarmCandidateVisits() { return alarmCandidateVisits; }
        public long alarmLineOfSightChecks() { return alarmLineOfSightChecks; }
        public long alarmRecipients() { return alarmRecipients; }
        public long alarmsReceived() { return alarmsReceived; }
        public long curiosityScans() { return curiosityScans; }
        public long curiosityCandidateVisits() { return curiosityCandidateVisits; }
        public long inspectionsStarted() { return inspectionsStarted; }
        public long inspectionsCompleted() { return inspectionsCompleted; }
        public long observationsStarted() { return observationsStarted; }
        public long observationsCompleted() { return observationsCompleted; }
        public long chargeGained() { return chargeGained; }
        public long chargeSpent() { return chargeSpent; }
        public long chargedGusts() { return chargedGusts; }
        public long plainGusts() { return plainGusts; }
        public long windowsCancelled() { return windowsCancelled; }
        public long routineStretchResets() { return routineStretchResets; }
        /** Must stay zero forever: the species reads weather and never writes it. */
        public long weatherWrites() { return weatherWrites; }
        /** Must stay zero forever: the species never edits the world. */
        public long blockWrites() { return blockWrites; }
    }

    /**
     * Execution scratch rebuilt after every load. Nothing here is meaning: losing it can delay work
     * by one cadence but can never replay an alarm, an inspection, an observation epoch or a
     * charged gust. The open window, the inspected object, the alarm recipients and the remembered
     * attacker all live here precisely so no save can resurrect them.
     */
    public static final class TransientState {
        boolean reconciled;
        PhaseTimer<Concern> window = PhaseTimer.none();
        int gripCursor = UNSEEDED_CURSOR;
        UUID inspectedObject;
        int lastAlarmRecipients;
        int awarenessTicks;
        int attackerMemoryTicks;
        UUID rememberedAttacker;
        Concern lastConcern = Concern.IDLE;

        public void resetForLoad() {
            reconciled = false;
            window = PhaseTimer.none();
            gripCursor = UNSEEDED_CURSOR;
            inspectedObject = null;
            lastAlarmRecipients = 0;
            awarenessTicks = 0;
            attackerMemoryTicks = 0;
            rememberedAttacker = null;
            lastConcern = Concern.IDLE;
        }

        public Optional<Concern> openWindow() {
            return window.activePhase();
        }

        public Optional<UUID> inspectedObject() {
            return Optional.ofNullable(inspectedObject);
        }

        public Optional<UUID> rememberedAttacker() {
            return Optional.ofNullable(rememberedAttacker);
        }

        public int lastAlarmRecipients() {
            return lastAlarmRecipients;
        }

        public int awarenessTicks() {
            return awarenessTicks;
        }

        public Concern lastConcern() {
            return lastConcern;
        }
    }

    // ---------------------------------------------------------------- the one tick

    public static void tick(final StormSimianEntity simian, final ServerLevel level) {
        reconcileOnLoad(simian);
        advanceLoadedTimers(simian);
        final Concern concern = StormSimianRules.select(observe(simian, level));
        cancelOutrankedWindow(simian, concern);
        resetLedgerOnRoutineEntry(simian, concern);
        final Counters counters = simian.stormSimianCounters();
        counters.decisions++;
        simian.stormSimianTransient().lastConcern = concern;
        switch (concern) {
            case INVALID, HAZARD, COMBAT, OWNER_TETHER, IDLE -> {
            }
            case ALARM -> raiseAlarm(simian, level);
            case CANOPY -> repositionCanopy(simian, level);
            case STORM_WATCH -> beginObservation(simian, level);
            case CURIOSITY -> beginInspection(simian, level);
        }
        closeExpiredWindow(simian, level);
    }

    /**
     * A legal direct attacker is remembered so the next tick can arm one alarm. Nothing is targeted
     * here: the shared {@code HurtByTargetGoal} already owns retaliation and this must not add a
     * second, illegal path to a target.
     */
    public static void onAcceptedDamage(
        final StormSimianEntity simian,
        final DamageSource source
    ) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == simian) {
            return;
        }
        final TransientState scratch = simian.stormSimianTransient();
        scratch.attackerMemoryTicks = StormSimianRules.ATTACKER_MEMORY_TICKS;
        scratch.rememberedAttacker = attacker.getUUID();
    }

    /**
     * Receives one alarm from a troop neighbour. Awareness only: no target is inherited, no relay is
     * scheduled and nothing is persisted, so a reload cannot replay it and an illegal target cannot
     * travel along the troop.
     */
    public static void receiveAlarm(final StormSimianEntity recipient) {
        final TransientState scratch = recipient.stormSimianTransient();
        scratch.awarenessTicks = StormSimianRules.AWARENESS_TICKS;
        recipient.stormSimianCounters().alarmsReceived++;
    }

    /**
     * The one place charge is spent. Called from the already legal ranged attack, so charge can
     * never create an attack, add a target, widen an area or start a fire; it only alters the
     * presentation and the bounded potency of the single owned wind charge that was going to be
     * thrown anyway.
     */
    public static float consumeGustCharge(final StormSimianEntity simian) {
        final StormSimianState state = simian.stormSimianState();
        final float power = StormSimianRules.gustPower(state.charge());
        if (!StormSimianRules.chargedGustReady(state.charge())) {
            simian.stormSimianCounters().plainGusts++;
            return power;
        }
        final int after = StormSimianRules.chargeAfterGust(state.charge());
        simian.stormSimianCounters().chargeSpent += state.charge() - after;
        simian.stormSimianCounters().chargedGusts++;
        simian.setStormSimianState(state.withCharge(after));
        return power;
    }

    // ---------------------------------------------------------------- lifecycle

    private static void reconcileOnLoad(final StormSimianEntity simian) {
        final TransientState scratch = simian.stormSimianTransient();
        if (scratch.reconciled) {
            return;
        }
        scratch.reconciled = true;
        scratch.gripCursor = StormSimianRules.seedGripCursor(simian.getUUID());
    }

    private static void advanceLoadedTimers(final StormSimianEntity simian) {
        final TransientState scratch = simian.stormSimianTransient();
        scratch.window = scratch.window.step();
        scratch.awarenessTicks = StormSimianRules.decrementLoaded(scratch.awarenessTicks);
        scratch.attackerMemoryTicks =
            StormSimianRules.decrementLoaded(scratch.attackerMemoryTicks);
        if (scratch.attackerMemoryTicks == 0) {
            scratch.rememberedAttacker = null;
        }
        simian.setStormSimianState(simian.stormSimianState().step());
    }

    /**
     * A window is abandoned, never completed, when something more urgent arrives. Abandoning is what
     * makes an observation epoch, an inspection or an alarm genuinely single: the completion branch
     * is the only place that applies charge, counts an inspection or clears the claim.
     */
    private static void cancelOutrankedWindow(
        final StormSimianEntity simian,
        final Concern concern
    ) {
        final TransientState scratch = simian.stormSimianTransient();
        final Optional<Concern> running = scratch.window.activePhase();
        if (running.isEmpty() || !StormSimianRules.preempts(concern, running.orElseThrow())) {
            return;
        }
        scratch.window = scratch.window.cancel();
        scratch.inspectedObject = null;
        simian.stormSimianCounters().windowsCancelled++;
    }

    /**
     * Recurring defect six: a route failure ledger accumulated while the arbiter was away on
     * something urgent, then inherited by the next routine stretch, which gave up before it had
     * tried anything. The failure run is cleared on re entry to the routine band; the open backoff
     * window is preserved because it describes the neighbourhood rather than the stretch.
     */
    private static void resetLedgerOnRoutineEntry(
        final StormSimianEntity simian,
        final Concern concern
    ) {
        final TransientState scratch = simian.stormSimianTransient();
        final boolean wasUrgent = urgent(scratch.lastConcern);
        if (!wasUrgent || urgent(concern)) {
            return;
        }
        final StormSimianState before = simian.stormSimianState();
        final StormSimianState after = before.startRoutineStretch();
        if (after != before) {
            simian.stormSimianCounters().routineStretchResets++;
            simian.setStormSimianState(after);
        }
    }

    private static boolean urgent(final Concern concern) {
        return switch (concern) {
            case INVALID, HAZARD, COMBAT, ALARM, OWNER_TETHER -> true;
            case CANOPY, STORM_WATCH, CURIOSITY, IDLE -> false;
        };
    }

    /** The single exit of every window. Only a window that ran its full length completes. */
    private static void closeExpiredWindow(
        final StormSimianEntity simian,
        final ServerLevel level
    ) {
        final TransientState scratch = simian.stormSimianTransient();
        final Optional<Concern> expired = scratch.window.expiredPhase();
        if (expired.isEmpty()) {
            return;
        }
        switch (expired.orElseThrow()) {
            case STORM_WATCH -> completeObservation(simian, level);
            case CURIOSITY -> completeInspection(simian);
            // An alarm has no completion work: everything it does, the cooldown, the recipients and
            // the attacker memory, is spent the moment it is raised. The window exists only so a
            // more urgent concern can be seen to abandon it.
            case ALARM, INVALID, HAZARD, COMBAT, OWNER_TETHER, CANOPY, IDLE -> {
            }
        }
        scratch.window = scratch.window.endExpired();
    }

    // ---------------------------------------------------------------- perception

    private static StormSimianRules.Facts observe(
        final StormSimianEntity simian,
        final ServerLevel level
    ) {
        final StormSimianState state = simian.stormSimianState();
        final TransientState scratch = simian.stormSimianTransient();
        final boolean operational = simian.isAlive() && !simian.isRemoved()
            && !simian.isNoAi() && !simian.isPassenger();
        final LivingEntity target = simian.getTarget();
        final boolean combat = target != null && target.isAlive();
        final boolean hazard = HazardEscapeRuntime.currentHazard(simian, level)
            .filter(found -> HazardEscapeRules.shouldEscape(CreatureKind.STORM_SIMIAN, found))
            .isPresent();
        final boolean weatherWorthWatching = currentWeather(level) != Weather.CLEAR
            || state.charge() > 0;
        // Both movement concerns are gated on the shared route ledger actually being able to make a
        // request. Without this a simian with no reachable grip would claim every tick for the
        // canopy, do nothing on nineteen ticks in twenty, and starve storm observation and
        // curiosity outright; a simian in an unusable neighbourhood would starve them forever.
        final boolean routeReady = state.route().request().mayRequest();
        return new StormSimianRules.Facts(
            operational,
            hazard,
            combat,
            scratch.attackerMemoryTicks > 0 && state.cooldowns().alarmTicks() == 0,
            ownerBeyondTether(simian, level),
            routeReady && (state.grip().isEmpty() || state.gripHoldTicks() == 0),
            state.cooldowns().observationTicks() == 0 && weatherWorthWatching,
            routeReady && state.cooldowns().curiosityTicks() == 0 && scratch.awarenessTicks == 0
        );
    }

    private static boolean ownerBeyondTether(
        final StormSimianEntity simian,
        final ServerLevel level
    ) {
        final Optional<UUID> owner = CreatureBehaviorState.owner(simian);
        if (owner.isEmpty()) {
            return false;
        }
        final Entity found = level.getEntity(owner.orElseThrow());
        if (!(found instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }
        return simian.distanceToSqr(living) >= StormSimianRules.OWNER_TETHER_DISTANCE_SQUARED;
    }

    // ---------------------------------------------------------------- canopy

    /**
     * One canopy reposition attempt. Search and routing go through the shared
     * {@link RouteRequest#attempt} so the empty search branch cannot leave the cadence unarmed, and
     * the twenty tick pacing plus the three failure backoff apply to both movement writers together.
     */
    private static void repositionCanopy(
        final StormSimianEntity simian,
        final ServerLevel level
    ) {
        final RouteRequest request = simian.stormSimianState().route().request();
        // The cadence is advanced exactly once per tick, by advanceLoadedTimers. Letting attempt
        // step it again on a not due tick would halve the declared twenty tick pacing.
        if (!request.mayRequest()) {
            return;
        }
        final RouteRequest.Attempt<BlockPos> attempt = request.attempt(
            StormSimianRules.ROUTE_BACKOFF,
            () -> searchGrip(simian, level),
            destination -> route(simian, destination, StormSimianRules.CANOPY_SPEED)
        );
        final StormSimianState updated =
            simian.stormSimianState().withRouteRequest(attempt.request());
        if (!attempt.outcome().accepted()) {
            simian.setStormSimianState(updated);
            return;
        }
        final BlockPos taken = attempt.destination().orElseThrow();
        simian.stormSimianCounters().gripsTaken++;
        simian.setStormSimianState(updated.withGrip(taken));
    }

    /**
     * The bounded grip sweep. The window comes from the shared {@link ScanEnvelope}, so the fixed
     * near anchor always contains the simian's own position and the rotating page reaches the far
     * corners of the envelope across successive sweeps. Every one of the four reads a candidate
     * needs is charged to the {@link ReadBudget} before any of them may reject it, so the declared
     * sixty four read ceiling genuinely binds.
     */
    private static Optional<BlockPos> searchGrip(
        final StormSimianEntity simian,
        final ServerLevel level
    ) {
        final Counters counters = simian.stormSimianCounters();
        final TransientState scratch = simian.stormSimianTransient();
        counters.gripSearches++;
        final ScanEnvelope envelope = StormSimianRules.gripEnvelope();
        if (scratch.gripCursor == UNSEEDED_CURSOR) {
            scratch.gripCursor = StormSimianRules.seedGripCursor(simian.getUUID());
        }
        final List<BlockPos> window =
            envelope.window(StormSimianRules.GRIP_CANDIDATE_CAP, scratch.gripCursor);
        scratch.gripCursor =
            envelope.advanceCursor(StormSimianRules.GRIP_CANDIDATE_CAP, scratch.gripCursor);
        final ReadBudget budget = ReadBudget.of(StormSimianRules.GRIP_READ_CAP);
        final BlockPos origin = simian.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int index = 0; index < window.size(); index++) {
            if (budget.remaining() < StormSimianRules.READS_PER_GRIP_CANDIDATE) {
                break;
            }
            final BlockPos candidate = origin.offset(window.get(index));
            counters.gripCandidateVisits++;
            final boolean loaded = budget.accepts(() -> level.isLoaded(candidate), Boolean::booleanValue);
            final boolean bodyClear =
                budget.accepts(() -> level.getBlockState(candidate), BlockState::isAir);
            final boolean headClear =
                budget.accepts(() -> level.getBlockState(candidate.above()), BlockState::isAir);
            final boolean supported = budget.accepts(
                () -> level.getBlockState(candidate.below()),
                found -> canHoldWeight(level, candidate.below(), found)
            );
            if (!StormSimianRules.gripAcceptable(loaded, bodyClear, headClear, supported)) {
                continue;
            }
            final double distance = candidate.distSqr(origin);
            if (best == null || distance < bestDistance || (distance == bestDistance
                && candidate.compareTo(best) < 0)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        counters.gripBlockReads += budget.spent();
        if (best == null) {
            counters.emptyGripSearches++;
        }
        return Optional.ofNullable(best);
    }

    /** Canopy, not phasing: leaves and logs hold a simian, and so does any sturdy face. */
    private static boolean canHoldWeight(
        final ServerLevel level,
        final BlockPos position,
        final BlockState state
    ) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
            || state.isFaceSturdy(level, position, Direction.UP);
    }

    private static RouteRequest.Outcome route(
        final StormSimianEntity simian,
        final BlockPos destination,
        final double speed
    ) {
        simian.stormSimianCounters().routeRequests++;
        final Path path = simian.getNavigation().createPath(destination, 0);
        final boolean reachable = path != null && path.canReach();
        final boolean accepted = reachable && simian.getNavigation().moveTo(path, speed);
        final RouteRequest.Outcome outcome =
            RouteRequest.outcomeOf(path != null, reachable, accepted);
        if (!outcome.accepted()) {
            simian.stormSimianCounters().routeFailures++;
        }
        return outcome;
    }

    // ---------------------------------------------------------------- troop alarm

    /**
     * One bounded local alarm. The cooldown and the attacker memory are cleared first, so an alarm
     * that finds nobody costs exactly what one that finds four recipients costs and cannot repeat
     * every tick.
     */
    private static void raiseAlarm(final StormSimianEntity simian, final ServerLevel level) {
        final Counters counters = simian.stormSimianCounters();
        final TransientState scratch = simian.stormSimianTransient();
        final StormSimianState state = simian.stormSimianState();
        simian.setStormSimianState(state.withCooldowns(new StormSimianState.Cooldowns(
            StormSimianRules.ALARM_COOLDOWN_TICKS,
            state.cooldowns().curiosityTicks(),
            state.cooldowns().observationTicks()
        )));
        scratch.attackerMemoryTicks = 0;
        scratch.rememberedAttacker = null;
        counters.alarmsRaised++;

        final AABB box = simian.getBoundingBox().inflate(StormSimianRules.ALARM_RADIUS);
        final List<StormSimianEntity> found = level.getEntitiesOfClass(
            StormSimianEntity.class, box, other -> other != simian && other.isAlive());
        found.sort(Candidates.byDistanceThenIdentity(simian::distanceToSqr, Entity::getUUID));
        final List<StormSimianEntity> retained =
            Candidates.retain(List.of(), found, StormSimianRules.TROOP_CANDIDATE_CAP);
        final ReadBudget sight = ReadBudget.of(StormSimianRules.ALARM_LINE_OF_SIGHT_CAP);
        int recipients = 0;
        for (int index = 0; index < retained.size(); index++) {
            if (recipients >= StormSimianRules.ALARM_RECIPIENT_CAP || !sight.charge()) {
                break;
            }
            final StormSimianEntity neighbour = retained.get(index);
            counters.alarmCandidateVisits++;
            counters.alarmLineOfSightChecks++;
            if (!simian.getSensing().hasLineOfSight(neighbour)) {
                continue;
            }
            receiveAlarm(neighbour);
            recipients++;
        }
        scratch.lastAlarmRecipients = recipients;
        counters.alarmRecipients += recipients;
        scratch.window = PhaseTimer.start(Concern.ALARM, StormSimianRules.ALARM_WINDOW_TICKS);
        level.playSound(null, simian.blockPosition(), SoundEvents.PARROT_IMITATE_VEX,
            SoundSource.NEUTRAL, 0.7F, 1.4F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, simian.getX(),
            simian.getEyeY() + 0.3D, simian.getZ(), 6, 0.2D, 0.2D, 0.2D, 0.01D);
    }

    // ---------------------------------------------------------------- storm observation

    /** Opens one observation window. The charge is applied only if that window is allowed to end. */
    private static void beginObservation(
        final StormSimianEntity simian,
        final ServerLevel level
    ) {
        final StormSimianState state = simian.stormSimianState();
        simian.setStormSimianState(state.withCooldowns(new StormSimianState.Cooldowns(
            state.cooldowns().alarmTicks(),
            state.cooldowns().curiosityTicks(),
            StormSimianRules.OBSERVATION_COOLDOWN_TICKS
        )));
        simian.stormSimianTransient().window =
            PhaseTimer.start(Concern.STORM_WATCH, StormSimianRules.OBSERVE_WINDOW_TICKS);
        simian.stormSimianCounters().observationsStarted++;
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, simian.getX(),
            simian.getEyeY() + 0.4D, simian.getZ(), 3, 0.15D, 0.15D, 0.15D, 0.0D);
    }

    /**
     * The completed epoch. The weather is read here, from the level the simian is loaded in, so a
     * simian that walked out of the storm during its own window gains nothing from it.
     */
    private static void completeObservation(
        final StormSimianEntity simian,
        final ServerLevel level
    ) {
        final StormSimianState state = simian.stormSimianState();
        final Weather weather = currentWeather(level);
        final int updated = StormSimianRules.chargeAfterObservation(state.charge(), weather);
        if (updated > state.charge()) {
            simian.stormSimianCounters().chargeGained += updated - state.charge();
        }
        simian.setStormSimianState(state.withCompletedObservation(updated));
        simian.stormSimianCounters().observationsCompleted++;
    }

    // ---------------------------------------------------------------- curiosity

    /**
     * Opens one inspection. This replaces the old unbounded nearest item stream: at most eight
     * loaded candidates are retained, at most four are looked at, exactly one is approached, and no
     * step of it ever removes, moves, shrinks, consumes or claims ownership of the stack.
     */
    private static void beginInspection(final StormSimianEntity simian, final ServerLevel level) {
        final Counters counters = simian.stormSimianCounters();
        final StormSimianState state = simian.stormSimianState();
        simian.setStormSimianState(state.withCooldowns(new StormSimianState.Cooldowns(
            state.cooldowns().alarmTicks(),
            StormSimianRules.CURIOSITY_COOLDOWN_TICKS,
            state.cooldowns().observationTicks()
        )));
        counters.curiosityScans++;
        final Optional<ItemEntity> chosen = chooseInspectionTarget(simian, level);
        if (chosen.isEmpty()) {
            return;
        }
        final ItemEntity object = chosen.orElseThrow();
        final RouteRequest request = simian.stormSimianState().route().request();
        if (!request.mayRequest()) {
            return;
        }
        final RouteRequest.Attempt<BlockPos> attempt = request.attempt(
            StormSimianRules.ROUTE_BACKOFF,
            () -> Optional.of(object.blockPosition()),
            destination -> route(simian, destination, StormSimianRules.CURIOSITY_SPEED)
        );
        simian.setStormSimianState(
            simian.stormSimianState().withRouteRequest(attempt.request()));
        if (!attempt.outcome().accepted()) {
            return;
        }
        simian.stormSimianTransient().inspectedObject = object.getUUID();
        simian.stormSimianTransient().window =
            PhaseTimer.start(Concern.CURIOSITY, StormSimianRules.INSPECT_WINDOW_TICKS);
        counters.inspectionsStarted++;
        simian.getLookControl().setLookAt(object, 30.0F, 30.0F);
    }

    private static Optional<ItemEntity> chooseInspectionTarget(
        final StormSimianEntity simian,
        final ServerLevel level
    ) {
        final Counters counters = simian.stormSimianCounters();
        final AABB box = simian.getBoundingBox().inflate(StormSimianRules.CURIOSITY_RADIUS);
        final List<ItemEntity> found =
            level.getEntitiesOfClass(ItemEntity.class, box, ItemEntity::isAlive);
        found.sort(Candidates.byDistanceThenIdentity(simian::distanceToSqr, Entity::getUUID));
        final List<ItemEntity> retained =
            Candidates.retain(List.of(), found, StormSimianRules.CURIOSITY_CANDIDATE_CAP);
        final ReadBudget sight = ReadBudget.of(StormSimianRules.CURIOSITY_INSPECT_CAP);
        final List<ItemEntity> visible = new ArrayList<>(StormSimianRules.CURIOSITY_INSPECT_CAP);
        for (int index = 0; index < retained.size(); index++) {
            if (!sight.charge()) {
                break;
            }
            final ItemEntity candidate = retained.get(index);
            counters.curiosityCandidateVisits++;
            if (simian.getSensing().hasLineOfSight(candidate)) {
                visible.add(candidate);
            }
        }
        return visible.isEmpty() ? Optional.empty() : Optional.of(visible.getFirst());
    }

    /**
     * The authoritative server weather flags, not {@code Level.isRaining}. Those two report the
     * interpolated rain and thunder <em>levels</em>, which ramp in over many ticks after the storm
     * actually starts and ramp out again after it stops, so a simian reading them would report a
     * thunderstorm as ordinary rain for its whole ramp and would disagree with the level's own
     * saved state. Reading the flags keeps the charge a fact about the storm rather than about the
     * client side visual fade.
     */
    static Weather currentWeather(final ServerLevel level) {
        final var weather = level.getWeatherData();
        return StormSimianRules.weatherOf(weather.isRaining(), weather.isThundering());
    }

    /** The completed inspection. It counts curiosity satisfied and releases the claim, nothing more. */
    private static void completeInspection(final StormSimianEntity simian) {
        simian.stormSimianTransient().inspectedObject = null;
        simian.stormSimianCounters().inspectionsCompleted++;
    }
}
