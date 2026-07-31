package com.kadamitas.warlockery.item;

import java.util.Comparator;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;

public final class PurifiedMilkItem extends Item {
    static final int STACK_SIZE = 64;

    public PurifiedMilkItem(final Properties properties) {
        super(properties
            .stacksTo(STACK_SIZE)
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
            selectEffect(consumer).ifPresent(consumer::removeEffect);
        }
        return super.finishUsingItem(stack, level, consumer);
    }

    static java.util.Optional<Holder<MobEffect>> selectEffect(final LivingEntity consumer) {
        return consumer.getActiveEffects().stream()
            .sorted(Comparator.comparing(PurifiedMilkItem::beneficial))
            .map(MobEffectInstance::getEffect)
            .findFirst();
    }

    static boolean beneficial(final MobEffectInstance effect) {
        return effect.getEffect().value().isBeneficial();
    }
}
