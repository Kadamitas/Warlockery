package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.MimicryRules.Species;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The Threshold Weaver. It never approaches and never pursues: it waits completely motionless while
 * a subject is bound, closes exactly once when that subject crosses the inner radius, and lets go
 * the moment the subject commits a deliberate act.
 *
 * <p>Its snare is the only foreign entity mutation in either family: one Slowness I of exactly
 * forty ticks, applied once per episode, removed once under a guard that proves the instance is the
 * one it applied or a weaker one. It places no web, applies no poison, climbs nothing and deals no
 * damage.</p>
 */
public final class IllusionSpiderEntity extends AbstractMimicEntity {

    public IllusionSpiderEntity(final EntityType<? extends Monster> type, final Level level) {
        super(type, level, Species.THRESHOLD_WEAVER);
    }

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.ILLUSION_SPIDER;
    }

    @Override
    protected SoundEvent ambientSound() {
        return SoundEvents.SPIDER_AMBIENT;
    }
}


