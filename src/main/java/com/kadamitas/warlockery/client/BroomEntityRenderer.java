package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.entity.BroomEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;

public final class BroomEntityRenderer extends EntityRenderer<BroomEntity, BroomEntityRenderer.State> {
    private final ItemModelResolver itemModelResolver;

    public BroomEntityRenderer(final EntityRendererProvider.Context context) {
        super(context);
        itemModelResolver = context.getItemModelResolver();
        shadowRadius = 0.22F;
        shadowStrength = 0.45F;
    }

    @Override
    public void submit(
        final State state,
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final CameraRenderState camera
    ) {
        final BroomRenderPose pose = state.pose();
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.22F + pose.bob(), 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(pose.yawDegrees()));
        poseStack.mulPose(Axis.XP.rotationDegrees(pose.pitchDegrees()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(pose.rollDegrees()));
        poseStack.scale(pose.scale(), pose.scale(), pose.scale());
        state.item.submit(
            poseStack,
            submitNodeCollector,
            state.lightCoords,
            OverlayTexture.NO_OVERLAY,
            state.outlineColor
        );
        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
        final BroomEntity entity,
        final State state,
        final float partialTicks
    ) {
        super.extractRenderState(entity, state, partialTicks);
        state.yaw = entity.getYRot(partialTicks);
        state.pitch = entity.getXRot(partialTicks);
        state.yawDelta = entity.getYRot() - entity.yRotO;
        state.speed = entity.getFlightSpeed();
        state.gliding = entity.isGliding();
        itemModelResolver.updateForNonLiving(
            state.item,
            entity.getBroomStack(),
            ItemDisplayContext.GROUND,
            entity
        );
    }

    public static final class State extends EntityRenderState {
        private final ItemStackRenderState item = new ItemStackRenderState();
        private float yaw;
        private float pitch;
        private float yawDelta;
        private float speed;
        private boolean gliding;

        BroomRenderPose pose() {
            return BroomRenderPose.resolve(yaw, pitch, yawDelta, speed, gliding, ageInTicks);
        }
    }
}
