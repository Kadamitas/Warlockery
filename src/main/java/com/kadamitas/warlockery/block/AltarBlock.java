package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.registry.ModBlockEntities;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
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
import org.jspecify.annotations.Nullable;

public final class AltarBlock extends BaseEntityBlock {
    public static final MapCodec<AltarBlock> CODEC = simpleCodec(AltarBlock::new);

    public AltarBlock(final BlockBehaviour.Properties properties) {
        super(properties);
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
        if (!stack.is(WarlockeryTags.Items.ALTAR_RANGE_FOCI)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof AltarBlockEntity altar) || !altar.installRangeFocus(stack)) {
            player.sendOverlayMessage(Component.translatable("message.warlockery.altar.focus_occupied"));
            return InteractionResult.FAIL;
        }
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        player.sendOverlayMessage(Component.translatable("message.warlockery.altar.focus_installed"));
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
            if (player.isShiftKeyDown() && altar.hasRangeFocus()) {
                final ItemStack removed = altar.removeRangeFocus();
                if (!player.getInventory().add(removed)) {
                    popResource(level, pos.above(), removed);
                }
                player.sendOverlayMessage(Component.translatable("message.warlockery.altar.focus_removed"));
                return InteractionResult.SUCCESS;
            }
            player.sendSystemMessage(Component.translatable(
                altar.isMultiblockValid() ? "message.warlockery.altar.power" : "message.warlockery.altar.incomplete",
                altar.getPower(),
                altar.getCapacity()
            ));
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
        if (level.getBlockEntity(pos) instanceof AltarBlockEntity altar && altar.hasRangeFocus()) {
            popResource(level, pos, altar.removeRangeFocus());
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
