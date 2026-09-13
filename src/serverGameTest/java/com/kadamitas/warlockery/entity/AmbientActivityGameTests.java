package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.AmbientActivityProfile.ActivityType;
import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class AmbientActivityGameTests {
    private AmbientActivityGameTests() {
    }

    public static void demonBuildsOneTemporarySnowHearth(final GameTestHelper helper) {
        buildFloor(helper);
        helper.setBlock(new BlockPos(4, 0, 4), Blocks.SNOW_BLOCK);
        final ArcaneMob demon = (ArcaneMob) ModEntities.ALL.get("demon").get()
            .create(helper.getLevel(), EntitySpawnReason.EVENT);
        if (demon == null) {
            throw new AssertionError("demon entity type must instantiate");
        }
        demon.snapTo(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 1, 4))));
        helper.getLevel().addFreshEntity(demon);
        helper.assertTrue(AmbientActivityRuntime.executeNow(
            demon,
            helper.getLevel(),
            CreatureKind.DEMON,
            ActivityType.WINTER_HEARTH
        ), "a fireproof demon must make a hearth when stranded in snow");
        helper.assertValueEqual(countCampfires(helper), 1L, "the activity may add only one campfire");
        helper.assertTrue(!AmbientActivityRuntime.executeNow(
            demon,
            helper.getLevel(),
            CreatureKind.DEMON,
            ActivityType.WINTER_HEARTH
        ), "an active hearth must prevent duplicate construction");
        helper.runAfterDelay(AmbientActivityRules.TEMPORARY_HEARTH_TICKS + 1, () -> {
            AmbientActivityRuntime.clearExpiredHearth(demon, helper.getLevel());
            helper.assertValueEqual(countCampfires(helper), 0L, "the temporary hearth must remove itself");
            helper.succeed();
        });
    }

    public static void entPlantsOneLooseSaplingWithoutDuplicatingIt(final GameTestHelper helper) {
        buildFloor(helper);
        final EntEntity ent = helper.spawn(
            ModEntities.ENT.get(),
            new BlockPos(4, 1, 4),
            EntitySpawnReason.EVENT
        );
        final ItemEntity sapling = new ItemEntity(
            helper.getLevel(),
            helper.absolutePos(new BlockPos(5, 1, 4)).getX() + 0.5,
            helper.absolutePos(new BlockPos(5, 1, 4)).getY(),
            helper.absolutePos(new BlockPos(5, 1, 4)).getZ() + 0.5,
            new ItemStack(Items.OAK_SAPLING)
        );
        helper.getLevel().addFreshEntity(sapling);
        helper.assertTrue(AmbientActivityRuntime.executeNow(
            ent,
            helper.getLevel(),
            CreatureKind.ENT,
            ActivityType.GROVE_TENDING
        ), "an ent must plant a loose sapling on valid nearby soil");
        helper.assertValueEqual(countSaplings(helper), 1L, "exactly one sapling block must be planted");
        helper.assertTrue(!sapling.isAlive() || sapling.getItem().isEmpty(),
            "the planted sapling must be consumed instead of duplicated");
        helper.succeed();
    }

    private static void buildFloor(final GameTestHelper helper) {
        BlockPos.betweenClosedStream(new BlockPos(0, 0, 0), new BlockPos(10, 0, 10))
            .forEach(position -> helper.setBlock(position, Blocks.DIRT));
    }

    private static long countCampfires(final GameTestHelper helper) {
        return BlockPos.betweenClosedStream(new BlockPos(0, 1, 0), new BlockPos(10, 3, 10))
            .filter(position -> helper.getBlockState(position).is(Blocks.CAMPFIRE))
            .count();
    }

    private static long countSaplings(final GameTestHelper helper) {
        return BlockPos.betweenClosedStream(new BlockPos(0, 1, 0), new BlockPos(10, 3, 10))
            .filter(position -> helper.getBlockState(position).is(Blocks.OAK_SAPLING))
            .count();
    }
}
