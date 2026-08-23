package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.HexBatEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public final class HexBatModel extends EntityModel<HexBatModel.State> {
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart leftWingRoot;
    private final ModelPart rightWingRoot;
    private final ModelPart leftWingForearm;
    private final ModelPart rightWingForearm;
    private final ModelPart leftWingTip;
    private final ModelPart rightWingTip;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;
    private final ModelPart leftClaw;
    private final ModelPart rightClaw;
    private final ModelPart tailMembrane;

    public HexBatModel(final ModelPart root) {
        super(root);
        body = root.getChild("body");
        head = body.getChild("head");
        leftWingRoot = body.getChild("left_wing_root");
        rightWingRoot = body.getChild("right_wing_root");
        leftWingForearm = leftWingRoot.getChild("left_wing_forearm");
        rightWingForearm = rightWingRoot.getChild("right_wing_forearm");
        leftWingTip = leftWingForearm.getChild("left_wing_tip");
        rightWingTip = rightWingForearm.getChild("right_wing_tip");
        leftLeg = body.getChild("left_leg");
        rightLeg = body.getChild("right_leg");
        leftClaw = leftLeg.getChild("left_claw");
        rightClaw = rightLeg.getChild("right_claw");
        tailMembrane = body.getChild("tail_membrane");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -3.0F, -5.0F, 9.0F, 6.0F, 10.0F), PartPose.offset(0.0F, 15.0F, 1.0F));
        final PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(38, 0).addBox(-5.0F, -2.5F, -3.5F, 10.0F, 5.0F, 7.0F), PartPose.offset(0.0F, -1.0F, -6.0F));
        head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(72, 0).addBox(-2.0F, -1.0F, -3.0F, 4.0F, 2.0F, 3.0F), PartPose.offset(0.0F, 1.0F, -3.4F));
        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(86, 0).addBox(-1.0F, -7.0F, -1.5F, 2.0F, 7.0F, 3.0F), PartPose.offsetAndRotation(3.4F, -2.0F, -0.2F, -0.18F, 0.0F, 0.25F));
        head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(96, 0).addBox(-1.0F, -7.0F, -1.5F, 2.0F, 7.0F, 3.0F), PartPose.offsetAndRotation(-3.4F, -2.0F, -0.2F, -0.18F, 0.0F, -0.25F));

        final PartDefinition leftWingRoot = body.addOrReplaceChild("left_wing_root", CubeListBuilder.create().texOffs(0, 18).addBox(0.0F, -1.0F, -3.0F, 7.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(5.3F, -1.0F, 0.8F, 0.06F, -0.08F, -1.38F));
        leftWingRoot.addOrReplaceChild("left_inner_membrane", CubeListBuilder.create().texOffs(0, 42).addBox(0.0F, 0.0F, -4.0F, 5.0F, 1.0F, 8.0F), PartPose.offset(1.0F, 0.5F, 0.0F));
        final PartDefinition leftForearm = leftWingRoot.addOrReplaceChild("left_wing_forearm", CubeListBuilder.create().texOffs(26, 18).addBox(0.0F, -1.0F, -2.5F, 10.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(6.2F, 0.0F, 0.0F, 0.0F, -0.04F, 2.86F));
        leftForearm.addOrReplaceChild("left_outer_membrane", CubeListBuilder.create().texOffs(52, 42).addBox(0.0F, 0.0F, -5.0F, 5.0F, 1.0F, 10.0F), PartPose.offset(2.0F, 0.5F, 0.0F));
        leftForearm.addOrReplaceChild("left_wing_tip", CubeListBuilder.create().texOffs(56, 18).addBox(0.0F, -0.5F, -3.5F, 12.0F, 1.0F, 7.0F), PartPose.offsetAndRotation(9.2F, 0.0F, 0.0F, 0.0F, -0.03F, -2.98F));

        final PartDefinition rightWingRoot = body.addOrReplaceChild("right_wing_root", CubeListBuilder.create().texOffs(0, 30).addBox(-7.0F, -1.0F, -3.0F, 7.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(-5.3F, -1.0F, 0.8F, 0.06F, 0.08F, 1.38F));
        rightWingRoot.addOrReplaceChild("right_inner_membrane", CubeListBuilder.create().texOffs(26, 42).addBox(-5.0F, 0.0F, -4.0F, 5.0F, 1.0F, 8.0F), PartPose.offset(-1.0F, 0.5F, 0.0F));
        final PartDefinition rightForearm = rightWingRoot.addOrReplaceChild("right_wing_forearm", CubeListBuilder.create().texOffs(26, 30).addBox(-10.0F, -1.0F, -2.5F, 10.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(-6.2F, 0.0F, 0.0F, 0.0F, 0.04F, -2.86F));
        rightForearm.addOrReplaceChild("right_outer_membrane", CubeListBuilder.create().texOffs(84, 42).addBox(-5.0F, 0.0F, -5.0F, 5.0F, 1.0F, 10.0F), PartPose.offset(-2.0F, 0.5F, 0.0F));
        rightForearm.addOrReplaceChild("right_wing_tip", CubeListBuilder.create().texOffs(56, 30).addBox(-12.0F, -0.5F, -3.5F, 12.0F, 1.0F, 7.0F), PartPose.offsetAndRotation(-9.2F, 0.0F, 0.0F, 0.0F, 0.03F, 2.98F));

        final PartDefinition leftLeg = body.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 56).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offset(2.5F, 3.0F, 2.2F));
        leftLeg.addOrReplaceChild("left_claw", CubeListBuilder.create().texOffs(16, 56).addBox(-1.5F, 0.0F, -3.0F, 3.0F, 2.0F, 4.0F), PartPose.offset(0.5F, 4.0F, -0.4F));
        final PartDefinition rightLeg = body.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(8, 56).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offset(-2.5F, 3.0F, 2.2F));
        rightLeg.addOrReplaceChild("right_claw", CubeListBuilder.create().texOffs(30, 56).addBox(-1.5F, 0.0F, -3.0F, 3.0F, 2.0F, 4.0F), PartPose.offset(-0.5F, 4.0F, -0.4F));
        body.addOrReplaceChild("tail_membrane", CubeListBuilder.create().texOffs(44, 56).addBox(-2.5F, 0.0F, -1.0F, 5.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 1.0F, 4.5F, 0.28F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        head.yRot = Mth.clamp(state.yRot * ((float) Math.PI / 180.0F), -0.9F, 0.9F);
        head.xRot = Mth.clamp(state.xRot * ((float) Math.PI / 180.0F), -0.55F, 0.55F);
        final float pulse = Mth.sin(state.ageInTicks * 0.38F) * 0.08F;
        leftWingRoot.zRot -= pulse;
        rightWingRoot.zRot += pulse;
        leftWingForearm.zRot += pulse * 0.7F;
        rightWingForearm.zRot -= pulse * 0.7F;
        if (state.roosting) {
            body.xRot = Mth.PI;
            body.y = 9.9162F;
            head.xRot = -0.22F;
            leftWingRoot.zRot = -1.3F;
            rightWingRoot.zRot = 1.3F;
            leftWingForearm.zRot = 0.74F;
            rightWingForearm.zRot = -0.74F;
            leftWingTip.zRot = 0.55F;
            rightWingTip.zRot = -0.55F;
            leftLeg.xRot = -0.7F;
            rightLeg.xRot = -0.7F;
            leftClaw.xRot = 0.75F;
            rightClaw.xRot = 0.75F;
        } else if (state.swooping) {
            body.xRot = 0.62F;
            head.xRot -= 0.55F;
            leftWingRoot.zRot = -0.12F;
            rightWingRoot.zRot = 0.12F;
            leftWingRoot.yRot = -0.34F;
            rightWingRoot.yRot = 0.34F;
            leftWingRoot.xRot = -0.42F;
            rightWingRoot.xRot = -0.42F;
            leftWingForearm.zRot = 0.08F;
            rightWingForearm.zRot = -0.08F;
            leftWingTip.zRot = 0.12F;
            rightWingTip.zRot = -0.12F;
            leftLeg.xRot = 0.8F;
            rightLeg.xRot = 0.8F;
            tailMembrane.xRot = 0.68F;
        } else {
            final float crawl = Mth.sin(state.walkAnimationPos * 0.82F) * state.walkAnimationSpeed * 0.3F;
            leftLeg.xRot -= crawl;
            rightLeg.xRot += crawl;
        }
    }

    public static void extractRenderState(final HexBatEntity entity, final State state, final float partialTicks) {
        state.roosting = entity.isRoosting();
        state.swooping = entity.isSwooping();
    }

    public static final class State extends LivingEntityRenderState {
        public boolean roosting;
        public boolean swooping;
    }
}
