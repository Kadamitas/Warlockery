package com.kadamitas.warlockery.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;

/**
 * The one pure policy shared by both F21 apparitions. No world, entity, level, path or random state
 * may enter this class: every input is a scalar or an immutable record, so the whole contract is
 * directly unit testable.
 *
 * <p>This class exists because Echo Shade and Spectre genuinely share <em>mechanism</em> while
 * sharing no motive at all. Both must sweep a bounded envelope without stalling in its innermost
 * ring, both must charge every read they actually spend, both must give up on a route after the
 * same number of consecutive failures, and both must arm a cadence even when a sweep qualifies
 * nothing. Those five contracts are written exactly once here so a defect in any of them cannot
 * ship twice inside one family. Everything that makes a Shade a Shade and a Spectre a Spectre
 * lives in {@link EchoShadeRules} and {@link SpectreRules} and never here.</p>
 */
public final class ApparitionEpisodeRules {
    /** Ordinary bounded movement speed for either apparition. */
    public static final double ROUTE_SPEED = 1.0D;
    /** Hazard escape speed. Faster than an ordinary approach, still an ordinary navigation. */
    public static final double ESCAPE_SPEED = 1.2D;

    public static final int PATH_INTERVAL_TICKS = 20;
    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;

    public static final int HAZARD_INTERVAL_TICKS = 20;
    /** Exact read ceiling of the 3 x 3 x 3 contact neighbourhood. */
    public static final int MAX_HAZARD_READS = 27;

    /**
     * The honest worst-case read cost of one destination candidate: the world-border test, the four
     * chunk-presence tests, the block state, the fluid state and the collision sweep. Charged before
     * a candidate may be filtered, so the ceiling bounds the real cost rather than the accepted
     * minority.
     */
    public static final int READS_PER_DESTINATION_CANDIDATE = 8;
    /** Charged-read ceiling of one bounded destination sweep. */
    public static final int MAX_DESTINATION_READS = 256;

    /** Players examined by one appointment sweep before it stops, qualified or not. */
    public static final int MAX_PLAYER_CANDIDATES = 8;
    /** Line-of-sight walks one appointment sweep may spend. Sensing caches these per tick. */
    public static final int MAX_LINE_OF_SIGHT_CHECKS = 4;

    /** Representative encoded-state ceiling asserted by both state suites. */
    public static final int MAX_STATE_BYTES = 512;

    private ApparitionEpisodeRules() {
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

    /** Staggers per-entity cadence deterministically without ever consulting absolute world time. */
    public static int stableOffset(final UUID id, final int span) {
        if (id == null || span <= 0) {
            return 0;
        }
        return (int) Math.floorMod(
            id.getMostSignificantBits() ^ id.getLeastSignificantBits(), (long) span
        );
    }

    // ---------------------------------------------------------------- route policy

    public record RouteResult(boolean pathFound, boolean reachable, boolean accepted) {
        public boolean success() {
            return pathFound && reachable && accepted;
        }

        /** The outcome of a sweep that qualified nothing: no path was even requested. */
        public static RouteResult unroutable() {
            return new RouteResult(false, false, false);
        }
    }

    /** The three route facts an apparition carries. Never a live path and never a destination. */
    public record RouteLedger(int pathCooldownTicks, int routeFailures, int routeRetryTicks) {
        public RouteLedger {
            pathCooldownTicks = clampRemaining(pathCooldownTicks, PATH_INTERVAL_TICKS);
            routeFailures = Math.clamp(routeFailures, 0, MAX_ROUTE_FAILURES);
            routeRetryTicks = clampRemaining(routeRetryTicks, ROUTE_BACKOFF_TICKS);
        }
    }

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

    /**
     * The single ledger transition used after every route attempt, including the attempt that never
     * happened because the sweep qualified nothing. The path cadence is always armed, so a caller
     * gated only by {@code getNavigation().isDone()} can never re-run a whole sweep every tick, and
     * the third consecutive failure always opens the backoff.
     */
    public static RouteLedger ledgerAfter(final RouteLedger ledger, final RouteResult result) {
        final int failures = routeFailuresAfter(
            Objects.requireNonNull(ledger, "ledger").routeFailures(), result
        );
        return new RouteLedger(
            PATH_INTERVAL_TICKS,
            failures,
            routeExhausted(failures) ? routeBackoffAfter(failures) : ledger.routeRetryTicks()
        );
    }

    // ---------------------------------------------------------------- envelope sweeps

    /** One evaluated destination candidate reduced to its ordering facts. */
    public record DestinationCandidate(
        double separationSquared,
        boolean hazardFree,
        double displacementSquared,
        long packedPosition
    ) {
    }

    /**
     * Greater separation from the avoided point first, then hazard safety, then shorter
     * displacement, then the stable packed position. Deliberately not a weighted score, so two
     * servers presented with identical facts always choose the identical block.
     */
    public static Comparator<DestinationCandidate> destinationPreference() {
        return Comparator.comparingDouble(DestinationCandidate::separationSquared).reversed()
            .thenComparing(candidate -> candidate.hazardFree() ? 0 : 1)
            .thenComparingDouble(DestinationCandidate::displacementSquared)
            .thenComparingLong(DestinationCandidate::packedPosition);
    }

    private static final Map<Long, List<BlockPos>> ENVELOPES = new ConcurrentHashMap<>();

    /**
     * The complete centre-out offset envelope for one box shape, sorted by squared distance from
     * the centre and then deterministically by y, x, z. The list always begins with
     * {@code (0, 0, 0)} and always ends inside the outermost corner, so no caller can accidentally
     * define an envelope whose far shell does not exist.
     */
    public static List<BlockPos> envelope(final int horizontalRadius, final int verticalRadius) {
        final int horizontal = Math.max(0, horizontalRadius);
        final int vertical = Math.max(0, verticalRadius);
        return ENVELOPES.computeIfAbsent((long) horizontal << 32 | vertical, _ -> {
            final List<BlockPos> offsets = new ArrayList<>();
            for (int dy = -vertical; dy <= vertical; dy++) {
                for (int dx = -horizontal; dx <= horizontal; dx++) {
                    for (int dz = -horizontal; dz <= horizontal; dz++) {
                        offsets.add(new BlockPos(dx, dy, dz));
                    }
                }
            }
            offsets.sort(Comparator
                .comparingInt((BlockPos offset) -> offset.getX() * offset.getX()
                    + offset.getY() * offset.getY() + offset.getZ() * offset.getZ())
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
            return List.copyOf(offsets);
        });
    }

    /** Offsets evaluated on every single sweep, always including the apparition's own position. */
    public static int anchorSize(final int envelopeSize, final int readCap) {
        return Math.min(Math.max(0, readCap) / 2, Math.max(0, envelopeSize));
    }

    /** The rotating page over the far tail left after the fixed near anchor. */
    public static int pageSize(final int envelopeSize, final int readCap) {
        final int anchor = anchorSize(envelopeSize, readCap);
        return Math.min(Math.max(0, readCap - anchor), Math.max(0, envelopeSize) - anchor);
    }

    /**
     * The exact offsets one sweep evaluates.
     *
     * <p>A charged read budget is normally far below the volume of its own box, so a naive raster
     * spends the entire budget inside one corner and the far shell, and often the apparition's own
     * position, is never evaluated at all. Instead the centre-out envelope is split in two: a fixed
     * near <em>anchor</em> of {@code readCap / 2} offsets that always contains {@code (0, 0, 0)} and
     * is evaluated on every sweep, and a rotating <em>page</em> over the remaining tail whose cursor
     * advances by one page per sweep and wraps, so the complete far envelope including the opposite
     * corner is evaluated within {@code ceil(tail / page)} successive sweeps.</p>
     *
     * <p>Worked case for the shared 5 x 3 x 5 destination box ({@code h = 2, v = 1}, volume 75, cap
     * 256 / 8 = 32 candidates): the anchor is 16 offsets, which covers every cell within squared
     * distance 2 of the apparition including its own block, and the page is 16 over the 59-offset
     * tail, so the far {@code (+2, +1, +2)} corner is reached within four successive sweeps and the
     * near envelope is never skipped on any of them.</p>
     */
    public static List<BlockPos> sweepWindow(
        final List<BlockPos> offsets,
        final int readCap,
        final int cursor
    ) {
        final List<BlockPos> source = List.copyOf(Objects.requireNonNull(offsets, "offsets"));
        final int anchor = anchorSize(source.size(), readCap);
        final int tail = source.size() - anchor;
        final int page = pageSize(source.size(), readCap);
        final List<BlockPos> window = new ArrayList<>(source.subList(0, anchor));
        if (tail > 0 && page > 0) {
            final int start = Math.floorMod(cursor, tail);
            for (int index = 0; index < page; index++) {
                window.add(source.get(anchor + (start + index) % tail));
            }
        }
        return List.copyOf(window);
    }

    /** Advances a sweep cursor by exactly one page, wrapping inside the tail. */
    public static int advanceCursor(final int envelopeSize, final int readCap, final int cursor) {
        final int tail = Math.max(0, envelopeSize) - anchorSize(envelopeSize, readCap);
        if (tail <= 0) {
            return 0;
        }
        return Math.floorMod(cursor + pageSize(envelopeSize, readCap), tail);
    }

    /** Seeds a fresh sweep cursor so two apparitions never sweep the same page on the same tick. */
    public static int seedCursor(final UUID id, final int envelopeSize, final int readCap) {
        final int tail = Math.max(0, envelopeSize) - anchorSize(envelopeSize, readCap);
        return tail <= 0 ? 0 : stableOffset(id, tail);
    }

    // ---------------------------------------------------------------- appointment sweeps

    /** One examined player reduced to the facts an apparition may order on. Never a live entity. */
    public record PlayerCandidate(
        UUID id,
        boolean eligible,
        boolean visible,
        double distanceSquared
    ) {
        public PlayerCandidate {
            id = Objects.requireNonNull(id, "id");
        }
    }

    /**
     * Nearest first, then the stable identity. Both kinds appoint exactly one subject, and both use
     * this ordering, so neither can ever appoint a crowd and neither depends on iteration order.
     */
    public static Comparator<PlayerCandidate> appointmentPreference() {
        return Comparator.comparingDouble(PlayerCandidate::distanceSquared)
            .thenComparing(PlayerCandidate::id);
    }

    /**
     * The one appointment selector. Only a candidate that is both eligible and visible may be
     * appointed: an apparition that cannot see a player has not observed one.
     */
    public static java.util.Optional<PlayerCandidate> appoint(
        final List<PlayerCandidate> inspected
    ) {
        return Objects.requireNonNull(inspected, "inspected").stream()
            .filter(PlayerCandidate::eligible)
            .filter(PlayerCandidate::visible)
            .min(appointmentPreference());
    }
}
