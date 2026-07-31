package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.transformation.SupernaturalAdvancement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class WolfAltarRuntime {
    private WolfAltarRuntime() {
    }

    public static UtilityDeviceRules.WolfAltarProgression completeTrial(
        final Player player,
        final ItemStack offering
    ) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return new UtilityDeviceRules.WolfAltarProgression(0, false, false);
        }
        final SupernaturalAdvancement.WolfAltarResult result = SupernaturalAdvancement.useWolfAltar(
            serverPlayer,
            offering
        );
        return new UtilityDeviceRules.WolfAltarProgression(result.level(), result.advanced(), false);
    }
}
