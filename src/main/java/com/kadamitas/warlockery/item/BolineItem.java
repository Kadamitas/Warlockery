package com.kadamitas.warlockery.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.gameevent.GameEvent;

public final class BolineItem extends ShearsItem {
    public BolineItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(
        final ItemStack stack,
        final Player player,
        final LivingEntity target,
        final InteractionHand hand
    ) {
        if (!(target instanceof Shearable shearable)) {
            return super.interactLivingEntity(stack, player, target, hand);
        }
        if (!shearable.readyForShearing() || !(target.level() instanceof ServerLevel level)) {
            return InteractionResult.CONSUME;
        }
        shearable.shear(level, SoundSource.PLAYERS, stack);
        target.gameEvent(GameEvent.SHEAR, player);
        stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
        return InteractionResult.SUCCESS_SERVER;
    }
}
