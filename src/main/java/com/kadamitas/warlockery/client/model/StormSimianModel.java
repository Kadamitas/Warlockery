package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.StormSimianEntity;
import com.kadamitas.warlockery.entity.StormSimianRules;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public final class StormSimianModel extends EntityModel<StormSimianModel.State> {
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart head;
    private final ModelPart stormCrown;
    private final ModelPart torso;
    private final ModelPart stormBand;
    private final ModelPart leftArm;
    private final ModelPart leftForearm;
    private final ModelPart leftHand;
    private final ModelPart rightArm;
    private final ModelPart rightForearm;
    private final ModelPart rightHand;
    private final ModelPart leftLeg;
    private final ModelPart leftShin;
    private final ModelPart rightLeg;
    private final ModelPart rightShin;
    private final ModelPart tailBase;
    private final ModelPart tailMid;
    private final ModelPart tailTip;

    public StormSimianModel(final ModelPart root) {
        super(root);
        head = root.getChild("head");
        stormCrown = head.getChild("storm_crown");
        torso = root.getChild("torso");
        stormBand = root.getChild("storm_band");
        leftArm = root.getChild("left_arm");
        leftForearm = leftArm.getChild("left_forearm");
        leftHand = leftForearm.getChild("left_hand");
        rightArm = root.getChild("right_arm");
        rightForearm = rightArm.getChild("right_forearm");
        rightHand = rightForearm.getChild("right_hand");
        leftLeg = root.getChild("left_leg");
        leftShin = leftLeg.getChild("left_shin");
        rightLeg = root.getChild("right_leg");
        rightShin = rightLeg.getChild("right_shin");
        tailBase = root.getChild("tail_base");
        tailMid = tailBase.getChild("tail_mid");
        tailTip = tailMid.getChild("tail_tip");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition head = root.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -2.5F, -2.5F, 6.0F, 5.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, 11.0F, -1.0F, 0.12F, 0.0F, 0.0F)
        );
        head.addOrReplaceChild(
            "muzzle",
            CubeListBuilder.create().texOffs(22, 0).addBox(-2.0F, -1.0F, -3.0F, 4.0F, 3.0F, 3.0F),
            PartPose.offset(0.0F, 0.3F, -1.5F)
        );
        head.addOrReplaceChild(
            "left_ear",
            CubeListBuilder.create().texOffs(38, 0).addBox(-1.0F, -1.5F, -0.5F, 2.0F, 3.0F, 1.0F),
            PartPose.offset(3.2F, -0.2F, 0.0F)
        );
        head.addOrReplaceChild(
            "right_ear",
            CubeListBuilder.create().texOffs(48, 0).addBox(-1.0F, -1.5F, -0.5F, 2.0F, 3.0F, 1.0F),
            PartPose.offset(-3.2F, -0.2F, 0.0F)
        );
        head.addOrReplaceChild(
            "storm_crown",
            CubeListBuilder.create().texOffs(58, 0).addBox(-2.0F, -1.0F, -1.5F, 4.0F, 1.0F, 3.0F),
            PartPose.offset(0.0F, -2.5F, 0.0F)
        );
        root.addOrReplaceChild(
            "torso",
            CubeListBuilder.create().texOffs(0, 18).addBox(-3.0F, -1.0F, -2.0F, 6.0F, 7.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 14.0F, 0.0F, 0.12F, 0.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "storm_band",
            CubeListBuilder.create().texOffs(26, 18).addBox(-3.5F, -1.0F, -2.5F, 7.0F, 2.0F, 5.0F),
            PartPose.offset(0.0F, 15.5F, 0.0F)
        );
        final PartDefinition leftArm = root.addOrReplaceChild(
            "left_arm",
            CubeListBuilder.create().texOffs(48, 18).addBox(-0.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F),
            PartPose.offsetAndRotation(3.0F, 14.0F, 0.0F, -0.2F, 0.0F, -0.12F)
        );
        final PartDefinition leftForearm = leftArm.addOrReplaceChild(
            "left_forearm",
            CubeListBuilder.create().texOffs(66, 18).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F),
            PartPose.offsetAndRotation(1.0F, 5.0F, 0.0F, -0.18F, 0.0F, 0.14F)
        );
        leftForearm.addOrReplaceChild(
            "left_hand",
            CubeListBuilder.create().texOffs(84, 18).addBox(-2.0F, 0.0F, -2.5F, 4.0F, 2.0F, 4.0F),
            PartPose.offset(0.0F, 5.0F, -0.3F)
        );
        final PartDefinition rightArm = root.addOrReplaceChild(
            "right_arm",
            CubeListBuilder.create().texOffs(48, 18).mirror().addBox(-2.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F),
            PartPose.offsetAndRotation(-3.0F, 14.0F, 0.0F, -0.2F, 0.0F, 0.12F)
        );
        final PartDefinition rightForearm = rightArm.addOrReplaceChild(
            "right_forearm",
            CubeListBuilder.create().texOffs(66, 18).mirror().addBox(-1.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F),
            PartPose.offsetAndRotation(-1.0F, 5.0F, 0.0F, -0.18F, 0.0F, -0.14F)
        );
        rightForearm.addOrReplaceChild(
            "right_hand",
            CubeListBuilder.create().texOffs(84, 18).mirror().addBox(-2.0F, 0.0F, -2.5F, 4.0F, 2.0F, 4.0F),
            PartPose.offset(0.0F, 5.0F, -0.3F)
        );
        final PartDefinition leftLeg = root.addOrReplaceChild(
            "left_leg",
            CubeListBuilder.create().texOffs(0, 38).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F),
            PartPose.offsetAndRotation(1.8F, 19.0F, 0.5F, -0.45F, 0.0F, -0.06F)
        );
        final PartDefinition leftShin = leftLeg.addOrReplaceChild(
            "left_shin",
            CubeListBuilder.create().texOffs(18, 38).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 3.0F, 0.5F, 0.6F, 0.0F, 0.0F)
        );
        leftShin.addOrReplaceChild(
            "left_foot",
            CubeListBuilder.create().texOffs(30, 38).addBox(-1.5F, 0.0F, -3.5F, 3.0F, 2.0F, 5.0F),
            PartPose.offset(0.0F, 2.5F, -0.5F)
        );
        final PartDefinition rightLeg = root.addOrReplaceChild(
            "right_leg",
            CubeListBuilder.create().texOffs(0, 38).mirror().addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F),
            PartPose.offsetAndRotation(-1.8F, 19.0F, 0.5F, -0.45F, 0.0F, 0.06F)
        );
        final PartDefinition rightShin = rightLeg.addOrReplaceChild(
            "right_shin",
            CubeListBuilder.create().texOffs(18, 38).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 3.0F, 0.5F, 0.6F, 0.0F, 0.0F)
        );
        rightShin.addOrReplaceChild(
            "right_foot",
            CubeListBuilder.create().texOffs(30, 38).mirror().addBox(-1.5F, 0.0F, -3.5F, 3.0F, 2.0F, 5.0F),
            PartPose.offset(0.0F, 2.5F, -0.5F)
        );
        final PartDefinition tailBase = root.addOrReplaceChild(
            "tail_base",
            CubeListBuilder.create().texOffs(50, 38).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 18.0F, 1.8F, 1.05F, 0.0F, 0.3F)
        );
        final PartDefinition tailMid = tailBase.addOrReplaceChild(
            "tail_mid",
            CubeListBuilder.create().texOffs(62, 38).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 4.5F, 0.0F, 0.45F, 0.0F, -0.65F)
        );
        tailMid.addOrReplaceChild(
            "tail_tip",
            CubeListBuilder.create().texOffs(74, 38).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 4.5F, 0.0F, 0.0F, 0.0F, -0.65F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot += state.xRot * Mth.DEG_TO_RAD;
        final float pace = state.walkAnimationPos * 0.56F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F);
        final float rightSwing = Mth.cos(pace) * stride;
        final float leftSwing = Mth.cos(pace + Mth.PI) * stride;
        leftArm.xRot += leftSwing * 0.82F;
        rightArm.xRot += rightSwing * 0.82F;
        leftForearm.xRot -= leftSwing * 0.28F;
        rightForearm.xRot -= rightSwing * 0.28F;
        leftLeg.xRot += rightSwing * 0.48F;
        rightLeg.xRot += leftSwing * 0.48F;
        tailBase.zRot += Mth.sin(state.ageInTicks * 0.09F) * 0.18F;
        tailMid.zRot += Mth.sin(state.ageInTicks * 0.09F + 0.8F) * 0.25F;
        tailTip.zRot += Mth.sin(state.ageInTicks * 0.09F + 1.4F) * 0.32F;
        final float charge = Mth.clamp(state.charge / (float) StormSimianRules.MAX_CHARGE, 0.0F, 1.0F);
        stormCrown.y -= charge * 0.5F;
        stormBand.yRot += Mth.sin(state.ageInTicks * 0.08F) * charge * 0.08F;
        if (state.hasGrip) {
            leftHand.yRot = -0.28F;
            rightHand.yRot = 0.28F;
            leftForearm.xRot -= 0.32F;
            rightForearm.xRot -= 0.32F;
        }
        if (state.airborne) {
            leftLeg.xRot -= 0.24F;
            rightLeg.xRot -= 0.24F;
            leftShin.xRot += 0.36F;
            rightShin.xRot += 0.36F;
        }
        if (state.chargedGustReady) {
            leftArm.xRot = -1.18F;
            leftArm.yRot = -0.55F;
            rightArm.xRot = -1.18F;
            rightArm.yRot = 0.55F;
            leftForearm.xRot = -0.78F;
            rightForearm.xRot = -0.78F;
            torso.xRot += 0.2F;
            head.xRot -= 0.2F;
        }
    }

    public static void extractRenderState(
        final StormSimianEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.charge = entity.presentationCharge();
        state.chargedGustReady = StormSimianRules.chargedGustReady(state.charge);
        state.hasGrip = entity.presentationHasGrip();
        state.airborne = !entity.onGround();
    }

    public static final class State extends LivingEntityRenderState {
        public int charge;
        public boolean chargedGustReady;
        public boolean hasGrip;
        public boolean airborne;
    }
}
