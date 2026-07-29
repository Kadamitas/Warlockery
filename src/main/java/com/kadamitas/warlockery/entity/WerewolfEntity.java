package com.kadamitas.warlockery.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

public final class WerewolfEntity extends ArcaneMob {
    public WerewolfEntity(final EntityType<? extends Zombie> type, final Level level) {
        super(type, level, CreatureKind.WEREWOLF);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Pillager.class, true));
    }
}
