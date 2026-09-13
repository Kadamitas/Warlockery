package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

public final class SpiritPortalBlockGameTests {
    private SpiritPortalBlockGameTests() { }

    public static void craftedFramesConnectInBothAxes(final GameTestHelper helper) {
        checkFrame(helper, new BlockPos(2, 2, 2), Direction.EAST);
        checkFrame(helper, new BlockPos(8, 2, 2), Direction.SOUTH);
        helper.succeed();
    }

    private static void checkFrame(final GameTestHelper helper, final BlockPos base, final Direction across) {
        final var level = helper.getLevel();
        final var portal = ModBlocks.ALL.get("spiritportal").get();
        final var layout = SpiritPortalStructure.layout(helper.absolutePos(base), across);
        layout.frame().forEach(pos -> level.setBlockAndUpdate(pos, Blocks.SNOW_BLOCK.defaultBlockState()));
        layout.interior().forEach(pos -> level.setBlockAndUpdate(pos, portal.defaultBlockState()));
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) {
            final var state = level.getBlockState(helper.absolutePos(base).relative(across, x).above(y));
            helper.assertTrue(state.getValue(SpiritPortalBlock.AXIS) == across.getAxis(), "Portal plane follows the real frame");
            helper.assertTrue(state.getValue(SpiritPortalBlock.LEFT) == (x > 0)
                && state.getValue(SpiritPortalBlock.RIGHT) == (x < 1)
                && state.getValue(SpiritPortalBlock.DOWN) == (y > 0)
                && state.getValue(SpiritPortalBlock.UP) == (y < 1), "Only outer edges retain their borders");
        }
        level.setBlockAndUpdate(helper.absolutePos(base).relative(across), Blocks.AIR.defaultBlockState());
        helper.assertTrue(!level.getBlockState(helper.absolutePos(base)).getValue(SpiritPortalBlock.RIGHT),
            "Removing a neighboring cell restores its exposed border");
    }

    public static void generatedReturnColumnJoinsVertically(final GameTestHelper helper) {
        final var portal = ModBlocks.ALL.get("spiritportal").get();
        final var base = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlockAndUpdate(base, portal.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(base.above(), portal.defaultBlockState());
        final var bottom = helper.getLevel().getBlockState(base);
        final var top = helper.getLevel().getBlockState(base.above());
        helper.assertTrue(bottom.getValue(SpiritPortalBlock.UP) && top.getValue(SpiritPortalBlock.DOWN),
            "Default-state generated column suppresses the shared horizontal border");
        helper.assertTrue(!bottom.getValue(SpiritPortalBlock.LEFT) && !bottom.getValue(SpiritPortalBlock.RIGHT),
            "The one-cell-wide return portal retains its outside vertical edges");
        helper.succeed();
    }
}
