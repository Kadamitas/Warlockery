package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

public final class HollowTearsLiquidBlock extends LiquidBlock {
    public HollowTearsLiquidBlock(
        final Supplier<? extends FlowingFluid> fluid,
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
        if (level.isClientSide() || !(entity instanceof LivingEntity living) || living.tickCount % 20 != 0) {
            return;
        }
        final ArcaneFluidRules.Outcome outcome = ArcaneFluidRules.hollowTearsOutcome(
            true,
            living.typeHolder().is(WarlockeryTags.EntityTypes.HOLLOW_TEARS_BENEFICIARIES),
            living.typeHolder().is(WarlockeryTags.EntityTypes.HOLLOW_TEARS_VICTIMS)
        );
        if (outcome == ArcaneFluidRules.Outcome.BENEFIT) {
            living.heal(2.0F);
            living.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 0, true, true));
        } else if (outcome == ArcaneFluidRules.Outcome.HARM) {
            living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1, true, true));
            living.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 80, 0, true, true));
        }
    }
}
