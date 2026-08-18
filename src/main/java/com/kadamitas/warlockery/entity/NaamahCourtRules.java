package com.kadamitas.warlockery.entity;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public final class NaamahCourtRules {
    public static final int DECISION_INTERVAL_TICKS = 10;
    public static final int CANDIDATE_SCAN_INTERVAL_TICKS = 40;
    public static final int EXPENSIVE_SCAN_INTERVAL_TICKS = 40;
    public static final int NAVIGATION_INTERVAL_TICKS = 20;
    public static final int MAX_CANDIDATES = 16;
    public static final double CANDIDATE_RADIUS = 24.0D;
    public static final int MAX_DESTINATION_BLOCKS = 256;
    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_RETRY_TICKS = 100;
    public static final double MAX_LOCAL_STEP = 8.0D;
    public static final double WAVE_RADIUS = 6.0D;
    public static final float WAVE_DAMAGE = 4.0F;
    /** Once a second, as Lilith healed; scaled to Naamah's hundred rather than her two hundred. */
    public static final int REGENERATION_INTERVAL_TICKS = 20;
    public static final float REGENERATION_PER_INTERVAL = 2.5F;
    /** How long her healing stays shut after a challenger breaks her gaze. */
    public static final int GAZE_BREAK_SUPPRESSION_TICKS = 100;

    /** How long the tentacles hold, and how hard they hold. */
    public static final int BIND_DURATION_TICKS = 60;
    public static final int BIND_SLOWNESS_AMPLIFIER = 5;
    public static final float BIND_DAMAGE = 2.0F;
    /** The surge falls on the challenger's ground, so it catches whoever stands with them. */
    public static final double SURGE_RADIUS = 4.0D;
    public static final float SURGE_DAMAGE = 3.0F;

    public static final int MIN_WINDUP_TICKS = 20;
    public static final int MIN_RECOVERY_TICKS = 30;

    public enum Phase {
        ENTHRONED,
        CHORUS_OF_WAVES,
        SOVEREIGN_REFUSAL,
        AUDIENCE_CONCLUDED
    }

    public enum Action {
        NONE,
        DREAM_APPROACH,
        COURT_WAVE,
        VEIL_STEP,
        /** Tentacles out of the flooded floor hold one challenger where they stand. */
        TENTACLE_BIND,
        /** A column of water breaks over the challenger's ground and everything standing in it. */
        DROWNING_SURGE
    }

    public enum AmbientMode {
        VEILED_REST,
        HOLD_COURT,
        SEA_BORNE_COMPOSURE
    }

    public enum CandidateType {
        PLAYER,
        NAMI,
        VILLAGER,
        GOLEM,
        TURTLE,
        NAAMAH,
        VAMPIRE,
        BLOOD_THRALL,
        OTHER
    }

    public record Candidate(
        UUID id,
        CandidateType type,
        boolean directAttacker,
        boolean trialOwner,
        boolean currentChallenger,
        double distanceSquared
    ) {
        public Candidate {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(type, "type");
            distanceSquared = Math.max(0.0D, distanceSquared);
        }

        public Candidate(
            final UUID id,
            final CandidateType type,
            final boolean directAttacker,
            final boolean trialOwner,
            final double distanceSquared
        ) {
            this(id, type, directAttacker, trialOwner, false, distanceSquared);
        }
    }

    private static final Comparator<Candidate> CANDIDATE_PRIORITY = Comparator
        .comparingInt((Candidate candidate) -> candidate.trialOwner() ? 0 : 1)
        .thenComparingInt(candidate -> candidate.directAttacker() ? 0 : 1)
        .thenComparingInt(candidate -> candidate.currentChallenger() ? 0 : 1)
        .thenComparingDouble(Candidate::distanceSquared)
        .thenComparing(Candidate::id);

    public static final class CandidateAccumulator {
        private final List<Candidate> retained = new ArrayList<>(MAX_CANDIDATES);

        public void accept(final Candidate candidate) {
            Objects.requireNonNull(candidate, "candidate");
            if (!canChallenge(candidate.type(), candidate.directAttacker())) {
                return;
            }
            for (int index = 0; index < retained.size(); index++) {
                if (retained.get(index).id().equals(candidate.id())) {
                    if (CANDIDATE_PRIORITY.compare(candidate, retained.get(index)) < 0) {
                        retained.set(index, candidate);
                    }
                    return;
                }
            }
            if (retained.size() < MAX_CANDIDATES) {
                retained.add(candidate);
                return;
            }
            int worstIndex = 0;
            for (int index = 1; index < retained.size(); index++) {
                if (CANDIDATE_PRIORITY.compare(retained.get(index), retained.get(worstIndex)) > 0) {
                    worstIndex = index;
                }
            }
            if (CANDIDATE_PRIORITY.compare(candidate, retained.get(worstIndex)) < 0) {
                retained.set(worstIndex, candidate);
            }
        }

        public int size() {
            return retained.size();
        }

        public List<Candidate> snapshot() {
            return retained.stream().sorted(CANDIDATE_PRIORITY).toList();
        }
    }

    public record ActionWindow(Action action, long startedAt, long executeAt, long recoverUntil) {
        public ActionWindow {
            Objects.requireNonNull(action, "action");
        }
    }

    public record RouteRetry(int failures, long retryAfter) {
        public RouteRetry {
            failures = Math.clamp(failures, 0, MAX_ROUTE_FAILURES);
            retryAfter = Math.max(0L, retryAfter);
        }
    }

    private NaamahCourtRules() {
    }

    public static Phase latchPhase(final Phase current, final float health, final float maximumHealth) {
        Objects.requireNonNull(current, "current");
        if (current == Phase.AUDIENCE_CONCLUDED) {
            return current;
        }
        final float ratio = maximumHealth <= 0.0F ? 0.0F : Math.max(0.0F, health) / maximumHealth;
        final Phase observed = ratio <= 0.34F
            ? Phase.SOVEREIGN_REFUSAL
            : ratio <= 0.67F ? Phase.CHORUS_OF_WAVES : Phase.ENTHRONED;
        return observed.ordinal() > current.ordinal() ? observed : current;
    }

    public static boolean canChallenge(final CandidateType type) {
        return type == CandidateType.PLAYER;
    }

    public static boolean canChallenge(final CandidateType type, final boolean directAttacker) {
        return type == CandidateType.PLAYER || type == CandidateType.OTHER && directAttacker;
    }

    public static Optional<UUID> chooseChallenger(final List<Candidate> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        return candidates.stream()
            .filter(candidate -> canChallenge(candidate.type(), candidate.directAttacker()))
            .min(CANDIDATE_PRIORITY)
            .map(Candidate::id);
    }

    public static ActionWindow begin(
        final Action action,
        final long now,
        final int requestedWindup,
        final int requestedRecovery
    ) {
        final long executeAt = now + Math.max(MIN_WINDUP_TICKS, requestedWindup);
        return new ActionWindow(
            action,
            now,
            executeAt,
            executeAt + Math.max(MIN_RECOVERY_TICKS, requestedRecovery)
        );
    }

    public static boolean canExecute(final ActionWindow window, final long now, final boolean validAtExecution) {
        return window.action() != Action.NONE && now >= window.executeAt() && validAtExecution;
    }

    public static boolean isRecovering(final ActionWindow window, final long now) {
        return now < window.recoverUntil();
    }

    /**
     * Naamah mends herself only while she is holding a challenger in her gaze.
     *
     * <p>Lilith regenerated relentlessly and was answered by knocking her own fireball back into
     * her. A guardian is answered instead by stepping out of its line of sight, which is why its
     * hall is full of pillars, and Naamah holds court in exactly that hall. So the counterplay is
     * the monument's own: break her line of sight and the mending stops, and stays stopped for a
     * while afterwards so that ducking out and back in is not free.</p>
     */
    public static boolean mayRegenerate(
        final boolean alive,
        final float health,
        final float maxHealth,
        final boolean holdsGaze,
        final long now,
        final long suppressedUntil
    ) {
        return alive && health > 0.0F && health < maxHealth && holdsGaze && now >= suppressedUntil;
    }

    public static Action automaticAction(final Phase phase, final long decisionSlot) {
        final long slot = Math.floorMod(decisionSlot, 32L);
        return switch (phase) {
            case ENTHRONED -> slot % 8L == 0L ? Action.DREAM_APPROACH : Action.NONE;
            case CHORUS_OF_WAVES -> slot % 4L == 1L ? Action.COURT_WAVE
                : slot % 4L == 3L ? Action.TENTACLE_BIND : Action.NONE;
            case SOVEREIGN_REFUSAL -> slot % 16L == 3L ? Action.VEIL_STEP
                : slot % 16L == 11L ? Action.COURT_WAVE
                : slot % 16L == 7L ? Action.DROWNING_SURGE
                : slot % 16L == 15L ? Action.TENTACLE_BIND : Action.NONE;
            case AUDIENCE_CONCLUDED -> Action.NONE;
        };
    }

    public static AmbientMode ambientMode(
        final boolean daylight,
        final boolean sheltered,
        final boolean inWater
    ) {
        if (inWater) return AmbientMode.SEA_BORNE_COMPOSURE;
        return daylight && sheltered ? AmbientMode.VEILED_REST : AmbientMode.HOLD_COURT;
    }

    public static int ambientFeedbackInterval(final AmbientMode mode) {
        Objects.requireNonNull(mode, "mode");
        return mode == AmbientMode.VEILED_REST ? 400 : 200;
    }

    public static boolean holdsPosition(final AmbientMode mode) {
        Objects.requireNonNull(mode, "mode");
        return true;
    }

    public static RouteRetry routeFailure(final int priorFailures, final long now) {
        final int failures = Math.min(MAX_ROUTE_FAILURES, Math.max(0, priorFailures) + 1);
        return new RouteRetry(failures, failures >= MAX_ROUTE_FAILURES ? now + ROUTE_RETRY_TICKS : 0L);
    }

    public static RouteRetry routeSuccess() {
        return new RouteRetry(0, 0L);
    }

    public static boolean navigationDue(final long lastNavigationAt, final long now) {
        return lastNavigationAt <= now - NAVIGATION_INTERVAL_TICKS;
    }

    public static boolean mayBeginMovementAction(
        final long now,
        final long retryAfter,
        final boolean hazardActive,
        final boolean hasSafeDestination
    ) {
        return now >= retryAfter && !hazardActive && hasSafeDestination;
    }

    public static long staggeredDeadline(final long now, final int entityId, final int interval) {
        return now + Math.floorMod(entityId, Math.max(1, interval));
    }

    public static boolean withinLocalStep(final BlockPos origin, final BlockPos destination) {
        final long deltaX = destination.getX() - origin.getX();
        final long deltaZ = destination.getZ() - origin.getZ();
        return deltaX * deltaX + deltaZ * deltaZ <= MAX_LOCAL_STEP * MAX_LOCAL_STEP;
    }

    public static boolean mayAttack(
        final UUID candidate,
        final boolean audienceConcluded,
        final Optional<UUID> concludedOwner
    ) {
        return !audienceConcluded || concludedOwner.filter(candidate::equals).isEmpty();
    }
}
