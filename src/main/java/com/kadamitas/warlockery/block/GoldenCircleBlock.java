package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.block.entity.CircleHeartBlockEntity;
import com.kadamitas.warlockery.item.ManualItem;
import com.kadamitas.warlockery.item.RitualBookAccess;
import com.kadamitas.warlockery.network.ModNetwork;
import com.kadamitas.warlockery.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public final class GoldenCircleBlock extends ConnectedGlyphBlock implements EntityBlock {
    public GoldenCircleBlock(final Properties properties) { super(properties); }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new CircleHeartBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
        final Level level, final BlockState state, final BlockEntityType<T> type) {
        return !level.isClientSide() && type == ModBlockEntities.CIRCLE_HEART.get()
            ? (world, pos, block, entity) -> CircleHeartBlockEntity.serverTick(world, pos, (CircleHeartBlockEntity) entity)
            : null;
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
        final Player player, final BlockHitResult hit) {
        final double x = hit.getLocation().x - pos.getX();
        final double z = hit.getLocation().z - pos.getZ();
        if (x >= 0.375 && x <= 0.625 && z >= 0.375 && z <= 0.625
            && player.getMainHandItem().isEmpty()
            && !(player.getOffhandItem().getItem() instanceof ManualItem manual
                && RitualBookAccess.BOOK.equals(manual.profile().id()))) {
            if (player instanceof ServerPlayer serverPlayer) ModNetwork.openRitualScreen(serverPlayer, pos);
            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }
}
