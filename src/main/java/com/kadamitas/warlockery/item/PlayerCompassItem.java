package com.kadamitas.warlockery.item;

import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.LodestoneTracker;
import org.jspecify.annotations.Nullable;

public final class PlayerCompassItem extends CompassItem {
    public PlayerCompassItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(
        final ItemStack stack,
        final Player player,
        final LivingEntity target,
        final InteractionHand hand
    ) {
        if (!(target instanceof ServerPlayer tracked)) {
            return InteractionResult.PASS;
        }
        SympatheticBinding.from(tracked).write(stack);
        stack.set(DataComponents.LORE, new ItemLore(java.util.List.of(
            Component.translatable("tooltip.warlockery.player_compass.bound", tracked.getDisplayName())
        )));
        updateTarget(stack, tracked);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(
        final ItemStack stack,
        final ServerLevel level,
        final Entity owner,
        final @Nullable EquipmentSlot slot
    ) {
        SympatheticBinding.read(stack)
            .flatMap(binding -> binding.resolve(level.getServer()))
            .filter(ServerPlayer.class::isInstance)
            .map(ServerPlayer.class::cast)
            .ifPresent(target -> updateTarget(stack, target));
    }

    @Override
    public Component getName(final ItemStack stack) {
        return Component.translatable(getDescriptionId());
    }

    private static void updateTarget(final ItemStack stack, final ServerPlayer target) {
        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(
            Optional.of(GlobalPos.of(target.level().dimension(), target.blockPosition())),
            false
        ));
    }
}
