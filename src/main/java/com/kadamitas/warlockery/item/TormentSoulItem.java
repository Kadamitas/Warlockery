package com.kadamitas.warlockery.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class TormentSoulItem extends Item {
    public TormentSoulItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(
        final ItemStack stack,
        final Player player,
        final LivingEntity target,
        final InteractionHand hand
    ) {
        if (player.level().isClientSide()) {
            return AbyssalBanishment.canBanish(target) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!(player.level() instanceof ServerLevel level) || !AbyssalBanishment.banish(level, target)) {
            player.sendSystemMessage(Component.translatable("message.warlockery.soul_of_torment.failed"));
            return InteractionResult.FAIL;
        }
        stack.hurtAndBreak(1, player, hand);
        player.getCooldowns().addCooldown(stack, 60);
        player.sendSystemMessage(Component.translatable("message.warlockery.soul_of_torment.success", target.getDisplayName()));
        return InteractionResult.SUCCESS;
    }
}
