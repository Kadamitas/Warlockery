package com.kadamitas.warlockery.brew;

import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class BrewTerrainRules {
    private static final List<Direction> WALL_FACES = List.of(
        Direction.NORTH,
        Direction.SOUTH,
        Direction.EAST,
        Direction.WEST
    );

    private BrewTerrainRules() {
    }

    public static int vineStepReach(final boolean hasToadFamiliar) {
        return hasToadFamiliar ? 2 : 1;
    }

    public static List<Direction> wallFaces() {
        return WALL_FACES;
    }

    public static List<BlockPos> vineTraversalOffsets(
        final Direction wallFace,
        final boolean hasToadFamiliar
    ) {
        if (!wallFace.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Vines require a horizontal wall face");
        }
        final Direction lateral = wallFace.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
        final int reach = vineStepReach(hasToadFamiliar);
        return Stream.concat(
            Stream.of(BlockPos.ZERO.above(), BlockPos.ZERO.below()),
            java.util.stream.IntStream.rangeClosed(1, reach)
                .boxed()
                .flatMap(distance -> Stream.of(
                    BlockPos.ZERO.relative(lateral, distance),
                    BlockPos.ZERO.relative(lateral.getOpposite(), distance),
                    BlockPos.ZERO.above().relative(wallFace, distance),
                    BlockPos.ZERO.above().relative(wallFace.getOpposite(), distance),
                    BlockPos.ZERO.below().relative(wallFace, distance),
                    BlockPos.ZERO.below().relative(wallFace.getOpposite(), distance)
                ))
        ).distinct().toList();
    }

    public static List<BlockPos> thornCageOffsets(final boolean hasToadFamiliar) {
        final int height = hasToadFamiliar ? 2 : 1;
        return java.util.stream.IntStream.range(0, height)
            .boxed()
            .flatMap(y -> WALL_FACES.stream().map(direction -> BlockPos.ZERO.relative(direction).above(y)))
            .toList();
    }

    public static int thornTrapDuration(final boolean hasToadFamiliar) {
        return hasToadFamiliar ? 200 : 120;
    }

    public static int enthralledOffspringCount(final int eligibleZombies) {
        return Math.max(0, eligibleZombies) / 2;
    }
}
