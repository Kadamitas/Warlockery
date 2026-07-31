package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class StockadeBlock extends FenceBlock {
    public StockadeBlock(final BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void entityInside(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Entity entity,
        final InsideBlockEffectApplier effectApplier,
        final boolean precise
    ) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof LivingEntity living)) {
            return;
        }
        final boolean spectator = entity instanceof Player player && player.isSpectator();
        if (StockadeRules.shouldImpale(
            true,
            living.isAlive(),
            spectator,
            living.typeHolder().is(WarlockeryTags.EntityTypes.BEARTRAP_IMMUNE),
            living.fallDistance
        )) {
            living.hurtServer(serverLevel, serverLevel.damageSources().cactus(), StockadeRules.damage(living.fallDistance));
            living.fallDistance = 0.0F;
        }
    }
}
