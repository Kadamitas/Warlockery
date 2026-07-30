package com.kadamitas.warlockery.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ConnectedGlyphBlock extends Block {
    public static final MapCodec<ConnectedGlyphBlock> CODEC = simpleCodec(ConnectedGlyphBlock::new);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final Map<Direction, BooleanProperty> CONNECTIONS = Map.of(
        Direction.NORTH, NORTH,
        Direction.EAST, EAST,
        Direction.SOUTH, SOUTH,
        Direction.WEST, WEST
    );
    public static final Set<String> IDS = ConnectedGlyphGeometry.IDS;

    private static final VoxelShape CENTER = voxelShape(ConnectedGlyphGeometry.CENTER);
    private static final Map<Direction, VoxelShape> ARMS = Map.of(
        Direction.NORTH, voxelShape(ConnectedGlyphGeometry.ARMS.get(ConnectedGlyphGeometry.Side.NORTH)),
        Direction.EAST, voxelShape(ConnectedGlyphGeometry.ARMS.get(ConnectedGlyphGeometry.Side.EAST)),
        Direction.SOUTH, voxelShape(ConnectedGlyphGeometry.ARMS.get(ConnectedGlyphGeometry.Side.SOUTH)),
        Direction.WEST, voxelShape(ConnectedGlyphGeometry.ARMS.get(ConnectedGlyphGeometry.Side.WEST))
    );

    private final Function<BlockState, VoxelShape> shapes;

    public ConnectedGlyphBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(NORTH, false)
            .setValue(EAST, false)
            .setValue(SOUTH, false)
            .setValue(WEST, false));
        shapes = getShapeForEachState(ConnectedGlyphBlock::shapeForState);
    }

    @Override
    public MapCodec<ConnectedGlyphBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        return connectedState(context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(
        final BlockState state,
        final LevelReader level,
        final ScheduledTickAccess ticks,
        final BlockPos pos,
        final Direction directionToNeighbor,
        final BlockPos neighborPos,
        final BlockState neighborState,
        final RandomSource random
    ) {
        if (directionToNeighbor == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        final BooleanProperty connection = CONNECTIONS.get(directionToNeighbor);
        return connection == null ? state : state.setValue(connection, connectsTo(neighborState));
    }

    @Override
    protected boolean canSurvive(final BlockState state, final LevelReader level, final BlockPos pos) {
        final BlockPos supportPos = pos.below();
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, Direction.UP);
    }

    @Override
    protected VoxelShape getShape(
        final BlockState state,
        final BlockGetter level,
        final BlockPos pos,
        final CollisionContext context
    ) {
        return shapes.apply(state);
    }

    @Override
    protected BlockState rotate(final BlockState state, final Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_180 -> state.setValue(NORTH, state.getValue(SOUTH))
                .setValue(EAST, state.getValue(WEST))
                .setValue(SOUTH, state.getValue(NORTH))
                .setValue(WEST, state.getValue(EAST));
            case COUNTERCLOCKWISE_90 -> state.setValue(NORTH, state.getValue(EAST))
                .setValue(EAST, state.getValue(SOUTH))
                .setValue(SOUTH, state.getValue(WEST))
                .setValue(WEST, state.getValue(NORTH));
            case CLOCKWISE_90 -> state.setValue(NORTH, state.getValue(WEST))
                .setValue(EAST, state.getValue(NORTH))
                .setValue(SOUTH, state.getValue(EAST))
                .setValue(WEST, state.getValue(SOUTH));
            default -> state;
        };
    }

    @Override
    protected BlockState mirror(final BlockState state, final Mirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT -> state.setValue(NORTH, state.getValue(SOUTH)).setValue(SOUTH, state.getValue(NORTH));
            case FRONT_BACK -> state.setValue(EAST, state.getValue(WEST)).setValue(WEST, state.getValue(EAST));
            default -> state;
        };
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    public static boolean supports(final String id) {
        return IDS.contains(id);
    }

    public static boolean connectsTo(final BlockState state) {
        return state.getBlock() instanceof ConnectedGlyphBlock;
    }

    public BlockState connectedState(final BlockGetter level, final BlockPos pos) {
        return connectToNeighbors(defaultBlockState(), level, pos);
    }

    public static VoxelShape shapeForState(final BlockState state) {
        return CONNECTIONS.entrySet().stream()
            .filter(entry -> state.getValue(entry.getValue()))
            .map(entry -> ARMS.get(entry.getKey()))
            .reduce(CENTER, Shapes::or);
    }

    private static BlockState connectToNeighbors(final BlockState state, final BlockGetter level, final BlockPos pos) {
        BlockState connected = state;
        for (final Map.Entry<Direction, BooleanProperty> entry : CONNECTIONS.entrySet()) {
            connected = connected.setValue(entry.getValue(), connectsTo(level.getBlockState(pos.relative(entry.getKey()))));
        }
        return connected;
    }

    private static VoxelShape voxelShape(final ConnectedGlyphGeometry.Bounds bounds) {
        return Block.box(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }
}
