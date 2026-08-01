package com.kadamitas.warlockery.mixin;

import com.kadamitas.warlockery.fabric.WarlockeryFabricEvents;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
abstract class ProjectileMixin {
    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true)
    private void warlockery$handleEntityImpact(final EntityHitResult hit, final CallbackInfo callback) {
        if (WarlockeryFabricEvents.dispatchProjectileImpact((Projectile) (Object) this, hit)) {
            callback.cancel();
        }
    }
}
