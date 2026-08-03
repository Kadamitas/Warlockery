package com.kadamitas.warlockery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class WolfMouthItemLayer extends RenderLayer<
    WolfFormRenderState,
    WolfFormAvatarRenderer.WolfAvatarModel
> {
    public WolfMouthItemLayer(final WolfFormAvatarRenderer renderer) {
        super(renderer);
    }

    @Override
    public void submit(
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final int lightCoords,
        final WolfFormRenderState state,
        final float yRot,
        final float xRot
    ) {
        if (state.mouthItem().isEmpty()) {
            return;
        }
        poseStack.pushPose();
        getParentModel().translateToMouth(poseStack, state.mouthPose());
        state.mouthItem().submit(
            poseStack,
            submitNodeCollector,
            lightCoords,
            OverlayTexture.NO_OVERLAY,
            state.outlineColor
        );
        poseStack.popPose();
    }
}
