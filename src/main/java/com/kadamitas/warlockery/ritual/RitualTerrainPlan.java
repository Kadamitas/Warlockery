package com.kadamitas.warlockery.ritual;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class RitualTerrainPlan {
    private RitualTerrainPlan() {
    }

    public static List<BlockPos> fissure(
        final BlockPos center,
        final Direction facing,
        final int requestedRadius
    ) {
        final Direction forward = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        final Direction sideways = forward.getClockWise();
        final int radius = Math.clamp(requestedRadius, 2, 12);
        final int length = radius * 2;
        final ArrayList<BlockPos> positions = new ArrayList<>();
        for (int distance = 1; distance <= length; distance++) {
            final int halfWidth = distance % 4 == 0 ? 1 : 0;
            final int depth = 2 + Math.min(radius - 1, distance / 2);
            final BlockPos slice = center.relative(forward, distance);
            for (int lateral = -halfWidth; lateral <= halfWidth; lateral++) {
                final BlockPos column = slice.relative(sideways, lateral);
                for (int below = 1; below <= depth; below++) {
                    positions.add(column.below(below));
                }
            }
        }
        return List.copyOf(positions);
    }

    public static List<BlockPos> forestColumns(final BlockPos center, final int requestedRadius) {
        final int radius = Math.clamp(requestedRadius, 3, 16);
        final ArrayList<BlockPos> positions = new ArrayList<>();
        for (int x = -radius; x <= radius; x += 3) {
            for (int z = -radius; z <= radius; z += 3) {
                if (x * x + z * z >= 9 && x * x + z * z <= radius * radius) {
                    positions.add(center.offset(x, 0, z));
                }
            }
        }
        return List.copyOf(positions);
    }

    public static List<BlockPos> fireRing(final BlockPos center, final int requestedRadius, final int count) {
        final int radius = Math.clamp(requestedRadius / 2, 2, 5);
        final int placements = Math.clamp(count, 4, 16);
        return java.util.stream.IntStream.range(0, placements)
            .mapToObj(index -> {
                final double angle = Math.PI * 2.0 * index / placements;
                return center.offset(
                    (int) Math.round(Math.cos(angle) * radius),
                    0,
                    (int) Math.round(Math.sin(angle) * radius)
                );
            })
            .distinct()
            .toList();
    }
}
