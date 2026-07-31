package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;

public final class ShadedGlassBlock extends TransparentBlock {
    private final boolean active;

    public ShadedGlassBlock(final BlockBehaviour.Properties properties, final boolean active) {
        super(properties);
        this.active = active;
    }

    @Override
    protected void onPlace(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final BlockState oldState,
        final boolean movedByPiston
    ) {
        syncPower(level, pos);
    }

    @Override
    protected void neighborChanged(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Block neighbor,
        final Orientation orientation,
        final boolean movedByPiston
    ) {
        syncPower(level, pos);
    }

    @Override
    protected boolean propagatesSkylightDown(final BlockState state) {
        return !active;
    }

    @Override
    protected int getLightDampening(final BlockState state) {
        return active ? 15 : 0;
    }

    private void syncPower(final Level level, final BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        final boolean powered = level.hasNeighborSignal(pos);
        if (powered != active) {
            level.setBlockAndUpdate(pos, ModBlocks.ALL.get(
                powered ? "shadedglass_active" : "shadedglass"
            ).get().defaultBlockState());
        }
    }
}
