package com.kadamitas.warlockery.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure F18 Death policy. No world, entity, level, path, or random state may enter this class:
 * every input is a scalar or an immutable record, so the whole appointment contract is decided
 * by unit-testable truth tables. Death is an appointment keeper, not a death handler: nothing
 * here reads, predicts, prevents, or records any player death, drop, or respawn.
 */
public final class DeathRules {
    /** Infrequent bounded acquisition cadence. */
    public static final int DISCOVERY_INTERVAL_TICKS = 60;
    public static final int APPOINT_RANGE = 24;
    public static final int RELEASE_RANGE = 32;
    public static final double APPOINT_RANGE_SQUARED = (double) APPOINT_RANGE * APPOINT_RANGE;
    public static final double RELEASE_RANGE_SQUARED = (double) RELEASE_RANGE * RELEASE_RANGE;
    public static final int MAX_CANDIDATES_VISITED = 16;
    public static final int MAX_RETAINED_CANDIDATES = 4;
    public static final int MAX_LINE_OF_SIGHT_CHECKS = 4;
    /** At most this many other loaded Deaths are inspected when claiming a subject lease. */
    public static final int MAX_LEASE_NEIGHBOURS = 8;
    public static final int LEASE_RADIUS = 32;

    /** Finite approach deadline in remaining loaded ticks. */
    public static final int APPROACH_DEADLINE_TICKS = 400;
    /** Clear finite telegraph held at melee reach before the single attempt. */
    public static final int TELEGRAPH_TICKS = 40;
    /** Recovery after the attempt, during which Death cannot attack again. */
    public static final int RECOVER_TICKS = 100;
    /** Backoff before a released Death may appoint again. */
    public static final int REAPPOINT_COOLDOWN_TICKS = 200;
    public static final int REACH = 3;
    public static final double REACH_SQUARED = (double) REACH * REACH;

    public static final int PATH_INTERVAL_TICKS = 20;
    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;
    public static final double APPROACH_SPEED = 1.0D;

    /** A retaliation memory is only fresh for this many loaded ticks. */
    public static final int DIRECT_ATTACKER_FRESHNESS_TICKS = 40;
    /** A retaliation approach is bounded and never creates an appointment. */
    public static final int DIRECT_ATTACKER_TICKS = 100;

    /** Preserved 1.4.0 combat surface. */
    public static final int WITHER_DURATION_TICKS = 120;
    public static final int WITHER_AMPLIFIER = 1;
    public static final int VIGIL_HEAL_INTERVAL_TICKS = 20;
    public static final float VIGIL_HEAL_AMOUNT = 1.0F;

    /** Per-level per-tick work quotas shared by every loaded Death. */
    public static final int MAX_LEVEL_PATH_REQUESTS_PER_TICK = 4;
    public static final int MAX_LEVEL_DISCOVERY_SCANS_PER_TICK = 2;

    /** Representative encoded-state ceiling asserted by the state tests. */
    public static final int MAX_STATE_BYTES = 512;

    private DeathRules() {
    }

    /** Fixed-cardinality episode. {@code RELEASE} is a one-decision settling phase. */
    public enum Phase {
        QUIESCENT,
        APPOINTED,
        APPROACH,
        TELEGRAPH,
        REAP,
        RECOVER,
        RELEASE
    }

    public enum ReleaseReason {
        NONE,
        INVALID_SUBJECT,
        DIMENSION,
        RANGE,
        DISGUISED,
        PEACEFUL,
        TIMEOUT,
        ROUTE_FAILURE
    }

    /** One bounded, already-inspected acquisition candidate. Never a live entity reference. */
    public record Candidate(UUID id, double distanceSquared, boolean visible) {
        public Candidate {
            Objects.requireNonNull(id, "id");
        }
    }

    /** The facts a runtime directly observed about the one appointed subject this decision. */
    public record SubjectObservation(
        boolean resolved,
        boolean sameDimension,
        boolean alive,
        boolean survivalMode,
        boolean invulnerable,
        boolean completelyDisguised,
        boolean peaceful,
        double distanceSquared,
        int approachRemainingTicks,
        int routeFailures
    ) {
    }

    /** The facts a runtime directly observed about one candidate before appointing it. */
    public record CandidateObservation(
        boolean alive,
        boolean survivalMode,
        boolean invulnerable,
        boolean completelyDisguised,
        boolean sameDimension,
        boolean loaded,
        double distanceSquared
    ) {
    }

    public record RouteResult(boolean pathFound, boolean reachable, boolean accepted) {
        public boolean success() {
            return pathFound && reachable && accepted;
        }
    }

    // ---------------------------------------------------------------- acquisition

    public static boolean appointable(final CandidateObservation observation) {
        return observation.alive()
            && observation.survivalMode()
            && !observation.invulnerable()
            && !observation.completelyDisguised()
            && observation.sameDimension()
            && observation.loaded()
            && observation.distanceSquared() <= APPOINT_RANGE_SQUARED;
    }

    /**
     * Quiescent Death only scans when nothing else claims it. Hurt, hazard, an existing
     * appointment, Peaceful, a live backoff, and a nearby completely disguised player all
     * suppress acquisition entirely.
     */
    public static boolean discoveryAllowed(
        final boolean quiescent,
        final boolean hazard,
        final boolean recentlyHurt,
        final boolean peaceful,
        final boolean disguisedPlayerNear,
        final int reappointCooldownTicks
    ) {
        return quiescent && !hazard && !recentlyHurt && !peaceful && !disguisedPlayerNear
            && reappointCooldownTicks <= 0;
    }

    /** Deterministic ranking over candidates that were actually inspected: distance, then UUID. */
    public static List<Candidate> rank(final List<Candidate> inspected) {
        final List<Candidate> ordered = new ArrayList<>(
            Objects.requireNonNull(inspected, "inspected").stream()
                .limit(MAX_CANDIDATES_VISITED)
                .toList()
        );
        ordered.sort(Comparator
            .comparingDouble(Candidate::distanceSquared)
            .thenComparing(candidate -> candidate.id().toString()));
        return List.copyOf(ordered.stream().limit(MAX_RETAINED_CANDIDATES).toList());
    }

    public static Optional<Candidate> select(final List<Candidate> inspected) {
        return rank(inspected).stream().filter(Candidate::visible).findFirst();
    }

    /**
     * The level-local subject lease. At most {@link #MAX_LEASE_NEIGHBOURS} nearby Deaths are
     * inspected; a subject already held by any of them is not appointable by this Death. This is
     * deliberately a bounded local claim, not a global uniqueness promise.
     */
    public static boolean leaseAvailable(final UUID subject, final List<UUID> inspectedHeldSubjects) {
        Objects.requireNonNull(subject, "subject");
        return Objects.requireNonNull(inspectedHeldSubjects, "inspectedHeldSubjects").stream()
            .limit(MAX_LEASE_NEIGHBOURS)
            .noneMatch(subject::equals);
    }

    // ---------------------------------------------------------------- retention

    public static ReleaseReason releaseReason(final SubjectObservation observation) {
        if (observation.peaceful()) {
            return ReleaseReason.PEACEFUL;
        }
        if (!observation.sameDimension()) {
            return ReleaseReason.DIMENSION;
        }
        if (!observation.resolved()) {
            return ReleaseReason.INVALID_SUBJECT;
        }
        if (!observation.alive() || !observation.survivalMode() || observation.invulnerable()) {
            return ReleaseReason.INVALID_SUBJECT;
        }
        if (observation.completelyDisguised()) {
            return ReleaseReason.DISGUISED;
        }
        if (observation.distanceSquared() > RELEASE_RANGE_SQUARED) {
            return ReleaseReason.RANGE;
        }
        if (routeExhausted(observation.routeFailures())) {
            return ReleaseReason.ROUTE_FAILURE;
        }
        if (observation.approachRemainingTicks() <= 0) {
            return ReleaseReason.TIMEOUT;
        }
        return ReleaseReason.NONE;
    }

    // ---------------------------------------------------------------- episode

    public static boolean withinReach(final double distanceSquared) {
        return distanceSquared <= REACH_SQUARED;
    }

    public static boolean telegraphComplete(final int telegraphRemainingTicks) {
        return telegraphRemainingTicks <= 0;
    }

    /**
     * The single attempt is permitted only once per episode, only after the whole telegraph
     * elapsed while loaded, and only while every retention predicate still holds at reach.
     */
    public static boolean reapAllowed(
        final boolean predicatesHold,
        final boolean withinReach,
        final int telegraphRemainingTicks,
        final boolean alreadyReaped
    ) {
        return predicatesHold && withinReach && telegraphComplete(telegraphRemainingTicks)
            && !alreadyReaped;
    }

    public static boolean recoveryComplete(final int recoverRemainingTicks) {
        return recoverRemainingTicks <= 0;
    }

    /** Death never attacks while recovering, so no catch-up or replacement strike can exist. */
    public static boolean mayAttack(final Phase phase, final int recoverRemainingTicks) {
        return phase != Phase.RECOVER && recoveryComplete(recoverRemainingTicks);
    }

    // ---------------------------------------------------------------- priority

    /** Hazard escape beats direct attacker defense, which beats the appointment, which beats idle. */
    public static int priority(final Phase phase, final boolean hazard, final boolean directAttacker) {
        if (hazard) {
            return 0;
        }
        if (directAttacker) {
            return 1;
        }
        return switch (phase) {
            case APPOINTED, APPROACH, TELEGRAPH, REAP -> 2;
            case RECOVER, RELEASE -> 3;
            case QUIESCENT -> 4;
        };
    }

    public static boolean hazardPreempts(final Phase phase, final boolean escapableHazard) {
        return escapableHazard && priority(phase, true, false) < priority(phase, false, false);
    }

    public static boolean attackerPreempts(final Phase phase, final boolean freshAttacker) {
        return freshAttacker && priority(phase, false, true) < priority(phase, false, false);
    }

    public static boolean attackerFresh(final int ticksSinceHurt) {
        return ticksSinceHurt >= 0 && ticksSinceHurt <= DIRECT_ATTACKER_FRESHNESS_TICKS;
    }

    // ---------------------------------------------------------------- movement

    public static boolean pathRequestAllowed(
        final int remainingPathTicks,
        final int backoffRemainingTicks
    ) {
        return remainingPathTicks <= 0 && backoffRemainingTicks <= 0;
    }

    public static int routeFailuresAfter(final int failures, final RouteResult result) {
        return result.success() ? 0 : Math.clamp(failures + 1, 0, MAX_ROUTE_FAILURES);
    }

    public static boolean routeExhausted(final int failures) {
        return failures >= MAX_ROUTE_FAILURES;
    }

    public static int routeBackoffAfter(final int failures) {
        return routeExhausted(failures) ? ROUTE_BACKOFF_TICKS : 0;
    }

    // ---------------------------------------------------------------- budgets

    public static boolean budgetAllows(final int used, final int cap) {
        return used < Math.max(0, cap);
    }

    // ---------------------------------------------------------------- durations

    public static int clampRemaining(final int stored, final int maximum) {
        final int bounded = Math.max(0, maximum);
        if (stored < 0) {
            return 0;
        }
        return Math.min(stored, bounded);
    }

    public static int decrementLoaded(final int remaining) {
        return Math.max(0, remaining - 1);
    }

    /** Staggers per-entity cadence deterministically without ever using absolute world time. */
    public static int stableOffset(final UUID id, final int span) {
        if (id == null || span <= 0) {
            return 0;
        }
        return (int) Math.floorMod(id.getMostSignificantBits() ^ id.getLeastSignificantBits(), (long) span);
    }

    // ---------------------------------------------------------------- preserved combat

    /**
     * The exact preserved 1.4.0 primary melee shape: at least one, or fifteen percent of the
     * victim's maximum health, expressed as the transient bonus added to the attack attribute.
     */
    public static float primaryMeleeBonus(final float targetMaximumHealth, final double attackAttribute) {
        return Math.max(0.0F, DeathCombatRules.meleeDamage(targetMaximumHealth) - (float) attackAttribute);
    }
}
