package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.LycanPackRules.Variant;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

public final class FeralLycanEntity extends WerewolfEntity {
    public FeralLycanEntity(final EntityType<? extends Zombie> type, final Level level) {
        super(type, level);
    }

    @Override
    public Variant variant() {
        return Variant.FERAL_LYCAN;
    }
}
