package com.kadamitas.warlockery.entity.behavior;

import java.util.UUID;

/**
 * The tick arithmetic that thirteen families each wrote for themselves.
 *
 * <p>Nothing here is new. Every helper below exists as an exact or near exact clone in several
 * committed families, and the clones are what this package is for: {@code stableOffset} has thirteen
 * definitions, {@code saturatingAdd} eight, {@code due} five, {@code clampDeadline} with its
 * {@link #MAX_FUTURE_HORIZON_TICKS} five, and {@code clampRemaining} with {@code decrementLoaded}
 * four byte identical pairs.</p>
 *
 * <p>Free functions, so a family takes the two it wants and keeps its own everything else.</p>
 *
 * <p>The two schedule dialects are both served and must not be mixed. A family either stores
 * absolute deadlines against {@code level.getGameTime()}, in which case it wants {@link #due} and
 * {@link #clampDeadline}, or it stores countdowns advanced only while the entity is loaded, in which
 * case it wants {@link #decrementLoaded} and {@link #clampRemaining}. {@code clampDeadline} bounds a
 * horizon so an unloaded gap cannot strand a deadline; {@code decrementLoaded} never consults world
 * time at all. They solve opposite problems and neither substitutes for the other.</p>
 */
public final class Ticks {

    /**
     * The furthest ahead a stored deadline may point. A deadline beyond this survived an unload of
     * unbounded length and would leave the entity inert, so it is pulled back to the horizon.
     */
    public static final long MAX_FUTURE_HORIZON_TICKS = 20_000L;

    private Ticks() {
    }

    /** Whether an absolute deadline has arrived. */
    public static boolean due(final long deadline, final long now) {
        return now >= deadline;
    }

    /** Addition that saturates rather than wrapping, for deadlines built from stored offsets. */
    public static long saturatingAdd(final long left, final long right) {
        final long sum = left + right;
        if (((left ^ sum) & (right ^ sum)) < 0) {
            return left > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return sum;
    }

    /** Pulls a stored deadline back inside the horizon, so a long unload cannot strand it. */
    public static long clampDeadline(final long deadline, final long now, final long horizonTicks) {
        if (deadline <= now) {
            return now;
        }
        return Math.min(deadline, saturatingAdd(now, horizonTicks));
    }

    public static long clampDeadline(final long deadline, final long now) {
        return clampDeadline(deadline, now, MAX_FUTURE_HORIZON_TICKS);
    }

    /** Advances a loaded countdown by one tick, never below zero. */
    public static int decrementLoaded(final int remaining) {
        return Math.max(0, remaining - 1);
    }

    /** Brings a persisted countdown back into range, so hand-edited or corrupt data cannot stick. */
    public static int clampRemaining(final int stored, final int maximum) {
        return Math.clamp(stored, 0, maximum);
    }

    /**
     * A deterministic per-entity offset in {@code [0, span)}, for staggering cadence across a crowd
     * without ever consulting absolute world time.
     *
     * <p>The committed families use two different hashes, {@code msb ^ lsb} and {@code lsb} alone.
     * This is the mixing variant. Adopting it changes only which tick within a period an entity
     * fires on, never whether it fires, so it is safe to switch to but is not bit compatible with a
     * family that persisted a schedule derived from the other variant.</p>
     */
    public static int stableOffset(final UUID identity, final int span) {
        if (span <= 0) {
            return 0;
        }
        final long bits = identity.getMostSignificantBits() ^ identity.getLeastSignificantBits();
        return (int) Math.floorMod(bits, span);
    }
}
