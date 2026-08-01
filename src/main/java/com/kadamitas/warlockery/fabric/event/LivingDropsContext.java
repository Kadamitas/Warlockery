package com.kadamitas.warlockery.fabric.event;

import java.util.Collection;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;

public record LivingDropsContext(
    LivingEntity entity,
    DamageSource source,
    Collection<ItemEntity> drops
) {
    public LivingEntity getEntity() {
        return entity;
    }

    public DamageSource getSource() {
        return source;
    }

    public Collection<ItemEntity> getDrops() {
        return drops;
    }
}
