package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.block.ConnectedGlyphGeometry.Side;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ConnectedGlyphBlock extends Block {
    public static final MapCodec<ConnectedGlyphBlock> CODEC = simpleCodec(ConnectedGlyphBlock::new);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty NORTH_EAST = BooleanProperty.create("north_east");
    public static final BooleanProperty SOUTH_EAST = BooleanProperty.create("south_east");
    public static final BooleanProperty SOUTH_WEST = BooleanProperty.create("south_west");
    public static final BooleanProperty NORTH_WEST = BooleanProperty.create("north_west");
    public static final Map<Direction, BooleanProperty> CONNECTIONS = Map.of(
        Direction.NORTH, NORTH,
        Direction.EAST, EAST,
        Direction.SOUTH, SOUTH,
        Direction.WEST, WEST
    );
    public static final Set<String> IDS = ConnectedGlyphGeometry.IDS;
    public static final Map<Side, BooleanProperty> ALL_CONNECTIONS = Map.of(
        Side.NORTH, NORTH, Side.NORTH_EAST, NORTH_EAST, Side.EAST, EAST, Side.SOUTH_EAST, SOUTH_EAST,
        Side.SOUTH, SOUTH, Side.SOUTH_WEST, SOUTH_WEST, Side.WEST, WEST, Side.NORTH_WEST, NORTH_WEST
    );

    private static final VoxelShape CENTER = voxelShape(ConnectedGlyphGeometry.CENTER);

    private final Function<BlockState, VoxelShape> shapes;

    public ConnectedGlyphBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(NORTH, false)
            .setValue(EAST, false)
            .setValue(SOUTH, false)
            .setValue(WEST, false)
            .setValue(NORTH_EAST, false)
            .setValue(SOUTH_EAST, false)
            .setValue(SOUTH_WEST, false)
            .setValue(NORTH_WEST, false));
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
        return connectToNeighbors(state, level, pos);
    }

    @Override
    protected void updateIndirectNeighbourShapes(
        final BlockState state, final LevelAccessor level, final BlockPos pos, final int updateFlags, final int updateLimit
    ) {
        refreshConnections(level, pos, updateFlags, updateLimit);
        for (final Side side : ConnectedGlyphGeometry.DIAGONALS.keySet()) {
            refreshConnections(level, pos.offset(side.dx(), 0, side.dz()), updateFlags, updateLimit);
        }
    }

    private static void refreshConnections(
        final LevelAccessor level, final BlockPos pos, final int updateFlags, final int updateLimit
    ) {
        final BlockState current = loadedBlockState(level, pos);
        if (current != null && connectsTo(current)) {
            final BlockState connected = connectToNeighbors(current, level, pos);
            if (current != connected) {
                // Connection bits cannot change another glyph's presence; do not cascade shape updates.
                level.setBlock(pos, connected, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE, updateLimit);
            }
        }
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
        final int turns = switch (rotation) {
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
            default -> 0;
        };
        return transform(state, side -> side.rotateQuarterTurns(turns));
    }

    @Override
    protected BlockState mirror(final BlockState state, final Mirror mirror) {
        return transform(state, side -> switch (mirror) {
            case LEFT_RIGHT -> side.mirrorZ();
            case FRONT_BACK -> side.mirrorX();
            default -> side;
        });
    }

    private static BlockState transform(final BlockState state, final Function<Side, Side> transformation) {
        BlockState result = state;
        for (final Side side : Side.values()) {
            result = result.setValue(ALL_CONNECTIONS.get(transformation.apply(side)), state.getValue(ALL_CONNECTIONS.get(side)));
        }
        return result;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, NORTH_EAST, SOUTH_EAST, SOUTH_WEST, NORTH_WEST);
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
        return ALL_CONNECTIONS.entrySet().stream()
            .filter(entry -> state.getValue(entry.getValue()))
            .flatMap(entry -> ConnectedGlyphGeometry.parts(entry.getKey()).stream())
            .map(ConnectedGlyphBlock::voxelShape)
            .reduce(CENTER, Shapes::or);
    }

    private static BlockState connectToNeighbors(final BlockState state, final BlockGetter level, final BlockPos pos) {
        BlockState connected = state;
        for (final Side side : Side.values()) {
            final BlockState neighbor = loadedBlockState(level, pos.offset(side.dx(), 0, side.dz()));
            if (neighbor != null) {
                connected = connected.setValue(ALL_CONNECTIONS.get(side), connectsTo(neighbor));
            }
        }
        return connected;
    }

    private static BlockState loadedBlockState(final BlockGetter level, final BlockPos pos) {
        if (level instanceof Level world) {
            final ChunkAccess chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
            return chunk == null ? null : chunk.getBlockState(pos);
        }
        return level.getBlockState(pos);
    }

    private static VoxelShape voxelShape(final ConnectedGlyphGeometry.Bounds bounds) {
        return Block.box(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }
}
