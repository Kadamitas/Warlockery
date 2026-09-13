package com.kadamitas.warlockery.brew;

import com.kadamitas.warlockery.registry.ModFluids;
import com.kadamitas.warlockery.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class SolidifyingBrewGameTests {
    private SolidifyingBrewGameTests() {
    }

    public static void stoneConvertsEveryHollowTearsState(final GameTestHelper helper) {
        assertSolidifies(helper, BrewKind.SOLIDIFY_STONE, Blocks.STONE.defaultBlockState());
    }

    public static void dirtConvertsEveryHollowTearsState(final GameTestHelper helper) {
        assertSolidifies(helper, BrewKind.SOLIDIFY_DIRT, Blocks.DIRT.defaultBlockState());
    }

    public static void sandConvertsEveryHollowTearsState(final GameTestHelper helper) {
        assertSolidifies(helper, BrewKind.SOLIDIFY_SAND, Blocks.SAND.defaultBlockState());
    }

    public static void sandstoneConvertsEveryHollowTearsState(final GameTestHelper helper) {
        assertSolidifies(helper, BrewKind.SOLIDIFY_SANDSTONE, Blocks.SANDSTONE.defaultBlockState());
    }

    public static void erosionClearsTerrainBelowEveryHollowTearsState(final GameTestHelper helper) {
        final BlockPos source = helper.absolutePos(new BlockPos(1, 3, 1));
        final BlockPos flowing = source.east();
        final BrewRuntime.ImpactResult failure = impact(helper, BrewKind.SOLIDIFY_EROSION, source);
        helper.assertValueEqual(failure, BrewRuntime.ImpactResult.ZERO, "erosion without Hollow Tears");

        placeHollowTears(helper, source, flowing);
        placeErosionColumn(helper, source);
        placeErosionColumn(helper, flowing);

        final BrewRuntime.ImpactResult result = impact(helper, BrewKind.SOLIDIFY_EROSION, source);
        helper.assertValueEqual(result.changedBlocks(), 4, "eroded terrain block count");
        assertBlock(helper, source.below(), Blocks.AIR.defaultBlockState());
        assertBlock(helper, source.below(2), Blocks.AIR.defaultBlockState());
        assertBlock(helper, flowing.below(), Blocks.AIR.defaultBlockState());
        assertBlock(helper, flowing.below(2), Blocks.AIR.defaultBlockState());
        helper.assertTrue(
            helper.getLevel().getFluidState(source).is(ModFluids.HOLLOW_TEARS_SOURCE.get()),
            "erosion must leave the source Hollow Tears in place"
        );
        helper.assertTrue(
            helper.getLevel().getFluidState(flowing).is(ModFluids.FLOWING_HOLLOW_TEARS.get()),
            "erosion must leave flowing Hollow Tears in place"
        );
        assertBlock(helper, source.below(3), Blocks.BEDROCK.defaultBlockState());
        assertBlock(helper, flowing.below(3), Blocks.BEDROCK.defaultBlockState());
        helper.succeed();
    }

    private static void assertSolidifies(
        final GameTestHelper helper,
        final BrewKind kind,
        final BlockState expected
    ) {
        final BlockPos source = helper.absolutePos(new BlockPos(1, 2, 1));
        final BlockPos flowing = source.east();
        final BrewRuntime.ImpactResult failure = impact(helper, kind, source);
        helper.assertValueEqual(failure, BrewRuntime.ImpactResult.ZERO, kind.id() + " without Hollow Tears");

        placeHollowTears(helper, source, flowing);
        final BrewRuntime.ImpactResult result = impact(helper, kind, source);
        helper.assertValueEqual(result.changedBlocks(), 2, kind.id() + " changed block count");
        assertBlock(helper, source, expected);
        assertBlock(helper, flowing, expected);
        helper.succeed();
    }

    private static void assertBlock(
        final GameTestHelper helper,
        final BlockPos position,
        final BlockState expected
    ) {
        helper.assertValueEqual(
            helper.getLevel().getBlockState(position).getBlock(),
            expected.getBlock(),
            "block at " + position
        );
    }

    private static BrewRuntime.ImpactResult impact(
        final GameTestHelper helper,
        final BrewKind kind,
        final BlockPos position
    ) {
        final ItemStack stack = new ItemStack(ModItems.ALL.get(BrewFactory.itemId(kind)).get());
        final BrewItem item = (BrewItem) stack.getItem();
        final BlockHitResult hit = new BlockHitResult(
            Vec3.atCenterOf(position), Direction.UP, position, false
        );
        return item.onImpact(helper.getLevel(), stack, hit, null, null);
    }

    private static void placeHollowTears(
        final GameTestHelper helper,
        final BlockPos source,
        final BlockPos flowing
    ) {
        helper.getLevel().setBlockAndUpdate(
            source,
            ModFluids.HOLLOW_TEARS_SOURCE.get().defaultFluidState().createLegacyBlock()
        );
        helper.getLevel().setBlockAndUpdate(
            flowing,
            ModFluids.FLOWING_HOLLOW_TEARS.get().getFlowing(4, false).createLegacyBlock()
        );
    }

    private static void placeErosionColumn(final GameTestHelper helper, final BlockPos tears) {
        helper.getLevel().setBlockAndUpdate(tears.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(tears.below(2), Blocks.DEEPSLATE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(tears.below(3), Blocks.BEDROCK.defaultBlockState());
    }
}
