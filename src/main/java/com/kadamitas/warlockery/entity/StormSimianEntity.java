package com.kadamitas.warlockery.entity;

import java.util.Comparator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class StormSimianEntity extends WingedArcaneMob {
    public StormSimianEntity(final EntityType<? extends Monster> type, final Level level) {
        super(type, level, CreatureKind.STORM_SIMIAN);
        setCanPickUpLoot(true);
    }

    @Override
    protected void registerArcaneTargets() {
        targetHostileMobs();
    }

    @Override
    protected void customWingedAiStep(final ServerLevel level) {
        if (getTarget() != null || tickCount % 10 != 0) {
            return;
        }
        level.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(10.0), ItemEntity::isAlive)
            .stream()
            .min(Comparator.comparingDouble(this::distanceToSqr))
            .ifPresent(item -> getNavigation().moveTo(item, 1.15));
    }

    @Override
    public void performRangedAttack(final LivingEntity target, final float power) {
        final Vec3 origin = getEyePosition();
        final Vec3 direction = target.getEyePosition().subtract(origin).normalize();
        final WindCharge gust = new WindCharge(level(), origin.x, origin.y, origin.z, direction);
        gust.setOwner(this);
        level().addFreshEntity(gust);
        level().playSound(null, blockPosition(), SoundEvents.WIND_CHARGE_THROW, SoundSource.NEUTRAL, 0.7F, 1.2F);
    }
}
