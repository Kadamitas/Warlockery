package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.magic.MagicPathRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class MirrorItem extends Item {
    public MirrorItem(final Properties properties) {
        super(properties.stacksTo(1).durability(128));
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        final var reflection = MirrorState.read(stack);
        if (reflection.isEmpty()) {
            show(player, UtilityDecision.failure("missing_reflection"));
            return InteractionResult.FAIL;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            final MirrorState destination = reflection.orElseThrow();
            final boolean moved = MagicPathRuntime.teleportToBoundPosition(
                serverPlayer, destination.dimension(), destination.position()
            );
            if (moved) {
                stack.hurtAndBreak(1, (net.minecraft.server.level.ServerLevel) serverPlayer.level(), serverPlayer, _ -> { });
            }
            show(player, moved ? UtilityDecision.success("travelled") : UtilityDecision.failure("blocked"));
            return moved ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }

    private static void show(final Player player, final UtilityDecision decision) {
        if (!player.level().isClientSide()) {
            player.sendOverlayMessage(Component.translatable(decision.messageKey("mirror"))
                .withStyle(decision.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }
}
