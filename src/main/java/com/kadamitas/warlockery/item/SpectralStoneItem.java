package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class SpectralStoneItem extends Item {
    public SpectralStoneItem(final Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult interactLivingEntity(
        final ItemStack stack,
        final Player player,
        final LivingEntity target,
        final InteractionHand hand
    ) {
        final SpectralStoneState state = SpectralStoneState.read(stack);
        final UtilityDecision decision = NecromancyRules.spectralStone(
            target.typeHolder().is(WarlockeryTags.EntityTypes.SPECTRAL),
            state.captured().size(),
            SpectralStoneState.CAPACITY
        );
        if (!decision.success()) {
            show(player, decision);
            return InteractionResult.FAIL;
        }
        final var targetType = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        if (!state.canCapture(targetType)) {
            show(player, target.getDisplayName(), false);
            return InteractionResult.FAIL;
        }
        if (!player.level().isClientSide()) {
            state.with(targetType).write(stack);
            target.discard();
            show(player, decision);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        final SpectralStoneState state = SpectralStoneState.read(stack);
        if (state.captured().isEmpty()) {
            show(player, UtilityDecision.failure("empty"));
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel serverLevel) {
            final var spawn = player.blockPosition().relative(player.getDirection(), 2).above();
            final var entity = BuiltInRegistries.ENTITY_TYPE.getValue(state.captured().getFirst())
                .create(serverLevel, EntitySpawnReason.EVENT);
            if (entity == null) {
                show(player, UtilityDecision.failure("invalid_capture"));
                return InteractionResult.FAIL;
            }
            entity.snapTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
            if (entity instanceof Mob mob) {
                CreatureBehaviorState.bind(mob, player.getUUID());
                mob.setTarget(null);
                mob.setPersistenceRequired();
            }
            serverLevel.addFreshEntity(entity);
            state.withoutFirst().write(stack);
            show(player, UtilityDecision.success("released"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(final ItemStack stack) {
        return !SpectralStoneState.read(stack).captured().isEmpty() || super.isFoil(stack);
    }

    private static void show(final Player player, final UtilityDecision decision) {
        if (!player.level().isClientSide()) {
            player.sendOverlayMessage(Component.translatable(decision.messageKey("spectral_stone"))
                .withStyle(decision.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }

    private static void show(final Player player, final Component subject, final boolean success) {
        if (!player.level().isClientSide()) {
            player.sendOverlayMessage(Component.empty()
                .append(success ? Component.literal("✓ ") : Component.literal("✗ "))
                .append(subject)
                .withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }
}
