package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class WarlockeryCropBlock extends CropBlock {
    public WarlockeryCropBlock(final BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ModItems.seedFor(this).orElseGet(super::getBaseSeedId);
    }

    @Override
    public void playerDestroy(
        final Level level,
        final Player player,
        final BlockPos pos,
        final BlockState state,
        final @Nullable BlockEntity blockEntity,
        final ItemStack destroyedWith
    ) {
        if (!isMatureMandrake(state) || !(level instanceof ServerLevel serverLevel)) {
            super.playerDestroy(level, player, pos, state, blockEntity, destroyedWith);
            return;
        }
        if (!MandrakeHarvestRules.awakens(serverLevel.isDarkOutside(), serverLevel.getRandom().nextFloat())) {
            super.playerDestroy(level, player, pos, state, blockEntity, destroyedWith);
            return;
        }
        player.causeFoodExhaustion(0.005F);
        final var mandrake = ModEntities.ALL.get("mandrake").get().create(serverLevel, EntitySpawnReason.EVENT);
        if (mandrake != null) {
            mandrake.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            serverLevel.addFreshEntity(mandrake);
        }
    }

    static boolean isMatureMandrake(final BlockState state) {
        return state.getValue(AGE) == MAX_AGE
            && "mandrake".equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
    }
}
