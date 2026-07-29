package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.ModItems;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;

public final class CircleTalismanItem extends Item {
    public CircleTalismanItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final Optional<CircleTalismanState> state = CircleTalismanState.read(context.getItemInHand());
        if (state.isEmpty()) {
            if (!context.getLevel().isClientSide() && context.getPlayer() != null) {
                context.getPlayer().sendOverlayMessage(Component.translatable("message.warlockery.circle_talisman.empty"));
            }
            return InteractionResult.FAIL;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        final BlockPos center = context.getClickedPos().relative(context.getClickedFace());
        final CircleTalismanState.RestoreResult result = state.orElseThrow().restore(level, center);
        if (!result.success()) {
            if (context.getPlayer() != null) {
                context.getPlayer().sendOverlayMessage(
                    Component.translatable("message.warlockery.circle_talisman.blocked", result.blocked())
                );
            }
            return InteractionResult.FAIL;
        }
        CircleTalismanState.clear(context.getItemInHand());
        context.getItemInHand().set(DataComponents.LORE, new ItemLore(java.util.List.of(
            Component.translatable("tooltip.warlockery.circle_talisman.empty")
        )));
        level.playSound(null, center, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.8F, 1.2F);
        if (context.getPlayer() != null) {
            context.getPlayer().sendOverlayMessage(
                Component.translatable("message.warlockery.circle_talisman.restored", state.orElseThrow().glyphs().size())
            );
        }
        return InteractionResult.SUCCESS;
    }

    public static boolean captureFromRitual(final ServerLevel level, final BlockPos center) {
        final Optional<CircleTalismanState> state = CircleTalismanState.capture(level, center);
        if (state.isEmpty()) {
            return false;
        }
        final Optional<ItemEntity> talisman = level.getEntitiesOfClass(
                ItemEntity.class,
                new AABB(center).inflate(6.0),
                entity -> entity.isAlive()
                    && entity.getItem().is(ModItems.ALL.get("circletalisman").get())
                    && CircleTalismanState.read(entity.getItem()).isEmpty()
            )
            .stream()
            .findFirst();
        if (talisman.isEmpty()) {
            return false;
        }
        final ItemStack stack = talisman.orElseThrow().getItem();
        state.orElseThrow().write(stack);
        stack.set(DataComponents.LORE, new ItemLore(java.util.List.of(
            Component.translatable("tooltip.warlockery.circle_talisman.bound", state.orElseThrow().glyphs().size())
        )));
        talisman.orElseThrow().setItem(stack);
        state.orElseThrow().removeCaptured(level, center);
        return true;
    }

    @Override
    public boolean isFoil(final ItemStack stack) {
        return CircleTalismanState.read(stack).isPresent() || super.isFoil(stack);
    }
}
