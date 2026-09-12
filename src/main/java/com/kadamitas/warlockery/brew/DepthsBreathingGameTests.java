package com.kadamitas.warlockery.brew;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

public final class DepthsBreathingGameTests {
    private DepthsBreathingGameTests() {
    }

    public static void depthsBreathingSurvivesVanillaAirRecovery(final GameTestHelper helper) {
        buildDryFloorAndSubmergedCell(helper);
        final Mob removedOnLand = spawnCow(helper, new BlockPos(3, 1, 4));
        final Mob expiredOnLand = spawnCow(helper, new BlockPos(7, 1, 4));
        final Mob submerged = spawnCow(helper, new BlockPos(14, 1, 4));
        final Mob outsideControl = spawnCow(helper, new BlockPos(25, 1, 25));
        final float[] healthAfterRemoval = new float[1];
        final float[] healthAfterExpiry = new float[1];

        BrewMarkerState.apply(removedOnLand, BrewMarkerKind.DEPTHS, 600);
        BrewMarkerState.apply(expiredOnLand, BrewMarkerKind.DEPTHS, 200);
        BrewMarkerState.apply(submerged, BrewMarkerKind.DEPTHS, 600);
        submerged.setAirSupply(40);

        helper.runAfterDelay(60, () -> {
            helper.assertTrue(submerged.isEyeInFluid(FluidTags.WATER),
                "the low-air Depths target must remain submerged during normal entity ticks");
            helper.assertTrue(submerged.getAirSupply() >= submerged.getMaxAirSupply() - 25,
                "Depths must refill depleted underwater air through the registered tick hook");
            helper.assertValueEqual(submerged.getHealth(), submerged.getMaxHealth(),
                "the submerged Depths target must not take drowning damage");
            assertUnaffectedControl(helper, outsideControl);
        });

        helper.runAfterDelay(180, () -> {
            assertDrylandSuffocation(helper, removedOnLand, "explicit-removal target");
            assertDrylandSuffocation(helper, expiredOnLand, "natural-expiry target");
            helper.assertTrue(BrewMarkerState.isActive(expiredOnLand, BrewMarkerKind.DEPTHS),
                "the expiry target must take real suffocation damage before its marker expires");
            helper.assertValueEqual(submerged.getHealth(), submerged.getMaxHealth(),
                "underwater Depths breathing must remain safe while dryland targets suffocate");
            assertUnaffectedControl(helper, outsideControl);
            healthAfterRemoval[0] = removedOnLand.getHealth();
            BrewMarkerState.remove(removedOnLand, BrewMarkerKind.DEPTHS);
            BrewMarkerState.remove(submerged, BrewMarkerKind.DEPTHS);
        });

        helper.runAfterDelay(220, () -> {
            helper.assertTrue(!BrewMarkerState.isActive(expiredOnLand, BrewMarkerKind.DEPTHS),
                "normal world ticks must expire the short-lived Depths marker");
            helper.assertTrue(expiredOnLand.isAlive(),
                "the naturally expired target must survive to demonstrate breathing recovery");
            healthAfterExpiry[0] = expiredOnLand.getHealth();
        });

        helper.runAfterDelay(340, () -> {
            assertRecoveredOnLand(helper, removedOnLand, healthAfterRemoval[0], "removed marker");
            assertRecoveredOnLand(helper, expiredOnLand, healthAfterExpiry[0], "expired marker");
            helper.assertTrue(submerged.isEyeInFluid(FluidTags.WATER),
                "the underwater control must stay submerged after Depths is removed");
            helper.assertTrue(!BrewMarkerState.isActive(submerged, BrewMarkerKind.DEPTHS),
                "the underwater target's Depths marker must remain removed");
            helper.assertTrue(submerged.getAirSupply() < submerged.getMaxAirSupply() - 80,
                "removing Depths must restore normal underwater air consumption");
            helper.assertValueEqual(submerged.getHealth(), submerged.getMaxHealth(),
                "the underwater recovery observation must finish before normal air is exhausted");
            assertUnaffectedControl(helper, outsideControl);
            helper.succeed();
        });
    }

    private static Mob spawnCow(final GameTestHelper helper, final BlockPos position) {
        final Mob cow = (Mob) helper.spawn(
            BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:cow")),
            position,
            EntitySpawnReason.EVENT
        );
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.setPersistenceRequired();
        cow.setAirSupply(cow.getMaxAirSupply());
        helper.assertValueEqual(cow.getHealth(), cow.getMaxHealth(),
            "each breathing target must begin at full natural health");
        return cow;
    }

    private static void assertDrylandSuffocation(
        final GameTestHelper helper, final Mob cow, final String label
    ) {
        helper.assertTrue(!cow.isInWater() && !cow.isEyeInFluid(FluidTags.WATER),
            label + " must remain on dry land");
        helper.assertTrue(cow.isAlive() && cow.getHealth() < cow.getMaxHealth(),
            label + " must lose health from Depths despite vanilla dryland air recovery");
    }

    private static void assertRecoveredOnLand(
        final GameTestHelper helper, final Mob cow, final float health, final String label
    ) {
        helper.assertTrue(!BrewMarkerState.isActive(cow, BrewMarkerKind.DEPTHS),
            label + " must remain inactive");
        helper.assertValueEqual(cow.getAirSupply(), cow.getMaxAirSupply(),
            label + " must restore normal dryland air recovery");
        helper.assertValueEqual(cow.getHealth(), health,
            label + " must stop further dryland suffocation damage");
    }

    private static void assertUnaffectedControl(final GameTestHelper helper, final Mob cow) {
        helper.assertTrue(!BrewMarkerState.isActive(cow, BrewMarkerKind.DEPTHS),
            "the distant outside control must never acquire Depths");
        helper.assertValueEqual(cow.getAirSupply(), cow.getMaxAirSupply(),
            "the outside control must retain ordinary dryland breathing");
        helper.assertValueEqual(cow.getHealth(), cow.getMaxHealth(),
            "the outside control must remain uninjured");
    }

    private static void buildDryFloorAndSubmergedCell(final GameTestHelper helper) {
        BlockPos.betweenClosedStream(new BlockPos(1, 0, 1), new BlockPos(28, 0, 28))
            .forEach(position -> helper.setBlock(position, Blocks.STONE));
        for (final BlockPos position : BlockPos.betweenClosed(new BlockPos(12, 0, 2), new BlockPos(16, 5, 6))) {
            final boolean wall = position.getX() == 12 || position.getX() == 16
                || position.getY() == 0 || position.getY() == 5
                || position.getZ() == 2 || position.getZ() == 6;
            helper.setBlock(position, wall ? Blocks.GLASS : Blocks.WATER);
        }
    }
}
