package com.kadamitas.warlockery.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure F19 Lost Soul policy. No world, entity, path, level, or random state may enter this class:
 * every input is a scalar or an immutable record so the whole contract is unit-testable.
 *
 * <p>The Lost Soul is a called human-dead-coded shade. Its only species attention is a finite
 * memorial episode: choose one loaded reachable memorial anchor, approach, pause in visible
 * petition, settle nearby briefly, then cool down. It never warns, never defends, never targets,
 * never collects souls, and never completes as a reward.</p>
 */
public final class LostSoulRules {
    /** Total loaded ticks one unbound memorial episode may occupy from acquisition. */
    public static final int EPISODE_TICKS = 600;
    public static final int PETITION_TICKS = 100;
    public static final int SETTLE_TICKS = 80;
    public static final int COOLDOWN_TICKS = 400;

    public static final int DISCOVERY_INTERVAL_TICKS = 80;
    public static final int ANCHOR_SEARCH_HORIZONTAL = 6;
    public static final int ANCHOR_SEARCH_VERTICAL = 2;
    /** Exact read ceiling of the 13 x 5 x 13 anchor envelope. Never a wider or unbounded scan. */
    public static final int MAX_ANCHOR_READS = 845;
    public static final int MAX_ANCHOR_CANDIDATES_RETAINED = 4;

    /** Horizontal band, in blocks, at which the shade stops approaching and begins petitioning. */
    public static final int PETITION_BAND_MIN = 1;
    public static final int PETITION_BAND_MAX = 3;
    public static final int SETTLE_BAND_MAX = 5;
    public static final int PETITION_PULSE_INTERVAL_TICKS = 40;
    public static final int MAX_PETITION_PULSES = 3;
    public static final int MAX_PETITION_PARTICLES = 8;

    /** Bound attendance keeps the shade inside this band, walking rather than blinking to it. */
    public static final int FOLLOW_BAND_MIN = 3;
    public static final int FOLLOW_BAND_MAX = 8;
    public static final int OWNER_RELEASE_RANGE = 48;
    public static final double OWNER_RELEASE_RANGE_SQUARED =
        (double) OWNER_RELEASE_RANGE * OWNER_RELEASE_RANGE;
    /** Preserved exactly from the audited generic aura: Night Vision 240 ticks every 20 ticks. */
    public static final int AURA_INTERVAL_TICKS = 20;
    public static final int AURA_NIGHT_VISION_TICKS = 240;

    public static final int WANDER_INTERVAL_TICKS = 240;
    public static final int WANDER_RADIUS_HORIZONTAL = 6;
    public static final int WANDER_RADIUS_VERTICAL = 3;
    public static final int MAX_WANDER_CANDIDATES = 12;

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

    private LostSoulRules() {
    }

    /**
     * The complete Lost Soul phase set. There is deliberately no warning, defence, target,
     * or reward phase: a Lost Soul that gains an owner only ever attends it quietly.
     */
    public enum Phase {
        WANDER,
        APPROACH,
        PETITION,
        SETTLE,
        COOLDOWN,
        BOUND
    }

    public enum BandAction {
        APPROACH,
        WITHDRAW,
        HOLD
    }

    public enum EpisodeEnd {
        NONE,
        ANCHOR_LOST,
        DIMENSION,
        EXPIRED,
        ROUTE_FAILURE,
        BOUND
    }

    /** One bounded, already-read memorial candidate. Never a live block or block entity. */
    public record AnchorCandidate(long packedPosition, double distanceSquared) {
    }

    /** The facts a runtime directly observed about the one current anchor this decision. */
    public record AnchorObservation(
        boolean present,
        boolean sameDimension,
        boolean loaded,
        boolean stillMemorial,
        int episodeRemainingTicks,
        int routeFailures,
        boolean bound
    ) {
    }

    public record RouteResult(boolean pathFound, boolean reachable, boolean accepted) {
        public boolean success() {
            return pathFound && reachable && accepted;
        }
    }

    // ---------------------------------------------------------------- anchor selection

    /**
     * Stable distance-then-block-position ordering over candidates that were actually read.
     * Ties never depend on iteration order, hash order, or randomness.
     */
    public static List<AnchorCandidate> rank(final List<AnchorCandidate> inspected) {
        final List<AnchorCandidate> ordered = new ArrayList<>(
            Objects.requireNonNull(inspected, "inspected")
        );
        ordered.sort(Comparator.comparingDouble(AnchorCandidate::distanceSquared)
            .thenComparingLong(AnchorCandidate::packedPosition));
        return List.copyOf(ordered.stream().limit(MAX_ANCHOR_CANDIDATES_RETAINED).toList());
    }

    public static Optional<AnchorCandidate> select(final List<AnchorCandidate> inspected) {
        return rank(inspected).stream().findFirst();
    }

    // ---------------------------------------------------------------- episode retention

    /**
     * Exact episode-end policy. Binding always wins, then a dimension mismatch, then a lost or
     * unloaded anchor, then the third route failure, then the loaded-time budget.
     */
    public static EpisodeEnd episodeEnd(final AnchorObservation observation) {
        if (observation.bound()) {
            return EpisodeEnd.BOUND;
        }
        if (!observation.sameDimension()) {
            return EpisodeEnd.DIMENSION;
        }
        if (!observation.present() || !observation.loaded() || !observation.stillMemorial()) {
            return EpisodeEnd.ANCHOR_LOST;
        }
        if (observation.routeFailures() >= MAX_ROUTE_FAILURES) {
            return EpisodeEnd.ROUTE_FAILURE;
        }
        if (observation.episodeRemainingTicks() <= 0) {
            return EpisodeEnd.EXPIRED;
        }
        return EpisodeEnd.NONE;
    }

    public static boolean episodeStartAllowed(
        final boolean bound,
        final int cooldownRemainingTicks,
        final boolean anchorPresent
    ) {
        return !bound && cooldownRemainingTicks <= 0 && !anchorPresent;
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

    public static BandAction petitionBand(final double distanceSquared) {
        return band(distanceSquared, PETITION_BAND_MIN, PETITION_BAND_MAX);
    }

    public static BandAction followBand(final double distanceSquared) {
        return band(distanceSquared, FOLLOW_BAND_MIN, FOLLOW_BAND_MAX);
    }

    public static boolean petitionReached(final double distanceSquared) {
        return distanceSquared <= (double) PETITION_BAND_MAX * PETITION_BAND_MAX;
    }

    public static boolean settleReached(final double distanceSquared) {
        return distanceSquared <= (double) SETTLE_BAND_MAX * SETTLE_BAND_MAX;
    }

    // ---------------------------------------------------------------- petition feedback

    /**
     * A pulse is due only when its remaining window actually elapsed while loaded and the
     * per-episode cap has not been reached. A freshly loaded zero interval is never read as due:
     * {@link #resetPulseIntervalOnLoad(int, int)} restores the full interval first.
     */
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

    public static int petitionPulsesRemaining(final int emitted) {
        return Math.max(0, MAX_PETITION_PULSES - Math.max(0, emitted));
    }

    /**
     * The shipped bound-companion recall, retained byte for byte from the generic writer that used
     * to own it: past {@link CreatureBehaviorRules#OWNER_TELEPORT_DISTANCE_SQUARED} an attending
     * shade snaps back to its owner rather than being silently left behind. This is a preserved
     * capability rather than a new one, and it is the only teleport in the package.
     */
    public static boolean ownerRecallRequired(final double distanceSquared) {
        return distanceSquared >= CreatureBehaviorRules.OWNER_TELEPORT_DISTANCE_SQUARED;
    }

    // ---------------------------------------------------------------- ownership

    /**
     * Binding is atomic: the very interaction that writes the one generic owner UUID cancels the
     * episode. A Lost Soul never gains, copies, or inherits a combat target from that owner.
     */
    public static Phase phaseAfterBinding() {
        return Phase.BOUND;
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

    /** A Lost Soul never attacks: no owner threat, no attacker, and no aggression may change it. */
    public static boolean canAttack(final boolean bound, final boolean ownerThreatened) {
        return false;
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
     * Frozen priority for this species: an escapable hazard preempts everything, then the binding
     * transition, then the memorial attention phases, then idle. Lost Soul has no defence rung.
     */
    public static int priority(final Phase phase, final boolean hazard, final boolean bindingTransition) {
        if (hazard) {
            return 0;
        }
        if (bindingTransition) {
            return 1;
        }
        return switch (phase) {
            case PETITION -> 2;
            case SETTLE -> 3;
            case APPROACH -> 4;
            case BOUND -> 5;
            case COOLDOWN -> 6;
            case WANDER -> 7;
        };
    }

    public static boolean hazardPreempts(final Phase phase, final boolean escapableHazard) {
        return escapableHazard && priority(phase, true, false) < priority(phase, false, false);
    }

    public static boolean bindingPreempts(final Phase phase, final boolean bindingTransition) {
        return bindingTransition && priority(phase, false, true) < priority(phase, false, false);
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
     * Up to {@code budget} deterministic offsets spanning the whole horizontal and vertical
     * envelope: eight rotated compass directions at the full ring, the same eight at the vertical
     * extremes, and eight at the half ring. The rotation is staggered per entity UUID, the origin
     * is never produced, and duplicates are removed so no candidate wastes the budget.
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
     * Greater separation from the avoided point first, then hazard safety, then shorter
     * displacement, then the stable packed position. Deliberately not a weighted score.
     */
    public static Comparator<SafeCandidate> safeCandidatePreference() {
        return Comparator.comparingDouble(SafeCandidate::separationSquared).reversed()
            .thenComparing(candidate -> candidate.hazardFree() ? 0 : 1)
            .thenComparingDouble(SafeCandidate::displacementSquared)
            .thenComparingLong(SafeCandidate::packedPosition);
    }
}
