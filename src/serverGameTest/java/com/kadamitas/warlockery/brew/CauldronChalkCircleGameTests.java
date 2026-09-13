package com.kadamitas.warlockery.brew;

import com.kadamitas.warlockery.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

public final class CauldronChalkCircleGameTests {
    private CauldronChalkCircleGameTests() {
    }

    public static void cauldronReadsExactIndependentChalkRings(final GameTestHelper helper) {
        final BlockPos center = helper.absolutePos(new BlockPos(6, 2, 6));
        placeRing(helper, center, CauldronChalkCircles.Size.SMALL, "circleglyphritual");
        placeRing(helper, center, CauldronChalkCircles.Size.MEDIUM, "circleglyphinfernal");

        final CauldronChalkCircles.State complete = CauldronChalkCircles.inspect(helper.getLevel(), center);
        helper.assertValueEqual(
            complete.small().kind(),
            CauldronChalkCircles.RingKind.RITUAL,
            "the twelve-mark inner ring"
        );
        helper.assertValueEqual(
            complete.medium().kind(),
            CauldronChalkCircles.RingKind.INFERNAL,
            "the twenty-four-mark outer ring"
        );
        helper.assertValueEqual(complete.potencyMultiplier(), 1.5F, "infernal outer ring potency");

        final BlockPos broken = CauldronChalkCircles.offsets(CauldronChalkCircles.Size.MEDIUM)
            .getFirst()
            .at(center);
        helper.getLevel().setBlockAndUpdate(broken, Blocks.AIR.defaultBlockState());
        final CauldronChalkCircles.State incomplete = CauldronChalkCircles.inspect(helper.getLevel(), center);
        helper.assertValueEqual(
            incomplete.medium().kind(),
            CauldronChalkCircles.RingKind.INCOMPLETE,
            "a broken outer ring"
        );
        helper.assertValueEqual(incomplete.potencyMultiplier(), 1.0F, "broken rings grant no potency");
        helper.succeed();
    }

    private static void placeRing(
        final GameTestHelper helper,
        final BlockPos center,
        final CauldronChalkCircles.Size size,
        final String glyph
    ) {
        CauldronChalkCircles.offsets(size).stream().map(offset -> offset.at(center)).forEach(position -> {
            helper.getLevel().setBlockAndUpdate(position.below(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(position, ModBlocks.ALL.get(glyph).get().defaultBlockState());
        });
    }
}
