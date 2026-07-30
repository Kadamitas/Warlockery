package com.kadamitas.warlockery.entity;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ImpEntity extends WingedArcaneMob {
    public ImpEntity(final EntityType<? extends Monster> type, final Level level) {
        super(type, level, CreatureKind.IMP);
    }

    @Override
    protected void registerArcaneTargets() {
        targetPlayers();
    }

    @Override
    public void performRangedAttack(final LivingEntity target, final float power) {
        final Vec3 direction = target.getEyePosition().subtract(getEyePosition()).normalize();
        final SmallFireball ember = new SmallFireball(level(), this, direction);
        ember.setPos(getX(), getEyeY() - 0.15, getZ());
        level().addFreshEntity(ember);
        level().playSound(null, blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.8F, 1.3F);
    }
}
