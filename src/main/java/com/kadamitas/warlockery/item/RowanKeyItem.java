package com.kadamitas.warlockery.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;

public final class RowanKeyItem extends Item {
    public static final int UNLIMITED_CAPACITY = Integer.MAX_VALUE;

    private final int capacity;

    public RowanKeyItem(final Properties properties, final int capacity) {
        super(properties.stacksTo(1));
        this.capacity = capacity;
    }

    public boolean isKeyring() {
        return capacity == UNLIMITED_CAPACITY;
    }

    public InteractionResult interactDoor(
        final ItemStack stack,
        final Level level,
        final BlockPos position,
        final Player player
    ) {
        final RowanKeyState.Door door = new RowanKeyState.Door(level.dimension().identifier(), lower(position));
        final RowanKeyState state = RowanKeyState.read(stack);
        if (state.opens(door)) {
            show(player, UtilityDecision.success("unlocked"));
            return InteractionResult.SUCCESS;
        }
        if (state.doors().size() >= capacity) {
            show(player, UtilityDecision.failure(capacity <= 1 ? "already_bound" : "keyring_full"));
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            state.bind(door, capacity).write(stack);
            show(player, UtilityDecision.success("bound"));
        }
        return InteractionResult.CONSUME;
    }

    public boolean opens(final ItemStack stack, final Level level, final BlockPos position) {
        return RowanKeyState.read(stack).opens(new RowanKeyState.Door(level.dimension().identifier(), lower(position)));
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack ring = player.getItemInHand(hand);
        final ItemStack source = player.getItemInHand(hand == InteractionHand.MAIN_HAND
            ? InteractionHand.OFF_HAND
            : InteractionHand.MAIN_HAND);
        if (capacity <= 1 || !(source.getItem() instanceof RowanKeyItem) || RowanKeyState.read(source).doors().isEmpty()) {
            show(player, UtilityDecision.failure(capacity <= 1 ? "requires_keyring" : "missing_bound_key"));
            return InteractionResult.FAIL;
        }
        final RowanKeyState current = RowanKeyState.read(ring);
        final RowanKeyState merged = current.merge(RowanKeyState.read(source), capacity);
        if (merged.equals(current)) {
            show(player, UtilityDecision.failure("already_bound"));
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            merged.write(ring);
            if (!player.hasInfiniteMaterials()) {
                source.shrink(1);
            }
            show(player, UtilityDecision.success("keys_combined"));
        }
        return InteractionResult.SUCCESS;
    }

    private static BlockPos lower(final BlockPos position) {
        return position.immutable();
    }

    private static void show(final Player player, final UtilityDecision decision) {
        if (!player.level().isClientSide()) {
            player.sendOverlayMessage(Component.translatable(decision.messageKey("rowan_key"))
                .withStyle(decision.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }
}
