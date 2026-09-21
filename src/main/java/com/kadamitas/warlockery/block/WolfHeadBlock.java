package com.kadamitas.warlockery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class WolfHeadBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(4.0, 2.0, 4.0, 12.0, 13.0, 15.0),
        Block.box(2.0, 10.0, 7.0, 6.0, 16.0, 14.0),
        Block.box(10.0, 10.0, 7.0, 14.0, 16.0, 14.0),
        Block.box(5.0, 1.0, 0.0, 11.0, 8.0, 8.0)
    );

    public WolfHeadBlock(final BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(
        final BlockState state,
        final BlockGetter level,
        final BlockPos pos,
        final CollisionContext context
    ) {
        return SHAPE;
    }
}
