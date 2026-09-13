package com.kadamitas.warlockery.brew;

import com.kadamitas.warlockery.ritual.hex.SinkingFluidContact;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

public final class SinkingFluidContactGameTests {
    private SinkingFluidContactGameTests() {
    }

    public static void sinkingBrewDetectsTaggedFluidAndBurdensNormalSwimming(final GameTestHelper helper) {
        buildPool(helper, 6);
        buildPool(helper, 18);
        helper.setBlock(new BlockPos(26, 0, 24), Blocks.STONE);
        final Mob marked = spawnCow(helper, new BlockPos(6, 6, 6));
        final Mob untreated = spawnCow(helper, new BlockPos(18, 6, 6));
        final Mob dryland = spawnCow(helper, new BlockPos(26, 1, 24));
        dryland.setNoAi(true);
        final double startingHeight = marked.getY();
        final double drylandHeight = dryland.getY();
        BrewMarkerState.apply(marked, BrewMarkerKind.SINKING, 200);
        BrewMarkerState.apply(dryland, BrewMarkerKind.SINKING, 200);

        helper.assertValueEqual(marked.getY(), untreated.getY(),
            "marked and untreated swimming cows must begin at the same height");

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(SinkingFluidContact.height(marked) > 0.0,
                "the marked swimmer must detect positive contact with tagged water");
            helper.assertTrue(SinkingFluidContact.height(untreated) > 0.0,
                "the matching untreated swimmer must also contact tagged water");
            helper.assertValueEqual(SinkingFluidContact.height(dryland), 0.0,
                "solid dry ground must not count as sinking-fluid contact");
        });

        helper.runAfterDelay(80, () -> {
            helper.assertTrue(BrewMarkerState.isActive(marked, BrewMarkerKind.SINKING),
                "the marked swimmer must retain Sinking during the physics observation");
            helper.assertTrue(!BrewMarkerState.isActive(untreated, BrewMarkerKind.SINKING),
                "the matched swimming control must remain untreated");
            helper.assertTrue(SinkingFluidContact.height(marked) > 0.0,
                "the sinking target must remain in actual tagged fluid");
            helper.assertTrue(marked.getY() <= untreated.getY() - 0.5,
                "Sinking must move the marked cow at least half a block below the untreated swimmer"
                    + "; marked=" + marked.getY() + ", untreated=" + untreated.getY());
            helper.assertTrue(marked.getY() <= startingHeight - 0.5,
                "normal physics must lower the marked cow from its own starting position");
            helper.assertValueEqual(marked.getHealth(), marked.getMaxHealth(),
                "the sinking observation must finish before drowning damage");
            helper.assertValueEqual(untreated.getHealth(), untreated.getMaxHealth(),
                "the untreated swimming control must remain uninjured");
            helper.assertValueEqual(SinkingFluidContact.height(dryland), 0.0,
                "the dryland control must remain outside tagged fluids");
            helper.assertTrue(Math.abs(dryland.getY() - drylandHeight) < 0.01,
                "Sinking must leave a dry cow standing on solid ground");
            helper.assertValueEqual(dryland.getHealth(), dryland.getMaxHealth(),
                "the dryland control must remain uninjured");
            helper.succeed();
        });
    }

    private static Mob spawnCow(final GameTestHelper helper, final BlockPos position) {
        final Mob cow = (Mob) helper.spawn(
            BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:cow")),
            position,
            EntitySpawnReason.EVENT
        );
        cow.setPersistenceRequired();
        helper.assertTrue(!cow.isNoAi() && !cow.isNoGravity(),
            "swimming targets must retain their ordinary AI and gravity");
        return cow;
    }

    private static void buildPool(final GameTestHelper helper, final int centerX) {
        for (final BlockPos position : BlockPos.betweenClosed(
            new BlockPos(centerX - 2, 0, 4), new BlockPos(centerX + 2, 8, 8)
        )) {
            final boolean wall = position.getX() == centerX - 2 || position.getX() == centerX + 2
                || position.getZ() == 4 || position.getZ() == 8 || position.getY() == 0;
            helper.setBlock(position, wall ? Blocks.STONE : position.getY() <= 6 ? Blocks.WATER : Blocks.AIR);
        }
    }
}
