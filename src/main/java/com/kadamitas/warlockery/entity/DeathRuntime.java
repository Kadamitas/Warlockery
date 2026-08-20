package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.DeathRules.Candidate;
import com.kadamitas.warlockery.entity.DeathRules.CandidateObservation;
import com.kadamitas.warlockery.entity.DeathRules.Phase;
import com.kadamitas.warlockery.entity.DeathRules.ReleaseReason;
import com.kadamitas.warlockery.entity.DeathRules.SubjectObservation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;

/**
 * The only server-side Death behavior controller. It owns MOVE completely; the entity's look
 * goals own LOOK only. Every scan, line-of-sight ray, and path request is counted against the
 * declared per-Death and per-level budgets. Nothing here enumerates a dimension, forces a chunk,
 * edits a block or inventory, listens to a global death event, records victim history, or writes
 * another entity's persistent state. The single approved outward mutation is one call to the
 * established primary melee path per telegraphed window, which leaves every vanilla mitigation,
 * death-protection mechanic, event cancellation, and attribution exactly where they already are.
 */
public final class DeathRuntime {
    private static final Map<ServerLevel, LevelBudget> LEVEL_BUDGETS = new WeakHashMap<>();

    private DeathRuntime() {
    }

    /** Structural work counters proving the exact caps. Pass-local, never persisted. */
    public static final class Counters {
        long discoveryScans;
        long candidateVisits;
        long lineOfSightChecks;
        long navigationRequests;
        long leaseDenials;
        long quotaDenials;
        long appointments;
        long retaliationEpisodes;
        long telegraphs;
        long reapAttempts;
        long recoveries;
        long releases;
        long hazardInterruptions;
        long heals;

        public long discoveryScans() { return discoveryScans; }
        public long candidateVisits() { return candidateVisits; }
        public long lineOfSightChecks() { return lineOfSightChecks; }
        public long navigationRequests() { return navigationRequests; }
        public long leaseDenials() { return leaseDenials; }
        public long quotaDenials() { return quotaDenials; }
        public long appointments() { return appointments; }
        public long retaliationEpisodes() { return retaliationEpisodes; }
        public long telegraphs() { return telegraphs; }
        public long reapAttempts() { return reapAttempts; }
        public long recoveries() { return recoveries; }
        public long releases() { return releases; }
        public long hazardInterruptions() { return hazardInterruptions; }
        public long heals() { return heals; }
    }

    /**
     * Execution scratch rebuilt after every load. Nothing here is meaning: losing it can delay
     * work by one cadence but can never replay a reaping call, a sound, or a path.
     */
    public static final class TransientState {
        boolean reconciled;
        boolean disguisedNearby;
        int pathCooldownTicks;
        int discoveryCooldownTicks;
        int healCooldownTicks;

        public void resetForLoad() {
            reconciled = false;
            disguisedNearby = false;
            pathCooldownTicks = 0;
            discoveryCooldownTicks = 0;
            healCooldownTicks = 0;
        }
    }

    /** Per-level per-tick work quota shared by every loaded Death in that level. */
    private static final class LevelBudget {
        long gameTime = Long.MIN_VALUE;
        int pathRequests;
        int discoveryScans;

        void rollOver(final long now) {
            if (gameTime != now) {
                gameTime = now;
                pathRequests = 0;
                discoveryScans = 0;
            }
        }

        boolean claimPathRequest() {
            if (!DeathRules.budgetAllows(pathRequests, DeathRules.MAX_LEVEL_PATH_REQUESTS_PER_TICK)) {
                return false;
            }
            pathRequests++;
            return true;
        }

        boolean claimDiscoveryScan() {
            if (!DeathRules.budgetAllows(discoveryScans, DeathRules.MAX_LEVEL_DISCOVERY_SCANS_PER_TICK)) {
                return false;
            }
            discoveryScans++;
            return true;
        }
    }

    /**
     * Fixture seam. The per-level quota rolls over on the level clock, which a directly
     * dispatched decision loop never advances, so a fixture that drives many decisions inside one
     * game tick would otherwise spend one tick's quota across its whole run.
     */
    static void resetLevelBudget(final ServerLevel level) {
        LEVEL_BUDGETS.remove(level);
    }

    /**
     * Fixture seam. The one-shot load reconciliation re-seeds the discovery and healing stagger
     * from inside the first {@link #tick}, so a fixture that clears those cadences immediately
     * before that first tick would have them silently overwritten and would observe no decision.
     * Running the real reconciliation up front settles it once, leaving the live path unchanged.
     */
    static void reconcileForFixture(final DeathEntity death, final ServerLevel level) {
        reconcileOnLoad(death, level);
    }

    public static void tick(final DeathEntity death, final ServerLevel level) {
        reconcileOnLoad(death, level);
        advanceLoadedTimers(death);
        tickVigilHealing(death);
        if (HazardEscapeRuntime.tick(death, level, CreatureKind.DEATH)) {
            death.deathCounters().hazardInterruptions++;
            death.syncPresentation(death.deathState().phase());
            return;
        }
        switch (death.deathState().phase()) {
            case QUIESCENT -> tickQuiescent(death, level);
            case APPOINTED, APPROACH -> tickApproach(death, level);
            case TELEGRAPH -> tickTelegraph(death, level);
            case REAP -> tickReap(death, level);
            case RECOVER -> tickRecover(death);
            case RELEASE -> settleRelease(death);
        }
        death.syncPresentation(death.deathState().phase());
    }

    // ---------------------------------------------------------------- lifecycle

    private static void reconcileOnLoad(final DeathEntity death, final ServerLevel level) {
        final TransientState scratch = death.deathTransient();
        if (scratch.reconciled) {
            return;
        }
        scratch.reconciled = true;
        scratch.discoveryCooldownTicks =
            DeathRules.stableOffset(death.getUUID(), DeathRules.DISCOVERY_INTERVAL_TICKS);
        scratch.healCooldownTicks =
            DeathRules.stableOffset(death.getUUID(), DeathRules.VIGIL_HEAL_INTERVAL_TICKS);
        final DeathState state = death.deathState();
        final boolean subjectElsewhere = state.appointment().present()
            && state.appointment().dimension()
                .map(stored -> !stored.equals(dimensionOf(level)))
                .orElse(true);
        if (subjectElsewhere) {
            releaseAppointment(death);
        }
    }

    private static void advanceLoadedTimers(final DeathEntity death) {
        final TransientState scratch = death.deathTransient();
        scratch.pathCooldownTicks = Math.max(0, scratch.pathCooldownTicks - 1);
        scratch.discoveryCooldownTicks = Math.max(0, scratch.discoveryCooldownTicks - 1);
        scratch.healCooldownTicks = Math.max(0, scratch.healCooldownTicks - 1);

        DeathState state = death.deathState();
        final DeathState.Cadence cadence = state.cadence();
        state = state.withCadence(new DeathState.Cadence(
            DeathRules.decrementLoaded(cadence.reappointCooldownTicks()),
            cadence.routeFailures(),
            DeathRules.decrementLoaded(cadence.routeRetryTicks())
        ));
        final DeathState.Appointment appointment = state.appointment();
        state = switch (state.phase()) {
            case APPOINTED, APPROACH -> state.withAppointment(new DeathState.Appointment(
                appointment.subject(), appointment.dimension(), appointment.lastSeen(),
                DeathRules.decrementLoaded(appointment.approachRemainingTicks()),
                appointment.telegraphRemainingTicks(), appointment.recoverRemainingTicks(),
                appointment.reaped()
            ));
            // The approach deadline deliberately stops running once the telegraph begins, so a
            // telegraph entered on the last approach tick still gets its whole finite hold
            // instead of aborting through the timeout release.
            case TELEGRAPH -> state.withAppointment(new DeathState.Appointment(
                appointment.subject(), appointment.dimension(), appointment.lastSeen(),
                appointment.approachRemainingTicks(),
                DeathRules.decrementLoaded(appointment.telegraphRemainingTicks()),
                appointment.recoverRemainingTicks(), appointment.reaped()
            ));
            case RECOVER -> state.withAppointment(new DeathState.Appointment(
                appointment.subject(), appointment.dimension(), appointment.lastSeen(),
                appointment.approachRemainingTicks(), appointment.telegraphRemainingTicks(),
                DeathRules.decrementLoaded(appointment.recoverRemainingTicks()),
                appointment.reaped()
            ));
            case REAP, RELEASE, QUIESCENT -> state;
        };
        death.setDeathState(state);
    }

    /** The preserved one health per twenty loaded ticks. Loaded time only, never world time. */
    private static void tickVigilHealing(final DeathEntity death) {
        final TransientState scratch = death.deathTransient();
        if (scratch.healCooldownTicks > 0) {
            return;
        }
        scratch.healCooldownTicks = DeathRules.VIGIL_HEAL_INTERVAL_TICKS;
        if (death.getHealth() < death.getMaxHealth()) {
            death.deathCounters().heals++;
            death.heal(DeathRules.VIGIL_HEAL_AMOUNT);
        }
    }

    // ---------------------------------------------------------------- acquisition

    private static void tickQuiescent(final DeathEntity death, final ServerLevel level) {
        final TransientState scratch = death.deathTransient();
        final DeathState state = death.deathState();
        if (scratch.discoveryCooldownTicks > 0) {
            return;
        }
        scratch.discoveryCooldownTicks = DeathRules.DISCOVERY_INTERVAL_TICKS;
        // The hazard overlay already returned before this phase could run, so the hazard fact is
        // decided here; the disguise fact is what the previous bounded scan actually observed and
        // it suppresses exactly one acquisition cadence before being re-observed.
        final boolean allowed = DeathRules.discoveryAllowed(
            true,
            false,
            recentlyHurt(death),
            peaceful(level),
            scratch.disguisedNearby,
            state.cadence().reappointCooldownTicks()
        );
        scratch.disguisedNearby = false;
        if (!allowed) {
            return;
        }
        if (!budget(level).claimDiscoveryScan()) {
            death.deathCounters().quotaDenials++;
            return;
        }
        discoverSubject(death, level);
    }

    private static void discoverSubject(final DeathEntity death, final ServerLevel level) {
        final Counters counters = death.deathCounters();
        counters.discoveryScans++;
        final List<Candidate> inspected = new ArrayList<>();
        int visited = 0;
        int sightChecks = 0;
        boolean disguisedNear = false;
        for (final ServerPlayer player : level.players()) {
            if (visited >= DeathRules.MAX_CANDIDATES_VISITED) {
                break;
            }
            final double distanceSquared = death.distanceToSqr(player);
            // A player out of appointment range can never be appointed, so it is not a candidate
            // and must not be charged a visit. Charging it would let players who are merely
            // earlier in the level's player list exhaust the budget before the ones in reach are
            // ever examined, which makes the whole sweep depend on join order rather than range.
            if (distanceSquared > DeathRules.APPOINT_RANGE_SQUARED) {
                continue;
            }
            visited++;
            counters.candidateVisits++;
            final boolean disguised = DeathImpersonationRules.isComplete(player);
            if (disguised && distanceSquared <= DeathRules.APPOINT_RANGE_SQUARED) {
                disguisedNear = true;
                break;
            }
            if (!DeathRules.appointable(new CandidateObservation(
                player.isAlive(),
                !player.isCreative() && !player.isSpectator(),
                player.isInvulnerable(),
                disguised,
                player.level() == level,
                level.hasChunkAt(player.blockPosition()),
                distanceSquared
            ))) {
                continue;
            }
            if (inspected.size() >= DeathRules.MAX_RETAINED_CANDIDATES
                || sightChecks >= DeathRules.MAX_LINE_OF_SIGHT_CHECKS) {
                break;
            }
            sightChecks++;
            counters.lineOfSightChecks++;
            inspected.add(new Candidate(
                player.getUUID(), distanceSquared, death.getSensing().hasLineOfSight(player)
            ));
        }
        death.deathTransient().disguisedNearby = disguisedNear;
        if (disguisedNear) {
            return;
        }
        DeathRules.select(inspected).ifPresent(selected -> {
            if (!DeathRules.leaseAvailable(selected.id(), heldSubjectsNearby(death, level))) {
                counters.leaseDenials++;
                return;
            }
            counters.appointments++;
            death.setDeathState(death.deathState()
                .withAppointment(DeathState.Appointment.appointed(selected.id(), dimensionOf(level)))
                .withPhase(Phase.APPOINTED)
                .withCadence(new DeathState.Cadence(
                    death.deathState().cadence().reappointCooldownTicks(),
                    0,
                    death.deathState().cadence().routeRetryTicks()
                )));
        });
    }

    /**
     * The bounded level-local lease read. At most {@link DeathRules#MAX_LEASE_NEIGHBOURS} other
     * loaded Deaths inside one fixed radius are inspected; this is a local claim, never a global
     * uniqueness promise and never a level-wide entity enumeration.
     */
    private static List<UUID> heldSubjectsNearby(final DeathEntity death, final ServerLevel level) {
        final List<UUID> held = new ArrayList<>();
        for (final DeathEntity other : level.getEntitiesOfClass(
            DeathEntity.class,
            death.getBoundingBox().inflate(DeathRules.LEASE_RADIUS),
            candidate -> candidate != death && candidate.isAlive()
        )) {
            if (held.size() >= DeathRules.MAX_LEASE_NEIGHBOURS) {
                break;
            }
            death.deathCounters().candidateVisits++;
            other.deathState().appointment().subject().ifPresent(held::add);
        }
        return held;
    }

    // ---------------------------------------------------------------- episode

    private static void tickApproach(final DeathEntity death, final ServerLevel level) {
        final Optional<ServerPlayer> resolved = observeSubject(death, level);
        if (resolved.isEmpty()) {
            return;
        }
        final ServerPlayer subject = resolved.orElseThrow();
        final double distanceSquared = death.distanceToSqr(subject);
        death.getLookControl().setLookAt(subject, 30.0F, 30.0F);
        if (DeathRules.withinReach(distanceSquared)) {
            death.getNavigation().stop();
            death.deathCounters().telegraphs++;
            death.setDeathState(death.deathState().withPhase(Phase.TELEGRAPH));
            beginTelegraphFeedback(death, level);
            return;
        }
        requestRoute(death, level, subject);
    }

    private static void tickTelegraph(final DeathEntity death, final ServerLevel level) {
        final Optional<ServerPlayer> resolved = observeSubject(death, level);
        if (resolved.isEmpty()) {
            return;
        }
        final ServerPlayer subject = resolved.orElseThrow();
        death.getNavigation().stop();
        death.getLookControl().setLookAt(subject, 30.0F, 30.0F);
        final DeathState state = death.deathState();
        if (!DeathRules.withinReach(death.distanceToSqr(subject))) {
            death.setDeathState(state
                .withAppointment(withTelegraph(state.appointment(), DeathRules.TELEGRAPH_TICKS))
                .withPhase(Phase.APPROACH));
            return;
        }
        if (DeathRules.telegraphComplete(state.appointment().telegraphRemainingTicks())) {
            death.setDeathState(state.withPhase(Phase.REAP));
        }
    }

    private static void tickReap(final DeathEntity death, final ServerLevel level) {
        final Optional<ServerPlayer> resolved = observeSubject(death, level);
        if (resolved.isEmpty()) {
            return;
        }
        final ServerPlayer subject = resolved.orElseThrow();
        final DeathState state = death.deathState();
        death.getNavigation().stop();
        // legalTarget is the load-bearing predicate here, not a formality: it is the only check
        // that re-reads the complete-disguise pacification and the recovery bar on the exact
        // tick the melee path would be called.
        if (!DeathRules.reapAllowed(
            legalTarget(death, subject),
            DeathRules.withinReach(death.distanceToSqr(subject)),
            state.appointment().telegraphRemainingTicks(),
            state.appointment().reaped()
        )) {
            releaseAppointment(death);
            return;
        }
        death.deathCounters().reapAttempts++;
        death.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        death.doHurtTarget(level, subject);
        death.deathCounters().recoveries++;
        death.setDeathState(death.deathState()
            .withAppointment(new DeathState.Appointment(
                state.appointment().subject(), state.appointment().dimension(),
                Optional.of(subject.blockPosition()),
                state.appointment().approachRemainingTicks(), 0,
                DeathRules.RECOVER_TICKS, true
            ))
            .withPhase(Phase.RECOVER));
    }

    /**
     * The single transition out of a recovery. This is deliberately the only place a completed
     * attempt can end: it counts the release and starts the reappointment backoff, so no episode
     * can slip into the settling phase without paying both.
     */
    private static void tickRecover(final DeathEntity death) {
        death.getNavigation().stop();
        final DeathState state = death.deathState();
        if (!DeathRules.recoveryComplete(state.appointment().recoverRemainingTicks())) {
            return;
        }
        if (state.appointment().present()) {
            releaseAppointment(death);
            return;
        }
        // The appointment was already released earlier in this episode and only kept the
        // recovery it still owed. That release was already counted and already started the
        // backoff, so the finished recovery only has the settling transition left.
        death.setDeathState(state.withPhase(Phase.RELEASE));
    }

    private static void settleRelease(final DeathEntity death) {
        death.getNavigation().stop();
        death.setDeathState(death.deathState()
            .withAppointment(DeathState.Appointment.none())
            .withPhase(Phase.QUIESCENT));
    }

    /**
     * One shared observation and retention decision for every appointment phase. Returns the
     * resolved subject only when the episode may continue; any release stops navigation, clears
     * the appointment, and starts the reappointment backoff before returning empty.
     */
    private static Optional<ServerPlayer> observeSubject(final DeathEntity death, final ServerLevel level) {
        final DeathState state = death.deathState();
        final DeathState.Appointment appointment = state.appointment();
        if (!appointment.present()) {
            releaseAppointment(death);
            return Optional.empty();
        }
        final boolean sameDimension = appointment.dimension()
            .map(dimensionOf(level)::equals)
            .orElse(false);
        final Optional<ServerPlayer> resolved = sameDimension
            ? resolvePlayer(level, appointment.subject().orElseThrow())
            : Optional.empty();
        final SubjectObservation observation = resolved
            .map(player -> new SubjectObservation(
                true,
                true,
                player.isAlive(),
                !player.isCreative() && !player.isSpectator(),
                player.isInvulnerable(),
                DeathImpersonationRules.isComplete(player),
                peaceful(level),
                death.distanceToSqr(player),
                appointment.approachRemainingTicks(),
                state.cadence().routeFailures()
            ))
            .orElseGet(() -> new SubjectObservation(
                false, sameDimension, false, false, false, false, peaceful(level),
                Double.MAX_VALUE, appointment.approachRemainingTicks(), state.cadence().routeFailures()
            ));
        if (DeathRules.releaseReason(observation) != ReleaseReason.NONE) {
            releaseAppointment(death);
            return Optional.empty();
        }
        final ServerPlayer subject = resolved.orElseThrow();
        death.setDeathState(state.withAppointment(new DeathState.Appointment(
            appointment.subject(), appointment.dimension(), Optional.of(subject.blockPosition()),
            appointment.approachRemainingTicks(), appointment.telegraphRemainingTicks(),
            appointment.recoverRemainingTicks(), appointment.reaped()
        )));
        return Optional.of(subject);
    }

    private static DeathState.Appointment withTelegraph(
        final DeathState.Appointment appointment,
        final int telegraphTicks
    ) {
        return new DeathState.Appointment(
            appointment.subject(), appointment.dimension(), appointment.lastSeen(),
            appointment.approachRemainingTicks(), telegraphTicks,
            appointment.recoverRemainingTicks(), appointment.reaped()
        );
    }

    private static void releaseAppointment(final DeathEntity death) {
        death.deathCounters().releases++;
        death.getNavigation().stop();
        death.setDeathState(death.deathState().releaseAppointment());
    }

    private static void beginTelegraphFeedback(final DeathEntity death, final ServerLevel level) {
        level.playSound(null, death.getX(), death.getY(), death.getZ(),
            SoundEvents.SOUL_ESCAPE.value(), death.getSoundSource(), 1.0F, 0.5F);
        level.sendParticles(ParticleTypes.SOUL, death.getX(), death.getY() + 1.6D, death.getZ(),
            8, 0.3D, 0.4D, 0.3D, 0.01D);
    }

    // ---------------------------------------------------------------- movement

    /**
     * Strict route request against both the per-Death cadence and the per-level quota. The third
     * consecutive failure stops navigation and starts the backoff while the failure counter stays
     * at its observable maximum, so the next decision's retention check releases on it.
     */
    private static void requestRoute(
        final DeathEntity death,
        final ServerLevel level,
        final ServerPlayer subject
    ) {
        final TransientState scratch = death.deathTransient();
        DeathState state = death.deathState();
        if (!DeathRules.pathRequestAllowed(scratch.pathCooldownTicks, state.cadence().routeRetryTicks())) {
            return;
        }
        if (!budget(level).claimPathRequest()) {
            death.deathCounters().quotaDenials++;
            return;
        }
        scratch.pathCooldownTicks = DeathRules.PATH_INTERVAL_TICKS;
        death.deathCounters().navigationRequests++;
        final Path path = death.getNavigation().createPath(subject, 0);
        final boolean reachable = path != null && path.canReach();
        final boolean accepted = reachable
            && death.getNavigation().moveTo(path, DeathRules.APPROACH_SPEED);
        final int failures = DeathRules.routeFailuresAfter(
            state.cadence().routeFailures(),
            new DeathRules.RouteResult(path != null, reachable, accepted)
        );
        int retry = state.cadence().routeRetryTicks();
        if (DeathRules.routeExhausted(failures)) {
            retry = DeathRules.routeBackoffAfter(failures);
            death.getNavigation().stop();
        }
        state = state.withPhase(Phase.APPROACH).withCadence(
            new DeathState.Cadence(state.cadence().reappointCooldownTicks(), failures, retry)
        );
        death.setDeathState(state);
    }

    // ---------------------------------------------------------------- entity hooks

    /**
     * Complete-disguise pacification, preserved exactly, plus the recovery bar. Death never
     * attacks a completely disguised player and never attacks at all while recovering.
     */
    public static boolean legalTarget(final DeathEntity death, final LivingEntity target) {
        if (target instanceof Player player && DeathImpersonationRules.isComplete(player)) {
            return false;
        }
        return DeathRules.mayAttack(
            death.deathState().phase(),
            death.deathState().appointment().recoverRemainingTicks()
        );
    }

    /**
     * Direct-attacker defense. A legal attacker becomes the one bounded episode subject without
     * taking an acquisition lease, which is why it is deliberately allowed to replace an existing
     * appointment but never to interrupt a recovery or to run in Peaceful.
     */
    public static void onAcceptedDamage(
        final DeathEntity death,
        final ServerLevel level,
        final DamageSource source
    ) {
        if (peaceful(level) || death.deathState().phase() == Phase.RECOVER) {
            return;
        }
        if (!(source.getEntity() instanceof Player attacker)
            || !(attacker instanceof ServerPlayer)
            || attacker.level() != level) {
            return;
        }
        if (!attacker.isAlive() || attacker.isCreative() || attacker.isSpectator()
            || attacker.isInvulnerable() || DeathImpersonationRules.isComplete(attacker)) {
            return;
        }
        if (death.deathState().appointment().subject()
            .map(attacker.getUUID()::equals).orElse(false)) {
            return;
        }
        death.deathCounters().retaliationEpisodes++;
        death.deathTransient().pathCooldownTicks = 0;
        death.setDeathState(death.deathState()
            .withAppointment(DeathState.Appointment.retaliation(attacker.getUUID(), dimensionOf(level)))
            .withPhase(Phase.APPROACH)
            .withCadence(new DeathState.Cadence(
                death.deathState().cadence().reappointCooldownTicks(), 0, 0
            )));
    }

    /** Explicit cleanup on death, discard, and every other removal route. */
    public static void onRemoved(final DeathEntity death) {
        death.getNavigation().stop();
        death.deathTransient().resetForLoad();
        death.setDeathState(death.deathState()
            .withAppointment(DeathState.Appointment.none())
            .withPhase(Phase.QUIESCENT));
    }

    // ---------------------------------------------------------------- helpers

    private static LevelBudget budget(final ServerLevel level) {
        final LevelBudget budget = LEVEL_BUDGETS.computeIfAbsent(level, _ -> new LevelBudget());
        budget.rollOver(level.getGameTime());
        return budget;
    }

    private static boolean peaceful(final ServerLevel level) {
        return level.getDifficulty() == Difficulty.PEACEFUL;
    }

    private static boolean recentlyHurt(final DeathEntity death) {
        return death.getLastHurtByMob() != null
            && DeathRules.attackerFresh(death.tickCount - death.getLastHurtByMobTimestamp());
    }

    private static Optional<ServerPlayer> resolvePlayer(final ServerLevel level, final UUID id) {
        final Player resolved = level.getPlayerByUUID(id);
        return resolved instanceof ServerPlayer player && player.level() == level
            ? Optional.of(player)
            : Optional.empty();
    }

    static String dimensionOf(final ServerLevel level) {
        return level.dimension().identifier().toString();
    }
}
