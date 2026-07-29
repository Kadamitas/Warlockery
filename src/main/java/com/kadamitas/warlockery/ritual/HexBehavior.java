package com.kadamitas.warlockery.ritual;

import net.minecraft.world.entity.LivingEntity;

public interface HexBehavior {
    void apply(LivingEntity target, int duration);

    void remove(LivingEntity target);
}
