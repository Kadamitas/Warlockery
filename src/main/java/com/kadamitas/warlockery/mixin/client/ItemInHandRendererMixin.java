package com.kadamitas.warlockery.mixin.client;

import com.kadamitas.warlockery.client.WolfFormAvatarRenderBridge;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Routes transformed first-person forearms through the same authored rigs used in third person. */
@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin {
    @Inject(method = "renderPlayerArm", at = @At("HEAD"), cancellable = true)
    private void warlockery$renderTransformedArm(
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final int packedLight,
        final float equippedProgress,
        final float swingProgress,
        final HumanoidArm arm,
        final CallbackInfo callback
    ) {
        if (WolfFormAvatarRenderBridge.submitFirstPersonArm(
            poseStack,
            submitNodeCollector,
            packedLight,
            arm
        )) {
            callback.cancel();
        }
    }
}
