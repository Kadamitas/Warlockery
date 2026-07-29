package com.kadamitas.warlockery.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;

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
                    .onConsume(new ApplyStatusEffectsConsumeEffect(
                        new MobEffectInstance(MobEffects.REGENERATION, 100, 0)
                    ))
                    .build()
            ));
    }
}
