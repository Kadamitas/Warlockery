package com.kadamitas.warlockery.crafting;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class AltarRangeIndex {
    private static final Map<ServerLevel, Set<BlockPos>> FOCUSED_ALTARS = new WeakHashMap<>();

    private AltarRangeIndex() {
    }

    public static synchronized void update(final ServerLevel level, final BlockPos position, final boolean focused) {
        if (focused) {
            FOCUSED_ALTARS.computeIfAbsent(level, ignored -> new HashSet<>()).add(position.immutable());
            return;
        }
        final Set<BlockPos> positions = FOCUSED_ALTARS.get(level);
        if (positions == null) {
            return;
        }
        positions.remove(position);
        if (positions.isEmpty()) {
            FOCUSED_ALTARS.remove(level);
        }
    }

    public static synchronized Stream<BlockPos> within(
        final ServerLevel level,
        final BlockPos center,
        final int horizontalRange,
        final int downRange,
        final int upRange
    ) {
        return Set.copyOf(FOCUSED_ALTARS.getOrDefault(level, Set.of())).stream()
            .filter(position -> Math.abs(position.getX() - center.getX()) <= horizontalRange)
            .filter(position -> Math.abs(position.getZ() - center.getZ()) <= horizontalRange)
            .filter(position -> position.getY() >= center.getY() - downRange)
            .filter(position -> position.getY() <= center.getY() + upRange);
    }

    public static int effectiveRange(final int baseRange, final boolean focused) {
        if (baseRange < 1) {
            throw new IllegalArgumentException("Altar range must be positive");
        }
        return focused ? Math.multiplyExact(baseRange, 2) : baseRange;
    }
}
