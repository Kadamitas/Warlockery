package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.block.entity.DollShelfBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public final class DollShelfBlock extends BaseEntityBlock {
    public static final MapCodec<DollShelfBlock> CODEC = simpleCodec(DollShelfBlock::new);

    public DollShelfBlock(final BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos position, final BlockState state) {
        return new DollShelfBlockEntity(position, state);
    }

    @Override
    protected RenderShape getRenderShape(final BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(
        final BlockState state,
        final Level level,
        final BlockPos position,
        final Player player,
        final BlockHitResult hit
    ) {
        if (!level.isClientSide() && level.getBlockEntity(position) instanceof DollShelfBlockEntity shelf) {
            player.openMenu(shelf);
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
        if (level.getBlockEntity(pos) instanceof DollShelfBlockEntity shelf) {
            net.minecraft.world.Containers.dropContents(level, pos, shelf);
        }
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }
}
