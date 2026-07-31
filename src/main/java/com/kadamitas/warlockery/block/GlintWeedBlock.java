package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.block.MagicalPlantBlockFactory.Behavior;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class GlintWeedBlock extends MagicalPlantBlock {
    public static final BooleanProperty HANGING = BooleanProperty.create("hanging");
    public static final MapCodec<BushBlock> CODEC = simpleCodec(GlintWeedBlock::new);

    public GlintWeedBlock(final BlockBehaviour.Properties properties) {
        super(Behavior.GLINT_WEED, properties);
        registerDefaultState(stateDefinition.any().setValue(HANGING, false));
    }

    @Override
    public MapCodec<BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HANGING);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        final BlockPos position = context.getClickedPos();
        final LevelReader level = context.getLevel();
        final boolean floorSupported = supported(level, position.below(), Direction.UP);
        final boolean ceilingSupported = supported(level, position.above(), Direction.DOWN);
        if (GlintWeedPlacementRules.usesCeiling(context.getClickedFace(), floorSupported, ceilingSupported)) {
            return defaultBlockState().setValue(HANGING, true);
        }
        if (floorSupported) {
            return defaultBlockState();
        }
        return null;
    }

    @Override
    protected boolean canSurvive(final BlockState state, final LevelReader level, final BlockPos position) {
        final boolean hanging = state.getValue(HANGING);
        final Direction supportFace = hanging ? Direction.DOWN : Direction.UP;
        final BlockPos support = hanging ? position.above() : position.below();
        return supported(level, support, supportFace);
    }

    private static boolean supported(final LevelReader level, final BlockPos support, final Direction face) {
        return level.getBlockState(support).isFaceSturdy(level, support, face);
    }
}
