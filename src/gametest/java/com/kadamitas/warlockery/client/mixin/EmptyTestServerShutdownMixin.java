package com.kadamitas.warlockery.client.mixin;

import net.fabricmc.fabric.impl.client.gametest.threading.ThreadingImpl;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(IntegratedServer.class)
abstract class EmptyTestServerShutdownMixin {
    @Redirect(method = "halt", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/server/IntegratedServer;executeBlocking(Ljava/lang/Runnable;)V"), require = 1)
    private void avoidEmptyPlayerCleanupDeadlock(IntegratedServer server, Runnable cleanup) {
        // Fabric 6.0.0 parks the server at its test barrier while halt waits on this
        // cleanup. With no players, vanilla's cleanup loop has no work to perform.
        // Keep the original call for populated worlds; this adapter is test-only.
        if (ThreadingImpl.testThread != null && server.getPlayerList().getPlayerCount() == 0) return;
        server.executeBlocking(cleanup);
    }
}
