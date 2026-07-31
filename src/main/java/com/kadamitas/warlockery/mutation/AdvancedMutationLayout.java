package com.kadamitas.warlockery.mutation;

import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class AdvancedMutationLayout {
    public static final int MIN_DISTANCE = 1;
    public static final int MAX_DISTANCE = 3;
    public static final int ENTITY_RADIUS = 4;
    private static final List<Direction> CARDINAL_DIRECTIONS = List.of(
        Direction.NORTH,
        Direction.EAST,
        Direction.SOUTH,
        Direction.WEST
    );

    private AdvancedMutationLayout() {
    }

    public static List<List<BlockPos>> cardinalRays(final BlockPos center) {
        return CARDINAL_DIRECTIONS.stream()
            .map(direction -> IntStream.rangeClosed(MIN_DISTANCE, MAX_DISTANCE)
                .mapToObj(distance -> center.relative(direction, distance))
                .toList())
            .toList();
    }

    public static List<List<BlockPos>> diagonalRays(final BlockPos center) {
        return List.of(
            diagonalRay(center, 1, 1),
            diagonalRay(center, 1, -1),
            diagonalRay(center, -1, 1),
            diagonalRay(center, -1, -1)
        );
    }

    private static List<BlockPos> diagonalRay(final BlockPos center, final int xSign, final int zSign) {
        return IntStream.rangeClosed(MIN_DISTANCE, MAX_DISTANCE)
            .mapToObj(distance -> center.offset(xSign * distance, 0, zSign * distance))
            .toList();
    }
}
