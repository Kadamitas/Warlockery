package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.client.model.WerewolfModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/** Renders the upright player werewolf in third person and its authored forearm in first person. */
public final class WerewolfFormAvatarRenderer extends LivingEntityRenderer<
    AbstractClientPlayer,
    WerewolfModel.State,
    WerewolfModel
> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
        Warlockery.MOD_ID,
        "textures/entity/werewolf.png"
    );

    public WerewolfFormAvatarRenderer(final EntityRendererProvider.Context context) {
        super(context, new WerewolfModel(WerewolfModel.createBodyLayer().bakeRoot()), 0.52F);
    }

    @Override
    public WerewolfModel.State createRenderState() {
        return new WerewolfModel.State();
    }

    @Override
    public Identifier getTextureLocation(final WerewolfModel.State state) {
        return TEXTURE;
    }

    @Override
    public void extractRenderState(
        final AbstractClientPlayer player,
        final WerewolfModel.State state,
        final float partialTicks
    ) {
        super.extractRenderState(player, state, partialTicks);
        state.aggressive = player.isSprinting();
        state.pouncing = player.isFallFlying();
        state.airborne = !player.onGround();
    }

    public void submitAvatar(
        final AbstractClientPlayer player,
        final AvatarRenderState avatarState,
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final CameraRenderState cameraState
    ) {
        final float partialTicks = Mth.clamp(avatarState.ageInTicks - player.tickCount, 0.0F, 1.0F);
        submit(createRenderState(player, partialTicks), poseStack, submitNodeCollector, cameraState);
    }

    public void submitFirstPersonArm(
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final int packedLight,
        final HumanoidArm arm
    ) {
        poseStack.pushPose();
        poseStack.translate(arm == HumanoidArm.RIGHT ? -0.08F : 0.08F, -0.22F, -0.32F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-18.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(arm == HumanoidArm.RIGHT ? -8.0F : 8.0F));
        poseStack.scale(0.72F, 0.72F, 0.72F);
        submitNodeCollector.submitModelPart(
            model.firstPersonArm(arm),
            poseStack,
            RenderTypes.entityTranslucent(TEXTURE),
            packedLight,
            OverlayTexture.NO_OVERLAY,
            null
        );
        poseStack.popPose();
    }
}
