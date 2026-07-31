package com.kadamitas.warlockery.item;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;

public final class UniversalAntidoteItem extends Item {
    public UniversalAntidoteItem(final Properties properties) {
        super(properties
            .stacksTo(4)
            .usingConvertsTo(Items.GLASS_BOTTLE)
            .component(
                DataComponents.CONSUMABLE,
                Consumable.builder()
                    .consumeSeconds(1.6F)
                    .animation(ItemUseAnimation.DRINK)
                    .sound(SoundEvents.GENERIC_DRINK)
                    .hasConsumeParticles(false)
                    .build()
            ));
    }

    @Override
    public ItemStack finishUsingItem(final ItemStack stack, final Level level, final LivingEntity consumer) {
        if (!level.isClientSide()) {
            consumer.getActiveEffects().stream()
                .map(effect -> effect.getEffect())
                .filter(UniversalAntidoteItem::isCurable)
                .toList()
                .forEach(consumer::removeEffect);
        }
        return super.finishUsingItem(stack, level, consumer);
    }

    static boolean isCurable(final Holder<MobEffect> effect) {
        return effect == MobEffects.POISON || effect == MobEffects.WITHER;
    }
}
