package com.kadamitas.warlockery.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;

public final class HomewardSlippersItem extends Item {
    public HomewardSlippersItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer) || player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.PASS;
        }
        final TeleportTransition transition = serverPlayer.findRespawnPositionAndUseSpawnBlock(
            false,
            TeleportTransition.DO_NOTHING
        );
        if (serverPlayer.teleport(transition) == null) {
            return InteractionResult.FAIL;
        }
        player.getCooldowns().addCooldown(stack, 20 * 60 * 5);
        return InteractionResult.SUCCESS;
    }
}
