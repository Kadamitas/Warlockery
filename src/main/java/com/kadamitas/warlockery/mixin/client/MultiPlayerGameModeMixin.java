package com.kadamitas.warlockery.mixin.client;

import com.kadamitas.warlockery.fabric.WarlockeryFabricItemEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void warlockery$transformBlockWithSprig(
        final BlockPos position,
        final CallbackInfoReturnable<Boolean> callback
    ) {
        final var player = Minecraft.getInstance().player;
        if (player != null && WarlockeryFabricItemEvents.handleBlockBreak(player, position)) {
            callback.setReturnValue(false);
        }
    }
}
