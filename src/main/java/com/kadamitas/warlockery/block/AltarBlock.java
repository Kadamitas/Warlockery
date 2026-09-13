package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.item.AttunedStoneItem;
import com.kadamitas.warlockery.registry.ModBlockEntities;
import com.kadamitas.warlockery.registry.ModSounds;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.Map;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

public final class AltarBlock extends BaseEntityBlock {
    public static final MapCodec<AltarBlock> CODEC = simpleCodec(AltarBlock::new);

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    private static final Map<Direction, BooleanProperty> CONNECTIONS = Map.of(
        Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH, Direction.WEST, WEST);

    public AltarBlock(final BlockBehaviour.Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false)
            .setValue(SOUTH, false).setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        return connectedState(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(
        final BlockState state, final LevelReader level, final ScheduledTickAccess ticks,
        final BlockPos pos, final Direction directionToNeighbor, final BlockPos neighborPos,
        final BlockState neighborState, final RandomSource random
    ) {
        return connectedState(state, level, pos);
    }

    public void refreshConnections(final Level level, final BlockPos pos, final BlockState state) {
        final BlockState connected = connectedState(state, level, pos);
        if (connected != state) {
            level.setBlock(pos, connected, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private BlockState connectedState(final BlockState state, final LevelReader level, final BlockPos pos) {
        BlockState connected = state;
        for (final var entry : CONNECTIONS.entrySet()) {
            final BlockPos neighbor = pos.relative(entry.getKey());
            final var chunk = level.getChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4, ChunkStatus.FULL, false);
            if (chunk != null) {
                connected = connected.setValue(entry.getValue(), chunk.getBlockState(neighbor).is(this));
            }
        }
        return connected;
    }

    @Override
    protected BlockState rotate(final BlockState state, final Rotation rotation) {
        BlockState rotated = state;
        for (final var entry : CONNECTIONS.entrySet()) {
            rotated = rotated.setValue(CONNECTIONS.get(rotation.rotate(entry.getKey())), state.getValue(entry.getValue()));
        }
        return rotated;
    }

    @Override
    protected BlockState mirror(final BlockState state, final Mirror mirror) {
        BlockState mirrored = state;
        for (final var entry : CONNECTIONS.entrySet()) {
            mirrored = mirrored.setValue(CONNECTIONS.get(mirror.mirror(entry.getKey())), state.getValue(entry.getValue()));
        }
        return mirrored;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(final BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useItemOn(
        final ItemStack stack,
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Player player,
        final InteractionHand hand,
        final BlockHitResult hitResult
    ) {
        if (stack.getItem() instanceof AttunedStoneItem) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof AltarBlockEntity altar) || !altar.supportsAttachment(stack)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!altar.installAttachment(stack)) {
            player.sendOverlayMessage(Component.translatable("message.warlockery.altar.attachment_occupied"));
            return InteractionResult.FAIL;
        }
        final Component attachmentName = stack.getHoverName();
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        player.sendOverlayMessage(Component.translatable(
            "message.warlockery.altar.attachment_installed",
            attachmentName
        ));
        level.playSound(null, pos, ModSounds.ALTAR_ATTUNE.get(), SoundSource.BLOCKS, 0.55F, 1.15F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new AltarBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Player player,
        final BlockHitResult hitResult
    ) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AltarBlockEntity altar) {
            if (player.isShiftKeyDown() && altar.attachmentCount() > 0) {
                final ItemStack removed = altar.removeLastAttachment();
                if (!player.getInventory().add(removed)) {
                    popResource(level, pos.above(), removed);
                }
                player.sendOverlayMessage(Component.translatable(
                    "message.warlockery.altar.attachment_removed",
                    removed.getHoverName()
                ));
                level.playSound(null, pos, ModSounds.ALTAR_ATTUNE.get(), SoundSource.BLOCKS, 0.45F, 0.8F);
                return InteractionResult.SUCCESS;
            }
            player.sendSystemMessage(Component.translatable(
                altar.isMultiblockValid() ? "message.warlockery.altar.power" : "message.warlockery.altar.incomplete",
                altar.getPower(),
                altar.getCapacity()
            ));
            level.playSound(null, pos, ModSounds.ALTAR_ATTUNE.get(), SoundSource.BLOCKS, 0.4F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(
        final BlockState state,
        final net.minecraft.server.level.ServerLevel level,
        final BlockPos pos,
        final boolean moved
    ) {
        if (level.getBlockEntity(pos) instanceof AltarBlockEntity altar) {
            altar.removeAllAttachments().forEach(stack -> popResource(level, pos, stack));
        }
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
        final Level level,
        final BlockState state,
        final BlockEntityType<T> type
    ) {
        return level.isClientSide()
            ? null
            : createTickerHelper(type, ModBlockEntities.ALTAR.get(), AltarBlockEntity::serverTick);
    }
}
