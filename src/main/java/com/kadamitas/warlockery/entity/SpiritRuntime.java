package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.SpiritRules.AttackerObservation;
import com.kadamitas.warlockery.entity.SpiritRules.AttendCandidate;
import com.kadamitas.warlockery.entity.SpiritRules.BandAction;
import com.kadamitas.warlockery.entity.SpiritRules.GuardEnd;
import com.kadamitas.warlockery.entity.SpiritRules.Phase;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * The only server-side Spirit behavior controller and the sole ordinary navigation writer for
 * this species. Every scan, block read, path request, pulse and strike is counted against the
 * declared hard budgets. Nothing here enumerates a dimension, forces a chunk, iterates entities
 * globally, edits a block or an inventory, writes another entity's persistent state, or
 * proactively targets a player.
 *
 * <p>The approved outward mutations are exactly two: the preserved Night Vision owner aura, and
 * at most one ordinary attributed strike per bounded defence window against the owner's recent
 * valid direct attacker.</p>
 */
public final class SpiritRuntime {
    private SpiritRuntime() {
    }

    /** Structural work counters proving the exact caps. Pass-local, never persisted. */
    public static final class Counters {
        long attendScans;
        long attendReads;
        long attendCandidates;
        long blockReads;
        long proximityVisits;
        long lineOfSightChecks;
        long navigationRequests;
        long safeSearches;
        long safeCandidateVisits;
        long waryReactions;
        long attendPulses;
        long warnPulses;
        long defencesOpened;
        long defenceWindowsOpened;
        long strikes;
        long auraPulses;
        long ownerRecalls;
        long hazardInterruptions;
        long guardsEnded;

        public long attendScans() { return attendScans; }
        public long attendReads() { return attendReads; }
        public long attendCandidates() { return attendCandidates; }
        public long blockReads() { return blockReads; }
        public long proximityVisits() { return proximityVisits; }
        public long lineOfSightChecks() { return lineOfSightChecks; }
        public long navigationRequests() { return navigationRequests; }
        public long safeSearches() { return safeSearches; }
        public long safeCandidateVisits() { return safeCandidateVisits; }
        public long waryReactions() { return waryReactions; }
        public long attendPulses() { return attendPulses; }
        public long warnPulses() { return warnPulses; }
        public long defencesOpened() { return defencesOpened; }
        public long defenceWindowsOpened() { return defenceWindowsOpened; }
        public long strikes() { return strikes; }
        public long auraPulses() { return auraPulses; }
        public long ownerRecalls() { return ownerRecalls; }
        public long hazardInterruptions() { return hazardInterruptions; }
        public long guardsEnded() { return guardsEnded; }
    }

    /**
     * Execution scratch rebuilt after every load. Nothing here is meaning: losing it can delay
     * work by one cadence but can never replay a pulse, an aura, a strike, or a path.
     */
    public static final class TransientState {
        boolean reconciled;
        int pathCooldownTicks;
        int hazardCooldownTicks;
        int proximityCooldownTicks;
        int attendCooldownTicks;
        int wanderCooldownTicks;
        boolean hazardActive;
        BlockPos destination;
        /**
         * The last observed distance to the nearest tracked player, refreshed on the proximity
         * cadence. It is scratch, not meaning: a fresh load simply reports no one nearby, which
         * makes the Spirit look separated and can only delay an attendance by one cadence.
         */
        double nearestPlayerDistanceSquared = Double.MAX_VALUE;

        public void resetForLoad() {
            reconciled = false;
            pathCooldownTicks = 0;
            hazardCooldownTicks = 0;
            proximityCooldownTicks = 0;
            attendCooldownTicks = 0;
            wanderCooldownTicks = 0;
            hazardActive = false;
            destination = null;
            nearestPlayerDistanceSquared = Double.MAX_VALUE;
        }

        public boolean hazardActive() {
            return hazardActive;
        }
    }

    public static void tick(final SpiritEntity spirit, final ServerLevel level) {
        reconcileOnLoad(spirit, level);
        advanceLoadedTimers(spirit);
        if (tickHazard(spirit, level)) {
            return;
        }
        final boolean bound = CreatureBehaviorState.owner(spirit).isPresent();
        if (!bound && isGuarding(spirit)) {
            releaseGuard(spirit);
        }
        if (bound) {
            tickBound(spirit, level);
            return;
        }
        if (spirit.spiritState().phase() == Phase.BOUND) {
            spirit.setSpiritState(spirit.spiritState().unbind());
            spirit.getNavigation().stop();
            return;
        }
        switch (spirit.spiritState().phase()) {
            case WARY -> tickWary(spirit, level);
            case ATTEND -> tickAttend(spirit, level);
            case RECOVER, WANDER -> tickFree(spirit, level);
            case BOUND, WARN, DEFEND -> tickFree(spirit, level);
        }
    }

    private static boolean isGuarding(final SpiritEntity spirit) {
        final Phase phase = spirit.spiritState().phase();
        return phase == Phase.WARN || phase == Phase.DEFEND;
    }

    // ---------------------------------------------------------------- lifecycle

    /**
     * Fixture seam. The one-shot load reconciliation re-seeds the proximity and wander stagger
     * from inside the first {@link #tick}, so a fixture that clears those cadences immediately
     * before that first tick would have them silently overwritten and would observe no decision.
     * Running the real reconciliation up front settles it once, leaving the live path unchanged.
     */
    static void reconcileForFixture(final SpiritEntity spirit, final ServerLevel level) {
        reconcileOnLoad(spirit, level);
    }

    private static void reconcileOnLoad(final SpiritEntity spirit, final ServerLevel level) {
        final TransientState scratch = spirit.spiritTransient();
        if (scratch.reconciled) {
            return;
        }
        scratch.reconciled = true;
        scratch.proximityCooldownTicks =
            SpiritRules.stableOffset(spirit.getUUID(), SpiritRules.PROXIMITY_INTERVAL_TICKS);
        scratch.wanderCooldownTicks =
            SpiritRules.stableOffset(spirit.getUUID(), SpiritRules.WANDER_INTERVAL_TICKS);
        SpiritState state = spirit.spiritState();
        if (CreatureBehaviorState.owner(spirit).isPresent()
            && (state.phase() == Phase.WARY || state.phase() == Phase.ATTEND)) {
            state = state.bind();
        }
        spirit.setSpiritState(state);
    }

    private static void advanceLoadedTimers(final SpiritEntity spirit) {
        final TransientState scratch = spirit.spiritTransient();
        scratch.pathCooldownTicks = Math.max(0, scratch.pathCooldownTicks - 1);
        scratch.hazardCooldownTicks = Math.max(0, scratch.hazardCooldownTicks - 1);
        scratch.proximityCooldownTicks = Math.max(0, scratch.proximityCooldownTicks - 1);
        scratch.attendCooldownTicks = Math.max(0, scratch.attendCooldownTicks - 1);
        scratch.wanderCooldownTicks = Math.max(0, scratch.wanderCooldownTicks - 1);

        SpiritState state = spirit.spiritState();
        state = state.withCadence(new SpiritState.Cadence(
            state.cadence().routeFailures(),
            SpiritRules.decrementLoaded(state.cadence().routeRetryTicks()),
            SpiritRules.decrementLoaded(state.cadence().attendCooldownTicks())
        ));
        state = state.withWary(new SpiritState.Wary(
            state.phase() == Phase.WARY
                ? SpiritRules.decrementLoaded(state.wary().remainingTicks())
                : state.wary().remainingTicks(),
            SpiritRules.decrementLoaded(state.wary().cooldownTicks())
        ));
        if (state.phase() == Phase.ATTEND) {
            state = state.withAttendance(new SpiritState.Attendance(
                SpiritRules.decrementLoaded(state.attendance().remainingTicks()),
                SpiritRules.decrementLoaded(state.attendance().pulseRemainingTicks()),
                state.attendance().pulsesEmitted()
            ));
        }
        final SpiritState.Guard guard = state.guard();
        state = state.withGuard(new SpiritState.Guard(
            guard.attackerId(),
            guard.dimension(),
            state.phase() == Phase.WARN
                ? SpiritRules.decrementLoaded(guard.warnRemainingTicks())
                : guard.warnRemainingTicks(),
            state.phase() == Phase.WARN
                ? SpiritRules.decrementLoaded(guard.warnPulseRemainingTicks())
                : guard.warnPulseRemainingTicks(),
            guard.warnPulsesEmitted(),
            state.phase() == Phase.DEFEND
                ? SpiritRules.decrementLoaded(guard.defendRemainingTicks())
                : guard.defendRemainingTicks(),
            guard.strikes(),
            SpiritRules.decrementLoaded(guard.recoverRemainingTicks())
        ));
        if (state.phase() == Phase.RECOVER && state.guard().recoverRemainingTicks() <= 0) {
            state = state.withPhase(Phase.WANDER);
        }
        spirit.setSpiritState(state);
    }

    // ---------------------------------------------------------------- hazard overlay

    private static boolean tickHazard(final SpiritEntity spirit, final ServerLevel level) {
        final TransientState scratch = spirit.spiritTransient();
        if (scratch.hazardCooldownTicks > 0) {
            return scratch.hazardActive && !spirit.getNavigation().isDone();
        }
        scratch.hazardCooldownTicks = SpiritRules.HAZARD_INTERVAL_TICKS;
        scratch.hazardActive = SpectralEntity.observeHazard(
            spirit, level, SpiritRules.MAX_HAZARD_READS
        );
        spirit.spiritCounters().blockReads += SpiritRules.MAX_HAZARD_READS;
        if (!SpiritRules.hazardPreempts(spirit.spiritState().phase(), scratch.hazardActive)) {
            return false;
        }
        spirit.spiritCounters().hazardInterruptions++;
        searchAndRoute(spirit, level, spirit.blockPosition(),
            SpiritRules.ESCAPE_SEARCH_HORIZONTAL, SpiritRules.ESCAPE_SEARCH_VERTICAL,
            SpiritRules.MAX_SAFE_CANDIDATES, Optional.empty(), true, SpectralEntity.ESCAPE_SPEED);
        return true;
    }

    // ---------------------------------------------------------------- bound behavior

    private static void tickBound(final SpiritEntity spirit, final ServerLevel level) {
        final Optional<LivingEntity> owner = SpectralEntity.resolveOwner(spirit, level);
        if (owner.isEmpty()) {
            if (isGuarding(spirit)) {
                releaseGuard(spirit);
            }
            spirit.getNavigation().stop();
            return;
        }
        final LivingEntity attended = owner.orElseThrow();
        if (SpiritRules.auraDue(spirit.tickCount)
            && SpiritRules.ownerAttendanceAllowed(true, attended.isAlive(),
                attended.level() == level, spirit.distanceToSqr(attended))) {
            spirit.spiritCounters().auraPulses++;
            SpectralEntity.applyOwnerAura(attended, SpiritRules.AURA_NIGHT_VISION_TICKS);
        }
        if (isGuarding(spirit)
            && SpiritRules.defencePreempts(spirit.spiritState().phase(), true)) {
            tickGuard(spirit, level, attended);
            return;
        }
        if (openGuardIfOwnerWasAttacked(spirit, level, attended)) {
            return;
        }
        followOwner(spirit, level, attended);
    }

    private static void followOwner(
        final SpiritEntity spirit,
        final ServerLevel level,
        final LivingEntity owner
    ) {
        SpiritState state = spirit.spiritState();
        // RECOVER is deliberately left alone. It is a bound phase with its own countdown, and
        // normalizing it to BOUND here would clear the recovery window that forbids an immediate
        // second defence. advanceLoadedTimers returns it to WANDER, which is normalized below.
        if (state.phase() == Phase.WARY || state.phase() == Phase.ATTEND) {
            state = state.bind();
            spirit.setSpiritState(state);
        } else if (state.phase() == Phase.WANDER) {
            state = state.withPhase(Phase.BOUND);
            spirit.setSpiritState(state);
        }
        spirit.setTarget(null);
        final double distanceSquared = spirit.distanceToSqr(owner);
        // The recall is evaluated before the attendance range gate so a Spirit that drifted past
        // the gate is still recovered instead of being silently abandoned.
        if (SpiritRules.ownerRecallRequired(distanceSquared)) {
            spirit.spiritCounters().ownerRecalls++;
            spirit.getNavigation().stop();
            spirit.spiritTransient().destination = null;
            spirit.teleportTo(owner.getX() + 1.0D, owner.getY(), owner.getZ() + 1.0D);
            return;
        }
        if (!SpiritRules.ownerAttendanceAllowed(true, owner.isAlive(),
            owner.level() == level, distanceSquared)) {
            spirit.getNavigation().stop();
            return;
        }
        final BandAction action = SpiritRules.followBand(distanceSquared);
        if (action == BandAction.HOLD || !spirit.getNavigation().isDone()) {
            return;
        }
        final Vec3 toOwner = owner.position().subtract(spirit.position());
        final Vec3 direction = toOwner.lengthSqr() < 1.0E-4D
            ? new Vec3(1.0D, 0.0D, 0.0D)
            : toOwner.normalize();
        final double band = (SpiritRules.FOLLOW_BAND_MIN + SpiritRules.FOLLOW_BAND_MAX) / 2.0D;
        final Vec3 goal = action == BandAction.APPROACH
            ? owner.position().subtract(direction.scale(band))
            : owner.position().subtract(direction.scale(band + 1.0D));
        searchAndRoute(spirit, level, BlockPos.containing(goal.x, goal.y + 1.0D, goal.z),
            2, 2, SpiritRules.MAX_SAFE_CANDIDATES, Optional.empty(), false,
            SpectralEntity.ROUTE_SPEED);
    }

    /**
     * Atomic binding completion, invoked inside the very interaction that wrote the owner UUID.
     * Avoidance, attendance and route accounting are cancelled in one state write; no wary
     * reaction, attendance, or guard can survive the interaction that bound the Spirit.
     */
    public static void onBindingCommitted(
        final SpiritEntity spirit,
        final ServerLevel level,
        final UUID owner
    ) {
        spirit.getNavigation().stop();
        spirit.spiritTransient().destination = null;
        spirit.setTarget(null);
        final SpiritState before = spirit.spiritState();
        if (SpiritRules.bindingPreempts(before.phase(), true)
            && (before.wary().active() || before.attendance().active() || before.guard().present())) {
            spirit.spiritCounters().guardsEnded++;
        }
        spirit.setSpiritState(before.bind());
        SpectralEntity.resolveOwner(spirit, level)
            .filter(candidate -> candidate.getUUID().equals(owner))
            .ifPresent(candidate -> {
                spirit.spiritCounters().auraPulses++;
                SpectralEntity.applyOwnerAura(candidate, SpiritRules.AURA_NIGHT_VISION_TICKS);
            });
    }

    // ---------------------------------------------------------------- owner defence

    /**
     * The one accepted defence trigger. Only the owner's own recent direct attacker qualifies,
     * and only when the recovery window has elapsed. Nothing else the owner is fighting, and no
     * arbitrary owner target, can ever open this window.
     */
    private static boolean openGuardIfOwnerWasAttacked(
        final SpiritEntity spirit,
        final ServerLevel level,
        final LivingEntity owner
    ) {
        final SpiritState state = spirit.spiritState();
        if (state.guard().recoverRemainingTicks() > 0) {
            return false;
        }
        final LivingEntity attacker = owner.getLastHurtByMob();
        if (attacker == null) {
            return false;
        }
        final AttackerObservation observation = observeAttacker(spirit, level, owner, attacker);
        if (!SpiritRules.guardAllowed(true, SpiritRules.attackerLegal(observation),
            state.guard().recoverRemainingTicks())) {
            return false;
        }
        spirit.spiritCounters().defencesOpened++;
        spirit.getNavigation().stop();
        spirit.setSpiritState(state
            .withGuard(SpiritState.Guard.warning(
                attacker.getUUID(), SpectralEntity.dimensionOf(level), 0
            ))
            .withPhase(Phase.WARN));
        return true;
    }

    static AttackerObservation observeAttacker(
        final SpiritEntity spirit,
        final ServerLevel level,
        final LivingEntity owner,
        final LivingEntity attacker
    ) {
        final Optional<UUID> ownerId = CreatureBehaviorState.owner(spirit);
        final boolean isOwner = ownerId.map(attacker.getUUID()::equals).orElse(false);
        final boolean sameOwner = attacker instanceof Mob mob
            && ownerId.isPresent()
            && CreatureBehaviorState.owner(mob).equals(ownerId);
        final boolean isPlayer = attacker instanceof Player;
        final boolean eligibleGameMode = attacker instanceof Player player
            && !player.isCreative() && !player.isSpectator();
        return new AttackerObservation(
            true,
            attacker.isAlive(),
            attacker.level() == level,
            attacker == spirit,
            isOwner,
            sameOwner,
            isPlayer,
            eligibleGameMode,
            attacker.isInvulnerable(),
            Math.max(0, owner.tickCount - owner.getLastHurtByMobTimestamp())
        );
    }

    private static void tickGuard(
        final SpiritEntity spirit,
        final ServerLevel level,
        final LivingEntity owner
    ) {
        SpiritState state = spirit.spiritState();
        final Optional<UUID> attackerId = state.guard().attackerId();
        final Optional<LivingEntity> resolved = attackerId
            .map(level::getEntity)
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast)
            .filter(LivingEntity::isAlive);
        final boolean legal = resolved
            .map(attacker -> SpiritRules.attackerLegal(
                observeAttacker(spirit, level, owner, attacker)
            ))
            .orElse(false);
        final double distanceSquared = resolved.map(spirit::distanceToSqr).orElse(Double.MAX_VALUE);
        final boolean warning = state.phase() == Phase.WARN;
        final GuardEnd end = SpiritRules.guardEnd(new SpiritRules.GuardObservation(
            true,
            resolved.isPresent(),
            legal,
            state.guard().dimension()
                .map(SpectralEntity.dimensionOf(level)::equals)
                .orElse(false),
            warning,
            distanceSquared,
            state.guard().strikes(),
            warning
                ? state.guard().warnRemainingTicks()
                : state.guard().defendRemainingTicks(),
            state.cadence().routeFailures()
        ));
        if (end != GuardEnd.NONE) {
            releaseGuard(spirit);
            return;
        }
        final LivingEntity attacker = resolved.orElseThrow();
        if (warning) {
            state = tickWarning(spirit, level, state, attacker);
            spirit.setSpiritState(state);
            return;
        }
        spirit.setTarget(attacker);
        spirit.spiritCounters().lineOfSightChecks++;
        final boolean visible = spirit.getSensing().hasLineOfSight(attacker);
        if (SpiritRules.strikeAllowed(state.guard().strikes(), distanceSquared, visible,
            state.guard().defendRemainingTicks())) {
            strikeOnce(spirit, level, attacker);
            return;
        }
        if (spirit.getNavigation().isDone()) {
            searchAndRoute(spirit, level, attacker.blockPosition(), 2, 2,
                SpiritRules.MAX_SAFE_CANDIDATES, Optional.empty(), true,
                SpectralEntity.ROUTE_SPEED);
        }
    }

    /**
     * Visible, finite warning. It applies no damage and no effect: it exists so the attacker and
     * every onlooker can see the defence coming before a single strike is permitted.
     */
    private static SpiritState tickWarning(
        final SpiritEntity spirit,
        final ServerLevel level,
        final SpiritState input,
        final LivingEntity attacker
    ) {
        SpiritState state = input;
        spirit.getLookControl().setLookAt(attacker, 30.0F, 30.0F);
        if (SpiritRules.warnPulsesRemaining(state.guard().warnPulsesEmitted()) > 0
            && SpiritRules.pulseDue(state.guard().warnPulseRemainingTicks(),
                state.guard().warnPulsesEmitted(), SpiritRules.MAX_WARN_PULSES)) {
            state = state.withGuard(new SpiritState.Guard(
                state.guard().attackerId(), state.guard().dimension(),
                state.guard().warnRemainingTicks(),
                SpiritRules.WARN_PULSE_INTERVAL_TICKS,
                state.guard().warnPulsesEmitted() + 1,
                state.guard().defendRemainingTicks(),
                state.guard().strikes(),
                state.guard().recoverRemainingTicks()
            ));
            spirit.spiritCounters().warnPulses++;
            level.playSound(null, spirit.getX(), spirit.getY(), spirit.getZ(),
                SoundEvents.VEX_CHARGE, spirit.getSoundSource(), 0.7F, 1.4F);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, spirit.getX(),
                spirit.getY() + 0.6D, spirit.getZ(), SpiritRules.MAX_WARN_PARTICLES,
                0.3D, 0.4D, 0.3D, 0.005D);
        }
        if (SpiritRules.warningGraduates(state.guard().warnRemainingTicks())) {
            spirit.spiritCounters().defenceWindowsOpened++;
            state = state.withGuard(new SpiritState.Guard(
                state.guard().attackerId(), state.guard().dimension(), 0, 0,
                state.guard().warnPulsesEmitted(), SpiritRules.DEFEND_TICKS,
                state.guard().strikes(), state.guard().recoverRemainingTicks()
            )).withPhase(Phase.DEFEND);
        }
        return state;
    }

    /**
     * Exactly one ordinary attributed attack per defence window. The strike is recorded before it
     * is delivered so no reentrant damage handler can produce a second one, and the guard closes
     * into recovery in the same pass.
     */
    private static void strikeOnce(
        final SpiritEntity spirit,
        final ServerLevel level,
        final LivingEntity attacker
    ) {
        final SpiritState state = spirit.spiritState();
        if (SpiritRules.strikeDamage(
            (float) spirit.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
        ) <= 0.0F) {
            // A Spirit stripped of its attack attribute has nothing to contribute, so the guard
            // closes rather than delivering a zero-damage attack that still reads as a defence.
            releaseGuard(spirit);
            return;
        }
        spirit.setSpiritState(state.withGuard(new SpiritState.Guard(
            state.guard().attackerId(), state.guard().dimension(), 0, 0,
            state.guard().warnPulsesEmitted(), state.guard().defendRemainingTicks(),
            state.guard().strikes() + 1, state.guard().recoverRemainingTicks()
        )));
        spirit.spiritCounters().strikes++;
        spirit.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        spirit.doHurtTarget(level, attacker);
        releaseGuard(spirit);
    }

    private static void releaseGuard(final SpiritEntity spirit) {
        spirit.spiritCounters().guardsEnded++;
        spirit.setTarget(null);
        spirit.getNavigation().stop();
        spirit.spiritTransient().destination = null;
        spirit.setSpiritState(spirit.spiritState().endGuard());
    }

    // ---------------------------------------------------------------- free behavior

    private static void tickFree(final SpiritEntity spirit, final ServerLevel level) {
        if (beginWaryIfCrowded(spirit, level)) {
            return;
        }
        SpiritState state = spirit.spiritState();
        if (state.phase() == Phase.WANDER
            && SpiritRules.attendAllowed(false,
                SpiritRules.separated(spirit.spiritTransient().nearestPlayerDistanceSquared),
                state.cadence().attendCooldownTicks())
            && spirit.spiritTransient().attendCooldownTicks <= 0
            && discoverAttendance(spirit, level)) {
            return;
        }
        if (spirit.spiritTransient().wanderCooldownTicks <= 0 && spirit.getNavigation().isDone()) {
            spirit.spiritTransient().wanderCooldownTicks = SpiritRules.WANDER_INTERVAL_TICKS
                + SpiritRules.stableOffset(spirit.getUUID(), 40);
            searchAndRoute(spirit, level, spirit.blockPosition(),
                SpiritRules.WANDER_RADIUS_HORIZONTAL, SpiritRules.WANDER_RADIUS_VERTICAL,
                SpiritRules.MAX_WANDER_CANDIDATES, Optional.empty(), true,
                SpectralEntity.ROUTE_SPEED);
        }
    }

    /**
     * Bounded wary reaction. Only players already inside this Spirit's own tracked bounding box
     * are inspected, at most {@link SpiritRules#MAX_PROXIMITY_CANDIDATES} of them, and the
     * withdrawal destination stays deliberately local so a computed goal can never strand the
     * Spirit outside the space it is actually in.
     */
    private static boolean beginWaryIfCrowded(final SpiritEntity spirit, final ServerLevel level) {
        final SpiritState state = spirit.spiritState();
        if (spirit.spiritTransient().proximityCooldownTicks > 0) {
            return state.phase() == Phase.WARY;
        }
        spirit.spiritTransient().proximityCooldownTicks = SpiritRules.PROXIMITY_INTERVAL_TICKS;
        final Optional<Player> nearest = nearestTrackedPlayer(spirit, level);
        if (nearest.isEmpty()) {
            return false;
        }
        final Player closest = nearest.orElseThrow();
        final double closestDistance = spirit.distanceToSqr(closest);
        if (!SpiritRules.shouldWithdraw(false, closest.isAlive(),
            !closest.isCreative() && !closest.isSpectator(), closestDistance,
            state.wary().cooldownTicks())) {
            return false;
        }
        if (state.phase() != Phase.WARY) {
            spirit.spiritCounters().waryReactions++;
            spirit.setSpiritState(state
                .withWary(SpiritState.Wary.started())
                .withAnchor(SpiritState.Anchor.none())
                .withAttendance(SpiritState.Attendance.none())
                .withPhase(Phase.WARY));
        }
        return true;
    }

    private static void tickWary(final SpiritEntity spirit, final ServerLevel level) {
        final SpiritState state = spirit.spiritState();
        if (state.wary().remainingTicks() <= 0) {
            spirit.getNavigation().stop();
            spirit.setSpiritState(state
                .withWary(new SpiritState.Wary(0, SpiritRules.WARY_COOLDOWN_TICKS))
                .withPhase(Phase.WANDER));
            return;
        }
        final Optional<Player> nearest = nearestTrackedPlayer(spirit, level);
        if (nearest.isEmpty()) {
            spirit.getNavigation().stop();
            spirit.setSpiritState(state
                .withWary(new SpiritState.Wary(0, SpiritRules.WARY_COOLDOWN_TICKS))
                .withPhase(Phase.WANDER));
            return;
        }
        final Player avoided = nearest.orElseThrow();
        if (SpiritRules.separated(spirit.distanceToSqr(avoided))) {
            spirit.getNavigation().stop();
            spirit.setSpiritState(state
                .withWary(new SpiritState.Wary(0, SpiritRules.WARY_COOLDOWN_TICKS))
                .withPhase(Phase.WANDER));
            return;
        }
        if (spirit.getNavigation().isDone()) {
            searchAndRoute(spirit, level, spirit.blockPosition(),
                SpiritRules.WARY_WITHDRAW_HORIZONTAL, SpiritRules.WARY_WITHDRAW_VERTICAL,
                SpiritRules.MAX_SAFE_CANDIDATES, Optional.of(avoided.position()), true,
                SpectralEntity.ESCAPE_SPEED);
        }
    }

    /**
     * The one player query in this package. It reaches the separation range rather than only the
     * wary radius so the separation precondition behind soul-light attendance is evaluated on
     * real evidence, and it stays capped at
     * {@link SpiritRules#MAX_PROXIMITY_CANDIDATES} inspected players.
     */
    private static Optional<Player> nearestTrackedPlayer(
        final SpiritEntity spirit,
        final ServerLevel level
    ) {
        Player closest = null;
        double closestDistance = Double.MAX_VALUE;
        int visited = 0;
        for (final Player player : BoundedEntityQuery.collect(
            level,
            Player.class,
            spirit.getBoundingBox().inflate(SpiritRules.SEPARATION_RANGE),
            player -> player.isAlive() && !player.isSpectator(),
            SpiritRules.MAX_PROXIMITY_CANDIDATES
        )) {
            visited++;
            spirit.spiritCounters().proximityVisits++;
            final double distanceSquared = spirit.distanceToSqr(player);
            if (distanceSquared < closestDistance) {
                closestDistance = distanceSquared;
                closest = player;
            }
        }
        spirit.spiritTransient().nearestPlayerDistanceSquared = closestDistance;
        return Optional.ofNullable(closest);
    }

    /**
     * One bounded soul-light scan over the exact 13 x 5 x 13 envelope. The whole envelope must
     * already be loaded before a single read happens and the read count can never exceed
     * {@link SpiritRules#MAX_ATTEND_READS}.
     */
    private static boolean discoverAttendance(final SpiritEntity spirit, final ServerLevel level) {
        final Counters counters = spirit.spiritCounters();
        spirit.spiritTransient().attendCooldownTicks = SpiritRules.WANDER_INTERVAL_TICKS;
        counters.attendScans++;
        final BlockPos origin = spirit.blockPosition();
        if (!SpectralEntity.envelopeLoaded(level, origin,
            SpiritRules.ATTEND_SEARCH_HORIZONTAL, SpiritRules.ATTEND_SEARCH_VERTICAL)) {
            return false;
        }
        final List<AttendCandidate> inspected = new ArrayList<>();
        int reads = 0;
        for (int dx = -SpiritRules.ATTEND_SEARCH_HORIZONTAL;
             dx <= SpiritRules.ATTEND_SEARCH_HORIZONTAL; dx++) {
            for (int dy = -SpiritRules.ATTEND_SEARCH_VERTICAL;
                 dy <= SpiritRules.ATTEND_SEARCH_VERTICAL; dy++) {
                for (int dz = -SpiritRules.ATTEND_SEARCH_HORIZONTAL;
                     dz <= SpiritRules.ATTEND_SEARCH_HORIZONTAL; dz++) {
                    if (reads >= SpiritRules.MAX_ATTEND_READS || inspected.size() >= 16) {
                        break;
                    }
                    reads++;
                    counters.attendReads++;
                    counters.blockReads++;
                    final BlockPos candidate = origin.offset(dx, dy, dz);
                    if (!level.getBlockState(candidate).is(SpectralEntity.SOUL_LIGHTS)) {
                        continue;
                    }
                    counters.attendCandidates++;
                    inspected.add(new AttendCandidate(candidate.asLong(), origin.distSqr(candidate)));
                }
            }
        }
        final Optional<AttendCandidate> selected = SpiritRules.select(inspected);
        if (selected.isEmpty()) {
            return false;
        }
        spirit.setSpiritState(spirit.spiritState()
            .withAnchor(SpiritState.Anchor.at(
                BlockPos.of(selected.orElseThrow().packedPosition()),
                SpectralEntity.dimensionOf(level)
            ))
            .withAttendance(SpiritState.Attendance.started())
            .withPhase(Phase.ATTEND));
        return true;
    }

    private static void tickAttend(final SpiritEntity spirit, final ServerLevel level) {
        if (beginWaryIfCrowded(spirit, level)) {
            return;
        }
        SpiritState state = spirit.spiritState();
        final Optional<BlockPos> anchor = state.anchor().position();
        final boolean sameDimension = state.anchor().dimension()
            .map(SpectralEntity.dimensionOf(level)::equals)
            .orElse(false);
        final boolean loaded = anchor.map(level::hasChunkAt).orElse(false);
        if (loaded) {
            spirit.spiritCounters().blockReads++;
        }
        final boolean stillSoulLight = loaded && anchor
            .map(position -> level.getBlockState(position).is(SpectralEntity.SOUL_LIGHTS))
            .orElse(false);
        if (!sameDimension || !stillSoulLight
            || state.attendance().remainingTicks() <= 0
            || SpiritRules.routeExhausted(state.cadence().routeFailures())) {
            spirit.getNavigation().stop();
            spirit.setSpiritState(state.endAttendance());
            return;
        }
        final BlockPos site = anchor.orElseThrow();
        final double distanceSquared = spirit.distanceToSqr(Vec3.atCenterOf(site));
        if (SpiritRules.attendBand(distanceSquared) != BandAction.HOLD
            && spirit.getNavigation().isDone()) {
            searchAndRoute(spirit, level, site.above(1), 2, 2,
                SpiritRules.MAX_SAFE_CANDIDATES, Optional.empty(), true,
                SpectralEntity.ROUTE_SPEED);
        }
        if (SpiritRules.attendPulsesRemaining(state.attendance().pulsesEmitted()) > 0
            && SpiritRules.pulseDue(state.attendance().pulseRemainingTicks(),
                state.attendance().pulsesEmitted(), SpiritRules.MAX_ATTEND_PULSES)) {
            state = state.withAttendance(new SpiritState.Attendance(
                state.attendance().remainingTicks(),
                SpiritRules.ATTEND_PULSE_INTERVAL_TICKS,
                state.attendance().pulsesEmitted() + 1
            ));
            spirit.spiritCounters().attendPulses++;
            level.sendParticles(ParticleTypes.SOUL, spirit.getX(), spirit.getY() + 0.6D,
                spirit.getZ(), SpiritRules.MAX_ATTEND_PARTICLES, 0.3D, 0.4D, 0.3D, 0.005D);
            spirit.setSpiritState(state);
        }
    }

    // ---------------------------------------------------------------- damage entry

    /**
     * Accepted damage never opens a defence: the Spirit defends its owner, not itself. It only
     * abandons an attendance so a hurt Spirit stops standing at a soul light.
     */
    public static void onAcceptedDamage(final SpiritEntity spirit, final ServerLevel level) {
        final SpiritState state = spirit.spiritState();
        if (state.phase() == Phase.ATTEND) {
            spirit.getNavigation().stop();
            spirit.setSpiritState(state.endAttendance());
        }
    }

    // ---------------------------------------------------------------- movement

    private static boolean searchAndRoute(
        final SpiritEntity spirit,
        final ServerLevel level,
        final BlockPos center,
        final int horizontalRadius,
        final int verticalRadius,
        final int candidateBudget,
        final Optional<Vec3> awayFrom,
        final boolean avoidHazards,
        final double speed
    ) {
        if (!SpiritRules.pathRequestAllowed(
            spirit.spiritTransient().pathCooldownTicks,
            spirit.spiritState().cadence().routeRetryTicks()
        )) {
            return false;
        }
        final Optional<BlockPos> destination = findSafeDestination(
            spirit, level, center, horizontalRadius, verticalRadius, candidateBudget,
            awayFrom, avoidHazards
        );
        if (destination.isEmpty()) {
            recordUnroutableSearch(spirit);
            return false;
        }
        return requestRoute(spirit, destination.orElseThrow(), speed);
    }

    /**
     * A search that qualified no candidate costs the same real work as one that did, so it arms
     * the same path cadence and counts the same route failure. Without this a caller gated only
     * by {@code getNavigation().isDone()} would re-run the whole candidate sweep every tick for
     * as long as the surroundings stayed unusable.
     */
    private static void recordUnroutableSearch(final SpiritEntity spirit) {
        final TransientState scratch = spirit.spiritTransient();
        scratch.pathCooldownTicks = SpiritRules.PATH_INTERVAL_TICKS;
        scratch.destination = null;
        final SpiritState state = spirit.spiritState();
        final int failures = SpiritRules.routeFailuresAfter(
            state.cadence().routeFailures(),
            new SpiritRules.RouteResult(false, false, false)
        );
        int retry = state.cadence().routeRetryTicks();
        if (SpiritRules.routeExhausted(failures)) {
            retry = SpiritRules.routeBackoffAfter(failures);
            spirit.getNavigation().stop();
        }
        spirit.setSpiritState(state.withCadence(new SpiritState.Cadence(
            failures, retry, state.cadence().attendCooldownTicks()
        )));
    }

    static boolean requestRoute(
        final SpiritEntity spirit,
        final BlockPos destination,
        final double speed
    ) {
        final TransientState scratch = spirit.spiritTransient();
        SpiritState state = spirit.spiritState();
        if (!SpiritRules.pathRequestAllowed(
            scratch.pathCooldownTicks, state.cadence().routeRetryTicks()
        )) {
            return false;
        }
        scratch.pathCooldownTicks = SpiritRules.PATH_INTERVAL_TICKS;
        spirit.spiritCounters().navigationRequests++;
        final Path path = spirit.getNavigation().createPath(destination, 0);
        final boolean reachable = path != null && path.canReach();
        final boolean accepted = reachable && spirit.getNavigation().moveTo(path, speed);
        final int failures = SpiritRules.routeFailuresAfter(
            state.cadence().routeFailures(),
            new SpiritRules.RouteResult(path != null, reachable, accepted)
        );
        int retry = state.cadence().routeRetryTicks();
        if (SpiritRules.routeExhausted(failures)) {
            retry = SpiritRules.routeBackoffAfter(failures);
            spirit.getNavigation().stop();
            scratch.destination = null;
        } else if (accepted) {
            scratch.destination = destination.immutable();
        }
        spirit.setSpiritState(state.withCadence(new SpiritState.Cadence(
            failures, retry, state.cadence().attendCooldownTicks()
        )));
        return accepted;
    }

    static Optional<BlockPos> findSafeDestination(
        final SpiritEntity spirit,
        final ServerLevel level,
        final BlockPos center,
        final int horizontalRadius,
        final int verticalRadius,
        final int candidateBudget,
        final Optional<Vec3> awayFrom,
        final boolean avoidHazards
    ) {
        final Counters counters = spirit.spiritCounters();
        counters.safeSearches++;
        final BlockPos origin = spirit.blockPosition();
        final java.util.Comparator<SpiritRules.SafeCandidate> preference =
            SpiritRules.safeCandidatePreference();
        int reads = 0;
        BlockPos best = null;
        SpiritRules.SafeCandidate bestFacts = null;
        for (final SpiritRules.SafeSearchOffset offset : SpiritRules.safeSearchOffsets(
            spirit.getUUID(), horizontalRadius, verticalRadius, candidateBudget
        )) {
            if (reads + SpectralEntity.READS_PER_SAFE_CANDIDATE > SpiritRules.MAX_CHARGED_READS) {
                break;
            }
            final BlockPos candidate = center.offset(offset.dx(), offset.dy(), offset.dz());
            if (candidate.equals(origin)) {
                continue;
            }
            counters.safeCandidateVisits++;
            // Charged before the filter: a rejected candidate spent these reads too, so the
            // ceiling binds the real cost instead of only the accepted minority.
            reads += SpectralEntity.READS_PER_SAFE_CANDIDATE;
            counters.blockReads += SpectralEntity.READS_PER_SAFE_CANDIDATE;
            final Optional<SpectralEntity.SafeQualification> qualified =
                SpectralEntity.qualifySafeCandidate(spirit, level, candidate, avoidHazards);
            if (qualified.isEmpty()) {
                continue;
            }
            final SpiritRules.SafeCandidate facts = new SpiritRules.SafeCandidate(
                awayFrom.map(threat -> Vec3.atCenterOf(candidate).distanceToSqr(threat)).orElse(0.0D),
                qualified.orElseThrow().hazardFree(),
                candidate.distSqr(origin),
                candidate.asLong()
            );
            if (bestFacts == null || preference.compare(facts, bestFacts) < 0) {
                bestFacts = facts;
                best = candidate.immutable();
            }
        }
        return Optional.ofNullable(best);
    }

    /** The accepted attacker is the only entity a Spirit may ever attack, and only while defending. */
    static boolean isAcceptedAttacker(final SpiritEntity spirit, final Entity candidate) {
        return spirit.spiritState().guard().attackerId()
            .map(candidate.getUUID()::equals)
            .orElse(false);
    }
}
