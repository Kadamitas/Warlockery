package com.kadamitas.warlockery.mixin;

import com.kadamitas.warlockery.fabric.WarlockeryFabricEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownEnderpearl.class)
abstract class ThrownEnderpearlMixin {
    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void warlockery$blockPearlTeleport(final HitResult hit, final CallbackInfo callback) {
        final ThrownEnderpearl pearl = (ThrownEnderpearl) (Object) this;
        if (pearl.getOwner() instanceof LivingEntity owner && WarlockeryFabricEvents.blocksTeleport(owner)) {
            pearl.discard();
            callback.cancel();
        }
    }
}
