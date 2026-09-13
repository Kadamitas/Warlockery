package com.kadamitas.warlockery.mixin;

import com.kadamitas.warlockery.dream.SpiritWorldRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fabric exposes only AFTER level-change events; capture the actual source before native teleport mutates it. */
@Mixin(ServerPlayer.class)
abstract class SpiritWorldTransferMixin {
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
        at = @At("HEAD"), cancellable = true)
    private void warlockery$prepareSpiritEntry(final TeleportTransition transition,
        final CallbackInfoReturnable<ServerPlayer> callback) {
        if (!SpiritWorldRuntime.prepareExternalTransfer((ServerPlayer) (Object) this, transition.newLevel())) {
            callback.setReturnValue(null);
        }
    }

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
        at = @At("RETURN"))
    private void warlockery$finishSpiritEntry(final TeleportTransition transition,
        final CallbackInfoReturnable<ServerPlayer> callback) {
        SpiritWorldRuntime.finishExternalTransfer((ServerPlayer) (Object) this, callback.getReturnValue() != null);
    }
}
