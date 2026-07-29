package com.kadamitas.warlockery.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;

public final class BiomeNoteItem extends Item {
    public BiomeNoteItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        final var key = context.getLevel().getBiome(context.getClickedPos()).unwrapKey();
        if (key.isEmpty()) {
            if (context.getPlayer() != null) {
                context.getPlayer().sendOverlayMessage(Component.translatable("message.warlockery.biome_note.failed")
                    .withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        final var biome = key.orElseThrow().identifier();
        BiomeNoteState.write(context.getItemInHand(), biome);
        context.getItemInHand().set(DataComponents.CUSTOM_NAME, Component.translatable(
            "item.warlockery.biomenote",
            biome.toString()
        ));
        context.getItemInHand().set(DataComponents.LORE, new ItemLore(java.util.List.of(
            Component.translatable("tooltip.warlockery.biome_note", biome.toString())
        )));
        if (context.getPlayer() != null) {
            context.getPlayer().sendOverlayMessage(Component.translatable(
                "message.warlockery.biome_note.recorded",
                biome.toString()
            ).withStyle(ChatFormatting.GREEN));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(final net.minecraft.world.item.ItemStack stack) {
        return BiomeNoteState.read(stack).isPresent() || super.isFoil(stack);
    }
}
