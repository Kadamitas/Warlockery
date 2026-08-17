package com.kadamitas.warlockery.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure F19 Spirit policy. No world, entity, path, level, or random state may enter this class:
 * every input is a scalar or an immutable record so the whole contract is unit-testable.
 *
 * <p>The Spirit is a naturally present, nonhuman-unspecified local spirit. Free, it keeps a
 * bounded wary radius from an approaching unbound player but does not flee forever: after
 * separation it may attend one nearby soul-light place for a finite interval. Bound, it follows,
 * supplies the established aura, and may warn and then defend exactly once against the owner's
 * recent valid direct attacker before recovering.</p>
 */
public final class SpiritRules {
    /** Preserved from the audited free-Spirit avoidance predicate: a 12 block wary radius. */
    public static final int WARY_RANGE = 12;
    public static final double WARY_RANGE_SQUARED = (double) WARY_RANGE * WARY_RANGE;
    /** The wary reaction is finite: it never becomes permanent flight. */
    public static final int WARY_TICKS = 120;
    public static final int WARY_COOLDOWN_TICKS = 200;
    /** Withdrawal stays deliberately short so a computed destination never leaves the locality. */
    public static final int WARY_WITHDRAW_HORIZONTAL = 5;
    public static final int WARY_WITHDRAW_VERTICAL = 2;
    public static final int SEPARATION_RANGE = 16;
    public static final double SEPARATION_RANGE_SQUARED = (double) SEPARATION_RANGE * SEPARATION_RANGE;

    public static final int ATTEND_TICKS = 200;
    public static final int ATTEND_COOLDOWN_TICKS = 600;
    public static final int ATTEND_SEARCH_HORIZONTAL = 6;
    public static final int ATTEND_SEARCH_VERTICAL = 2;
    /** Exact read ceiling of the 13 x 5 x 13 attendance envelope. Never a wider scan. */
    public static final int MAX_ATTEND_READS = 845;
    public static final int MAX_ATTEND_CANDIDATES_RETAINED = 4;
    public static final int ATTEND_BAND_MIN = 1;
    public static final int ATTEND_BAND_MAX = 3;
    public static final int ATTEND_PULSE_INTERVAL_TICKS = 60;
    public static final int MAX_ATTEND_PULSES = 2;
    public static final int MAX_ATTEND_PARTICLES = 6;

    public static final int FOLLOW_BAND_MIN = 3;
    public static final int FOLLOW_BAND_MAX = 8;
    public static final int OWNER_RELEASE_RANGE = 48;
    public static final double OWNER_RELEASE_RANGE_SQUARED =
        (double) OWNER_RELEASE_RANGE * OWNER_RELEASE_RANGE;
    /** Preserved exactly from the audited generic aura: Night Vision 240 ticks every 20 ticks. */
    public static final int AURA_INTERVAL_TICKS = 20;
    public static final int AURA_NIGHT_VISION_TICKS = 240;

    /** A defence is only ever raised against an attack the owner took within this many ticks. */
    public static final int ATTACKER_FRESHNESS_TICKS = 40;
    public static final int WARN_TICKS = 40;
    public static final int MAX_WARN_PULSES = 2;
    public static final int WARN_PULSE_INTERVAL_TICKS = 20;
    public static final int MAX_WARN_PARTICLES = 8;
    /** One bounded window inside which exactly one ordinary attributed strike is permitted. */
    public static final int DEFEND_TICKS = 60;
    public static final int MAX_DEFENCE_STRIKES = 1;
    public static final int DEFEND_RANGE = 24;
    public static final double DEFEND_RANGE_SQUARED = (double) DEFEND_RANGE * DEFEND_RANGE;
    public static final double STRIKE_REACH_SQUARED = 4.0D;
    public static final int RECOVER_TICKS = 200;

    public static final int WANDER_INTERVAL_TICKS = 200;
    public static final int WANDER_RADIUS_HORIZONTAL = 6;
    public static final int WANDER_RADIUS_VERTICAL = 3;
    public static final int MAX_WANDER_CANDIDATES = 12;

    public static final int PROXIMITY_INTERVAL_TICKS = 20;
    public static final int MAX_PROXIMITY_CANDIDATES = 8;
    public static final int PATH_INTERVAL_TICKS = 20;
    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;

    public static final int HAZARD_INTERVAL_TICKS = 20;
    public static final int MAX_HAZARD_READS = 27;
    public static final int MAX_SAFE_CANDIDATES = 24;
    public static final int MAX_CHARGED_READS = 256;
    public static final int ESCAPE_SEARCH_HORIZONTAL = 6;
    public static final int ESCAPE_SEARCH_VERTICAL = 3;

    /** Representative encoded-state ceiling asserted by the state tests. */
    public static final int MAX_STATE_BYTES = 512;

    private SpiritRules() {
    }

    /**
     * The complete Spirit phase set. There is deliberately no petition, memorial settle, or
     * reward phase: those belong to the Lost Soul, which is a different being.
     */
    public enum Phase {
        WANDER,
        WARY,
        ATTEND,
        BOUND,
        WARN,
        DEFEND,
        RECOVER
    }

    public enum BandAction {
        APPROACH,
        WITHDRAW,
        HOLD
    }

    public enum GuardEnd {
        NONE,
        NO_OWNER,
        INVALID_ATTACKER,
        RANGE,
        DIMENSION,
        STRUCK,
        EXPIRED,
        ROUTE_FAILURE
    }

    /** One bounded, already-read soul-light candidate. Never a live block or block entity. */
    public record AttendCandidate(long packedPosition, double distanceSquared) {
    }

    /** The facts a runtime directly observed about one candidate direct attacker. */
    public record AttackerObservation(
        boolean living,
        boolean alive,
        boolean sameDimension,
        boolean self,
        boolean owner,
        boolean sameOwner,
        boolean player,
        boolean eligibleGameMode,
        boolean invulnerable,
        int ticksSinceOwnerWasHurt
    ) {
    }

    /**
     * The facts a runtime directly observed about the one accepted guard subject this decision.
     *
     * <p>{@code warning} is load bearing. While the Spirit is still warning, the guard has no
     * expiry of its own: the warning graduates into the defence window through
     * {@link #warningGraduates(int)}. Only the defence window may expire the guard, so a warning
     * countdown reaching zero can never race the graduation it is supposed to cause.</p>
     */
    public record GuardObservation(
        boolean ownerResolved,
        boolean attackerResolved,
        boolean attackerLegal,
        boolean sameDimension,
        boolean warning,
        double distanceSquared,
        int strikes,
        int remainingTicks,
        int routeFailures
    ) {
    }

    public record RouteResult(boolean pathFound, boolean reachable, boolean accepted) {
        public boolean success() {
            return pathFound && reachable && accepted;
        }
    }

    // ---------------------------------------------------------------- free temperament

    /**
     * Retained free-Spirit contract: an unbound Spirit keeps its distance from a living player
     * inside the wary radius. Binding stops it outright, which is what makes the transition
     * observable rather than a gradual decay.
     */
    public static boolean shouldWithdraw(
        final boolean bound,
        final boolean playerAlive,
        final boolean eligibleGameMode,
        final double distanceSquared,
        final int waryCooldownTicks
    ) {
        return !bound && playerAlive && eligibleGameMode
            && distanceSquared <= WARY_RANGE_SQUARED
            && waryCooldownTicks <= 0;
    }

    public static boolean separated(final double distanceSquared) {
        return distanceSquared > SEPARATION_RANGE_SQUARED;
    }

    public static boolean attendAllowed(
        final boolean bound,
        final boolean separated,
        final int attendCooldownTicks
    ) {
        return !bound && separated && attendCooldownTicks <= 0;
    }

    // ---------------------------------------------------------------- attendance selection

    /** Stable distance-then-block-position ordering over candidates that were actually read. */
    public static List<AttendCandidate> rank(final List<AttendCandidate> inspected) {
        final List<AttendCandidate> ordered = new ArrayList<>(
            Objects.requireNonNull(inspected, "inspected")
        );
        ordered.sort(Comparator.comparingDouble(AttendCandidate::distanceSquared)
            .thenComparingLong(AttendCandidate::packedPosition));
        return List.copyOf(ordered.stream().limit(MAX_ATTEND_CANDIDATES_RETAINED).toList());
    }

    public static Optional<AttendCandidate> select(final List<AttendCandidate> inspected) {
        return rank(inspected).stream().findFirst();
    }

    // ---------------------------------------------------------------- owner defence

    /**
     * A defence subject must be the owner's recent, valid, direct attacker. The owner, another
     * mob owned by the same player, the Spirit itself, a creative or spectator player, an
     * invulnerable entity, a stale attack, or a cross-dimension attacker are all rejected. The
     * Spirit never derives a target from anything else the owner happens to be fighting.
     */
    public static boolean attackerLegal(final AttackerObservation observation) {
        if (!observation.living() || !observation.alive() || !observation.sameDimension()) {
            return false;
        }
        if (observation.self() || observation.owner() || observation.sameOwner()) {
            return false;
        }
        if (observation.invulnerable()) {
            return false;
        }
        if (observation.ticksSinceOwnerWasHurt() < 0
            || observation.ticksSinceOwnerWasHurt() > ATTACKER_FRESHNESS_TICKS) {
            return false;
        }
        return !observation.player() || observation.eligibleGameMode();
    }

    public static boolean guardAllowed(
        final boolean bound,
        final boolean attackerLegal,
        final int recoverRemainingTicks
    ) {
        return bound && attackerLegal && recoverRemainingTicks <= 0;
    }

    public static GuardEnd guardEnd(final GuardObservation observation) {
        if (!observation.ownerResolved()) {
            return GuardEnd.NO_OWNER;
        }
        if (!observation.sameDimension()) {
            return GuardEnd.DIMENSION;
        }
        if (!observation.attackerResolved() || !observation.attackerLegal()) {
            return GuardEnd.INVALID_ATTACKER;
        }
        if (observation.strikes() >= MAX_DEFENCE_STRIKES) {
            return GuardEnd.STRUCK;
        }
        if (observation.distanceSquared() > DEFEND_RANGE_SQUARED) {
            return GuardEnd.RANGE;
        }
        if (observation.routeFailures() >= MAX_ROUTE_FAILURES) {
            return GuardEnd.ROUTE_FAILURE;
        }
        if (!observation.warning() && observation.remainingTicks() <= 0) {
            return GuardEnd.EXPIRED;
        }
        return GuardEnd.NONE;
    }

    /**
     * The one producer of the defence window. A warning whose countdown has elapsed graduates
     * into DEFEND rather than ending the guard, which is what makes the single attributed strike
     * reachable at all.
     */
    public static boolean warningGraduates(final int warnRemainingTicks) {
        return warnRemainingTicks <= 0;
    }

    public static boolean strikeAllowed(
        final int strikes,
        final double distanceSquared,
        final boolean visible,
        final int defendRemainingTicks
    ) {
        return strikes < MAX_DEFENCE_STRIKES
            && visible
            && defendRemainingTicks > 0
            && distanceSquared <= STRIKE_REACH_SQUARED;
    }

    /**
     * Ordinary attribution: the strike carries the Spirit's own attack attribute and its own
     * damage source. No owner-derived amplification, typed bypass, or effect rider is added.
     */
    public static float strikeDamage(final float attackAttribute) {
        return Float.isFinite(attackAttribute) ? Math.max(0.0F, attackAttribute) : 0.0F;
    }

    /** The Spirit never proactively targets a player, bound or free. */
    public static boolean canAttack(
        final boolean bound,
        final boolean defending,
        final boolean isAcceptedAttacker
    ) {
        return bound && defending && isAcceptedAttacker;
    }

    // ---------------------------------------------------------------- bands

    public static BandAction band(final double distanceSquared, final int min, final int max) {
        if (distanceSquared > (double) max * max) {
            return BandAction.APPROACH;
        }
        if (distanceSquared < (double) min * min) {
            return BandAction.WITHDRAW;
        }
        return BandAction.HOLD;
    }

    public static BandAction followBand(final double distanceSquared) {
        return band(distanceSquared, FOLLOW_BAND_MIN, FOLLOW_BAND_MAX);
    }

    public static BandAction attendBand(final double distanceSquared) {
        return band(distanceSquared, ATTEND_BAND_MIN, ATTEND_BAND_MAX);
    }

    public static boolean ownerAttendanceAllowed(
        final boolean ownerPresent,
        final boolean ownerAlive,
        final boolean sameDimension,
        final double distanceSquared
    ) {
        return ownerPresent && ownerAlive && sameDimension
            && distanceSquared <= OWNER_RELEASE_RANGE_SQUARED;
    }

    public static boolean auraDue(final int loadedTicks) {
        return loadedTicks >= 0 && Math.floorMod(loadedTicks, AURA_INTERVAL_TICKS) == 0;
    }

    // ---------------------------------------------------------------- feedback

    public static boolean pulseDue(
        final int remainingIntervalTicks,
        final int emittedPulses,
        final int maxPulses
    ) {
        return emittedPulses < maxPulses && remainingIntervalTicks <= 0;
    }

    public static int resetPulseIntervalOnLoad(final int storedRemaining, final int normalInterval) {
        return storedRemaining <= 0 ? normalInterval : Math.clamp(storedRemaining, 0, normalInterval);
    }

    public static int warnPulsesRemaining(final int emitted) {
        return Math.max(0, MAX_WARN_PULSES - Math.max(0, emitted));
    }

    public static int attendPulsesRemaining(final int emitted) {
        return Math.max(0, MAX_ATTEND_PULSES - Math.max(0, emitted));
    }

    /**
     * The shipped bound-companion recall, retained byte for byte from the generic writer that used
     * to own it: past {@link CreatureBehaviorRules#OWNER_TELEPORT_DISTANCE_SQUARED} a bound Spirit
     * snaps back to its owner rather than being silently left behind. This is a preserved
     * capability rather than a new one, and it is the only teleport in the package.
     */
    public static boolean ownerRecallRequired(final double distanceSquared) {
        return distanceSquared >= CreatureBehaviorRules.OWNER_TELEPORT_DISTANCE_SQUARED;
    }

    // ---------------------------------------------------------------- movement

    public static boolean pathRequestAllowed(final int remainingPathTicks, final int backoffRemainingTicks) {
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

    // ---------------------------------------------------------------- priority

    /**
     * Frozen priority for this species: an escapable hazard preempts everything, then the owner
     * defence, then the binding transition, then the species attention phases, then idle.
     */
    public static int priority(
        final Phase phase,
        final boolean hazard,
        final boolean defence,
        final boolean bindingTransition
    ) {
        if (hazard) {
            return 0;
        }
        if (defence) {
            return 1;
        }
        if (bindingTransition) {
            return 2;
        }
        return switch (phase) {
            case DEFEND -> 3;
            case WARN -> 4;
            case WARY -> 5;
            case ATTEND -> 6;
            case BOUND -> 7;
            case RECOVER -> 8;
            case WANDER -> 9;
        };
    }

    public static boolean hazardPreempts(final Phase phase, final boolean escapableHazard) {
        return escapableHazard
            && priority(phase, true, false, false) < priority(phase, false, false, false);
    }

    public static boolean defencePreempts(final Phase phase, final boolean defence) {
        return defence
            && priority(phase, false, true, false) < priority(phase, false, false, false);
    }

    public static boolean bindingPreempts(final Phase phase, final boolean bindingTransition) {
        return bindingTransition
            && priority(phase, false, false, true) < priority(phase, false, false, false);
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

    public static int stableOffset(final UUID id, final int span) {
        if (id == null || span <= 0) {
            return 0;
        }
        return (int) Math.floorMod(id.getMostSignificantBits() ^ id.getLeastSignificantBits(), (long) span);
    }

    // ---------------------------------------------------------------- safe-destination policy

    public record SafeSearchOffset(int dx, int dy, int dz) {
    }

    public record SafeCandidate(
        double separationSquared,
        boolean hazardFree,
        double displacementSquared,
        long packedPosition
    ) {
    }

    public static List<SafeSearchOffset> safeSearchOffsets(
        final UUID id,
        final int horizontalRadius,
        final int verticalRadius,
        final int budget
    ) {
        final int[][] compass = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        final int rotation = stableOffset(id, compass.length);
        final int full = Math.max(1, horizontalRadius);
        final int half = Math.max(1, horizontalRadius / 2);
        final int vertical = Math.max(0, verticalRadius);
        final int cap = Math.clamp(budget, 0, MAX_SAFE_CANDIDATES);
        final java.util.LinkedHashSet<SafeSearchOffset> offsets = new java.util.LinkedHashSet<>();
        for (int index = 0; index < MAX_SAFE_CANDIDATES && offsets.size() < cap; index++) {
            final int[] direction = compass[(index + rotation) % compass.length];
            final int layer = index / compass.length;
            final int radius = layer == 2 ? half : full;
            final int dy = layer == 1 ? (index % 2 == 0 ? vertical : -vertical) : 0;
            final SafeSearchOffset offset =
                new SafeSearchOffset(direction[0] * radius, dy, direction[1] * radius);
            if (offset.dx() != 0 || offset.dy() != 0 || offset.dz() != 0) {
                offsets.add(offset);
            }
        }
        return List.copyOf(offsets);
    }

    public static Comparator<SafeCandidate> safeCandidatePreference() {
        return Comparator.comparingDouble(SafeCandidate::separationSquared).reversed()
            .thenComparing(candidate -> candidate.hazardFree() ? 0 : 1)
            .thenComparingDouble(SafeCandidate::displacementSquared)
            .thenComparingLong(SafeCandidate::packedPosition);
    }
}
