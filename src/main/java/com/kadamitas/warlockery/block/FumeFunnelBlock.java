package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.block.entity.MagicMachineBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class FumeFunnelBlock extends Block {
    public static final MapCodec<FumeFunnelBlock> CODEC = simpleCodec(FumeFunnelBlock::new);

    public FumeFunnelBlock(final BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
        if (!(level.getBlockEntity(pos.below()) instanceof MagicMachineBlockEntity machine)
            || !machine.getBlockState().getValue(MagicMachineBlock.LIT)) {
            return;
        }
        final String id = BuiltInRegistries.BLOCK.getKey(this).getPath();
        final int interval = "filteredfumefunnel".equals(id) ? 7 : 2;
        if (random.nextInt(interval) != 0) {
            return;
        }
        level.addParticle(
            ParticleTypes.SMOKE,
            pos.getX() + 0.5D + random.nextGaussian() * 0.04D,
            pos.getY() + 0.9D,
            pos.getZ() + 0.5D + random.nextGaussian() * 0.04D,
            0,
            "filteredfumefunnel".equals(id) ? 0.025D : 0.055D,
            0
        );
    }
}
