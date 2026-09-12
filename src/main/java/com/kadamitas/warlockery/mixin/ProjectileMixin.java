package com.kadamitas.warlockery.mixin;

import com.kadamitas.warlockery.fabric.WarlockeryFabricEvents;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
abstract class ProjectileMixin {
    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void warlockery$handleEntityImpact(final HitResult hit, final CallbackInfo callback) {
        if (hit instanceof EntityHitResult entityHit
            && WarlockeryFabricEvents.dispatchProjectileImpact((Projectile) (Object) this, entityHit)) {
            callback.cancel();
        }
    }
}
