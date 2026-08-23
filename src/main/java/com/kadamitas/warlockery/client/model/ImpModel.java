package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.ImpEntity;
import com.kadamitas.warlockery.entity.ImpLifeRules.Action;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

public final class ImpModel extends EntityModel<ImpModel.State> implements ArmedModel<ImpModel.State> {
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart head;
    private final ModelPart muzzle;
    private final ModelPart leftHorn;
    private final ModelPart rightHorn;
    private final ModelPart leftEar;
    private final ModelPart rightEar;
    private final ModelPart torso;
    private final ModelPart leftWing;
    private final ModelPart leftWingTip;
    private final ModelPart rightWing;
    private final ModelPart rightWingTip;
    private final ModelPart leftArm;
    private final ModelPart leftHand;
    private final ModelPart rightArm;
    private final ModelPart rightHand;
    private final ModelPart leftLeg;
    private final ModelPart leftFoot;
    private final ModelPart rightLeg;
    private final ModelPart rightFoot;
    private final ModelPart tailBase;
    private final ModelPart tailMid;
    private final ModelPart tailTip;

    public ImpModel(final ModelPart root) {
        super(root);
        head = root.getChild("head");
        muzzle = head.getChild("muzzle");
        leftHorn = head.getChild("left_horn");
        rightHorn = head.getChild("right_horn");
        leftEar = head.getChild("left_ear");
        rightEar = head.getChild("right_ear");
        torso = root.getChild("torso");
        leftWing = root.getChild("left_wing");
        leftWingTip = leftWing.getChild("left_wing_tip");
        rightWing = root.getChild("right_wing");
        rightWingTip = rightWing.getChild("right_wing_tip");
        leftArm = root.getChild("left_arm");
        leftHand = leftArm.getChild("left_hand");
        rightArm = root.getChild("right_arm");
        rightHand = rightArm.getChild("right_hand");
        leftLeg = root.getChild("left_leg");
        leftFoot = leftLeg.getChild("left_foot");
        rightLeg = root.getChild("right_leg");
        rightFoot = rightLeg.getChild("right_foot");
        tailBase = root.getChild("tail_base");
        tailMid = tailBase.getChild("tail_mid");
        tailTip = tailMid.getChild("tail_tip");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition head = root.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -5.0F, -3.5F, 8.0F, 7.0F, 7.0F),
            PartPose.offsetAndRotation(0.0F, 7.5F, -0.5F, -0.08F, 0.0F, 0.0F)
        );
        head.addOrReplaceChild(
            "muzzle",
            CubeListBuilder.create().texOffs(32, 0).addBox(-3.0F, -1.5F, -3.0F, 6.0F, 3.0F, 3.0F),
            PartPose.offset(0.0F, 0.1F, -3.1F)
        );
        head.addOrReplaceChild(
            "lower_jaw",
            CubeListBuilder.create().texOffs(32, 0).addBox(-2.0F, -0.4F, -3.0F, 4.0F, 1.4F, 3.0F),
            PartPose.offset(0.0F, 1.25F, -2.9F)
        );
        final PartDefinition leftHorn = head.addOrReplaceChild(
            "left_horn",
            CubeListBuilder.create().texOffs(52, 0).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(2.25F, -4.3F, 0.3F, -0.3F, 0.0F, 0.3F)
        );
        leftHorn.addOrReplaceChild(
            "left_horn_tip",
            CubeListBuilder.create().texOffs(52, 0).addBox(-0.7F, -4.5F, -0.7F, 1.4F, 4.5F, 1.4F),
            PartPose.offsetAndRotation(0.0F, -4.65F, 0.0F, -0.18F, 0.0F, 0.12F)
        );
        final PartDefinition rightHorn = head.addOrReplaceChild(
            "right_horn",
            CubeListBuilder.create().texOffs(62, 0).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(-2.25F, -4.3F, 0.3F, -0.3F, 0.0F, -0.3F)
        );
        rightHorn.addOrReplaceChild(
            "right_horn_tip",
            CubeListBuilder.create().texOffs(62, 0).addBox(-0.7F, -4.5F, -0.7F, 1.4F, 4.5F, 1.4F),
            PartPose.offsetAndRotation(0.0F, -4.65F, 0.0F, -0.18F, 0.0F, -0.12F)
        );
        head.addOrReplaceChild(
            "left_ear",
            CubeListBuilder.create().texOffs(72, 0).addBox(0.0F, -1.5F, -2.0F, 1.0F, 3.0F, 4.0F),
            PartPose.offsetAndRotation(3.75F, -1.6F, -0.15F, 0.0F, -0.22F, -0.58F)
        );
        head.addOrReplaceChild(
            "right_ear",
            CubeListBuilder.create().texOffs(84, 0).addBox(-1.0F, -1.5F, -2.0F, 1.0F, 3.0F, 4.0F),
            PartPose.offsetAndRotation(-3.75F, -1.6F, -0.15F, 0.0F, 0.22F, 0.58F)
        );
        root.addOrReplaceChild(
            "torso",
            CubeListBuilder.create().texOffs(0, 16).addBox(-3.5F, -1.0F, -2.5F, 7.0F, 9.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, 9.0F, 0.0F, 0.12F, 0.0F, 0.0F)
        );
        final PartDefinition leftWing = root.addOrReplaceChild(
            "left_wing",
            CubeListBuilder.create().texOffs(26, 16).addBox(0.0F, -1.0F, -1.0F, 1.0F, 9.0F, 2.0F),
            PartPose.offsetAndRotation(3.0F, 9.5F, 1.5F, -0.24F, 0.32F, -0.9F)
        );
        leftWing.addOrReplaceChild(
            "left_inner_membrane",
            CubeListBuilder.create().texOffs(26, 16).addBox(0.0F, -0.5F, -0.5F, 1.0F, 7.0F, 8.0F),
            PartPose.offsetAndRotation(0.1F, 0.4F, 0.8F, 0.02F, 0.08F, 0.08F)
        );
        final PartDefinition leftWingTip = leftWing.addOrReplaceChild(
            "left_wing_tip",
            CubeListBuilder.create().texOffs(74, 16).addBox(0.0F, 0.0F, -1.0F, 1.0F, 7.0F, 2.0F),
            PartPose.offsetAndRotation(0.6F, 7.2F, 0.8F, 0.0F, 0.08F, -0.45F)
        );
        leftWingTip.addOrReplaceChild(
            "left_outer_membrane",
            CubeListBuilder.create().texOffs(74, 16).addBox(0.0F, 0.0F, -0.5F, 1.0F, 5.0F, 7.0F),
            PartPose.offsetAndRotation(0.1F, 0.2F, 0.3F, 0.0F, 0.08F, 0.06F)
        );
        leftWingTip.addOrReplaceChild(
            "left_trailing_notch",
            CubeListBuilder.create().texOffs(74, 16).addBox(0.0F, 0.0F, -0.5F, 1.0F, 2.0F, 3.0F),
            PartPose.offsetAndRotation(0.1F, 3.6F, 4.9F, 0.18F, 0.08F, -0.18F)
        );
        final PartDefinition rightWing = root.addOrReplaceChild(
            "right_wing",
            CubeListBuilder.create().texOffs(50, 16).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 9.0F, 2.0F),
            PartPose.offsetAndRotation(-3.0F, 9.5F, 1.5F, -0.24F, -0.32F, 0.9F)
        );
        rightWing.addOrReplaceChild(
            "right_inner_membrane",
            CubeListBuilder.create().texOffs(50, 16).addBox(-1.0F, -0.5F, -0.5F, 1.0F, 7.0F, 8.0F),
            PartPose.offsetAndRotation(-0.1F, 0.4F, 0.8F, 0.02F, -0.08F, -0.08F)
        );
        final PartDefinition rightWingTip = rightWing.addOrReplaceChild(
            "right_wing_tip",
            CubeListBuilder.create().texOffs(94, 16).addBox(-1.0F, 0.0F, -1.0F, 1.0F, 7.0F, 2.0F),
            PartPose.offsetAndRotation(-0.6F, 7.2F, 0.8F, 0.0F, -0.08F, 0.45F)
        );
        rightWingTip.addOrReplaceChild(
            "right_outer_membrane",
            CubeListBuilder.create().texOffs(94, 16).addBox(-1.0F, 0.0F, -0.5F, 1.0F, 5.0F, 7.0F),
            PartPose.offsetAndRotation(-0.1F, 0.2F, 0.3F, 0.0F, -0.08F, -0.06F)
        );
        rightWingTip.addOrReplaceChild(
            "right_trailing_notch",
            CubeListBuilder.create().texOffs(94, 16).addBox(-1.0F, 0.0F, -0.5F, 1.0F, 2.0F, 3.0F),
            PartPose.offsetAndRotation(-0.1F, 3.6F, 4.9F, 0.18F, -0.08F, 0.18F)
        );
        final PartDefinition leftArm = root.addOrReplaceChild(
            "left_arm",
            CubeListBuilder.create().texOffs(0, 36).addBox(-0.5F, 0.0F, -1.5F, 3.0F, 7.0F, 3.0F),
            PartPose.offsetAndRotation(3.25F, 10.5F, -0.4F, -0.18F, 0.0F, -0.2F)
        );
        final PartDefinition leftHand = leftArm.addOrReplaceChild(
            "left_hand",
            CubeListBuilder.create().texOffs(14, 36).addBox(-0.5F, 0.0F, -2.0F, 3.0F, 3.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 6.2F, 0.0F, 0.18F, 0.0F, 0.12F)
        );
        leftHand.addOrReplaceChild(
            "left_finger_inner",
            CubeListBuilder.create().texOffs(14, 36).addBox(-0.4F, 0.0F, -2.2F, 0.8F, 1.0F, 2.2F),
            PartPose.offset(0.1F, 2.6F, -1.6F)
        );
        leftHand.addOrReplaceChild(
            "left_finger_middle",
            CubeListBuilder.create().texOffs(14, 36).addBox(-0.4F, 0.0F, -2.6F, 0.8F, 1.0F, 2.6F),
            PartPose.offset(1.0F, 2.6F, -1.6F)
        );
        leftHand.addOrReplaceChild(
            "left_finger_outer",
            CubeListBuilder.create().texOffs(14, 36).addBox(-0.4F, 0.0F, -2.2F, 0.8F, 1.0F, 2.2F),
            PartPose.offset(1.9F, 2.6F, -1.6F)
        );
        final PartDefinition rightArm = root.addOrReplaceChild(
            "right_arm",
            CubeListBuilder.create().texOffs(30, 36).addBox(-2.5F, 0.0F, -1.5F, 3.0F, 7.0F, 3.0F),
            PartPose.offsetAndRotation(-3.25F, 10.5F, -0.4F, -0.18F, 0.0F, 0.2F)
        );
        final PartDefinition rightHand = rightArm.addOrReplaceChild(
            "right_hand",
            CubeListBuilder.create().texOffs(44, 36).addBox(-2.5F, 0.0F, -2.0F, 3.0F, 3.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 6.2F, 0.0F, 0.18F, 0.0F, -0.12F)
        );
        rightHand.addOrReplaceChild(
            "right_finger_inner",
            CubeListBuilder.create().texOffs(44, 36).addBox(-0.4F, 0.0F, -2.2F, 0.8F, 1.0F, 2.2F),
            PartPose.offset(-0.1F, 2.6F, -1.6F)
        );
        rightHand.addOrReplaceChild(
            "right_finger_middle",
            CubeListBuilder.create().texOffs(44, 36).addBox(-0.4F, 0.0F, -2.6F, 0.8F, 1.0F, 2.6F),
            PartPose.offset(-1.0F, 2.6F, -1.6F)
        );
        rightHand.addOrReplaceChild(
            "right_finger_outer",
            CubeListBuilder.create().texOffs(44, 36).addBox(-0.4F, 0.0F, -2.2F, 0.8F, 1.0F, 2.2F),
            PartPose.offset(-1.9F, 2.6F, -1.6F)
        );
        final PartDefinition leftLeg = root.addOrReplaceChild(
            "left_leg",
            CubeListBuilder.create().texOffs(0, 50).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F),
            PartPose.offsetAndRotation(2.0F, 16.0F, 0.2F, -0.18F, 0.0F, -0.08F)
        );
        final PartDefinition leftFoot = leftLeg.addOrReplaceChild(
            "left_foot",
            CubeListBuilder.create().texOffs(14, 50).addBox(-1.5F, 0.0F, -4.0F, 3.0F, 2.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, 6.038F, -0.2F, 0.18F, 0.0F, 0.0F)
        );
        leftFoot.addOrReplaceChild(
            "left_toe_inner",
            CubeListBuilder.create().texOffs(14, 50).addBox(-0.4F, 0.0F, -2.0F, 0.8F, 0.8F, 2.0F),
            PartPose.offset(-0.9F, 1.2F, -3.6F)
        );
        leftFoot.addOrReplaceChild(
            "left_toe_middle",
            CubeListBuilder.create().texOffs(14, 50).addBox(-0.4F, 0.0F, -2.4F, 0.8F, 0.8F, 2.4F),
            PartPose.offset(0.0F, 1.2F, -3.6F)
        );
        leftFoot.addOrReplaceChild(
            "left_toe_outer",
            CubeListBuilder.create().texOffs(14, 50).addBox(-0.4F, 0.0F, -2.0F, 0.8F, 0.8F, 2.0F),
            PartPose.offset(0.9F, 1.2F, -3.6F)
        );
        final PartDefinition rightLeg = root.addOrReplaceChild(
            "right_leg",
            CubeListBuilder.create().texOffs(32, 50).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F),
            PartPose.offsetAndRotation(-2.0F, 16.0F, 0.2F, -0.18F, 0.0F, 0.08F)
        );
        final PartDefinition rightFoot = rightLeg.addOrReplaceChild(
            "right_foot",
            CubeListBuilder.create().texOffs(46, 50).addBox(-1.5F, 0.0F, -4.0F, 3.0F, 2.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, 6.038F, -0.2F, 0.18F, 0.0F, 0.0F)
        );
        rightFoot.addOrReplaceChild(
            "right_toe_inner",
            CubeListBuilder.create().texOffs(46, 50).addBox(-0.4F, 0.0F, -2.0F, 0.8F, 0.8F, 2.0F),
            PartPose.offset(0.9F, 1.2F, -3.6F)
        );
        rightFoot.addOrReplaceChild(
            "right_toe_middle",
            CubeListBuilder.create().texOffs(46, 50).addBox(-0.4F, 0.0F, -2.4F, 0.8F, 0.8F, 2.4F),
            PartPose.offset(0.0F, 1.2F, -3.6F)
        );
        rightFoot.addOrReplaceChild(
            "right_toe_outer",
            CubeListBuilder.create().texOffs(46, 50).addBox(-0.4F, 0.0F, -2.0F, 0.8F, 0.8F, 2.0F),
            PartPose.offset(-0.9F, 1.2F, -3.6F)
        );
        final PartDefinition tailBase = root.addOrReplaceChild(
            "tail_base",
            CubeListBuilder.create().texOffs(0, 66).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 13.5F, 2.2F, 1.15F, 0.0F, 0.0F)
        );
        final PartDefinition tailMid = tailBase.addOrReplaceChild(
            "tail_mid",
            CubeListBuilder.create().texOffs(10, 66).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 5.2F, 0.0F, 0.65F, 0.0F, -0.32F)
        );
        final PartDefinition tailTip = tailMid.addOrReplaceChild(
            "tail_tip",
            CubeListBuilder.create().texOffs(20, 66).addBox(-2.5F, 0.0F, -1.0F, 5.0F, 4.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 5.2F, 0.0F, 0.22F, 0.0F, 0.32F)
        );
        tailTip.addOrReplaceChild(
            "ember_arrow_left",
            CubeListBuilder.create().texOffs(20, 66).addBox(-0.5F, -2.8F, -0.5F, 1.0F, 3.0F, 1.0F),
            PartPose.offsetAndRotation(1.8F, 1.0F, 0.0F, 0.0F, 0.0F, 0.72F)
        );
        tailTip.addOrReplaceChild(
            "ember_arrow_right",
            CubeListBuilder.create().texOffs(20, 66).addBox(-0.5F, -2.8F, -0.5F, 1.0F, 3.0F, 1.0F),
            PartPose.offsetAndRotation(-1.8F, 1.0F, 0.0F, 0.0F, 0.0F, -0.72F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        head.yRot += state.yRot * Mth.DEG_TO_RAD;
        head.xRot += state.xRot * Mth.DEG_TO_RAD;
        final float pace = state.walkAnimationPos * 0.72F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F);
        final float flap = Mth.sin(state.ageInTicks * (state.airborne ? 0.78F : 0.16F));
        leftWing.zRot -= flap * (state.airborne ? 0.42F : 0.06F);
        rightWing.zRot += flap * (state.airborne ? 0.42F : 0.06F);
        leftWingTip.zRot -= flap * 0.24F;
        rightWingTip.zRot += flap * 0.24F;
        leftArm.xRot += Mth.cos(pace + Mth.PI) * stride * 0.75F;
        rightArm.xRot += Mth.cos(pace) * stride * 0.75F;
        leftLeg.xRot += Mth.cos(pace) * stride * 0.92F;
        rightLeg.xRot += Mth.cos(pace + Mth.PI) * stride * 0.92F;
        torso.zRot += Mth.sin(state.ageInTicks * 0.11F) * 0.035F;
        tailBase.zRot += Mth.sin(state.ageInTicks * 0.13F) * 0.22F;
        tailMid.zRot += Mth.sin(state.ageInTicks * 0.13F + 0.7F) * 0.28F;
        tailTip.zRot += Mth.sin(state.ageInTicks * 0.13F + 1.35F) * 0.32F;
        if (state.action == Action.RANGED_WINDUP) {
            leftArm.xRot = -1.42F;
            leftArm.yRot = -0.46F;
            leftArm.zRot = -0.28F;
            rightArm.xRot = -0.76F;
            rightArm.yRot = 0.3F;
            rightArm.zRot = 0.52F;
            leftHand.xRot += 0.62F;
            rightHand.xRot += 0.24F;
            leftWing.zRot -= 0.54F;
            rightWing.zRot += 0.22F;
            tailMid.zRot -= 0.18F;
            tailTip.zRot += 0.24F;
            head.xRot -= 0.14F;
        }
    }

    @Override
    public void translateToHand(final State state, final HumanoidArm arm, final PoseStack poseStack) {
        final ModelPart proximal = arm == HumanoidArm.LEFT ? leftArm : rightArm;
        final ModelPart hand = arm == HumanoidArm.LEFT ? leftHand : rightHand;
        proximal.translateAndRotate(poseStack);
        hand.translateAndRotate(poseStack);
    }

    public static void extractRenderState(
        final ImpEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.action = entity.presentationAction();
        state.airborne = !entity.onGround();
    }

    public static final class State extends ArmedEntityRenderState {
        public Action action = Action.NONE;
        public boolean airborne;
    }
}
