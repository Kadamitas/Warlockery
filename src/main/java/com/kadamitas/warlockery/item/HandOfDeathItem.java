package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.entity.DeathImpersonationRules;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public final class HandOfDeathItem extends Item {
    private static final String SCYTHE = "WarlockeryDeathScythe";

    public HandOfDeathItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        if (!DeathImpersonationRules.isComplete(player)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.warlockery.death_hand.incomplete"));
            }
            return InteractionResult.FAIL;
        }
        final ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            final boolean next = !isScythe(stack);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.putBoolean(SCYTHE, next));
            player.sendOverlayMessage(Component.translatable(
                next ? "message.warlockery.death_hand.scythe" : "message.warlockery.death_hand.hand"
            ));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(final ItemStack stack) {
        return isScythe(stack) || super.isFoil(stack);
    }

    public static boolean isScythe(final ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
            .copyTag()
            .getBooleanOr(SCYTHE, false);
    }
}
