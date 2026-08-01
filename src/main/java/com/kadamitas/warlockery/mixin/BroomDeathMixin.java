package com.kadamitas.warlockery.mixin;

import com.kadamitas.warlockery.item.FlyingBroomItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
abstract class BroomDeathMixin {
    @Inject(method = "die", at = @At("HEAD"))
    private void warlockery$returnMountedBroomBeforeDeathLoot(
        final DamageSource source,
        final CallbackInfo callback
    ) {
        FlyingBroomItem.handleDeath((ServerPlayer) (Object) this);
    }
}
