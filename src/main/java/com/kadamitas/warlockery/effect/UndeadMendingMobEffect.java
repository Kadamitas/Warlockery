package com.kadamitas.warlockery.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public final class UndeadMendingMobEffect extends MobEffect {
    public UndeadMendingMobEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xCD5CAB);
    }

    @Override
    public boolean applyEffectTick(final ServerLevel level, final LivingEntity entity, final int amplifier) {
        final float amount = UndeadMendingRules.healingAmount(
            entity.typeHolder().is(EntityTypeTags.UNDEAD),
            entity.getHealth(),
            entity.getMaxHealth()
        );
        if (amount > 0.0F) {
            entity.heal(amount);
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(final int duration, final int amplifier) {
        return UndeadMendingRules.healsThisTick(duration, amplifier);
    }
}
