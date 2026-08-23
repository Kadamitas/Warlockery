package com.kadamitas.warlockery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import com.kadamitas.warlockery.transformation.WerewolfShape;

public final class WolfFormAvatarRenderBridge {
    private static WolfFormAvatarRenderer wolfRenderer;
    private static WerewolfFormAvatarRenderer werewolfRenderer;

    private WolfFormAvatarRenderBridge() {
    }

    public static void initialize(final EntityRendererProvider.Context context) {
        if (wolfRenderer == null) {
            wolfRenderer = new WolfFormAvatarRenderer(context);
        }
        if (werewolfRenderer == null) {
            werewolfRenderer = new WerewolfFormAvatarRenderer(context);
        }
    }

    public static boolean submit(
        final AvatarRenderState state,
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final CameraRenderState cameraState
    ) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (wolfRenderer == null || werewolfRenderer == null || minecraft.level == null) {
            return false;
        }
        final Entity entity = minecraft.level.getEntity(state.id);
        if (!(entity instanceof AbstractClientPlayer player)) {
            return false;
        }
        return switch (PlayerWolfVisualState.shape(player.getUUID())) {
            case WOLF -> {
                wolfRenderer.submitAvatar(player, state, poseStack, submitNodeCollector, cameraState);
                yield true;
            }
            case WOLFMAN -> {
                werewolfRenderer.submitAvatar(player, state, poseStack, submitNodeCollector, cameraState);
                yield true;
            }
            case HUMAN -> false;
        };
    }

    public static boolean submitFirstPersonArm(
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final int packedLight,
        final HumanoidArm arm
    ) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || wolfRenderer == null || werewolfRenderer == null) {
            return false;
        }
        final WerewolfShape shape = PlayerWolfVisualState.shape(minecraft.player.getUUID());
        return switch (shape) {
            case WOLF -> {
                wolfRenderer.submitFirstPersonArm(poseStack, submitNodeCollector, packedLight, arm);
                yield true;
            }
            case WOLFMAN -> {
                werewolfRenderer.submitFirstPersonArm(poseStack, submitNodeCollector, packedLight, arm);
                yield true;
            }
            case HUMAN -> false;
        };
    }
}
