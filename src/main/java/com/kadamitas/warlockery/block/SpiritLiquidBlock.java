package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.dream.SpiritWorldRuntime;
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

public final class SpiritLiquidBlock extends LiquidBlock {
    public SpiritLiquidBlock(final Supplier<? extends FlowingFluid> fluid, final BlockBehaviour.Properties properties) {
        super(fluid, properties);
    }

    @Override
    protected void onPlace(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final BlockState oldState,
        final boolean movedByPiston
    ) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && SpiritWorldRuntime.isSpiritWorld(level)) {
            SpiritPortalStructure.find(level, pos).ifPresent(layout -> layout.interior().forEach(interior ->
                level.setBlockAndUpdate(interior, ModBlocks.ALL.get("spiritportal").get().defaultBlockState())
            ));
        }
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
        if (!level.isClientSide() && entity instanceof LivingEntity living && living.tickCount % 20 == 0) {
            final ArcaneFluidRules.Outcome outcome = ArcaneFluidRules.flowingSpiritOutcome(
                true,
                living.typeHolder().is(WarlockeryTags.EntityTypes.NIGHTMARES),
                living.typeHolder().is(WarlockeryTags.EntityTypes.HOLLOW_TEARS_BENEFICIARIES)
            );
            if (outcome == ArcaneFluidRules.Outcome.HARM) {
                living.invulnerableTime = 0;
                living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1, true, true));
            } else if (outcome == ArcaneFluidRules.Outcome.BENEFIT) {
                living.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, true));
            }
        }
    }
}
