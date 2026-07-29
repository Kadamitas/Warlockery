package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalProgression;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class WolfAltarRuntime {
    private WolfAltarRuntime() {
    }

    public static UtilityDeviceRules.WolfAltarProgression completeTrial(
        final Player player,
        final ItemStack offering
    ) {
        final int currentLevel = SupernaturalProgression.level(player, SupernaturalProgression.Path.WEREWOLF);
        final var progression = UtilityDeviceRules.advanceWolf(currentLevel);
        if (!progression.advanced()) {
            return progression;
        }
        SupernaturalState.setForm(player, SupernaturalForm.WEREWOLF);
        SupernaturalProgression.setLevel(player, SupernaturalProgression.Path.WEREWOLF, progression.level());
        player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 1_200, 1));
        if (!player.hasInfiniteMaterials()) {
            offering.shrink(1);
        }
        if (progression.hornEarned()) {
            grantOrDrop(player, new ItemStack(ModItems.ALL.get("hornofthehunt").get()));
        }
        return progression;
    }

    private static void grantOrDrop(final Player player, final ItemStack reward) {
        if (!player.getInventory().add(reward)) {
            player.drop(reward, false);
        }
    }
}
