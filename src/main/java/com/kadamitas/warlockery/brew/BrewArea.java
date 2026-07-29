package com.kadamitas.warlockery.brew;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;

public final class BrewArea {
    private BrewArea() {
    }

    public static Stream<BlockPos> sphere(final BlockPos center, final int radius) {
        final int safeRadius = Math.clamp(radius, 0, 12);
        final long radiusSquared = (long) safeRadius * safeRadius;
        return BlockPos.betweenClosedStream(
            center.offset(-safeRadius, -safeRadius, -safeRadius),
            center.offset(safeRadius, safeRadius, safeRadius)
        ).filter(pos -> pos.distSqr(center) <= radiusSquared).map(BlockPos::immutable);
    }

    public static List<BlockPos> connected(
        final BlockPos origin,
        final int limit,
        final Predicate<BlockPos> accepted
    ) {
        if (limit <= 0 || !accepted.test(origin)) {
            return List.of();
        }
        final ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        final Set<BlockPos> visited = new HashSet<>();
        final List<BlockPos> result = new ArrayList<>(Math.min(limit, 256));
        pending.add(origin.immutable());
        visited.add(origin.immutable());
        while (!pending.isEmpty() && result.size() < limit) {
            final BlockPos current = pending.removeFirst();
            if (!accepted.test(current)) {
                continue;
            }
            result.add(current);
            neighbours(current).filter(visited::add).forEach(pending::addLast);
        }
        return List.copyOf(result);
    }

    private static Stream<BlockPos> neighbours(final BlockPos center) {
        return BlockPos.betweenClosedStream(center.offset(-1, -1, -1), center.offset(1, 1, 1))
            .filter(pos -> !pos.equals(center))
            .map(BlockPos::immutable);
    }
}
