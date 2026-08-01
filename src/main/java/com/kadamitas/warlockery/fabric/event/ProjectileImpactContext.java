package com.kadamitas.warlockery.fabric.event;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;

public final class ProjectileImpactContext {
    private final Projectile projectile;
    private final HitResult hitResult;
    private boolean skipEntity;

    public ProjectileImpactContext(final Projectile projectile, final HitResult hitResult) {
        this.projectile = projectile;
        this.hitResult = hitResult;
    }

    public Projectile getProjectile() {
        return projectile;
    }

    public HitResult getRayTraceResult() {
        return hitResult;
    }

    public void skipEntity() {
        skipEntity = true;
    }

    public boolean shouldSkipEntity() {
        return skipEntity;
    }
}
