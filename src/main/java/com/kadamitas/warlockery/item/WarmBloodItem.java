package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;

public final class WarmBloodItem extends Item {
    static final int VAMPIRE_RESERVE = 20;

    public WarmBloodItem(final Properties properties) {
        super(properties
            .stacksTo(16)
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
        if (!level.isClientSide() && consumer instanceof Player player) {
            if (SupernaturalState.getForm(player) == SupernaturalForm.VAMPIRE) {
                SupernaturalState.addReserve(player, VAMPIRE_RESERVE);
            } else {
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 600, 1));
            }
        }
        return super.finishUsingItem(stack, level, consumer);
    }

    static int reserveRestored(final SupernaturalForm form) {
        return form == SupernaturalForm.VAMPIRE ? VAMPIRE_RESERVE : 0;
    }
}
