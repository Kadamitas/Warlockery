package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.network.ModNetwork;
import com.kadamitas.warlockery.magic.MagicPathRuntime;
import com.kadamitas.warlockery.ritual.RitualManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class ArcaneFocusItem extends Item {
    public ArcaneFocusItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || context.getPlayer() == null) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.FAIL;
        }
        if (RitualManager.isCircleCenter(level, context.getClickedPos())) {
            ModNetwork.openRitualScreen(player, context.getClickedPos());
            return InteractionResult.SUCCESS;
        }
        return MagicPathRuntime.useBlock(
            player,
            context.getClickedPos(),
            context.getClickedFace(),
            player.isSecondaryUseActive()
        );
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        return MagicPathRuntime.useSelf(serverPlayer, player.isSecondaryUseActive());
    }

    @Override
    public InteractionResult interactLivingEntity(
        final ItemStack stack,
        final Player player,
        final LivingEntity target,
        final InteractionHand hand
    ) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        return MagicPathRuntime.useTarget(serverPlayer, target, player.isSecondaryUseActive());
    }
}
