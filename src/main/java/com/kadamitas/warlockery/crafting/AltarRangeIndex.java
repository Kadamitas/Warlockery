package com.kadamitas.warlockery.crafting;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class AltarRangeIndex {
    private static final Map<ServerLevel, Map<BlockPos, Entry>> LOADED_ALTARS = new WeakHashMap<>();

    private AltarRangeIndex() {
    }

    public static synchronized void update(final ServerLevel level, final BlockPos position, final boolean focused) {
        update(level, position, focused, null);
    }

    public static synchronized void update(
        final ServerLevel level,
        final BlockPos position,
        final boolean focused,
        final Object owner
    ) {
        LOADED_ALTARS.computeIfAbsent(level, ignored -> new HashMap<>())
            .put(position.immutable(), new Entry(focused, owner));
    }

    public static synchronized void remove(final ServerLevel level, final BlockPos position) {
        final Map<BlockPos, Entry> positions = LOADED_ALTARS.get(level);
        if (positions == null) {
            return;
        }
        positions.remove(position);
        if (positions.isEmpty()) {
            LOADED_ALTARS.remove(level);
        }
    }

    public static synchronized void remove(final ServerLevel level, final BlockPos position, final Object owner) {
        final Map<BlockPos, Entry> positions = LOADED_ALTARS.get(level);
        final Entry existing = positions == null ? null : positions.get(position);
        if (existing != null && existing.owner() == owner) {
            remove(level, position);
        }
    }

    public static synchronized Stream<BlockPos> within(
        final ServerLevel level,
        final BlockPos center,
        final int horizontalRange,
        final int downRange,
        final int upRange
    ) {
        return Map.copyOf(LOADED_ALTARS.getOrDefault(level, Map.of())).entrySet().stream()
            .filter(entry -> reaches(entry.getKey(), center, horizontalRange, downRange, upRange,
                entry.getValue().focused()))
            .map(Map.Entry::getKey);
    }

    static boolean reaches(
        final BlockPos position,
        final BlockPos center,
        final int horizontalRange,
        final int downRange,
        final int upRange,
        final boolean focused
    ) {
        return Math.abs((long) position.getX() - center.getX()) <= effectiveRange(horizontalRange, focused)
            && Math.abs((long) position.getZ() - center.getZ()) <= effectiveRange(horizontalRange, focused)
            && (long) position.getY() >= (long) center.getY() - effectiveRange(downRange, focused)
            && (long) position.getY() <= (long) center.getY() + effectiveRange(upRange, focused);
    }

    public static int effectiveRange(final int baseRange, final boolean focused) {
        if (baseRange < 1) {
            throw new IllegalArgumentException("Altar range must be positive");
        }
        return focused ? Math.multiplyExact(baseRange, 2) : baseRange;
    }

    // Owners are standalone identity tokens, never block entities that would retain the weak map's level.
    private record Entry(boolean focused, Object owner) { }
}
