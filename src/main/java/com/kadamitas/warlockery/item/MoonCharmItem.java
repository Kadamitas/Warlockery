package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalProgression;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import com.kadamitas.warlockery.transformation.WerewolfShape;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

public final class MoonCharmItem extends Item {
    private static final int SHIFT_DURATION = 60;

    public MoonCharmItem(final Properties properties) {
        super(properties.stacksTo(1).durability(49));
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        final int lycanthropy = SupernaturalProgression.level(player, SupernaturalProgression.Path.WEREWOLF);
        if (SupernaturalState.getForm(player) != SupernaturalForm.WEREWOLF || lycanthropy < 2) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.warlockery.moon_charm.locked")
                    .withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown() && lycanthropy < 5) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.warlockery.moon_charm.wolfman_locked")
                    .withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemStack finishUsingItem(final ItemStack stack, final Level level, final LivingEntity user) {
        if (user instanceof Player player && !level.isClientSide()) {
            final WerewolfShape current = SupernaturalProgression.werewolfShape(player);
            final WerewolfShape requested = player.isShiftKeyDown() ? WerewolfShape.WOLFMAN : WerewolfShape.WOLF;
            final WerewolfShape next = current == requested ? WerewolfShape.HUMAN : requested;
            SupernaturalProgression.setWerewolfShape(player, next);
            if (!player.hasInfiniteMaterials()) {
                stack.hurtAndBreak(1, player, player.getUsedItemHand());
            }
            player.sendOverlayMessage(Component.translatable(
                "message.warlockery.moon_charm.shifted",
                Component.translatable("shape.warlockery." + next.name().toLowerCase(java.util.Locale.ROOT))
            ).withStyle(ChatFormatting.AQUA));
        }
        return stack;
    }

    @Override
    public ItemUseAnimation getUseAnimation(final ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    @Override
    public int getUseDuration(final ItemStack stack, final LivingEntity user) {
        return SHIFT_DURATION;
    }
}
