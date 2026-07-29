package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.kadamitas.warlockery.item.RowanKeyItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
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
    protected InteractionResult useItemOn(
        final ItemStack itemStack,
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Player player,
        final InteractionHand hand,
        final BlockHitResult hitResult
    ) {
        if (itemStack.getItem() instanceof RowanKeyItem key) {
            final BlockPos doorPos = state.getValue(HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
                ? pos.below()
                : pos;
            final InteractionResult keyResult = key.interactDoor(itemStack, level, doorPos, player);
            return keyResult == InteractionResult.SUCCESS
                ? super.useWithoutItem(state, level, pos, player, hitResult)
                : keyResult;
        }
        if (itemStack.is(WarlockeryTags.Items.ROWAN_DOOR_KEYS)) {
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
        return locked(player, level);
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
    }

    private static InteractionResult locked(final Player player, final Level level) {
        if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable("message.warlockery.rowan_door.locked"));
        }
        return InteractionResult.SUCCESS;
    }
}
