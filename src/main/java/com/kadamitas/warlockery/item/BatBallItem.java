package com.kadamitas.warlockery.item;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

public final class BatBallItem extends Item {
    private static final String CAPTURED_BATS = "WarlockeryCapturedBats";
    private static final int CAPACITY = 8;

    public BatBallItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(
        final ItemStack stack,
        final Player player,
        final LivingEntity target,
        final InteractionHand hand
    ) {
        if (!(target instanceof Bat) || captured(stack) >= CAPACITY) {
            if (!player.level().isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.warlockery.bat_ball.cannot_capture"));
            }
            return InteractionResult.FAIL;
        }
        if (!player.level().isClientSide()) {
            final int count = captured(stack) + 1;
            setCaptured(stack, count);
            target.discard();
            player.sendOverlayMessage(Component.translatable("message.warlockery.bat_ball.captured", count, CAPACITY));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        final int count = captured(stack);
        if (count == 0) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.warlockery.bat_ball.empty"));
            }
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel serverLevel) {
            final var spawn = player.blockPosition().relative(player.getDirection(), 2).above();
            for (int index = 0; index < count; index++) {
                final Bat bat = EntityTypes.BAT.spawn(serverLevel, spawn.offset(index % 3 - 1, index / 3, index % 2), EntitySpawnReason.EVENT);
                if (bat != null) {
                    bat.setResting(false);
                }
            }
            setCaptured(stack, 0);
            serverLevel.playSound(null, player.blockPosition(), SoundEvents.BAT_TAKEOFF, SoundSource.NEUTRAL, 1.0F, 1.0F);
            player.sendOverlayMessage(Component.translatable("message.warlockery.bat_ball.released", count));
        }
        return InteractionResult.SUCCESS;
    }

    public static int captured(final ItemStack stack) {
        return captured(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }

    static int captured(final CompoundTag data) {
        return Math.clamp(data.getIntOr(CAPTURED_BATS, 0), 0, CAPACITY);
    }

    static void setCaptured(final ItemStack stack, final int count) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> setCaptured(data, count));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
            Component.translatable(
                count == 0 ? "tooltip.warlockery.bat_ball.empty" : "tooltip.warlockery.bat_ball.bound",
                count,
                CAPACITY
            )
        )));
    }

    static void setCaptured(final CompoundTag data, final int count) {
        if (count == 0) {
            data.remove(CAPTURED_BATS);
        } else {
            data.putInt(CAPTURED_BATS, Math.clamp(count, 0, CAPACITY));
        }
    }

    @Override
    public boolean isFoil(final ItemStack stack) {
        return captured(stack) > 0 || super.isFoil(stack);
    }
}
