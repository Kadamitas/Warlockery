package com.kadamitas.warlockery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;

public final class WolfFormAvatarRenderBridge {
    private static WolfFormAvatarRenderer renderer;

    private WolfFormAvatarRenderBridge() {
    }

    public static void initialize(final EntityRendererProvider.Context context) {
        if (renderer == null) {
            renderer = new WolfFormAvatarRenderer(context);
        }
    }

    public static boolean submit(
        final AvatarRenderState state,
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final CameraRenderState cameraState
    ) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (renderer == null || minecraft.level == null) {
            return false;
        }
        final Entity entity = minecraft.level.getEntity(state.id);
        if (!(entity instanceof AbstractClientPlayer player) || !PlayerWolfVisualState.isWolf(player.getUUID())) {
            return false;
        }
        renderer.submitAvatar(player, state, poseStack, submitNodeCollector, cameraState);
        return true;
    }
}
