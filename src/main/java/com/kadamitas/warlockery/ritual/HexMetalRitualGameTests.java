package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.ritual.hex.HexKind;
import com.kadamitas.warlockery.ritual.hex.HexRuntime;
import com.kadamitas.warlockery.ritual.hex.HexState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class HexMetalRitualGameTests {
    private HexMetalRitualGameTests() {
    }

    public static void heatMetalRitualTargetReachesThePersistentHex(final GameTestHelper helper) {
        final Zombie victim = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        victim.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));

        HexBehaviors.require("heat_metal").apply(victim, 400);

        helper.assertTrue(
            HexState.isActive(victim, HexKind.HEAT_METAL),
            "the heat_metal ritual target must reach HexKind.HEAT_METAL, not a fallback status effect"
        );
        helper.succeed();
    }

    public static void heatMetalBurnsAWearerOfTaggedMetal(final GameTestHelper helper) {
        final Zombie victim = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        victim.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        victim.setNoAi(true);
        HexRuntime.apply(victim, HexKind.HEAT_METAL, 400);

        victim.tickCount = 20;
        HexRuntime.tick(victim);

        helper.assertTrue(
            victim.isOnFire(),
            "heat metal must ignite a victim wearing tagged metal through the live hex tick"
        );
        helper.succeed();
    }

    public static void heatMetalSparesAVictimCarryingNoMetal(final GameTestHelper helper) {
        final Zombie victim = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        victim.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        victim.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        victim.setNoAi(true);
        HexRuntime.apply(victim, HexKind.HEAT_METAL, 400);

        victim.tickCount = 20;
        HexRuntime.tick(victim);

        helper.assertFalse(
            victim.isOnFire(),
            "heat metal must not burn a victim carrying no tagged metal"
        );
        helper.succeed();
    }

    public static void heatMetalCureRitualClearsTheHex(final GameTestHelper helper) {
        final Zombie victim = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        victim.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        HexRuntime.apply(victim, HexKind.HEAT_METAL, 4_000);
        helper.assertTrue(HexState.isActive(victim, HexKind.HEAT_METAL), "heat metal must begin active");

        HexBehaviors.require("heat_metal").remove(victim);

        helper.assertFalse(
            HexState.isActive(victim, HexKind.HEAT_METAL),
            "the cure_heat_metal cleanse target must clear the persistent hex"
        );
        helper.succeed();
    }

    public static void everyDatapackHexTargetResolvesInALiveRegistry(final GameTestHelper helper) {
        RitualManager.INSTANCE.all().stream()
            .filter(entry -> RitualAction.HEX.id().equals(entry.definition().action())
                || RitualAction.CLEANSE.id().equals(entry.definition().action()))
            .forEach(entry -> helper.assertTrue(
                HexBehaviors.find(entry.definition().target()).isPresent(),
                entry.id() + " names hex target " + entry.definition().target() + " which reaches no behavior"
            ));
        helper.succeed();
    }

    public static void heatMetalRitualIsLoadedAndPairedWithItsCure(final GameTestHelper helper) {
        helper.assertTrue(
            RitualManager.INSTANCE.byId(ritual("hex_heat_metal")).isPresent(),
            "hex_heat_metal must survive load-time validation"
        );
        helper.assertTrue(
            RitualManager.INSTANCE.byId(ritual("cure_heat_metal")).isPresent(),
            "cure_heat_metal must survive load-time validation"
        );
        helper.succeed();
    }

    private static Identifier ritual(final String path) {
        return Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path);
    }
}
