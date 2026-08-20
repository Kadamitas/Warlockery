package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.HazardEscapeRules.Hazard;
import com.kadamitas.warlockery.entity.IronboundSentinelRules.Band;
import com.kadamitas.warlockery.entity.IronboundSentinelRules.Candidate;
import com.kadamitas.warlockery.entity.IronboundSentinelRules.Charge;
import com.kadamitas.warlockery.entity.IronboundSentinelRules.Interposition;
import com.kadamitas.warlockery.entity.IronboundSentinelRules.Legality;
import com.kadamitas.warlockery.entity.IronboundSentinelRules.Phase;
import com.kadamitas.warlockery.entity.IronboundSentinelRules.SocketAct;
import com.kadamitas.warlockery.entity.behavior.Cadence;
import com.kadamitas.warlockery.entity.behavior.Candidates;
import com.kadamitas.warlockery.entity.behavior.ReadBudget;
import com.kadamitas.warlockery.entity.behavior.RouteRequest;
import com.kadamitas.warlockery.entity.behavior.Ticks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The only server-side Ironbound Sentinel controller and the sole navigation writer for the species.
 *
 * <p>Every tick resolves exactly one band from {@link IronboundSentinelRules#band}, and only that
 * band may write navigation, effects or state. Nothing here iterates a dimension, enumerates a
 * player list, queries the whole ward, performs a routine block search, calls {@code level.isVillage},
 * creates an entity, edits a block, opens a container, reads an inventory, mutates an item, creates a
 * chunk ticket, calls {@code getChunk} or teleports. The only world query on the routine path is one
 * capped entity visit over one quadrant of the ward, at most once per twenty loaded ticks.</p>
 *
 * <p>Every duration below is a remaining loaded-tick countdown. An unloaded Sentinel performs no
 * decrement, sweep, bar, repel, strain change, transition step, sound, path or catch-up.</p>
 */
public final class IronboundSentinelRuntime {
    private static final CreatureKind KIND = CreatureKind.IRONBOUND_SENTINEL;
    /** Cadence for the one bounded footprint observation that catches contact and fire blocks. */
    private static final int HAZARD_OBSERVATION_TICKS = 20;
    private static final int FEEDBACK_PARTICLES = 8;

    private IronboundSentinelRuntime() {
    }

    /** Structural work counters proving the declared caps. Pass-local, never persisted. */
    public static final class Counters {
        long sweeps;
        long unchangedSweeps;
        long emptySweeps;
        long candidateVisits;
        long sightRays;
        long bindings;
        long releases;
        long bars;
        long repelAttempts;
        long repelHits;
        long bearingAdvances;
        long lookWrites;
        long navigationRequests;
        long routeFailures;
        long strainRises;
        long strainFalls;
        long seizes;
        long wakings;
        long standDowns;
        long socketSeats;
        long socketDraws;
        long socketPasses;
        long hazardInterruptions;
        long damageReactions;
        long staleAttributions;
        long restations;
        long returns;

        public long sweeps() { return sweeps; }
        public long unchangedSweeps() { return unchangedSweeps; }
        public long emptySweeps() { return emptySweeps; }
        public long candidateVisits() { return candidateVisits; }
        public long sightRays() { return sightRays; }
        public long bindings() { return bindings; }
        public long releases() { return releases; }
        public long bars() { return bars; }
        public long repelAttempts() { return repelAttempts; }
        public long repelHits() { return repelHits; }
        public long bearingAdvances() { return bearingAdvances; }
        public long lookWrites() { return lookWrites; }
        public long navigationRequests() { return navigationRequests; }
        public long routeFailures() { return routeFailures; }
        public long strainRises() { return strainRises; }
        public long strainFalls() { return strainFalls; }
        public long seizes() { return seizes; }
        public long wakings() { return wakings; }
        public long standDowns() { return standDowns; }
        public long socketSeats() { return socketSeats; }
        public long socketDraws() { return socketDraws; }
        public long socketPasses() { return socketPasses; }
        public long hazardInterruptions() { return hazardInterruptions; }
        public long damageReactions() { return damageReactions; }
        public long staleAttributions() { return staleAttributions; }
        public long restations() { return restations; }
        public long returns() { return returns; }
    }

    /**
     * Execution scratch rebuilt after every load. Losing any of it can delay work by one cadence but
     * can never replay a bar, a repel, a strain change or a transition. The bound subject and the
     * attacker attribution live here precisely so no save can resurrect one into a delayed strike.
     */
    public static final class TransientState {
        private boolean reconciled;
        private Phase phase = Phase.STILLED;
        private UUID subjectId;
        private UUID attackerId;
        private int attackerAgeTicks;
        private int subjectSightLossTicks;
        private int episodeTicks;
        private int repelCooldownTicks;
        private int seizeRemainingTicks;
        private int returnElapsedTicks;
        private int strainAccrualTicks;
        private int strainDecayTicks;
        private boolean hazardActive;
        private boolean wardClear = true;
        private Cadence sweep = Cadence.every(IronboundSentinelRules.SWEEP_TICKS);
        private Cadence revalidation = Cadence.every(IronboundSentinelRules.REVALIDATION_TICKS);
        private Cadence bearing = Cadence.every(IronboundSentinelRules.BEARING_ADVANCE_TICKS);
        private Cadence hazardObservation = Cadence.every(HAZARD_OBSERVATION_TICKS);
        private RouteRequest route = RouteRequest.every(IronboundSentinelRules.PATH_CADENCE_TICKS);
        private final UUID[] retained = new UUID[IronboundSentinelRules.RETAINED_IDENTITIES];
        private int retainedCount;

        public void resetForLoad() {
            reconciled = false;
            phase = Phase.STILLED;
            subjectId = null;
            attackerId = null;
            attackerAgeTicks = 0;
            subjectSightLossTicks = 0;
            episodeTicks = 0;
            repelCooldownTicks = 0;
            seizeRemainingTicks = 0;
            returnElapsedTicks = 0;
            strainAccrualTicks = 0;
            strainDecayTicks = 0;
            hazardActive = false;
            wardClear = true;
            sweep = Cadence.every(IronboundSentinelRules.SWEEP_TICKS);
            revalidation = Cadence.every(IronboundSentinelRules.REVALIDATION_TICKS);
            bearing = Cadence.every(IronboundSentinelRules.BEARING_ADVANCE_TICKS);
            hazardObservation = Cadence.every(HAZARD_OBSERVATION_TICKS);
            route = RouteRequest.every(IronboundSentinelRules.PATH_CADENCE_TICKS);
            clearRetained();
        }

        /**
         * Starts a fresh episode. Every accumulator that belongs to <em>an</em> episode is zeroed
         * here, so a ledger built up during vigil can never be inherited by the episode that
         * follows and released before it has done anything. The route request is deliberately
         * carried over untouched: its consecutive-failure run and any open backoff window describe
         * the terrain, not the episode, and resetting them would let a Sentinel standing in
         * unroutable ground re-request a path the moment anything walked past it.
         */
        void resetForEpisodeStart() {
            episodeTicks = 0;
            subjectSightLossTicks = 0;
            strainAccrualTicks = 0;
            repelCooldownTicks = 0;
        }

        void clearRetained() {
            for (int index = 0; index < retained.length; index++) {
                retained[index] = null;
            }
            retainedCount = 0;
        }

        public Phase phase() {
            return phase;
        }

        public Optional<UUID> boundSubject() {
            return Optional.ofNullable(subjectId);
        }

        public Optional<UUID> attributedAttacker() {
            return Optional.ofNullable(attackerId);
        }

        public boolean hazardActive() {
            return hazardActive;
        }

        public int episodeTicks() {
            return episodeTicks;
        }

        public int routeFailures() {
            return route.consecutiveFailures();
        }

        public int routeBackoffRemaining() {
            return route.backoffRemaining();
        }
    }

    // ---------------------------------------------------------------- the tick

    public static void tick(final IronboundSentinelEntity sentinel, final ServerLevel level) {
        final TransientState scratch = sentinel.sentinelTransient();
        reconcileOnLoad(sentinel, level, scratch);
        advanceLoadedTimers(scratch);
        advanceCharge(sentinel, level, scratch);
        observeHazard(sentinel, level, scratch);

        final IronboundSentinelState state = sentinel.sentinelState();
        final Band band = IronboundSentinelRules.band(
            state.charge(),
            scratch.phase,
            scratch.hazardActive,
            IronboundSentinelRules.seizeDue(state.strain()),
            scratch.subjectId != null,
            awayFromStation(sentinel, state)
        );
        switch (band) {
            case HAZARD -> tickHazard(sentinel, level, scratch);
            case SHUTDOWN -> tickShutdown(sentinel, scratch);
            case SEIZE -> tickSeize(sentinel, level, scratch);
            case EPISODE -> tickEpisode(sentinel, level, scratch);
            case RETURN -> tickReturn(sentinel, level, scratch);
            case ROUTINE -> tickRoutine(sentinel, level, scratch);
        }
    }

    /**
     * Runs once per load. The station is captured here rather than in the constructor because a
     * constructor has no position yet; capturing at the first server tick is the creation position
     * for every acquisition route. It is never re-derived from the current position afterwards,
     * so a Sentinel knocked into a river cannot adopt the river as its post.
     */
    private static void reconcileOnLoad(
        final IronboundSentinelEntity sentinel,
        final ServerLevel level,
        final TransientState scratch
    ) {
        if (scratch.reconciled) {
            return;
        }
        scratch.reconciled = true;
        final UUID identity = sentinel.getUUID();
        scratch.sweep = new Cadence(IronboundSentinelRules.SWEEP_TICKS,
            IronboundSentinelRules.sweepOffset(identity));
        scratch.bearing = new Cadence(IronboundSentinelRules.BEARING_ADVANCE_TICKS,
            IronboundSentinelRules.bearingOffset(identity));
        scratch.revalidation = Cadence.every(IronboundSentinelRules.REVALIDATION_TICKS);
        scratch.hazardObservation = Cadence.every(HAZARD_OBSERVATION_TICKS);
        scratch.route = RouteRequest.every(IronboundSentinelRules.PATH_CADENCE_TICKS);

        final IronboundSentinelState state = sentinel.sentinelState();
        final BlockPos here = sentinel.blockPosition();
        final boolean needsStation = state.station()
            .map(station -> IronboundSentinelRules.stationCorrupt(station.distSqr(here)))
            .orElse(true);
        if (needsStation) {
            sentinel.setSentinelState(state.stationedAt(here));
            sentinel.sentinelCounters().restations++;
        }
        scratch.phase = IronboundSentinelRules.phaseFor(sentinel.sentinelState().charge());
        sentinel.normalizeLifecycle();
        cancel(sentinel, level, scratch);
    }

    /** Loaded-tick countdowns and cadences. Nothing here decides that anything has ended. */
    private static void advanceLoadedTimers(final TransientState scratch) {
        scratch.sweep = scratch.sweep.step();
        scratch.revalidation = scratch.revalidation.step();
        scratch.bearing = scratch.bearing.step();
        scratch.hazardObservation = scratch.hazardObservation.step();
        scratch.route = scratch.route.step();
        scratch.repelCooldownTicks = Ticks.decrementLoaded(scratch.repelCooldownTicks);
        scratch.seizeRemainingTicks = Ticks.decrementLoaded(scratch.seizeRemainingTicks);
        scratch.attackerAgeTicks = Math.min(scratch.attackerAgeTicks + 1,
            IronboundSentinelRules.ATTRIBUTION_FRESHNESS_TICKS + 1);
        if (scratch.attackerId != null
            && !IronboundSentinelRules.attributionFresh(scratch.attackerAgeTicks)) {
            scratch.attackerId = null;
        }
    }

    /**
     * The one tick branch that owns ending a charge transition. It decrements every loaded tick
     * whichever band owns navigation, and only it may observe a transitional arm at zero and end it,
     * because ending is also what emits the bounded feedback and what clears strain on the way into
     * {@code INERT}. The state record deliberately performs no such reconciliation of its own.
     */
    private static void advanceCharge(
        final IronboundSentinelEntity sentinel,
        final ServerLevel level,
        final TransientState scratch
    ) {
        final IronboundSentinelState state = sentinel.sentinelState();
        if (!state.charge().transitional()) {
            return;
        }
        if (state.transitionRemaining() > 0) {
            sentinel.setSentinelState(
                state.withTransitionRemaining(Ticks.decrementLoaded(state.transitionRemaining()))
            );
            return;
        }
        final Charge settled = IronboundSentinelRules.chargeAfterTransition(state.charge());
        sentinel.setSentinelState(state.withCharge(settled));
        scratch.phase = IronboundSentinelRules.phaseFor(settled);
        feedback(sentinel, level);
        if (settled == Charge.CHARGED) {
            sentinel.sentinelCounters().wakings++;
        } else {
            sentinel.sentinelCounters().standDowns++;
        }
    }

    /**
     * Constant entity flags every loaded tick with no block enumeration, plus one bounded footprint
     * observation at most every twenty ticks that catches fire and contact blocks the flags cannot
     * see. Neither reads a block outside the shared hazard runtime's own declared box.
     */
    private static void observeHazard(
        final IronboundSentinelEntity sentinel,
        final ServerLevel level,
        final TransientState scratch
    ) {
        final boolean flagged = sentinel.isOnFire()
            || sentinel.isInLava()
            || sentinel.isUnderWater() && sentinel.getAirSupply() < sentinel.getMaxAirSupply();
        if (flagged) {
            scratch.hazardActive = true;
            return;
        }
        if (!scratch.hazardObservation.due()) {
            return;
        }
        scratch.hazardObservation = scratch.hazardObservation.arm();
        final Optional<Hazard> observed = HazardEscapeRuntime.currentHazard(sentinel, level);
        scratch.hazardActive = observed
            .filter(hazard -> HazardEscapeRules.shouldEscape(KIND, hazard))
            .isPresent();
    }

    // ---------------------------------------------------------------- bands

    /**
     * Hazard outranks everything including the shutdown band, because the invariant that a species
     * escapes hazards it is not immune to outranks the concept. The episode is torn down before the
     * shared hazard runtime is allowed to write navigation, so no queued strike survives the escape.
     */
    private static void tickHazard(
        final IronboundSentinelEntity sentinel,
        final ServerLevel level,
        final TransientState scratch
    ) {
        if (scratch.phase != Phase.EVADE) {
            cancel(sentinel, level, scratch);
            scratch.phase = Phase.EVADE;
            sentinel.sentinelCounters().hazardInterruptions++;
        }
        HazardEscapeRuntime.tick(sentinel, level, KIND);
    }

    /** Inert or going down: no sweep, no perception, no path, no look write, nothing at all. */
    private static void tickShutdown(
        final IronboundSentinelEntity sentinel,
        final TransientState scratch
    ) {
        final Phase implied = IronboundSentinelRules.phaseFor(sentinel.sentinelState().charge());
        if (scratch.phase != implied) {
            scratch.phase = implied;
            sentinel.getNavigation().stop();
            sentinel.getMoveControl().setWait();
        }
    }

    /** The strain cap: stand still for forty ticks, then draw the charge. No rampage, no growth. */
    private static void tickSeize(
        final IronboundSentinelEntity sentinel,
        final ServerLevel level,
        final TransientState scratch
    ) {
        if (scratch.phase != Phase.SEIZE) {
            cancel(sentinel, level, scratch);
            scratch.phase = Phase.SEIZE;
            scratch.seizeRemainingTicks = IronboundSentinelRules.SEIZE_TICKS;
            sentinel.sentinelCounters().seizes++;
            return;
        }
        if (scratch.seizeRemainingTicks > 0) {
            return;
        }
        sentinel.setSentinelState(sentinel.sentinelState().withCharge(Charge.STANDING_DOWN));
        scratch.phase = Phase.UNDOING;
        feedback(sentinel, level);
    }

    /** One bound subject, barred inside the tether and repelled at ordinary melee reach. */
    private static void tickEpisode(
        final IronboundSentinelEntity sentinel,
        final ServerLevel level,
        final TransientState scratch
    ) {
        scratch.episodeTicks++;
        if (scratch.episodeTicks >= IronboundSentinelRules.EPISODE_CAP_TICKS) {
            release(sentinel, level, scratch);
            return;
        }
        final LivingEntity subject = resolveSubject(sentinel, level, scratch);
        if (subject == null) {
            release(sentinel, level, scratch);
            return;
        }
        final IronboundSentinelState state = sentinel.sentinelState();
        final BlockPos station = state.station().orElseGet(sentinel::blockPosition);
        final double subjectDistanceSqr = sentinel.distanceToSqr(subject);
        final boolean insideWard = insideWard(station, subject);

        if (scratch.revalidation.due()) {
            scratch.revalidation = scratch.revalidation.arm();
            if (!revalidate(sentinel, level, scratch, subject, station, subjectDistanceSqr)) {
                release(sentinel, level, scratch);
                return;
            }
        }

        accrueStrain(sentinel, scratch, insideWard);

        if (sentinel.isWithinMeleeAttackRange(subject)) {
            scratch.phase = Phase.REPEL;
            repel(sentinel, level, scratch, subject);
            return;
        }
        if (scratch.phase != Phase.BAR) {
            scratch.phase = Phase.BAR;
            sentinel.sentinelCounters().bars++;
        }
        bar(sentinel, scratch, subject, station);
    }

    /** Walks back to the station it already has. It never re-stations except on the timeout path. */
    private static void tickReturn(
        final IronboundSentinelEntity sentinel,
        final ServerLevel level,
        final TransientState scratch
    ) {
        if (scratch.phase != Phase.RETURN) {
            scratch.phase = Phase.RETURN;
            scratch.returnElapsedTicks = 0;
            sentinel.sentinelCounters().returns++;
        }
        scratch.returnElapsedTicks++;
        final IronboundSentinelState state = sentinel.sentinelState();
        if (scratch.returnElapsedTicks >= IronboundSentinelRules.RETURN_TIMEOUT_TICKS
            || scratch.route.consecutiveFailures()
                >= IronboundSentinelRules.ROUTE_FAILURES_BEFORE_BACKOFF) {
            restationInPlace(sentinel, scratch);
            return;
        }
        final BlockPos station = state.station().orElseGet(sentinel::blockPosition);
        requestRoute(sentinel, scratch,
            new Interposition(station.getX() + 0.5D, station.getZ() + 0.5D), station.getY());
    }

    /** Standing vigil: one capped quadrant sweep, one bearing advance, and strain decay. */
    private static void tickRoutine(
        final IronboundSentinelEntity sentinel,
        final ServerLevel level,
        final TransientState scratch
    ) {
        if (scratch.phase != Phase.VIGIL) {
            scratch.phase = Phase.VIGIL;
            sentinel.getNavigation().stop();
            sentinel.getMoveControl().setWait();
            sentinel.setDeltaMovement(0.0D, sentinel.getDeltaMovement().y, 0.0D);
        }
        if (scratch.sweep.due()) {
            // Armed before the search runs, not inside its success branch, so a sweep that
            // qualifies nothing costs one cadence rather than repeating on every tick.
            scratch.sweep = scratch.sweep.arm();
            sweep(sentinel, level, scratch);
        }
        if (scratch.bearing.due()) {
            scratch.bearing = scratch.bearing.arm();
            advanceBearing(sentinel, scratch);
        }
        decayStrain(sentinel, scratch);
    }

    // ---------------------------------------------------------------- the sweep

    /**
     * Exactly one quadrant of the ward, at the current bearing, as one capped entity query. The four
     * bearings each include the station's own column on both axes, so their union is the whole ward
     * and the position the Sentinel is standing on is evaluated on every bearing rather than on
     * none. The retained identity set is per bearing and is cleared when the bearing advances, so an
     * unchanged quadrant costs nothing further and a stationary candidate is still re-judged within
     * one full circuit of attention.
     */
    private static void sweep(
        final IronboundSentinelEntity sentinel,
        final ServerLevel level,
        final TransientState scratch
    ) {
        final Counters counters = sentinel.sentinelCounters();
        counters.sweeps++;
        final IronboundSentinelState state = sentinel.sentinelState();
        final BlockPos station = state.station().orElseGet(sentinel::blockPosition);
        final List<LivingEntity> visited = visitQuadrant(sentinel, level, station, state.bearing());
        if (visited.isEmpty()) {
            counters.emptySweeps++;
            scratch.wardClear = true;
            scratch.clearRetained();
            return;
        }
        visited.sort(Candidates.byDistanceThenIdentity(sentinel::distanceToSqr, Entity::getUUID));
        if (retainedMatches(scratch, visited)) {
            counters.unchangedSweeps++;
            return;
        }
        storeRetained(scratch, visited);
        scratch.wardClear = false;

        final ReadBudget sight = ReadBudget.of(IronboundSentinelRules.SWEEP_SIGHT_RAYS);
        for (int index = 0; index < visited.size(); index++) {
            final LivingEntity candidate = visited.get(index);
            final Candidate facts = facts(sentinel, station, candidate, true);
            if (!IronboundSentinelRules.legality(state.charge(), facts).eligible()) {
                continue;
            }
            // The ray is charged before the trace runs, so a candidate rejected for being unseen
            // costs exactly what it actually spent and the declared two-ray cap genuinely binds.
            if (!sight.charge()) {
                return;
            }
            counters.sightRays++;
            final boolean seen = sentinel.getSensing().hasLineOfSight(candidate);
            if (!seen) {
                continue;
            }
            bind(sentinel, scratch, candidate);
            return;
        }
    }

    private static List<LivingEntity> visitQuadrant(
        final IronboundSentinelEntity sentinel,
        final ServerLevel level,
        final BlockPos station,
        final int bearing
    ) {
        final Counters counters = sentinel.sentinelCounters();
        final List<LivingEntity> visited =
            new ArrayList<>(IronboundSentinelRules.SWEEP_ENTITY_VISITS);
        com.kadamitas.warlockery.entity.BoundedEntityQuery.visit(level, 
            EntityTypeTest.forClass(LivingEntity.class),
            quadrantBox(station, bearing),
            candidate -> {
                // Charged before any filter can reject: a candidate the legality function will
                // refuse still costs one visit, so the cap bounds the query and not the outcome.
                counters.candidateVisits++;
                visited.add(candidate);
                return visited.size() >= IronboundSentinelRules.SWEEP_ENTITY_VISITS
                    ? AbortableIterationConsumer.Continuation.ABORT
                    : AbortableIterationConsumer.Continuation.CONTINUE;
            }
        );
        return visited;
    }

    static AABB quadrantBox(final BlockPos station, final int bearing) {
        final int signX = IronboundSentinelRules.quadrantSignX(bearing);
        final int signZ = IronboundSentinelRules.quadrantSignZ(bearing);
        final double centreX = station.getX() + 0.5D;
        final double centreY = station.getY() + 0.5D;
        final double centreZ = station.getZ() + 0.5D;
        return new AABB(
            centreX + IronboundSentinelRules.quadrantLow(signX),
            centreY - IronboundSentinelRules.WARD_VERTICAL,
            centreZ + IronboundSentinelRules.quadrantLow(signZ),
            centreX + IronboundSentinelRules.quadrantHigh(signX),
            centreY + IronboundSentinelRules.WARD_VERTICAL,
            centreZ + IronboundSentinelRules.quadrantHigh(signZ)
        );
    }

    private static boolean retainedMatches(
        final TransientState scratch,
        final List<LivingEntity> visited
    ) {
        if (scratch.retainedCount != visited.size()) {
            return false;
        }
        for (int index = 0; index < visited.size(); index++) {
            if (!visited.get(index).getUUID().equals(scratch.retained[index])) {
                return false;
            }
        }
        return true;
    }

    private static void storeRetained(
        final TransientState scratch,
        final List<LivingEntity> visited
    ) {
        scratch.clearRetained();
        final int stored = Math.min(visited.size(), scratch.retained.length);
        for (int index = 0; index < stored; index++) {
            scratch.retained[index] = visited.get(index).getUUID();
        }
        scratch.retainedCount = stored;
    }

    private static void advanceBearing(
        final IronboundSentinelEntity sentinel,
        final TransientState scratch
    ) {
        final IronboundSentinelState state = sentinel.sentinelState();
        final int next = IronboundSentinelRules.nextBearing(state.bearing());
        sentinel.setSentinelState(state.withBearing(next));
        scratch.clearRetained();
        scratch.wardClear = true;
        final BlockPos station = state.station().orElseGet(sentinel::blockPosition);
        final Interposition centre = IronboundSentinelRules.quadrantCentre(
            station.getX() + 0.5D, station.getZ() + 0.5D, next
        );
        // Look only. The bearing turns the Sentinel's attention, never its feet.
        sentinel.getLookControl().setLookAt(centre.x(), sentinel.getEyeY(), centre.z());
        final Counters counters = sentinel.sentinelCounters();
        counters.bearingAdvances++;
        counters.lookWrites++;
    }

    // ---------------------------------------------------------------- the episode

    private static void bind(
        final IronboundSentinelEntity sentinel,
        final TransientState scratch,
        final LivingEntity subject
    ) {
        scratch.subjectId = subject.getUUID();
        scratch.resetForEpisodeStart();
        scratch.phase = Phase.BAR;
        scratch.wardClear = false;
        sentinel.setTarget(subject);
        sentinel.sentinelCounters().bindings++;
        sentinel.sentinelCounters().bars++;
    }

    private static LivingEntity resolveSubject(
        final IronboundSentinelEntity sentinel,
        final ServerLevel level,
        final TransientState scratch
    ) {
        if (scratch.subjectId == null) {
            return null;
        }
        return level.getEntity(scratch.subjectId) instanceof LivingEntity subject
            && subject.isAlive()
            && !subject.isRemoved()
            ? subject
            : null;
    }

    /**
     * Retention on its own cadence: both anchors, the retention radius, the full legality function
     * and one sight trace with an explicit continuous-loss grace. Membership never retains on its
     * own; a subject that walks behind a wall is released after the grace rather than followed.
     */
    private static boolean revalidate(
        final IronboundSentinelEntity sentinel,
        final ServerLevel level,
        final TransientState scratch,
        final LivingEntity subject,
        final BlockPos station,
        final double subjectDistanceSqr
    ) {
        if (!IronboundSentinelRules.withinRetention(subjectDistanceSqr)) {
            return false;
        }
        sentinel.sentinelCounters().sightRays++;
        final boolean seen = sentinel.getSensing().hasLineOfSight(subject);
        final Candidate facts = facts(sentinel, station, subject, seen);
        final Legality verdict =
            IronboundSentinelRules.legality(sentinel.sentinelState().charge(), facts);
        if (verdict == Legality.UNSEEN) {
            scratch.subjectSightLossTicks += IronboundSentinelRules.REVALIDATION_TICKS;
            return scratch.subjectSightLossTicks
                < IronboundSentinelRules.SIGHT_LOSS_RELEASE_TICKS;
        }
        if (!verdict.eligible()) {
            return false;
        }
        scratch.subjectSightLossTicks = 0;
        return true;
    }

    private static void bar(
        final IronboundSentinelEntity sentinel,
        final TransientState scratch,
        final LivingEntity subject,
        final BlockPos station
    ) {
        final Interposition point = IronboundSentinelRules.interposition(
            subject.getX(),
            subject.getZ(),
            station.getX() + 0.5D,
            station.getZ() + 0.5D,
            sentinel.getBbWidth() + subject.getBbWidth()
        );
        requestRoute(sentinel, scratch, point, subject.getBlockY());
    }

    private static void repel(
        final IronboundSentinelEntity sentinel,
        final ServerLevel level,
        final TransientState scratch,
        final LivingEntity subject
    ) {
        if (scratch.repelCooldownTicks > 0) {
            return;
        }
        scratch.repelCooldownTicks = IronboundSentinelRules.REPEL_CADENCE_TICKS;
        sentinel.getNavigation().stop();
        sentinel.getMoveControl().setWait();
        sentinel.sentinelCounters().repelAttempts++;
        if (!sentinel.getSensing().hasLineOfSight(subject)) {
            return;
        }
        sentinel.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (sentinel.doHurtTarget(level, subject)) {
            sentinel.sentinelCounters().repelHits++;
        }
    }

    private static void release(
        final IronboundSentinelEntity sentinel,
        final ServerLevel level,
        final TransientState scratch
    ) {
        if (scratch.subjectId == null) {
            return;
        }
        sentinel.sentinelCounters().releases++;
        cancel(sentinel, level, scratch);
    }

    // ---------------------------------------------------------------- strain

    private static void accrueStrain(
        final IronboundSentinelEntity sentinel,
        final TransientState scratch,
        final boolean insideWard
    ) {
        if (!insideWard) {
            scratch.strainAccrualTicks = 0;
            return;
        }
        scratch.strainAccrualTicks++;
        if (scratch.strainAccrualTicks < IronboundSentinelRules.STRAIN_ACCRUAL_TICKS) {
            return;
        }
        scratch.strainAccrualTicks = 0;
        final IronboundSentinelState state = sentinel.sentinelState();
        final int raised = IronboundSentinelRules.strainAfterHeldSubject(state.strain());
        if (raised != state.strain()) {
            sentinel.setSentinelState(state.withStrain(raised));
            sentinel.sentinelCounters().strainRises++;
        }
    }

    private static void decayStrain(
        final IronboundSentinelEntity sentinel,
        final TransientState scratch
    ) {
        if (scratch.subjectId != null || !scratch.wardClear) {
            scratch.strainDecayTicks = 0;
            return;
        }
        scratch.strainDecayTicks++;
        if (scratch.strainDecayTicks < IronboundSentinelRules.STRAIN_DECAY_TICKS) {
            return;
        }
        scratch.strainDecayTicks = 0;
        final IronboundSentinelState state = sentinel.sentinelState();
        final int lowered = IronboundSentinelRules.strainAfterDecay(state.strain());
        if (lowered != state.strain()) {
            sentinel.setSentinelState(state.withStrain(lowered));
            sentinel.sentinelCounters().strainFalls++;
        }
    }

    // ---------------------------------------------------------------- routes

    /**
     * The single route gate. Every band that moves goes through it, so the path cadence, the
     * consecutive-failure run and the backoff window are shared rather than restated, and a search
     * that qualifies nothing arms the cadence exactly as a routing failure does.
     */
    private static void requestRoute(
        final IronboundSentinelEntity sentinel,
        final TransientState scratch,
        final Interposition point,
        final int destinationY
    ) {
        final Interposition clamped = clampInsideTether(sentinel, point);
        final RouteRequest.Attempt<Interposition> attempt = scratch.route.attempt(
            IronboundSentinelRules.ROUTE_BACKOFF,
            () -> Optional.of(clamped),
            destination -> {
                sentinel.sentinelCounters().navigationRequests++;
                final Path path = sentinel.getNavigation().createPath(
                    destination.x(), destinationY, destination.z(), 0
                );
                final boolean accepted = path != null
                    && sentinel.getNavigation().moveTo(path, IronboundSentinelRules.ROUTE_SPEED);
                return RouteRequest.outcomeOf(path != null, path != null && path.canReach(), accepted);
            }
        );
        final boolean failed = attempt.request().consecutiveFailures()
            > scratch.route.consecutiveFailures();
        scratch.route = attempt.request();
        if (failed) {
            sentinel.sentinelCounters().routeFailures++;
            final IronboundSentinelState state = sentinel.sentinelState();
            final int raised = IronboundSentinelRules.strainAfterRouteFailure(state.strain());
            if (raised != state.strain()) {
                sentinel.setSentinelState(state.withStrain(raised));
                sentinel.sentinelCounters().strainRises++;
            }
        }
    }

    private static Interposition clampInsideTether(
        final IronboundSentinelEntity sentinel,
        final Interposition point
    ) {
        return sentinel.sentinelState().station()
            .map(station -> IronboundSentinelRules.clampToTether(
                point.x(), point.z(), station.getX() + 0.5D, station.getZ() + 0.5D
            ))
            .orElse(point);
    }

    /** The timeout and exhausted-route path: keep the post you can actually stand on. */
    private static void restationInPlace(
        final IronboundSentinelEntity sentinel,
        final TransientState scratch
    ) {
        sentinel.setSentinelState(sentinel.sentinelState().stationedAt(sentinel.blockPosition()));
        sentinel.sentinelCounters().restations++;
        scratch.returnElapsedTicks = 0;
        scratch.phase = Phase.VIGIL;
        sentinel.getNavigation().stop();
        sentinel.getMoveControl().setWait();
    }

    // ---------------------------------------------------------------- cancellation

    /**
     * The one teardown every cancellation trigger performs. The durable charge, transition counter,
     * station, bearing and strain are preserved and never replayed; everything a save could turn
     * into a delayed strike is dropped.
     */
    static void cancel(
        final IronboundSentinelEntity sentinel,
        final ServerLevel level,
        final TransientState scratch
    ) {
        scratch.subjectId = null;
        scratch.attackerId = null;
        scratch.attackerAgeTicks = 0;
        scratch.subjectSightLossTicks = 0;
        scratch.episodeTicks = 0;
        scratch.repelCooldownTicks = 0;
        scratch.returnElapsedTicks = 0;
        scratch.strainAccrualTicks = 0;
        scratch.clearRetained();
        sentinel.setTarget(null);
        sentinel.getNavigation().stop();
        sentinel.getMoveControl().setWait();
        sentinel.setDeltaMovement(0.0D, sentinel.getDeltaMovement().y, 0.0D);
        scratch.phase = IronboundSentinelRules.phaseFor(sentinel.sentinelState().charge());
    }

    /** Called from the entity on any removal, so no held scratch outlives the entity. */
    public static void onRemoved(final IronboundSentinelEntity sentinel) {
        sentinel.sentinelTransient().resetForLoad();
    }

    // ---------------------------------------------------------------- events

    /**
     * DC3. An accepted-damage attribution rouses the Sentinel and may make or replace the one
     * subject, but only when the attribution is at most forty ticks old on the attacker's own clock
     * and only when the attacker passes every rung of the same legality function, sight included.
     * Damage never raises strain: that is exactly what separates a keeper from a grudge.
     */
    public static void onAcceptedDamage(
        final IronboundSentinelEntity sentinel,
        final ServerLevel level,
        final DamageSource source
    ) {
        if (!(source.getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        final TransientState scratch = sentinel.sentinelTransient();
        final int age = attacker.tickCount - attacker.getLastHurtMobTimestamp();
        if (!IronboundSentinelRules.attributionFresh(age)) {
            sentinel.sentinelCounters().staleAttributions++;
            return;
        }
        scratch.attackerId = attacker.getUUID();
        scratch.attackerAgeTicks = 0;
        sentinel.sentinelCounters().damageReactions++;
        if (!sentinel.sentinelState().charge().mayAct()) {
            return;
        }
        final BlockPos station =
            sentinel.sentinelState().station().orElseGet(sentinel::blockPosition);
        sentinel.sentinelCounters().sightRays++;
        final Candidate facts = facts(sentinel, station, attacker,
            sentinel.getSensing().hasLineOfSight(attacker));
        if (IronboundSentinelRules.legality(sentinel.sentinelState().charge(), facts).eligible()) {
            bind(sentinel, scratch, attacker);
        }
    }

    /** The pure half of the socket act, so a unit test can reach it without a level. */
    public static SocketAct socketDecision(
        final IronboundSentinelEntity sentinel,
        final Player player
    ) {
        final Vec3 toPlayer = player.position().subtract(sentinel.position());
        final double lengthSqr = toPlayer.lengthSqr();
        final double lookDot = lengthSqr < 1.0E-6D
            ? 1.0D
            : sentinel.getLookAngle().normalize().dot(toPlayer.normalize());
        return IronboundSentinelRules.socketAct(
            sentinel.sentinelState().charge(),
            player.isCrouching(),
            player.getMainHandItem().isEmpty(),
            player.getOffhandItem().isEmpty(),
            sentinel.distanceToSqr(player),
            lookDot,
            sentinel.sentinelTransient().boundSubject()
                .filter(player.getUUID()::equals)
                .isPresent()
        );
    }

    /**
     * The effect half. One idempotent transition owns the whole install and teardown, so a Sentinel
     * that is stood down cannot leave a queued strike, a live navigation target or a running timer
     * behind, and a Sentinel that is woken starts from a clean scratch with no inherited ledger.
     */
    public static net.minecraft.world.InteractionResult applySocketAct(
        final IronboundSentinelEntity sentinel,
        final ServerLevel level,
        final SocketAct act
    ) {
        final TransientState scratch = sentinel.sentinelTransient();
        final Counters counters = sentinel.sentinelCounters();
        switch (act) {
            case SEAT -> {
                sentinel.setSentinelState(sentinel.sentinelState().withCharge(Charge.WAKING));
                counters.socketSeats++;
            }
            case DRAW -> {
                sentinel.setSentinelState(
                    sentinel.sentinelState().withCharge(Charge.STANDING_DOWN)
                );
                counters.socketDraws++;
            }
            case PASS -> {
                counters.socketPasses++;
                return net.minecraft.world.InteractionResult.PASS;
            }
        }
        cancel(sentinel, level, scratch);
        feedback(sentinel, level);
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    /**
     * The final absolute offence gate, reached from {@code canAttack}. The Sentinel can only ever
     * strike the one subject its own runtime has already bound, so an external {@code setTarget}
     * cannot turn a ward keeper into a hunter.
     */
    public static boolean legalSubject(
        final IronboundSentinelEntity sentinel,
        final LivingEntity target
    ) {
        return sentinel.sentinelState().charge().mayAct()
            && target != null
            && sentinel.sentinelTransient().boundSubject()
                .filter(target.getUUID()::equals)
                .isPresent();
    }

    // ---------------------------------------------------------------- shared helpers

    private static boolean awayFromStation(
        final IronboundSentinelEntity sentinel,
        final IronboundSentinelState state
    ) {
        return state.station()
            .map(station -> !IronboundSentinelRules.atStation(
                station.distToCenterSqr(sentinel.position())
            ))
            .orElse(false);
    }

    private static boolean insideWard(final BlockPos station, final Entity candidate) {
        return IronboundSentinelRules.insideWard(
            candidate.getX() - (station.getX() + 0.5D),
            candidate.getY() - (station.getY() + 0.5D),
            candidate.getZ() - (station.getZ() + 0.5D)
        );
    }

    /**
     * Everything the ordered legality function needs, gathered without resolving anything further.
     * Species is deliberately absent: a villager, an iron golem, a familiar, another Warlockery kind
     * and a player are described by exactly the same nine facts.
     */
    private static Candidate facts(
        final IronboundSentinelEntity sentinel,
        final BlockPos station,
        final LivingEntity candidate,
        final boolean seen
    ) {
        final boolean creativeOrSpectator = candidate instanceof Player player
            && (player.isCreative() || player.isSpectator());
        return new Candidate(
            candidate == sentinel,
            candidate instanceof IronboundSentinelEntity,
            CreatureBehaviorState.isOwnedBy(sentinel, candidate.getUUID()),
            creativeOrSpectator,
            candidate.isAlive() && !candidate.isRemoved(),
            occupied(candidate),
            insideWard(station, candidate),
            IronboundSentinelRules.withinReach(sentinel.distanceToSqr(candidate)),
            seen
        );
    }

    /**
     * Trade, sleep, raid, panic and breeding are all one rung: a party already occupied by one of
     * them is never bound and a bound subject that enters one is released. No cross-family internal
     * is consulted, only the vanilla surfaces every mob already exposes.
     */
    private static boolean occupied(final LivingEntity candidate) {
        if (candidate.isSleeping()) {
            return true;
        }
        if (candidate instanceof Player player && player.isSleeping()) {
            return true;
        }
        if (candidate instanceof net.minecraft.world.entity.npc.villager.AbstractVillager villager
            && villager.isTrading()) {
            return true;
        }
        if (candidate instanceof net.minecraft.world.entity.raid.Raider raider
            && raider.hasActiveRaid()) {
            return true;
        }
        return candidate instanceof net.minecraft.world.entity.animal.Animal animal
            && animal.isInLove();
    }

    /** One existing sound and eight existing particles. No new registration, no message, no name. */
    private static void feedback(final IronboundSentinelEntity sentinel, final ServerLevel level) {
        level.playSound(null, sentinel.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR,
            SoundSource.HOSTILE, 0.6F, 1.0F);
        level.sendParticles(ParticleTypes.CRIT, sentinel.getX(), sentinel.getY() + 1.2D,
            sentinel.getZ(), FEEDBACK_PARTICLES, 0.25D, 0.4D, 0.25D, 0.0D);
    }
}
