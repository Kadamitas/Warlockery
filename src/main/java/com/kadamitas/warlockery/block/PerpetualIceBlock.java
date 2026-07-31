package com.kadamitas.warlockery.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class PerpetualIceBlock extends IceBlock {
    public static final MapCodec<PerpetualIceBlock> CODEC = simpleCodec(PerpetualIceBlock::new);

    public PerpetualIceBlock(final BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends PerpetualIceBlock> codec() {
        return CODEC;
    }

    @Override
    protected void randomTick(
        final BlockState state,
        final ServerLevel level,
        final BlockPos pos,
        final RandomSource random
    ) {
    }
}
