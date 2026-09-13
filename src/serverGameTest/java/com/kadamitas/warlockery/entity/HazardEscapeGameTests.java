package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.HazardEscapeRules.Hazard;
import com.kadamitas.warlockery.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;

public final class HazardEscapeGameTests {
    private HazardEscapeGameTests() {
    }

    public static void vulnerableMobRoutesAwayFromContactHazards(final GameTestHelper helper) {
        buildFloor(helper);
        helper.setBlock(new BlockPos(3, 0, 0), Blocks.SAND);
        helper.setBlock(new BlockPos(3, 1, 0), Blocks.CACTUS);
        final WerewolfHunterEntity hunter = helper.spawn(
            ModEntities.WEREWOLF_HUNTER.get(),
            new BlockPos(3, 1, 1),
            EntitySpawnReason.EVENT
        );
        helper.runAfterDelay(2, () -> {
            helper.assertValueEqual(
                HazardEscapeRuntime.currentHazard(hunter, helper.getLevel()),
                java.util.Optional.of(Hazard.CONTACT),
                "the nearby cactus must be recognized as an immediate contact hazard"
            );
            final BlockPos safe = HazardEscapeRuntime.findSafeDestination(
                hunter,
                helper.getLevel(),
                Hazard.CONTACT
            ).orElseThrow(() -> new AssertionError("the mob must find reachable safe ground"));
            helper.assertTrue(HazardEscapeRuntime.isSafe(helper.getLevel(), safe, Hazard.CONTACT),
                "the chosen destination must be clear of contact hazards");
            helper.assertTrue(HazardEscapeRuntime.tick(hunter, helper.getLevel()),
                "a vulnerable mob must prioritize escaping the cactus");
            final BlockPos target = hunter.getNavigation().getTargetPos();
            helper.assertTrue(target != null && target.distSqr(helper.absolutePos(new BlockPos(3, 1, 0))) > 2.0,
                "the installed navigation path must lead away from the cactus");
            helper.succeed();
        });
    }

    public static void drowningMobRoutesFromWaterToDryGround(final GameTestHelper helper) {
        buildFloor(helper);
        for (int x = 2; x <= 4; x++) {
            for (int z = -1; z <= 1; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.WATER);
            }
        }
        final WerewolfHunterEntity hunter = helper.spawn(
            ModEntities.WEREWOLF_HUNTER.get(),
            new BlockPos(3, 1, 0),
            EntitySpawnReason.EVENT
        );
        helper.runAfterDelay(2, () -> {
            hunter.setAirSupply(hunter.getMaxAirSupply() - 20);
            helper.assertValueEqual(
                HazardEscapeRuntime.currentHazard(hunter, helper.getLevel()),
                java.util.Optional.of(Hazard.DROWNING),
                "submerged mobs losing air must recognize drowning danger"
            );
            final BlockPos safe = HazardEscapeRuntime.findSafeDestination(
                hunter,
                helper.getLevel(),
                Hazard.DROWNING
            ).orElseThrow(() -> new AssertionError("the drowning mob must find dry ground"));
            helper.assertTrue(helper.getLevel().getFluidState(safe).isEmpty()
                && helper.getLevel().getFluidState(safe.above()).isEmpty(),
                "the drowning escape destination must be dry");
            helper.assertTrue(HazardEscapeRuntime.tick(hunter, helper.getLevel()),
                "a drowning mob must prioritize reaching dry ground");
            helper.assertTrue(hunter.getNavigation().getTargetPos() != null,
                "drowning escape must install a navigation path");
            helper.succeed();
        });
    }

    private static void buildFloor(final GameTestHelper helper) {
        BlockPos.betweenClosedStream(new BlockPos(-1, 0, -3), new BlockPos(12, 0, 3))
            .forEach(position -> helper.setBlock(position, Blocks.STONE));
    }
}

