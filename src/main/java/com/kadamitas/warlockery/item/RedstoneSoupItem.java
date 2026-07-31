package com.kadamitas.warlockery.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class RedstoneSoupItem extends Item {
    public RedstoneSoupItem(final Properties properties) {
        super(properties
            .stacksTo(16)
            .usingConvertsTo(Items.BOWL)
            .food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.2F).build())
            .component(
                DataComponents.CONSUMABLE,
                Consumable.builder()
                    .consumeSeconds(1.2F)
                    .animation(ItemUseAnimation.DRINK)
                    .sound(SoundEvents.GENERIC_DRINK)
                    .hasConsumeParticles(false)
                    .build()
            ));
    }

    @Override
    public ItemStack finishUsingItem(final ItemStack stack, final Level level, final LivingEntity consumer) {
        final boolean grantsHealth = !(consumer instanceof Player player)
            || grantsHealthBoost(player.getFoodData().getFoodLevel());
        final ItemStack result = super.finishUsingItem(stack, level, consumer);
        if (!level.isClientSide() && grantsHealth) {
            consumer.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, 3_600, 1));
            consumer.heal(8.0F);
        }
        return result;
    }

    static boolean grantsHealthBoost(final int foodLevel) {
        return foodLevel >= 20;
    }
}
