package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.brew.BrewMarkerKind;
import com.kadamitas.warlockery.brew.BrewMarkerState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

public final class ErosionBrewLiquidBlock extends LiquidBlock {
    public ErosionBrewLiquidBlock(
        final FlowingFluid fluid,
        final BlockBehaviour.Properties properties
    ) {
        super(fluid, properties);
    }

    @Override
    protected void entityInside(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Entity entity,
        final InsideBlockEffectApplier effectApplier,
        final boolean isPrecise
    ) {
        if (!(level instanceof ServerLevel serverLevel)
            || !(entity instanceof LivingEntity living)
            || living.tickCount % 20 != 0) {
            return;
        }
        BrewMarkerState.apply(living, BrewMarkerKind.EROSION, 80);
        living.hurtServer(serverLevel, living.damageSources().magic(), 2.0F);
        EquipmentSlot.VALUES.stream()
            .map(slot -> java.util.Map.entry(slot, living.getItemBySlot(slot)))
            .filter(entry -> entry.getValue().isDamageableItem())
            .forEach(entry -> entry.getValue().hurtAndBreak(1, living, entry.getKey()));
    }
}
