package com.kadamitas.warlockery.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Gives a held focus priority over an entity's own menu, feeding, or taming interaction. */
public final class ArcaneFocusEntityInteraction {
    private ArcaneFocusEntityInteraction() { }

    public static InteractionResult dispatch(final Player player, final Entity target, final InteractionHand hand) {
        final var stack = player.getItemInHand(hand);
        if (player.isSpectator() || !(target instanceof LivingEntity living)
                || !(stack.getItem() instanceof ArcaneFocusItem focus)) {
            return InteractionResult.PASS;
        }
        if (!living.isAlive() || target.level() != player.level()
                || !player.isWithinEntityInteractionRange(target, player instanceof ServerPlayer ? 3.0D : 0.0D)) {
            return InteractionResult.FAIL;
        }
        // Client success sends the normal interaction packet and prevents vanilla fallback.
        if (!(player instanceof ServerPlayer)) {
            return InteractionResult.SUCCESS;
        }
        final var result = focus.interactLivingEntity(stack, player, living, hand);
        return result == InteractionResult.PASS ? InteractionResult.FAIL : result;
    }

    public static void registerEvents() {
        net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific.BUS.addListener(event -> {
            final var result = dispatch(event.getEntity(), event.getTarget(), event.getHand());
            if (result == InteractionResult.PASS) return false;
            event.setCancellationResult(result);
            return true;
        });
    }
}
