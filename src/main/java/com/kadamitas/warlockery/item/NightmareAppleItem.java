package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.dream.SpiritWorldRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class NightmareAppleItem extends Item {
    public NightmareAppleItem(final Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(final ItemStack stack, final Level level, final LivingEntity consumer) {
        final ItemStack result = super.finishUsingItem(stack, level, consumer);
        if (consumer instanceof ServerPlayer player) {
            SpiritWorldRuntime.enterFromSleepingApple(player);
        }
        return result;
    }
}
