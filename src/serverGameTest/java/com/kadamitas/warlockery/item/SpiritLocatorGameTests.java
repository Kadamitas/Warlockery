package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

public final class SpiritLocatorGameTests {
    private static final BlockPos CENTER = new BlockPos(4, 2, 4);

    private SpiritLocatorGameTests() {
    }

    public static void spiritLocatorRequiresAnExactRitualChalkRing(final GameTestHelper helper) {
        BlockPos.betweenClosedStream(CENTER.offset(-1, -1, -1), CENTER.offset(1, -1, 1))
            .forEach(position -> helper.setBlock(position, Blocks.STONE));
        SpiritLocatorRules.ringOffsets().forEach(offset ->
            helper.setBlock(CENTER.offset(offset), ModBlocks.ALL.get("circleglyphritual").get()));

        final BlockPos absoluteCenter = helper.absolutePos(CENTER);
        helper.assertValueEqual(
            SpiritLocatorRuntime.ringCenter(helper.getLevel(), absoluteCenter).orElseThrow(),
            absoluteCenter,
            "exact Ritual Chalk locator center"
        );

        final BlockPos wrongMark = CENTER.offset(SpiritLocatorRules.ringOffsets().getFirst());
        helper.setBlock(wrongMark, ModBlocks.ALL.get("circleglyphinfernal").get());
        helper.assertTrue(
            SpiritLocatorRuntime.ringCenter(helper.getLevel(), absoluteCenter).isEmpty(),
            "a mixed-chalk locator ring must remain dormant"
        );
        helper.succeed();
    }
}
