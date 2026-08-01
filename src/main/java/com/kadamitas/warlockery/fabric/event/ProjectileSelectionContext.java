package com.kadamitas.warlockery.fabric.event;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class ProjectileSelectionContext {
    private final LivingEntity entity;
    private ItemStack projectile;

    public ProjectileSelectionContext(final LivingEntity entity, final ItemStack projectile) {
        this.entity = entity;
        this.projectile = projectile;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public ItemStack getProjectileItemStack() {
        return projectile;
    }

    public void setProjectileItemStack(final ItemStack projectile) {
        this.projectile = projectile;
    }
}
