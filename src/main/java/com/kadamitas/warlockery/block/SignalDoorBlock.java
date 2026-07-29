package com.kadamitas.warlockery.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.BlockHitResult;

public final class SignalDoorBlock extends DoorBlock {
    public static final MapCodec<SignalDoorBlock> CODEC = simpleCodec(SignalDoorBlock::new);

    public SignalDoorBlock(final BlockBehaviour.Properties properties) {
        super(BlockSetType.OAK, properties);
    }

    @Override
    public MapCodec<? extends SignalDoorBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Player player,
        final BlockHitResult hitResult
    ) {
        final InteractionResult result = super.useWithoutItem(state, level, pos, player, hitResult);
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(state.getValue(HALF).getDirectionToOther().getStepY() > 0 ? pos.above() : pos.below(), this);
        return result;
    }

    @Override
    protected boolean isSignalSource(final BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(
        final BlockState state,
        final BlockGetter level,
        final BlockPos pos,
        final Direction direction
    ) {
        return emittedSignal(state);
    }

    public static int emittedSignal(final BlockState state) {
        return emittedSignal(state.getValue(OPEN));
    }

    public static int emittedSignal(final boolean open) {
        return DoorSignalRules.signalForOpen(open);
    }
}
