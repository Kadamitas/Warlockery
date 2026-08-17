package com.kadamitas.warlockery.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure F16 Banshee policy. No world, entity, path, level, or random state may enter this class:
 * every input is a scalar or an immutable record so the whole contract is unit-testable.
 */
public final class BansheeRules {
    /** Health fraction at or below which a player may be acquired as a warning subject. */
    public static final float ACQUIRE_HEALTH_FRACTION = 0.40F;
    /** Health fraction at or above which a sustained recovery releases the subject. */
    public static final float RELEASE_HEALTH_FRACTION = 0.60F;
    /** Loaded ticks of sustained recovery required before release. */
    public static final int RECOVERY_TICKS = 60;
    public static final int ACQUIRE_RANGE = 24;
    public static final int RELEASE_RANGE = 32;
    public static final double ACQUIRE_RANGE_SQUARED = (double) ACQUIRE_RANGE * ACQUIRE_RANGE;
    public static final double RELEASE_RANGE_SQUARED = (double) RELEASE_RANGE * RELEASE_RANGE;
    public static final int MISSING_GRACE_TICKS = 60;
    public static final int LOST_SIGHT_TICKS = 60;
    public static final int EPISODE_TICKS = 400;
    public static final int REACQUIRE_COOLDOWN_TICKS = 200;

    public static final int DISCOVERY_INTERVAL_TICKS = 40;
    public static final int MAX_CANDIDATES_VISITED = 16;
    public static final int MAX_RETAINED_CANDIDATES = 4;
    public static final int MAX_LINE_OF_SIGHT_CHECKS = 4;
    public static final int SUBJECT_SIGHT_INTERVAL_TICKS = 20;

    public static final int STANDOFF_MIN = 6;
    public static final int STANDOFF_MAX = 10;
    /** Matches the F15 HexBat flying-speed baseline: a slow, deliberate vigil glide. */
    public static final double FLYING_SPEED = 0.34D;
    public static final int WARNING_HOLD_TICKS = 20;
    /**
     * Deliberate interval-first timing: the pulse interval counts down from acquisition, so the
     * first warning pulse lands about eighty loaded ticks into the episode once the twenty-tick
     * hold is armed. This satisfies every frozen invariant (pulses at least eighty apart, at most
     * three per episode, hold plus line of sight required) and shares the load-reset rule that a
     * persisted zero interval restores the full interval instead of replaying.
     */
    public static final int WARNING_PULSE_INTERVAL_TICKS = 80;
    public static final int MAX_WARNING_PULSES = 3;
    public static final int MAX_WARNING_PARTICLES = 12;

    public static final int LAMENT_TICKS = 120;
    public static final int LAMENT_PULSE_INTERVAL_TICKS = 60;
    /** Short arming delay so both capped lament pulses fit inside one 120-tick report. */
    public static final int LAMENT_FIRST_PULSE_DELAY_TICKS = 20;
    public static final int MAX_LAMENT_PULSES = 2;
    public static final int MAX_LAMENT_PARTICLES = 12;
    public static final int LAMENT_STANDOFF_MIN = 4;
    public static final int LAMENT_STANDOFF_MAX = 8;

    public static final int TABOO_EFFECT_TICKS = 120;
    public static final int TABOO_COOLDOWN_TICKS = 120;
    public static final int RECOIL_TICKS = 60;
    public static final int RECOIL_SEARCH_HORIZONTAL = 4;
    public static final int RECOIL_SEARCH_VERTICAL = 2;
    public static final int MAX_RECOIL_PARTICLES = 16;

    public static final int ANCHOR_COMFORT_HORIZONTAL = 10;
    public static final int ANCHOR_COMFORT_VERTICAL = 6;
    public static final int ANCHOR_RETURN_HORIZONTAL = 16;
    public static final int ANCHOR_RETURN_VERTICAL = 10;
    public static final int ANCHOR_UNAVAILABLE_TICKS = 200;
    public static final int IDLE_DESTINATION_INTERVAL_TICKS = 100;
    public static final int IDLE_RADIUS_HORIZONTAL = 8;
    public static final int IDLE_RADIUS_VERTICAL = 4;
    public static final int MAX_IDLE_CANDIDATES = 12;

    public static final int MAX_SAFE_CANDIDATES = 24;
    public static final int MAX_CHARGED_READS = 256;
    public static final int HAZARD_INTERVAL_TICKS = 20;
    public static final int MAX_HAZARD_READS = 27;

    public static final int PATH_INTERVAL_TICKS = 20;
    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;

    public static final int MAX_FEEDBACK_NEIGHBOURS = 8;
    public static final int FEEDBACK_RADIUS = 24;
    public static final int AMBIENT_INTERVAL_TICKS = 200;
    public static final int MAX_AMBIENT_PARTICLES = 6;

    /** Representative encoded-state ceiling asserted by the state tests. */
    public static final int MAX_STATE_BYTES = 512;

    private BansheeRules() {
    }

    public enum Mode {
        VIGIL,
        APPROACH,
        WARNING,
        LAMENT,
        RECOIL,
        RECOVERY
    }

    public enum StandoffAction {
        APPROACH,
        WITHDRAW,
        HOLD
    }

    public enum ReleaseReason {
        NONE,
        INVALID_PLAYER,
        DIMENSION,
        RANGE,
        MISSING,
        LOST_SIGHT,
        RECOVERED,
        EPISODE_EXPIRED,
        ROUTE_FAILURE
    }

    /** One bounded, already-inspected discovery candidate. Never a live entity reference. */
    public record Candidate(
        UUID id,
        boolean ownerPriority,
        float healthFraction,
        double distanceSquared,
        boolean visible
    ) {
        public Candidate {
            Objects.requireNonNull(id, "id");
        }
    }

    /** The facts a runtime directly observed about the one current subject this decision. */
    public record SubjectObservation(
        boolean resolved,
        boolean sameDimension,
        boolean alive,
        boolean eligibleGameMode,
        boolean invulnerable,
        boolean visible,
        float healthFraction,
        double distanceSquared,
        int missingTicks,
        int lostSightTicks,
        int recoveryTicks,
        int episodeRemainingTicks,
        int routeFailures
    ) {
    }

    /** The facts a runtime directly observed about one accepted direct attacker. */
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
        boolean hostileEnemy
    ) {
    }

    public record RouteResult(boolean pathFound, boolean reachable, boolean accepted) {
        public boolean success() {
            return pathFound && reachable && accepted;
        }
    }

    // ---------------------------------------------------------------- eligibility

    public static float healthFraction(final float health, final float maxHealth) {
        if (!Float.isFinite(health) || !Float.isFinite(maxHealth) || maxHealth <= 0.0F) {
            return 1.0F;
        }
        return Math.clamp(health / maxHealth, 0.0F, 1.0F);
    }

    public static boolean atRisk(final float healthFraction) {
        return healthFraction <= ACQUIRE_HEALTH_FRACTION;
    }

    public static boolean recovered(final float healthFraction) {
        return healthFraction >= RELEASE_HEALTH_FRACTION;
    }

    /**
     * Deterministic ranking over candidates that were actually inspected. Owner priority first,
     * then lower health fraction, then shorter distance, then UUID as the final tie-break.
     */
    public static List<Candidate> rank(final List<Candidate> inspected) {
        final List<Candidate> ordered = new ArrayList<>(
            Objects.requireNonNull(inspected, "inspected").stream()
                .limit(MAX_CANDIDATES_VISITED)
                .toList()
        );
        ordered.sort(Comparator
            .comparing((Candidate candidate) -> candidate.ownerPriority() ? 0 : 1)
            .thenComparingDouble(Candidate::healthFraction)
            .thenComparingDouble(Candidate::distanceSquared)
            .thenComparing(candidate -> candidate.id().toString()));
        return List.copyOf(ordered.stream().limit(MAX_RETAINED_CANDIDATES).toList());
    }

    public static Optional<Candidate> select(final List<Candidate> inspected) {
        return rank(inspected).stream().filter(Candidate::visible).findFirst();
    }

    // ---------------------------------------------------------------- retention

    public static ReleaseReason releaseReason(final SubjectObservation observation) {
        if (observation.resolved()) {
            if (!observation.alive() || !observation.eligibleGameMode() || observation.invulnerable()) {
                return ReleaseReason.INVALID_PLAYER;
            }
            if (!observation.sameDimension()) {
                return ReleaseReason.DIMENSION;
            }
            if (observation.distanceSquared() > RELEASE_RANGE_SQUARED) {
                return ReleaseReason.RANGE;
            }
        } else if (!observation.sameDimension()) {
            return ReleaseReason.DIMENSION;
        }
        if (observation.routeFailures() >= MAX_ROUTE_FAILURES) {
            return ReleaseReason.ROUTE_FAILURE;
        }
        if (observation.episodeRemainingTicks() <= 0) {
            return ReleaseReason.EPISODE_EXPIRED;
        }
        if (!observation.resolved()) {
            return observation.missingTicks() >= MISSING_GRACE_TICKS
                ? ReleaseReason.MISSING : ReleaseReason.NONE;
        }
        if (observation.lostSightTicks() >= LOST_SIGHT_TICKS) {
            return ReleaseReason.LOST_SIGHT;
        }
        if (observation.recoveryTicks() >= RECOVERY_TICKS && recovered(observation.healthFraction())) {
            return ReleaseReason.RECOVERED;
        }
        return ReleaseReason.NONE;
    }

    /**
     * A death is only reportable when the selected subject was resolved in this loaded level and
     * directly observed to be not alive. Missing, unloaded, or cross-dimension is never evidence.
     */
    public static boolean deathReportable(
        final boolean resolved,
        final boolean sameDimension,
        final boolean alive,
        final boolean alreadyReported
    ) {
        return resolved && sameDimension && !alive && !alreadyReported;
    }

    // ---------------------------------------------------------------- warning

    public static StandoffAction standoff(final double distanceSquared, final int min, final int max) {
        if (distanceSquared > (double) max * max) {
            return StandoffAction.APPROACH;
        }
        if (distanceSquared < (double) min * min) {
            return StandoffAction.WITHDRAW;
        }
        return StandoffAction.HOLD;
    }

    public static StandoffAction warningStandoff(final double distanceSquared) {
        return standoff(distanceSquared, STANDOFF_MIN, STANDOFF_MAX);
    }

    public static StandoffAction lamentStandoff(final double distanceSquared) {
        return standoff(distanceSquared, LAMENT_STANDOFF_MIN, LAMENT_STANDOFF_MAX);
    }

    public static int advanceHold(final int holdTicks, final boolean inBand, final boolean visible) {
        return inBand && visible ? Math.min(WARNING_HOLD_TICKS, Math.max(0, holdTicks) + 1) : 0;
    }

    public static boolean holdArmed(final int holdTicks) {
        return holdTicks >= WARNING_HOLD_TICKS;
    }

    /**
     * A pulse is due only when its remaining window has actually elapsed while loaded and the
     * episode cap has not been reached. A freshly loaded zero interval is never read as due:
     * {@link #resetPulseIntervalOnLoad(int, int)} restores the full interval first.
     */
    public static boolean pulseDue(
        final int remainingIntervalTicks,
        final int emittedPulses,
        final int maxPulses,
        final boolean armed
    ) {
        return armed && emittedPulses < maxPulses && remainingIntervalTicks <= 0;
    }

    public static int resetPulseIntervalOnLoad(final int storedRemaining, final int normalInterval) {
        return storedRemaining <= 0 ? normalInterval : Math.clamp(storedRemaining, 0, normalInterval);
    }

    public static int warningPulsesRemaining(final int emitted) {
        return Math.max(0, MAX_WARNING_PULSES - Math.max(0, emitted));
    }

    public static int lamentPulsesRemaining(final int emitted) {
        return Math.max(0, MAX_LAMENT_PULSES - Math.max(0, emitted));
    }

    // ---------------------------------------------------------------- direct attacker

    public static boolean attackerLegal(final AttackerObservation observation) {
        if (!observation.living() || !observation.alive() || !observation.sameDimension()) {
            return false;
        }
        if (observation.self() || observation.owner() || observation.sameOwner()) {
            return false;
        }
        if (observation.player()) {
            return observation.eligibleGameMode() && !observation.invulnerable();
        }
        return observation.hostileEnemy();
    }

    public static boolean tabooResponseAllowed(
        final boolean attackerLegal,
        final int cooldownRemainingTicks
    ) {
        return attackerLegal && cooldownRemainingTicks <= 0;
    }

    public static boolean teleportAllowed(
        final boolean responseAllowed,
        final boolean alreadyAttempted
    ) {
        return responseAllowed && !alreadyAttempted;
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

    public static boolean anchorReturnRequired(final int horizontalDistance, final int verticalDistance) {
        return Math.abs(horizontalDistance) > ANCHOR_RETURN_HORIZONTAL
            || Math.abs(verticalDistance) > ANCHOR_RETURN_VERTICAL;
    }

    public static boolean withinAnchorComfort(final int horizontalDistance, final int verticalDistance) {
        return Math.abs(horizontalDistance) <= ANCHOR_COMFORT_HORIZONTAL
            && Math.abs(verticalDistance) <= ANCHOR_COMFORT_VERTICAL;
    }

    public static boolean reanchorRequired(final boolean dimensionMismatch, final int unavailableTicks) {
        return dimensionMismatch || unavailableTicks >= ANCHOR_UNAVAILABLE_TICKS;
    }

    // ---------------------------------------------------------------- hazard priority

    /**
     * Mode priority: an escapable hazard preempts every semantic activity, then recoil, then
     * lament, then warning, then approach, then recovery, then vigil.
     */
    public static int priority(final Mode mode, final boolean hazard) {
        if (hazard) {
            return 0;
        }
        return switch (mode) {
            case RECOIL -> 1;
            case LAMENT -> 2;
            case WARNING -> 3;
            case APPROACH -> 4;
            case RECOVERY -> 5;
            case VIGIL -> 6;
        };
    }

    public static boolean hazardPreempts(final Mode mode, final boolean escapableHazard) {
        return escapableHazard && priority(mode, true) < priority(mode, false);
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

    // ---------------------------------------------------------------- safe-destination policy

    /** One deterministic candidate offset inside the safe-destination envelope. */
    public record SafeSearchOffset(int dx, int dy, int dz) {
    }

    /** The lexicographic facts of one evaluated safe candidate. */
    public record SafeCandidate(
        double separationSquared,
        boolean hazardFree,
        double displacementSquared,
        long packedPosition
    ) {
    }

    /**
     * Up to {@code budget} (never more than {@link #MAX_SAFE_CANDIDATES}) deterministic offsets
     * that genuinely span the whole horizontal/vertical envelope: eight rotated compass
     * directions at the full ring with level height, the same eight at the vertical extremes,
     * and eight at the half ring. The compass rotation is staggered per entity UUID so repeated
     * searches sweep coverage; the origin is never produced and duplicates are removed so no
     * candidate wastes the budget.
     */
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

    /**
     * The design's lexicographic preference: greater separation from the attacker or threat
     * first, then hazard safety, then shorter displacement, then the stable packed position as
     * the final tie-break. Deliberately not a weighted score.
     */
    public static Comparator<SafeCandidate> safeCandidatePreference() {
        return Comparator.comparingDouble(SafeCandidate::separationSquared).reversed()
            .thenComparing(candidate -> candidate.hazardFree() ? 0 : 1)
            .thenComparingDouble(SafeCandidate::displacementSquared)
            .thenComparingLong(SafeCandidate::packedPosition);
    }

    // ---------------------------------------------------------------- feedback suppression

    /**
     * Capped local anti-spam gate. The evaluating Banshee is always part of its own inspected set,
     * at most {@link #MAX_FEEDBACK_NEIGHBOURS} entries are inspected, and it emits only when it
     * holds the lowest UUID inside that inspected set. This is deliberately not a global-uniqueness
     * promise: more than eight due Banshees or disjoint neighbourhoods may yield several emitters.
     */
    public static boolean mayEmit(final UUID self, final List<UUID> inspectedNeighbours) {
        Objects.requireNonNull(self, "self");
        final List<UUID> inspected = new ArrayList<>();
        inspected.add(self);
        for (final UUID neighbour : Objects.requireNonNull(inspectedNeighbours, "inspectedNeighbours")) {
            if (inspected.size() >= MAX_FEEDBACK_NEIGHBOURS) {
                break;
            }
            if (neighbour != null && !inspected.contains(neighbour)) {
                inspected.add(neighbour);
            }
        }
        return inspected.stream()
            .min(Comparator.comparing(UUID::toString))
            .map(self::equals)
            .orElse(true);
    }

    /** Every due Banshee advances its own schedule and count whether or not it actually emitted. */
    public static int advanceEmissionCount(final int emitted, final boolean due) {
        return due ? Math.max(0, emitted) + 1 : Math.max(0, emitted);
    }

    /**
     * A neighbour may suppress only when it is itself due on this tick: its interval elapsed with
     * pulses remaining, or it already advanced its own schedule this very tick. A neighbour whose
     * pulse is not due, or whose episode cap is exhausted, never silences a due pulse.
     */
    public static boolean neighbourPulseDue(
        final int remainingIntervalTicks,
        final int emittedPulses,
        final int maxPulses,
        final boolean advancedThisTick
    ) {
        return advancedThisTick
            || (remainingIntervalTicks <= 0 && emittedPulses < maxPulses);
    }

    /** Warning suppression applies only between Banshees holding the identical subject. */
    public static boolean sameWarningEvent(final Optional<UUID> mine, final Optional<UUID> theirs) {
        return mine.isPresent() && mine.equals(theirs);
    }

    /** Lament suppression applies only between Banshees mourning the identical observed death. */
    public static boolean sameDeathEvent(
        final Optional<Long> minePackedPosition,
        final Optional<String> mineDimension,
        final Optional<Long> theirPackedPosition,
        final Optional<String> theirDimension
    ) {
        return minePackedPosition.isPresent() && mineDimension.isPresent()
            && minePackedPosition.equals(theirPackedPosition)
            && mineDimension.equals(theirDimension);
    }
}
