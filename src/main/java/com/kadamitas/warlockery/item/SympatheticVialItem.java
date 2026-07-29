package com.kadamitas.warlockery.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

public final class SympatheticVialItem extends Item {
    public SympatheticVialItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(
        final ItemStack stack,
        final Player player,
        final LivingEntity target,
        final InteractionHand hand
    ) {
        if (!player.level().isClientSide()) {
            SympatheticBinding.from(target).write(stack);
            stack.set(DataComponents.LORE, new ItemLore(java.util.List.of(Component.translatable("tooltip.warlockery.sympathetic_vial.bound", target.getName()))));
            player.sendSystemMessage(Component.translatable("message.warlockery.sympathetic_vial.bound", target.getDisplayName()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(final ItemStack stack) {
        return SympatheticBinding.read(stack).isPresent() || super.isFoil(stack);
    }
}
