package com.kadamitas.warlockery.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;

public final class WaterArtichokeGlobeItem extends Item {
    static final int MAX_RESTORED_HUNGER = 20;

    public WaterArtichokeGlobeItem(final Properties properties) {
        super(properties
            .food(new FoodProperties.Builder().nutrition(MAX_RESTORED_HUNGER).saturationModifier(0.0F).build())
            .component(
                DataComponents.CONSUMABLE,
                Consumable.builder()
                    .consumeSeconds(1.6F)
                    .animation(ItemUseAnimation.EAT)
                    .sound(SoundEvents.GENERIC_EAT)
                    .hasConsumeParticles(true)
                    .build()
            ));
    }

    @Override
    public ItemStack finishUsingItem(final ItemStack stack, final Level level, final LivingEntity consumer) {
        final int duration = consumer instanceof Player player ? hungerDuration(player.getFoodData().getFoodLevel()) : 0;
        final ItemStack result = super.finishUsingItem(stack, level, consumer);
        if (!level.isClientSide() && duration > 0) {
            consumer.addEffect(new MobEffectInstance(MobEffects.HUNGER, duration, 2));
        }
        return result;
    }

    static int hungerDuration(final int currentFood) {
        return Math.max(0, Math.min(MAX_RESTORED_HUNGER, MAX_RESTORED_HUNGER - currentFood)) * 60;
    }
}
