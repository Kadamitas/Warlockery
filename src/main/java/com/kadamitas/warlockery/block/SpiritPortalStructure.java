package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.registry.ModBlocks;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;

public final class SpiritPortalStructure {
    private static final List<Direction> HORIZONTAL_AXES = List.of(Direction.EAST, Direction.SOUTH);

    private SpiritPortalStructure() {
    }

    public static Optional<Layout> find(final LevelReader level, final BlockPos flowingSpirit) {
        return HORIZONTAL_AXES.stream()
            .flatMap(horizontal -> Stream.of(
                layout(flowingSpirit, horizontal),
                layout(flowingSpirit.relative(horizontal.getOpposite()), horizontal)
            ))
            .filter(layout -> layout.frame().stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.SNOW_BLOCK)))
            .filter(layout -> layout.interior().stream().allMatch(pos -> pos.equals(flowingSpirit)
                || level.getBlockState(pos).canBeReplaced()
                || level.getBlockState(pos).is(ModBlocks.ALL.get("spiritportal").get())
                || level.getBlockState(pos).is(ModBlocks.ALL.get("spiritflowing").get())))
            .findFirst();
    }

    static Layout layout(final BlockPos lowerLeftInterior, final Direction horizontal) {
        if (!horizontal.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Spirit portal width must be horizontal");
        }
        final BlockPos lowerRightInterior = lowerLeftInterior.relative(horizontal);
        final BlockPos leftFrame = lowerLeftInterior.relative(horizontal.getOpposite());
        final BlockPos rightFrame = lowerLeftInterior.relative(horizontal, 2);
        return new Layout(
            List.of(
                lowerLeftInterior.below(),
                lowerRightInterior.below(),
                lowerLeftInterior.above(2),
                lowerRightInterior.above(2),
                leftFrame,
                leftFrame.above(),
                rightFrame,
                rightFrame.above()
            ),
            List.of(
                lowerLeftInterior,
                lowerRightInterior,
                lowerLeftInterior.above(),
                lowerRightInterior.above()
            )
        );
    }

    public record Layout(List<BlockPos> frame, List<BlockPos> interior) {
        public Layout {
            frame = List.copyOf(frame);
            interior = List.copyOf(interior);
        }
    }
}
