package com.kadamitas.warlockery.mixin.client;

import com.kadamitas.warlockery.client.WolfFormAvatarRenderBridge;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void warlockery$initializeWolfRenderer(
        final EntityRendererProvider.Context context,
        final boolean slim,
        final CallbackInfo callback
    ) {
        WolfFormAvatarRenderBridge.initialize(context);
    }
}
