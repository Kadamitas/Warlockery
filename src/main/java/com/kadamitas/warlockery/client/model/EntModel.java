package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.EntEntity;
import com.kadamitas.warlockery.entity.EntRules;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public final class EntModel extends EntityModel<EntModel.State> {
    public static final int TEXTURE_WIDTH = 256;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart splitTrunk;
    private final ModelPart hollowKnot;
    private final ModelPart highShoulder;
    private final ModelPart rightBranchArm;
    private final ModelPart rightBranchForearm;
    private final ModelPart rightTwigFan;
    private final ModelPart lowShoulder;
    private final ModelPart leftBranchArm;
    private final ModelPart leftBranchForearm;
    private final ModelPart leftTwigFan;
    private final ModelPart branchCrown;
    private final ModelPart crownForkLeft;
    private final ModelPart crownForkRight;
    private final ModelPart crownReach;
    private final ModelPart crownCanopyHigh;
    private final ModelPart crownCanopyLeft;
    private final ModelPart crownCanopyRight;
    private final ModelPart rightRootLeg;
    private final ModelPart rightRootFoot;
    private final ModelPart leftRootLeg;
    private final ModelPart leftRootFoot;

    public EntModel(final ModelPart root) {
        super(root);
        final ModelPart trunkBase = root.getChild("trunk_base");
        splitTrunk = trunkBase.getChild("split_trunk");
        hollowKnot = splitTrunk.getChild("hollow_knot");
        highShoulder = splitTrunk.getChild("high_shoulder");
        rightBranchArm = highShoulder.getChild("right_branch_arm");
        rightBranchForearm = rightBranchArm.getChild("right_branch_forearm");
        rightTwigFan = rightBranchForearm.getChild("right_twig_fan");
        lowShoulder = splitTrunk.getChild("low_shoulder");
        leftBranchArm = lowShoulder.getChild("left_branch_arm");
        leftBranchForearm = leftBranchArm.getChild("left_branch_forearm");
        leftTwigFan = leftBranchForearm.getChild("left_twig_fan");
        branchCrown = splitTrunk.getChild("branch_crown");
        crownForkLeft = branchCrown.getChild("crown_fork_left");
        crownForkRight = branchCrown.getChild("crown_fork_right");
        crownReach = branchCrown.getChild("crown_reach");
        crownCanopyHigh = branchCrown.getChild("crown_canopy_high");
        crownCanopyLeft = branchCrown.getChild("crown_canopy_left");
        crownCanopyRight = branchCrown.getChild("crown_canopy_right");
        rightRootLeg = root.getChild("right_root_leg");
        rightRootFoot = rightRootLeg.getChild("right_root_foot");
        leftRootLeg = root.getChild("left_root_leg");
        leftRootFoot = leftRootLeg.getChild("left_root_foot");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition trunkBase = root.addOrReplaceChild(
            "trunk_base",
            CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -4.0F, -5.0F, 16.0F, 11.0F, 10.0F),
            PartPose.offsetAndRotation(0.0F, -0.87995F, 0.5F, 0.07F, -0.04F, -0.06F)
        );
        final PartDefinition split = trunkBase.addOrReplaceChild(
            "split_trunk",
            CubeListBuilder.create().texOffs(54, 0).addBox(-7.0F, -14.0F, -5.0F, 14.0F, 18.0F, 10.0F),
            PartPose.offsetAndRotation(0.8F, -3.0F, -0.5F, -0.04F, 0.11F, 0.1F)
        );
        split.addOrReplaceChild(
            "hollow_knot",
            CubeListBuilder.create()
                .texOffs(104, 0).addBox(-4.0F, -4.0F, -1.0F, 8.0F, 8.0F, 2.0F)
                .texOffs(126, 0).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, -4.5F, -5.0F, 0.0F, 0.0F, -0.08F)
        );
        split.addOrReplaceChild(
            "upper_bark_plate",
            CubeListBuilder.create().texOffs(140, 0).addBox(-4.0F, -5.0F, -1.0F, 8.0F, 10.0F, 2.0F),
            PartPose.offsetAndRotation(-4.8F, -8.0F, 3.9F, 0.08F, -0.38F, -0.12F)
        );
        trunkBase.addOrReplaceChild(
            "lower_bark_plate",
            CubeListBuilder.create().texOffs(164, 0).addBox(-5.0F, -3.0F, -1.0F, 10.0F, 6.0F, 2.0F),
            PartPose.offsetAndRotation(4.6F, 1.0F, 4.7F, -0.12F, 0.42F, 0.18F)
        );

        final PartDefinition highShoulder = split.addOrReplaceChild(
            "high_shoulder",
            CubeListBuilder.create().texOffs(190, 0).addBox(-4.0F, -4.0F, -4.5F, 8.0F, 8.0F, 9.0F),
            PartPose.offsetAndRotation(-7.8F, -11.0F, -1.5F, -0.04F, 0.2F, 0.3F)
        );
        final PartDefinition rightArm = highShoulder.addOrReplaceChild(
            "right_branch_arm",
            CubeListBuilder.create().texOffs(226, 0).addBox(-4.0F, -2.0F, -2.5F, 5.0F, 15.0F, 5.0F),
            PartPose.offsetAndRotation(-2.0F, 1.0F, 0.0F, -0.18F, 0.02F, 0.18F)
        );
        final PartDefinition rightForearm = rightArm.addOrReplaceChild(
            "right_branch_forearm",
            CubeListBuilder.create().texOffs(0, 26).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 16.0F, 4.0F),
            PartPose.offsetAndRotation(-1.6F, 12.0F, 0.0F, -0.12F, -0.08F, 0.16F)
        );
        final PartDefinition rightFan = rightForearm.addOrReplaceChild(
            "right_twig_fan",
            CubeListBuilder.create().texOffs(18, 26).addBox(-4.5F, 0.0F, -2.0F, 7.0F, 7.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 14.0F, 0.0F, 0.15F, 0.1F, 0.08F)
        );
        rightFan.addOrReplaceChild("right_outer_twig", CubeListBuilder.create().texOffs(42, 26).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F), PartPose.offsetAndRotation(-3.5F, 4.0F, 0.0F, 0.0F, 0.0F, 0.34F));
        rightFan.addOrReplaceChild("right_inner_twig", CubeListBuilder.create().texOffs(52, 26).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F), PartPose.offsetAndRotation(1.8F, 4.0F, 0.0F, 0.0F, 0.0F, -0.28F));
        rightFan.addOrReplaceChild("right_rear_twig", CubeListBuilder.create().texOffs(42, 26).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F), PartPose.offsetAndRotation(-0.8F, 3.5F, 1.4F, -0.34F, 0.22F, 0.12F));

        final PartDefinition lowShoulder = split.addOrReplaceChild(
            "low_shoulder",
            CubeListBuilder.create().texOffs(62, 26).addBox(-4.0F, -3.5F, -4.0F, 8.0F, 7.0F, 8.0F),
            PartPose.offsetAndRotation(7.8F, -4.5F, 1.5F, 0.1F, -0.16F, -0.28F)
        );
        final PartDefinition leftArm = lowShoulder.addOrReplaceChild(
            "left_branch_arm",
            CubeListBuilder.create().texOffs(96, 26).addBox(-1.0F, -2.0F, -3.0F, 6.0F, 13.0F, 6.0F),
            PartPose.offsetAndRotation(2.0F, 1.0F, 0.0F, 0.12F, -0.06F, -0.24F)
        );
        final PartDefinition leftForearm = leftArm.addOrReplaceChild(
            "left_branch_forearm",
            CubeListBuilder.create().texOffs(122, 26).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 17.0F, 4.0F),
            PartPose.offsetAndRotation(2.0F, 10.0F, 0.0F, -0.08F, 0.12F, -0.18F)
        );
        final PartDefinition leftFan = leftForearm.addOrReplaceChild(
            "left_twig_fan",
            CubeListBuilder.create().texOffs(140, 26).addBox(-2.5F, 0.0F, -2.0F, 7.0F, 8.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 15.0F, 0.0F, 0.12F, -0.08F, -0.1F)
        );
        leftFan.addOrReplaceChild("left_outer_twig", CubeListBuilder.create().texOffs(164, 26).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 10.0F, 2.0F), PartPose.offsetAndRotation(3.2F, 4.0F, 0.0F, 0.0F, 0.0F, -0.34F));
        leftFan.addOrReplaceChild("left_inner_twig", CubeListBuilder.create().texOffs(174, 26).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 7.0F, 2.0F), PartPose.offsetAndRotation(-1.6F, 5.0F, 0.0F, 0.0F, 0.0F, 0.3F));
        leftFan.addOrReplaceChild("left_forward_twig", CubeListBuilder.create().texOffs(164, 26).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 10.0F, 2.0F), PartPose.offsetAndRotation(0.8F, 3.5F, -1.4F, 0.32F, -0.2F, -0.14F));

        final PartDefinition crown = split.addOrReplaceChild(
            "branch_crown",
            CubeListBuilder.create().texOffs(184, 26).addBox(-5.0F, -6.0F, -4.0F, 10.0F, 8.0F, 8.0F),
            PartPose.offsetAndRotation(0.0F, -14.0F, 0.0F, -0.04F, 0.0F, -0.08F)
        );
        final PartDefinition crownLeft = crown.addOrReplaceChild(
            "crown_fork_left",
            CubeListBuilder.create().texOffs(222, 26).addBox(-1.5F, -13.0F, -1.5F, 3.0F, 13.0F, 3.0F),
            PartPose.offsetAndRotation(-3.5F, -4.0F, 0.0F, -0.2F, -0.12F, -0.36F)
        );
        crownLeft.addOrReplaceChild("crown_left_tip", CubeListBuilder.create().texOffs(236, 26).addBox(-1.0F, -9.0F, -1.0F, 2.0F, 9.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -11.5F, 0.0F, 0.1F, 0.2F, 0.5F));
        final PartDefinition crownRight = crown.addOrReplaceChild(
            "crown_fork_right",
            CubeListBuilder.create().texOffs(0, 50).addBox(-1.5F, -11.0F, -1.5F, 3.0F, 11.0F, 3.0F),
            PartPose.offsetAndRotation(3.5F, -3.0F, 0.0F, 0.18F, 0.18F, 0.42F)
        );
        crownRight.addOrReplaceChild("crown_right_tip", CubeListBuilder.create().texOffs(14, 50).addBox(-1.0F, -8.0F, -1.0F, 2.0F, 8.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -10.0F, 0.0F, -0.18F, -0.2F, -0.54F));
        crown.addOrReplaceChild(
            "crown_reach",
            CubeListBuilder.create().texOffs(24, 50).addBox(-1.5F, -12.0F, -1.5F, 3.0F, 12.0F, 3.0F),
            PartPose.offsetAndRotation(-0.5F, -5.0F, 2.0F, -0.58F, 0.0F, 0.12F)
        );
        crown.addOrReplaceChild(
            "crown_canopy_high",
            CubeListBuilder.create()
                .texOffs(28, 92).addBox(-7.0F, -3.5F, -6.0F, 14.0F, 7.0F, 12.0F)
                .texOffs(0, 112).addBox(-6.0F, -6.0F, -4.0F, 10.0F, 5.0F, 9.0F),
            PartPose.offsetAndRotation(0.5F, -12.5F, -1.0F, 0.04F, -0.12F, 0.05F)
        );
        crown.addOrReplaceChild(
            "crown_canopy_left",
            CubeListBuilder.create()
                .texOffs(84, 92).addBox(-6.5F, -3.0F, -5.5F, 13.0F, 6.0F, 11.0F)
                .texOffs(42, 112).addBox(-3.0F, -5.0F, -4.0F, 8.0F, 4.0F, 8.0F),
            PartPose.offsetAndRotation(-10.8F, -6.8F, 0.5F, -0.06F, 0.18F, -0.1F)
        );
        crown.addOrReplaceChild(
            "crown_canopy_right",
            CubeListBuilder.create()
                .texOffs(136, 92).addBox(-6.0F, -3.0F, -5.0F, 12.0F, 6.0F, 10.0F)
                .texOffs(78, 112).addBox(-5.0F, -5.0F, -3.5F, 7.0F, 4.0F, 7.0F),
            PartPose.offsetAndRotation(10.0F, -6.0F, 1.0F, 0.06F, -0.16F, 0.12F)
        );

        final PartDefinition rightLeg = root.addOrReplaceChild(
            "right_root_leg",
            CubeListBuilder.create().texOffs(38, 50).addBox(-4.0F, 0.0F, -3.5F, 7.0F, 12.0F, 7.0F),
            PartPose.offsetAndRotation(-5.0F, -1.87995F, -3.5F, -0.1F, 0.1F, 0.12F)
        );
        final PartDefinition rightFoot = rightLeg.addOrReplaceChild(
            "right_root_foot",
            CubeListBuilder.create().texOffs(68, 50).addBox(-5.0F, 0.0F, -9.0F, 10.0F, 4.0F, 15.0F),
            PartPose.offsetAndRotation(-0.5F, 12.0F, -1.0F, 0.0F, -0.12F, 0.0F)
        );
        rightFoot.addOrReplaceChild("right_root_spur", CubeListBuilder.create().texOffs(120, 50).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 3.0F, 9.0F), PartPose.offsetAndRotation(-3.0F, 1.0F, 1.0F, 0.0F, -0.62F, 0.0F));
        rightFoot.addOrReplaceChild("right_root_inner", CubeListBuilder.create().texOffs(120, 50).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 3.0F, 9.0F), PartPose.offsetAndRotation(2.5F, 1.0F, -0.5F, 0.0F, 0.48F, 0.0F));
        final PartDefinition leftLeg = root.addOrReplaceChild(
            "left_root_leg",
            CubeListBuilder.create().texOffs(146, 50).addBox(-3.0F, 0.0F, -4.0F, 8.0F, 12.0F, 8.0F),
            PartPose.offsetAndRotation(4.5F, -1.87995F, 3.0F, 0.08F, -0.12F, -0.14F)
        );
        final PartDefinition leftFoot = leftLeg.addOrReplaceChild(
            "left_root_foot",
            CubeListBuilder.create().texOffs(180, 50).addBox(-6.0F, 0.0F, -8.0F, 12.0F, 4.0F, 13.0F),
            PartPose.offsetAndRotation(0.5F, 12.0F, -0.5F, 0.0F, 0.14F, 0.0F)
        );
        leftFoot.addOrReplaceChild("left_root_spur", CubeListBuilder.create().texOffs(0, 76).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 3.0F, 9.0F), PartPose.offsetAndRotation(3.0F, 1.0F, 1.0F, 0.0F, 0.68F, 0.0F));
        leftFoot.addOrReplaceChild("left_root_inner", CubeListBuilder.create().texOffs(0, 76).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 3.0F, 9.0F), PartPose.offsetAndRotation(-2.5F, 1.0F, -0.5F, 0.0F, -0.5F, 0.0F));
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        splitTrunk.yRot += state.yRot * Mth.DEG_TO_RAD * 0.45F;
        hollowKnot.xRot += state.xRot * Mth.DEG_TO_RAD * 0.35F;
        branchCrown.yRot += state.yRot * Mth.DEG_TO_RAD * 0.55F;
        final float pace = state.walkAnimationPos * 0.34F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F) * 0.46F;
        final float rightStep = Mth.cos(pace) * stride;
        final float leftStep = Mth.cos(pace + Mth.PI) * stride;
        rightRootLeg.xRot += rightStep;
        leftRootLeg.xRot += leftStep;
        rightRootFoot.xRot -= rightStep * 0.34F;
        leftRootFoot.xRot -= leftStep * 0.34F;
        rightBranchArm.xRot += leftStep * 0.38F;
        leftBranchArm.xRot += rightStep * 0.32F;
        rightBranchForearm.zRot += Mth.sin(state.ageInTicks * 0.035F) * 0.04F;
        leftBranchForearm.zRot -= Mth.sin(state.ageInTicks * 0.031F + 0.7F) * 0.045F;
        crownForkLeft.zRot += Mth.sin(state.ageInTicks * 0.026F) * 0.035F;
        crownForkRight.zRot -= Mth.sin(state.ageInTicks * 0.024F + 0.9F) * 0.04F;
        crownReach.xRot += Mth.sin(state.ageInTicks * 0.02F + 1.4F) * 0.03F;
        crownCanopyHigh.yRot += Mth.sin(state.ageInTicks * 0.018F) * 0.025F;
        crownCanopyLeft.zRot += Mth.sin(state.ageInTicks * 0.021F + 0.8F) * 0.02F;
        crownCanopyRight.zRot -= Mth.sin(state.ageInTicks * 0.019F + 1.3F) * 0.022F;
        final float sweep = Mth.clamp(state.attackProgress, 0.0F, 1.0F);
        if (state.roused) {
            splitTrunk.xRot -= 0.13F + sweep * 0.1F;
            highShoulder.zRot -= 0.16F;
            lowShoulder.zRot += 0.1F;
            rightBranchArm.xRot -= 0.95F * sweep;
            rightBranchArm.yRot -= 0.72F * sweep;
            rightBranchForearm.xRot -= 0.38F * sweep;
            rightTwigFan.zRot -= 0.34F * sweep;
            leftBranchArm.xRot -= 0.26F * sweep;
            leftTwigFan.zRot += 0.16F * sweep;
        }
    }

    public static void extractRenderState(
        final EntEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.tint = entity.variant().tint();
        state.attackProgress = entity.getAttackAnim(partialTicks);
        final EntRules.Phase phase = entity.presentationPhase();
        state.roused = phase == EntRules.Phase.ROUSED
            || phase == EntRules.Phase.WARN
            || phase == EntRules.Phase.STRIKE
            || state.attackProgress > 0.0F;
    }

    public static final class State extends LivingEntityRenderState {
        public int tint = 0xFFFFFFFF;
        public float attackProgress;
        public boolean roused;
    }
}
