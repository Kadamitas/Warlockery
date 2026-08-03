package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.WerewolfShape;
import com.kadamitas.warlockery.transformation.WolfMouthItemPresentation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.animal.wolf.AdultWolfModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class WolfFormAvatarRenderer extends LivingEntityRenderer<
    AbstractClientPlayer,
    WolfFormRenderState,
    WolfFormAvatarRenderer.WolfAvatarModel
> {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/wolf/wolf.png");

    public WolfFormAvatarRenderer(final EntityRendererProvider.Context context) {
        super(context, new WolfAvatarModel(context.bakeLayer(ModelLayers.WOLF)), 0.5F);
        addLayer(new WolfMouthItemLayer(this));
    }

    @Override
    public WolfFormRenderState createRenderState() {
        return new WolfFormRenderState();
    }

    @Override
    public Identifier getTextureLocation(final WolfFormRenderState state) {
        return state.texture;
    }

    @Override
    public void extractRenderState(
        final AbstractClientPlayer player,
        final WolfFormRenderState state,
        final float partialTicks
    ) {
        super.extractRenderState(player, state, partialTicks);
        state.isAngry = false;
        state.isSitting = player.isCrouching() && player.onGround() && state.walkAnimationSpeed < 0.01F;
        state.tailAngle = (float) (Math.PI / 5.0);
        state.headRollAngle = 0.0F;
        state.shakeAnim = 0.0F;
        state.wetShade = 1.0F;
        state.texture = TEXTURE;
        state.collarColor = null;
        state.bodyArmorItem = ItemStack.EMPTY;
        extractMouthItem(player, state);
    }

    public void submitAvatar(
        final AbstractClientPlayer player,
        final AvatarRenderState avatarState,
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final CameraRenderState cameraState
    ) {
        final float partialTicks = Mth.clamp(avatarState.ageInTicks - player.tickCount, 0.0F, 1.0F);
        final WolfFormRenderState state = createRenderState(player, partialTicks);
        state.applyAvatarPose(avatarState);
        submit(state, poseStack, submitNodeCollector, cameraState);
    }

    @Override
    protected void setupRotations(
        final WolfFormRenderState state,
        final PoseStack poseStack,
        final float bodyRotation,
        final float entityScale
    ) {
        super.setupRotations(state, poseStack, bodyRotation, entityScale);
        if (state.fallFlying()) {
            if (!state.isAutoSpinAttack) {
                poseStack.mulPose(Axis.XP.rotationDegrees(state.fallFlyingScale() * (-90.0F - state.xRot)));
            }
            if (state.applyFlyingYRotation()) {
                poseStack.mulPose(Axis.YP.rotation(state.flyingYRotation()));
            }
            return;
        }
        if (state.swimAmount() <= 0.0F) {
            return;
        }
        final float targetRotation = state.isInWater ? -90.0F - state.xRot : -90.0F;
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(state.swimAmount(), 0.0F, targetRotation)));
        if (state.visuallySwimming()) {
            poseStack.translate(0.0F, -1.0F, 0.3F);
        }
    }

    private void extractMouthItem(final AbstractClientPlayer player, final WolfFormRenderState state) {
        final ItemStack mainHand = player.getMainHandItem();
        final ItemStack offHand = player.getOffhandItem();
        final WolfMouthItemPresentation presentation = WolfMouthItemPresentation.resolve(
            SupernaturalForm.WEREWOLF,
            WerewolfShape.WOLF,
            mainHand.isEmpty(),
            offHand.isEmpty()
        );
        state.prepareMouthItem(presentation.pose());
        presentation.mouthHand()
            .map(hand -> hand == WolfMouthItemPresentation.Hand.MAIN ? mainHand : offHand)
            .filter(stack -> !stack.isEmpty())
            .ifPresent(stack -> itemModelResolver.updateForLiving(
                state.mouthItem(),
                stack,
                displayContext(presentation.pose().displayContext()),
                player
            ));
    }

    private static ItemDisplayContext displayContext(final WolfMouthItemPresentation.DisplayContext context) {
        return switch (context) {
            case GROUND -> ItemDisplayContext.GROUND;
        };
    }

    static final class WolfAvatarModel extends AdultWolfModel {
        WolfAvatarModel(final ModelPart root) {
            super(root);
        }

        void translateToMouth(
            final PoseStack poseStack,
            final WolfMouthItemPresentation.MouthPose mouthPose
        ) {
            root().translateAndRotate(poseStack);
            head.translateAndRotate(poseStack);
            poseStack.translate(mouthPose.translateX(), mouthPose.translateY(), mouthPose.translateZ());
            poseStack.mulPose(Axis.XP.rotationDegrees(mouthPose.rotateXDegrees()));
            poseStack.mulPose(Axis.YP.rotationDegrees(mouthPose.rotateYDegrees()));
            poseStack.mulPose(Axis.ZP.rotationDegrees(mouthPose.rotateZDegrees()));
            poseStack.scale(mouthPose.scale(), mouthPose.scale(), mouthPose.scale());
        }
    }
}
