package com.kadamitas.warlockery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** Joined rendering only; portal interactions and contact travel remain inherited. */
public final class SpiritPortalBlock extends InteractiveUtilityBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    public SpiritPortalBlock(final BlockBehaviour.Properties properties) {
        super(properties, UtilityDeviceProfile.SPIRIT_PORTAL);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X)
            .setValue(LEFT, false).setValue(RIGHT, false).setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, LEFT, RIGHT, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        return connectedState(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected void onPlace(final BlockState state, final Level level, final BlockPos pos,
                           final BlockState oldState, final boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !oldState.is(this)) {
            final BlockState joined = connectedState(state, level, pos);
            if (joined != state) level.setBlock(pos, joined, Block.UPDATE_ALL);
        }
    }

    @Override
    protected BlockState updateShape(final BlockState state, final LevelReader level,
        final ScheduledTickAccess ticks, final BlockPos pos, final Direction direction,
        final BlockPos neighborPos, final BlockState neighborState, final RandomSource random) {
        return connectedState(state, level, pos);
    }

    private BlockState connectedState(final BlockState state, final LevelReader level, final BlockPos pos) {
        Direction.Axis axis = state.getValue(AXIS);
        // Runtime-created blocks use default states: detect either crafted frame orientation from its neighbors.
        if (portal(level, pos.east()) || portal(level, pos.west())) axis = Direction.Axis.X;
        else if (portal(level, pos.north()) || portal(level, pos.south())) axis = Direction.Axis.Z;
        else if (snow(level, pos.east()) || snow(level, pos.west())) axis = Direction.Axis.X;
        else if (snow(level, pos.north()) || snow(level, pos.south())) axis = Direction.Axis.Z;
        else if (portal(level, pos.below())) axis = level.getBlockState(pos.below()).getValue(AXIS);
        else if (portal(level, pos.above())) axis = level.getBlockState(pos.above()).getValue(AXIS);
        final Direction right = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        return state.setValue(AXIS, axis)
            .setValue(LEFT, portal(level, pos.relative(right.getOpposite())))
            .setValue(RIGHT, portal(level, pos.relative(right)))
            .setValue(UP, portal(level, pos.above()))
            .setValue(DOWN, portal(level, pos.below()));
    }

    private boolean portal(final LevelReader level, final BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).is(this);
    }

    private static boolean snow(final LevelReader level, final BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.SNOW_BLOCK);
    }
}
