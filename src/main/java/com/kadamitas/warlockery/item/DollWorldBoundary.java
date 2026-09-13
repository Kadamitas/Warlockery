package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.dream.SpiritWorldRuntime;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** A doll keeps the physical or spirit origin of its creation across binding, saving and transport. */
public final class DollWorldBoundary {
    private static final String ORIGIN = "WarlockeryDollOrigin";
    private static final String PHYSICAL = "physical";
    private static final String SPIRIT = "spirit";

    private DollWorldBoundary() { }

    static void recordCreation(final ItemStack doll, final boolean spirit) {
        CustomData.update(DataComponents.CUSTOM_DATA, doll, data -> {
            if (!data.contains(ORIGIN)) data.putString(ORIGIN, spirit ? SPIRIT : PHYSICAL);
        });
    }

    static void recordLegacyOrigin(final ItemStack doll) {
        recordCreation(doll, false);
    }

    public static boolean inSpiritWorld(final Entity entity) {
        return entity instanceof Player player
            ? SpiritWorldRuntime.isSpiritWorld(entity.level(), player)
            : SpiritWorldRuntime.isSpiritWorld(entity.level());
    }

    public static boolean sameSide(final Entity source, final LivingEntity target) {
        return !SpiritWorldRuntime.isSleepingBody(target) && inSpiritWorld(source) == inSpiritWorld(target);
    }

    public static boolean sameSide(final Level source, final LivingEntity target) {
        return !SpiritWorldRuntime.isSleepingBody(target)
            && SpiritWorldRuntime.isSpiritWorld(source) == inSpiritWorld(target);
    }

    public static boolean allows(final ItemStack doll, final Entity source, final LivingEntity target) {
        return sameSide(source, target) && matchesOrigin(doll, inSpiritWorld(source));
    }

    public static boolean allows(final ItemStack doll, final Level source, final LivingEntity target) {
        return sameSide(source, target) && matchesOrigin(doll, SpiritWorldRuntime.isSpiritWorld(source));
    }

    private static boolean matchesOrigin(final ItemStack doll, final boolean spirit) {
        final String origin = doll.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
            .getStringOr(ORIGIN, PHYSICAL);
        return origin.equals(spirit ? SPIRIT : PHYSICAL);
    }
}
