package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.item.RowanKeyItem;
import com.kadamitas.warlockery.item.RowanKeyState;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public final class RunedDoorBlock extends DoorBlock {
    public static final MapCodec<RunedDoorBlock> CODEC = simpleCodec(RunedDoorBlock::new);

    public RunedDoorBlock(final BlockBehaviour.Properties properties) {
        super(BlockSetType.OAK, properties);
    }

    @Override
    public MapCodec<? extends RunedDoorBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
        final BlockState placed = super.getStateForPlacement(context);
        return placed == null ? null : placed.setValue(OPEN, false).setValue(POWERED, false);
    }

    @Override
    public void setPlacedBy(
        final Level level,
        final BlockPos pos,
        final BlockState state,
        final @Nullable LivingEntity placer,
        final ItemStack placedStack
    ) {
        super.setPlacedBy(level, pos, state, placer, placedStack);
        if (level.isClientSide() || !(placer instanceof Player player)) {
            return;
        }
        final ItemStack key = new ItemStack(ModItems.ALL.get("ingredient_door_key").get());
        new RowanKeyState(List.of(door(level, pos))).write(key);
        if (!player.getInventory().add(key)) {
            Block.popResource(level, pos, key);
        }
    }

    @Override
    protected InteractionResult useItemOn(
        final ItemStack itemStack,
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Player player,
        final InteractionHand hand,
        final BlockHitResult hitResult
    ) {
        final BlockPos doorPos = lower(state, pos);
        if (itemStack.getItem() instanceof RowanKeyItem key) {
            if (key.opens(itemStack, level, doorPos) || mayOpen(player, level, doorPos)) {
                return super.useWithoutItem(state, level, pos, player, hitResult);
            }
            return key.isKeyring() ? locked(player, level) : key.interactDoor(itemStack, level, doorPos, player);
        }
        if (itemStack.is(WarlockeryTags.Items.ROWAN_DOOR_KEYS) || mayOpen(player, level, doorPos)) {
            return super.useWithoutItem(state, level, pos, player, hitResult);
        }
        return locked(player, level);
    }

    @Override
    protected InteractionResult useWithoutItem(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Player player,
        final BlockHitResult hitResult
    ) {
        return mayOpen(player, level, lower(state, pos))
            ? super.useWithoutItem(state, level, pos, player, hitResult)
            : locked(player, level);
    }

    @Override
    protected void neighborChanged(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Block block,
        final @Nullable Orientation orientation,
        final boolean movedByPiston
    ) {
        final boolean signal = level.hasNeighborSignal(pos)
            || level.hasNeighborSignal(pos.relative(state.getValue(HALF).getDirectionToOther()));
        BlockState current = state;
        if (signal && state.getValue(OPEN)) {
            setOpen(null, level, state, pos, false);
            current = level.getBlockState(pos);
        }
        if (current.is(this) && current.getValue(POWERED) != signal) {
            level.setBlock(pos, current.setValue(POWERED, signal), 2);
        }
    }

    @Override
    protected boolean canSurvive(final BlockState state, final LevelReader level, final BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER || level.getBlockState(pos.below()).is(this);
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
        if (mayOpen(player, level, lower(state, pos))) {
            super.playerDestroy(level, player, pos, state, blockEntity, destroyedWith);
            return;
        }
        if (!level.isClientSide()) {
            Block.popResource(level, pos, new ItemStack(Items.STICK, 2));
        }
    }

    private static boolean mayOpen(final Player player, final Level level, final BlockPos doorPos) {
        return player.getInventory().contains(stack ->
            stack.getItem() instanceof RowanKeyItem key && key.opens(stack, level, doorPos)
                || !(stack.getItem() instanceof RowanKeyItem) && stack.is(WarlockeryTags.Items.ROWAN_DOOR_KEYS)
        );
    }

    private static RowanKeyState.Door door(final Level level, final BlockPos pos) {
        return new RowanKeyState.Door(level.dimension().identifier(), pos.immutable());
    }

    private static BlockPos lower(final BlockState state, final BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    private static InteractionResult locked(final Player player, final Level level) {
        if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable("message.warlockery.rowan_door.locked"));
        }
        return InteractionResult.SUCCESS;
    }
}
