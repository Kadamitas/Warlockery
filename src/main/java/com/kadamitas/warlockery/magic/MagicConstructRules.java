package com.kadamitas.warlockery.magic;

import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class MagicConstructRules {
    private MagicConstructRules() {
    }

    public static List<BlockPos> wall(final BlockPos origin, final Direction facing) {
        final Direction horizontal = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        final Direction side = horizontal.getClockWise();
        return IntStream.rangeClosed(-1, 1)
            .boxed()
            .flatMap(width -> IntStream.rangeClosed(0, 2)
                .mapToObj(height -> origin.relative(side, width).above(height)))
            .toList();
    }

    public static List<BlockPos> prison(final BlockPos origin) {
        return BlockPos.betweenClosedStream(origin.offset(-1, 0, -1), origin.offset(1, 2, 1))
            .filter(pos -> {
                final BlockPos relative = pos.subtract(origin);
                return Math.abs(relative.getX()) == 1 || Math.abs(relative.getZ()) == 1 || relative.getY() == 2;
            })
            .map(BlockPos::immutable)
            .toList();
    }
}
