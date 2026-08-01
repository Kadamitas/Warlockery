package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.data.WarlockeryEntityData;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public final class GrassperBlock extends BushBlock {
    public static final BooleanProperty OCCUPIED = BooleanProperty.create("occupied");
    private static final String ANCHOR = "WarlockeryGrassperAnchor";

    public GrassperBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OCCUPIED, false));
    }

    @Override
    protected InteractionResult useItemOn(
        final ItemStack stack,
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Player player,
        final InteractionHand hand,
        final BlockHitResult hitResult
    ) {
        if (state.getValue(OCCUPIED)) {
            return returnStored(level, pos, state, player);
        }
        if (stack.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel serverLevel) {
            final Display.ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(serverLevel, EntitySpawnReason.EVENT);
            if (display == null) {
                player.sendOverlayMessage(Component.translatable("message.warlockery.grassper.cannot_hold"));
                return InteractionResult.FAIL;
            }
            final SlotAccess slot = display.getSlot(0);
            if (!slot.set(stack.copyWithCount(1))) {
                display.discard();
                return InteractionResult.FAIL;
            }
            WarlockeryEntityData.get(display).putLong(ANCHOR, pos.asLong());
            display.snapTo(pos.getX() + 0.5, pos.getY() + 0.65, pos.getZ() + 0.5);
            serverLevel.addFreshEntity(display);
            stack.consume(1, player);
            level.setBlockAndUpdate(pos, state.setValue(OCCUPIED, true));
            player.sendOverlayMessage(Component.translatable("message.warlockery.grassper.holding"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Player player,
        final BlockHitResult hitResult
    ) {
        if (!state.getValue(OCCUPIED)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.warlockery.grassper.missing_item"));
            }
            return InteractionResult.FAIL;
        }
        return returnStored(level, pos, state, player);
    }

    private static InteractionResult returnStored(
        final Level level,
        final BlockPos pos,
        final BlockState state,
        final Player player
    ) {
        if (level instanceof ServerLevel serverLevel) {
            final Optional<ItemStack> stack = takeStoredItem(serverLevel, pos);
            if (stack.isEmpty()) {
                level.setBlockAndUpdate(pos, state.setValue(OCCUPIED, false));
                player.sendOverlayMessage(Component.translatable("message.warlockery.grassper.stored_item_missing"));
                return InteractionResult.FAIL;
            }
            if (!player.getInventory().add(stack.orElseThrow())) {
                Block.popResource(level, pos.above(), stack.orElseThrow());
            }
            player.sendOverlayMessage(Component.translatable("message.warlockery.grassper.returned"));
        }
        return InteractionResult.SUCCESS;
    }

    public static Optional<ItemStack> storedItem(final ServerLevel level, final BlockPos pos) {
        return storedDisplay(level, pos)
            .map(display -> display.getSlot(0).get())
            .filter(stack -> !stack.isEmpty())
            .map(ItemStack::copy);
    }

    public static Optional<ItemStack> takeStoredItem(final ServerLevel level, final BlockPos pos) {
        final Optional<Display.ItemDisplay> display = storedDisplay(level, pos);
        if (display.isEmpty()) {
            return Optional.empty();
        }
        final ItemStack stored = display.orElseThrow().getSlot(0).get().copy();
        display.orElseThrow().discard();
        final BlockState state = level.getBlockState(pos);
        if (state.hasProperty(OCCUPIED)) {
            level.setBlockAndUpdate(pos, state.setValue(OCCUPIED, false));
        }
        return stored.isEmpty() ? Optional.empty() : Optional.of(stored);
    }

    @Override
    public void playerDestroy(
        final Level level,
        final Player player,
        final BlockPos pos,
        final BlockState state,
        final @Nullable BlockEntity blockEntity,
        final ItemStack destroyedWith
    ) {
        if (level instanceof ServerLevel serverLevel) {
            storedDisplay(serverLevel, pos).ifPresent(display -> {
                Block.popResource(level, pos, display.getSlot(0).get());
                display.discard();
            });
        }
        super.playerDestroy(level, player, pos, state, blockEntity, destroyedWith);
    }

    private static Optional<Display.ItemDisplay> storedDisplay(final ServerLevel level, final BlockPos pos) {
        return level.getEntitiesOfClass(
            Display.ItemDisplay.class,
            new AABB(pos).inflate(1.0),
            display -> WarlockeryEntityData.get(display).getLongOr(ANCHOR, Long.MIN_VALUE) == pos.asLong()
        ).stream().findFirst();
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OCCUPIED);
    }
}
