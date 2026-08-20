package com.kadamitas.warlockery.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure F20 Poltergeist policy. No world, entity, block, path, level, or random state may enter this
 * class: every input is a scalar or an immutable record, so the whole contract is unit testable.
 *
 * <p>The Poltergeist is a noisy room-scale disturbance rather than a soul, a mourner, or an
 * attendant. Its one species attention is a finite disturbance episode:
 * {@code LURK -> RATTLE -> MARK -> LIFT -> THROW -> RECOVER}. It selects exactly one loaded living
 * attackable player and at most one loaded loose item, warns audibly, approaches into a bounded
 * band, applies one short levitation window, writes one velocity to the prop, permits at most one
 * separately attributed hit, then recovers the lifted target and cools down.</p>
 *
 * <p>It never binds, never gains an owner, never applies an aura, never applies fear or darkness,
 * never imitates, never edits or activates a block, never touches an inventory, and never mutates
 * the thrown stack.</p>
 */
public final class PoltergeistRules {
    // ---------------------------------------------------------------- episode shape

    /**
     * Total loaded ticks one disturbance episode may occupy from acquisition.
     *
     * <p>Deliberately shorter than the sum of every phase window
     * ({@code 40 + 120 + 40 + 40 = 240}) so {@link EpisodeEnd#EXPIRED} is genuinely reachable: a
     * disturbance that spends its whole approach window closing on a retreating target runs out of
     * budget inside the lift or throw and releases, while the ordinary chain, which holds its band
     * immediately and finishes in about 121 ticks plus a 60-tick recovery, never touches it.</p>
     */
    public static final int EPISODE_TICKS = 200;
    public static final int RATTLE_TICKS = 40;
    public static final int MARK_TICKS = 120;
    /** Exactly the audited levitation window, so the lift is a preserved duration. */
    public static final int LIFT_TICKS = 40;
    public static final int THROW_TICKS = 40;
    public static final int RECOVER_TICKS = 60;
    /** The long separation between episodes that stops lift accumulation. */
    public static final int COOLDOWN_TICKS = 600;
    /** The widest single phase window, used as the persisted phase-timer clamp. */
    public static final int MAX_PHASE_TICKS = MARK_TICKS;

    // ---------------------------------------------------------------- selection

    public static final int DISCOVERY_INTERVAL_TICKS = 40;
    /** Preserved exactly from the audited eight-block player pulse. */
    public static final int TARGET_SEARCH_RANGE = 8;
    public static final double TARGET_SEARCH_RANGE_SQUARED =
        (double) TARGET_SEARCH_RANGE * TARGET_SEARCH_RANGE;
    /** Preserved exactly from the audited six-block loose-item pulse. */
    public static final int PROP_SEARCH_RANGE = 6;
    public static final double PROP_SEARCH_RANGE_SQUARED =
        (double) PROP_SEARCH_RANGE * PROP_SEARCH_RANGE;
    public static final int MAX_RETAINED_CANDIDATES = 4;

    // ---------------------------------------------------------------- bands and effects

    /** The band the marked target must be inside before any lift is permitted. */
    public static final int MARK_BAND_MIN = 1;
    public static final int MARK_BAND_MAX = 3;
    public static final int LIFT_RANGE = 5;
    public static final double LIFT_RANGE_SQUARED = (double) LIFT_RANGE * LIFT_RANGE;

    /** Preserved exactly: Levitation I for forty ticks, applied once per episode. */
    public static final int LEVITATION_TICKS = 40;
    public static final int LEVITATION_AMPLIFIER = 0;
    /** The bounded recovery that turns the lift into displacement instead of fall damage. */
    public static final int SLOW_FALLING_TICKS = 100;
    public static final int SLOW_FALLING_AMPLIFIER = 0;

    public static final double THROW_HORIZONTAL_SPEED = 0.55D;
    /** Preserved exactly from the audited radial push's vertical component. */
    public static final double THROW_VERTICAL_SPEED = 0.2D;
    public static final int MAX_VELOCITY_WRITES = 1;
    public static final int MAX_THROW_HITS = 1;
    public static final float THROW_HIT_DAMAGE = 2.0F;
    public static final double THROW_HIT_RADIUS = 1.5D;
    public static final double THROW_HIT_RADIUS_SQUARED = THROW_HIT_RADIUS * THROW_HIT_RADIUS;

    public static final int MAX_LIFTS = 1;
    public static final int MAX_RECOVERIES = 1;

    // ---------------------------------------------------------------- rattle feedback

    public static final int RATTLE_PULSE_INTERVAL_TICKS = 12;
    public static final int MAX_RATTLE_PULSES = 3;
    public static final int MAX_RATTLE_PARTICLES = 6;
    public static final int MAX_BELL_RINGS = 1;
    public static final int BELL_SCAN_INTERVAL_TICKS = 20;
    public static final int BELL_SEARCH_HORIZONTAL = 4;
    public static final int BELL_SEARCH_VERTICAL = 2;
    /** Hard ceiling on charged reads per bell scan. Far below the 405-cell envelope on purpose. */
    public static final int MAX_BELL_READS = 96;
    /** Preserved exactly from the audited haunted-bell reach of sixteen squared blocks. */
    public static final double BELL_RING_RANGE_SQUARED = 16.0D;

    // ---------------------------------------------------------------- movement bounds

    public static final int PATH_INTERVAL_TICKS = 20;
    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;
    /**
     * The per-episode path quota. The approved design named a per-level quota; a level-wide counter
     * needs shared saved data, which the frozen F20 scope forbids, so the same bound is enforced
     * per entity per episode where it is actually observable and testable.
     *
     * <p>Four is chosen so the quota genuinely binds. The approach window is 120 ticks and one
     * request may be issued every 20, so up to six are possible; a disturbance that has to
     * re-path four times to close on a walking target gives up instead of following forever. It
     * sits above the three-failure release so both releases stay reachable: three consecutive
     * failures release first, while four requests that each succeeded release on the quota.</p>
     */
    public static final int MAX_EPISODE_PATH_REQUESTS = 4;

    public static final int IDLE_INTERVAL_TICKS = 240;
    public static final int IDLE_RADIUS_HORIZONTAL = 6;
    public static final int IDLE_RADIUS_VERTICAL = 3;
    public static final int MAX_IDLE_CANDIDATES = 12;

    public static final int HAZARD_INTERVAL_TICKS = 20;
    public static final int MAX_HAZARD_READS = 27;
    public static final int MAX_SAFE_CANDIDATES = 24;
    public static final int MAX_CHARGED_READS = 256;
    public static final int ESCAPE_SEARCH_HORIZONTAL = 6;
    public static final int ESCAPE_SEARCH_VERTICAL = 3;

    // ---------------------------------------------------------------- damage reaction

    /** Preserved exactly from the audited generic PHASED response: a blink past two damage. */
    public static final float BLINK_MIN_DAMAGE = 2.0F;
    public static final int BLINK_HORIZONTAL = 4;
    public static final int BLINK_VERTICAL = 2;

    /** Representative encoded-state ceiling asserted by the state tests. */
    public static final int MAX_STATE_BYTES = 512;

    private PoltergeistRules() {
    }

    /**
     * The complete Poltergeist phase set. There is deliberately no bind, attend, warn-for-owner,
     * fear, lament, or reward phase: those belong to the Spirit, Spectre and Banshee neighbours.
     */
    public enum Phase {
        LURK,
        RATTLE,
        MARK,
        LIFT,
        THROW,
        RECOVER
    }

    public enum BandAction {
        APPROACH,
        WITHDRAW,
        HOLD
    }

    public enum EpisodeEnd {
        NONE,
        DIMENSION,
        TARGET_LOST,
        ROUTE_FAILURE,
        PATH_QUOTA,
        EXPIRED
    }

    /** One already-read player candidate. Never a live entity reference. */
    public record TargetCandidate(UUID id, double distanceSquared) {
        public TargetCandidate {
            Objects.requireNonNull(id, "id");
        }
    }

    /** One already-read loose-item candidate. Never a live entity or stack reference. */
    public record PropCandidate(UUID id, double distanceSquared) {
        public PropCandidate {
            Objects.requireNonNull(id, "id");
        }
    }

    /** The facts a runtime directly observed about the one marked target this decision. */
    public record TargetObservation(
        boolean present,
        boolean sameDimension,
        boolean valid,
        int episodeRemainingTicks,
        int routeFailures,
        int pathRequests
    ) {
    }

    public record RouteResult(boolean pathFound, boolean reachable, boolean accepted) {
        public boolean success() {
            return pathFound && reachable && accepted;
        }
    }

    // ---------------------------------------------------------------- selection ordering

    /**
     * Stable distance-then-UUID ordering over candidates that were actually visited. Ties never
     * depend on iteration order, hash order, or randomness, so two servers observing the same scene
     * mark the same player.
     */
    public static List<TargetCandidate> rankTargets(final List<TargetCandidate> inspected) {
        final List<TargetCandidate> ordered = new ArrayList<>(
            Objects.requireNonNull(inspected, "inspected")
        );
        ordered.sort(Comparator.comparingDouble(TargetCandidate::distanceSquared)
            .thenComparing(candidate -> candidate.id().toString()));
        return List.copyOf(ordered.stream().limit(MAX_RETAINED_CANDIDATES).toList());
    }

    public static Optional<TargetCandidate> selectTarget(final List<TargetCandidate> inspected) {
        return rankTargets(inspected).stream().findFirst();
    }

    /** The same stable ordering for the single optional prop. */
    public static List<PropCandidate> rankProps(final List<PropCandidate> inspected) {
        final List<PropCandidate> ordered = new ArrayList<>(
            Objects.requireNonNull(inspected, "inspected")
        );
        ordered.sort(Comparator.comparingDouble(PropCandidate::distanceSquared)
            .thenComparing(candidate -> candidate.id().toString()));
        return List.copyOf(ordered.stream().limit(MAX_RETAINED_CANDIDATES).toList());
    }

    public static Optional<PropCandidate> selectProp(final List<PropCandidate> inspected) {
        return rankProps(inspected).stream().findFirst();
    }

    // ---------------------------------------------------------------- episode retention

    /**
     * Exact episode-end policy. A dimension mismatch wins, then a lost or invalid target, then the
     * third route failure, then the exhausted path quota, then the loaded-time budget.
     */
    public static EpisodeEnd episodeEnd(final TargetObservation observation) {
        if (!observation.sameDimension()) {
            return EpisodeEnd.DIMENSION;
        }
        if (!observation.present() || !observation.valid()) {
            return EpisodeEnd.TARGET_LOST;
        }
        if (observation.routeFailures() >= MAX_ROUTE_FAILURES) {
            return EpisodeEnd.ROUTE_FAILURE;
        }
        if (observation.pathRequests() >= MAX_EPISODE_PATH_REQUESTS) {
            return EpisodeEnd.PATH_QUOTA;
        }
        if (observation.episodeRemainingTicks() <= 0) {
            return EpisodeEnd.EXPIRED;
        }
        return EpisodeEnd.NONE;
    }

    public static boolean episodeStartAllowed(
        final int cooldownRemainingTicks,
        final boolean episodeActive
    ) {
        return cooldownRemainingTicks <= 0 && !episodeActive;
    }

    /**
     * The one phase every attack phase resumes as after a reload. No lift, velocity write, hit, or
     * bell ring can replay, because the only phase a saved episode can wake up in is the recovery
     * that closes it.
     */
    public static Phase phaseAfterLoad(final Phase stored) {
        return switch (stored) {
            case RATTLE, MARK, LIFT, THROW, RECOVER -> Phase.RECOVER;
            case LURK -> Phase.LURK;
        };
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

    public static BandAction markBand(final double distanceSquared) {
        return band(distanceSquared, MARK_BAND_MIN, MARK_BAND_MAX);
    }

    /** A lift is never applied to a target the disturbance never actually reached. */
    public static boolean liftAllowed(final double distanceSquared) {
        return distanceSquared <= LIFT_RANGE_SQUARED;
    }

    public static boolean throwHitAllowed(final int hits, final double propToTargetSquared) {
        return hits < MAX_THROW_HITS && propToTargetSquared <= THROW_HIT_RADIUS_SQUARED;
    }

    public static boolean bellRingAllowed(final int rings, final double distanceSquared) {
        return rings < MAX_BELL_RINGS && distanceSquared <= BELL_RING_RANGE_SQUARED;
    }

    // ---------------------------------------------------------------- rattle feedback

    /**
     * A pulse is due only when its remaining window actually elapsed while loaded and the
     * per-episode cap has not been reached.
     */
    public static boolean pulseDue(final int remainingIntervalTicks, final int emittedPulses) {
        return emittedPulses < MAX_RATTLE_PULSES && remainingIntervalTicks <= 0;
    }

    public static int rattlePulsesRemaining(final int emitted) {
        return Math.max(0, MAX_RATTLE_PULSES - Math.max(0, emitted));
    }

    // ---------------------------------------------------------------- damage reaction

    /**
     * The preserved PHASED blink, retained from the generic writer that used to own it: a hit of at
     * least two damage displaces the disturbance. It is a preserved capability rather than a new
     * one, and it is the only displacement in the package.
     */
    public static boolean blinkOnDamage(final float amount) {
        return amount >= BLINK_MIN_DAMAGE;
    }

    /** A struck Poltergeist abandons whatever attack phase it held instead of finishing it. */
    public static boolean damageReactionPreempts(final Phase phase) {
        return switch (phase) {
            case RATTLE, MARK, LIFT, THROW -> true;
            case LURK, RECOVER -> false;
        };
    }

    /** A Poltergeist never acquires a combat target: its only damage is the attributed prop hit. */
    public static boolean canAttack() {
        return false;
    }

    // ---------------------------------------------------------------- movement

    public static boolean pathRequestAllowed(
        final int remainingPathTicks,
        final int backoffRemainingTicks,
        final int spentPathRequests
    ) {
        return remainingPathTicks <= 0 && backoffRemainingTicks <= 0
            && spentPathRequests < MAX_EPISODE_PATH_REQUESTS;
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
     * Frozen priority for this species: an escapable hazard preempts everything, then the damage
     * reaction, then the active disturbance phases, then idle lurking. Poltergeist has no binding,
     * attendance, or defence rung at all.
     */
    public static int priority(final Phase phase, final boolean hazard, final boolean damaged) {
        if (hazard) {
            return 0;
        }
        if (damaged) {
            return 1;
        }
        return switch (phase) {
            case THROW -> 2;
            case LIFT -> 3;
            case MARK -> 4;
            case RATTLE -> 5;
            case RECOVER -> 6;
            case LURK -> 7;
        };
    }

    public static boolean hazardPreempts(final Phase phase, final boolean escapableHazard) {
        return escapableHazard && priority(phase, true, false) < priority(phase, false, false);
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

    /** The window a freshly entered phase is armed with. Never read from absolute world time. */
    public static int phaseWindowTicks(final Phase phase) {
        return switch (phase) {
            case LURK -> 0;
            case RATTLE -> RATTLE_TICKS;
            case MARK -> MARK_TICKS;
            case LIFT -> LIFT_TICKS;
            case THROW -> THROW_TICKS;
            case RECOVER -> RECOVER_TICKS;
        };
    }

    /** Staggers per-entity cadence deterministically without ever using absolute world time. */
    public static int stableOffset(final UUID id, final int span) {
        if (id == null || span <= 0) {
            return 0;
        }
        return (int) Math.floorMod(id.getMostSignificantBits() ^ id.getLeastSignificantBits(), (long) span);
    }

    // ---------------------------------------------------------------- bell envelope coverage

    /** One deterministic offset inside a bounded scan envelope. */
    public record ScanOffset(int dx, int dy, int dz) {
        public int distanceSquared() {
            return dx * dx + dy * dy + dz * dz;
        }
    }

    /**
     * The complete centre-out envelope of a {@code (2h+1) x (2v+1) x (2h+1)} box, ordered by squared
     * distance from the origin then deterministically by y, x, z. Identical facts always produce an
     * identical evaluation order on every server.
     */
    public static List<ScanOffset> envelope(final int horizontalRadius, final int verticalRadius) {
        final int horizontal = Math.max(0, horizontalRadius);
        final int vertical = Math.max(0, verticalRadius);
        final List<ScanOffset> offsets = new ArrayList<>();
        for (int dy = -vertical; dy <= vertical; dy++) {
            for (int dx = -horizontal; dx <= horizontal; dx++) {
                for (int dz = -horizontal; dz <= horizontal; dz++) {
                    offsets.add(new ScanOffset(dx, dy, dz));
                }
            }
        }
        offsets.sort(Comparator.comparingInt(ScanOffset::distanceSquared)
            .thenComparingInt(ScanOffset::dy)
            .thenComparingInt(ScanOffset::dx)
            .thenComparingInt(ScanOffset::dz));
        return List.copyOf(offsets);
    }

    /** The number of near-envelope offsets evaluated on every single scan of a given budget. */
    public static int anchorSize(final int envelopeSize, final int readCap) {
        return Math.min(Math.max(0, readCap) / 2, Math.max(0, envelopeSize));
    }

    /** The rotating page size, that is the budget left over for the far tail after the anchor. */
    public static int pageSize(final int envelopeSize, final int readCap) {
        final int anchor = anchorSize(envelopeSize, readCap);
        return Math.min(Math.max(0, readCap - anchor), Math.max(0, envelopeSize) - anchor);
    }

    /**
     * The exact offsets one scan evaluates.
     *
     * <p>The bell read cap is far below its own box volume (96 of 405), so a naive raster would
     * spend the entire budget on one corner of the envelope and never reach the entity's own level
     * or the opposite quadrant. Instead the envelope is enumerated centre-out and split in two: a
     * fixed near anchor of the first {@code readCap / 2} offsets, which always contains
     * {@code (0,0,0)} and the entity's own level and is evaluated on every scan, followed by one
     * rotating page over the remaining far tail whose cursor advances by the page size and wraps.
     * The whole far envelope, including the opposite quadrant, is therefore reachable within
     * {@code ceil(tail / page)} successive scans.</p>
     *
     * <p>Worked case, bells ({@code h=4, v=2}, volume 405, cap 96): anchor 48 offsets covering every
     * cell within squared distance four plus part of the squared-distance-five ring, then a 48-wide
     * page over the 357-offset tail, so the far {@code (+4,+2,+4)} corner is reached within eight
     * scans and the near envelope is never skipped.</p>
     */
    public static List<ScanOffset> scanWindow(
        final List<ScanOffset> offsets,
        final int readCap,
        final int cursor
    ) {
        Objects.requireNonNull(offsets, "offsets");
        final int anchor = anchorSize(offsets.size(), readCap);
        final int tail = offsets.size() - anchor;
        final int page = pageSize(offsets.size(), readCap);
        final int start = tail == 0 ? 0 : Math.floorMod(cursor, tail);
        final LinkedHashSet<ScanOffset> window = new LinkedHashSet<>(offsets.subList(0, anchor));
        for (int index = 0; index < page; index++) {
            window.add(offsets.get(anchor + (start + index) % tail));
        }
        return List.copyOf(window);
    }

    public static int advanceCursor(final int envelopeSize, final int readCap, final int cursor) {
        final int tail = Math.max(0, envelopeSize) - anchorSize(envelopeSize, readCap);
        if (tail <= 0) {
            return 0;
        }
        return Math.floorMod(cursor + pageSize(envelopeSize, readCap), tail);
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
     * extremes, and eight at the half ring. The rotation is staggered per entity UUID, the origin is
     * never produced, and duplicates are removed so no candidate wastes the budget.
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
        final LinkedHashSet<SafeSearchOffset> offsets = new LinkedHashSet<>();
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
