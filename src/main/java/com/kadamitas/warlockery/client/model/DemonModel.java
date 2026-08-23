package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.InfernalHierarchyEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public final class DemonModel extends EntityModel<DemonModel.State> {
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart infernalTorso;
    private final ModelPart hornedHead;
    private final ModelPart goldEyeBand;
    private final ModelPart rightUprightHorn;
    private final ModelPart leftUprightHorn;
    private final ModelPart rightArm;
    private final ModelPart rightEmberClaw;
    private final ModelPart leftArm;
    private final ModelPart leftEmberClaw;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart rightPointedTasset;
    private final ModelPart leftPointedTasset;
    private final ModelPart backSpines;

    public DemonModel(final ModelPart root) {
        super(root);
        infernalTorso = root.getChild("infernal_torso");
        hornedHead = infernalTorso.getChild("horned_head");
        goldEyeBand = hornedHead.getChild("gold_eye_band");
        rightUprightHorn = hornedHead.getChild("right_upright_horn");
        leftUprightHorn = hornedHead.getChild("left_upright_horn");
        rightArm = root.getChild("right_arm");
        rightEmberClaw = rightArm.getChild("right_ember_claw");
        leftArm = root.getChild("left_arm");
        leftEmberClaw = leftArm.getChild("left_ember_claw");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
        rightPointedTasset = infernalTorso.getChild("right_pointed_tasset");
        leftPointedTasset = infernalTorso.getChild("left_pointed_tasset");
        backSpines = infernalTorso.getChild("back_spines");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition torso = root.addOrReplaceChild(
            "infernal_torso",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.5F, -4.0F, -2.5F, 7.0F, 10.0F, 5.0F)
                .texOffs(26, 0).addBox(-5.0F, -3.5F, -3.0F, 10.0F, 3.0F, 6.0F)
                .texOffs(60, 0).addBox(-2.5F, -1.5F, -3.3F, 5.0F, 4.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, 7.0F, 0.0F, 0.07F, 0.0F, 0.0F)
                .scaled(1.21F, 1.0F, 1.28F)
        );
        final PartDefinition head = torso.addOrReplaceChild(
            "horned_head",
            CubeListBuilder.create()
                .texOffs(0, 18).addBox(-3.0F, -4.5F, -3.0F, 6.0F, 5.0F, 6.0F)
                .texOffs(26, 18).addBox(-3.2F, -2.5F, -3.5F, 6.4F, 2.0F, 1.0F),
            PartPose.offset(0.0F, -3.5F, -0.3F)
        );
        head.addOrReplaceChild(
            "gold_eye_band",
            CubeListBuilder.create().texOffs(42, 18).addBox(-2.5F, -0.5F, -0.6F, 5.0F, 1.0F, 1.0F),
            PartPose.offset(0.0F, -1.8F, -3.3F)
        );
        head.addOrReplaceChild(
            "right_upright_horn",
            CubeListBuilder.create()
                .texOffs(56, 18).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F)
                .texOffs(66, 18).addBox(-0.5F, -8.5F, -0.5F, 1.0F, 3.0F, 1.0F),
            PartPose.offsetAndRotation(-2.2F, -3.8F, 0.0F, -0.04F, 0.0F, -0.09F)
        );
        head.addOrReplaceChild(
            "left_upright_horn",
            CubeListBuilder.create().texOffs(56, 18).mirror()
                .addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F)
                .texOffs(66, 18).addBox(-0.5F, -8.5F, -0.5F, 1.0F, 3.0F, 1.0F),
            PartPose.offsetAndRotation(2.2F, -3.8F, 0.0F, -0.04F, 0.0F, 0.09F)
        );
        torso.addOrReplaceChild(
            "right_soot_pauldron",
            CubeListBuilder.create().texOffs(72, 18).addBox(-5.0F, -1.5F, -2.5F, 5.0F, 3.0F, 5.0F),
            PartPose.offsetAndRotation(-3.8F, -2.2F, 0.0F, 0.0F, 0.0F, -0.16F)
        );
        torso.addOrReplaceChild(
            "left_soot_pauldron",
            CubeListBuilder.create().texOffs(72, 18).mirror().addBox(0.0F, -1.5F, -2.5F, 5.0F, 3.0F, 5.0F),
            PartPose.offsetAndRotation(3.8F, -2.2F, 0.0F, 0.0F, 0.0F, 0.16F)
        );

        final PartDefinition rightArm = root.addOrReplaceChild(
            "right_arm",
            CubeListBuilder.create().texOffs(0, 34).addBox(-3.0F, -1.5F, -2.0F, 3.0F, 8.0F, 4.0F),
            PartPose.offsetAndRotation(-5.69F, 7.0F, 0.0F, 0.12F, 0.0F, 0.18F)
                .scaled(1.21F, 1.0F, 1.28F)
        );
        rightArm.addOrReplaceChild(
            "right_ember_claw",
            CubeListBuilder.create()
                .texOffs(16, 34).addBox(-2.4F, -0.5F, -2.5F, 4.0F, 5.0F, 5.0F)
                .texOffs(36, 34).addBox(-2.8F, 3.5F, -3.8F, 1.0F, 1.0F, 3.0F)
                .texOffs(44, 34).addBox(-1.1F, 3.7F, -4.2F, 1.0F, 1.0F, 3.5F)
                .texOffs(52, 34).addBox(0.6F, 3.5F, -3.8F, 1.0F, 1.0F, 3.0F),
            PartPose.offsetAndRotation(-0.8F, 5.5F, 0.0F, -0.18F, 0.0F, -0.08F)
        );
        final PartDefinition leftArm = root.addOrReplaceChild(
            "left_arm",
            CubeListBuilder.create().texOffs(0, 34).mirror().addBox(0.0F, -1.5F, -2.0F, 3.0F, 8.0F, 4.0F),
            PartPose.offsetAndRotation(5.69F, 7.0F, 0.0F, 0.12F, 0.0F, -0.18F)
                .scaled(1.21F, 1.0F, 1.28F)
        );
        leftArm.addOrReplaceChild(
            "left_ember_claw",
            CubeListBuilder.create().texOffs(16, 34).mirror()
                .addBox(-1.6F, -0.5F, -2.5F, 4.0F, 5.0F, 5.0F)
                .texOffs(36, 34).addBox(1.8F, 3.5F, -3.8F, 1.0F, 1.0F, 3.0F)
                .texOffs(44, 34).addBox(0.1F, 3.7F, -4.2F, 1.0F, 1.0F, 3.5F)
                .texOffs(52, 34).addBox(-1.6F, 3.5F, -3.8F, 1.0F, 1.0F, 3.0F),
            PartPose.offsetAndRotation(0.8F, 5.5F, 0.0F, -0.18F, 0.0F, 0.08F)
        );

        final PartDefinition rightLeg = root.addOrReplaceChild(
            "right_leg",
            CubeListBuilder.create().texOffs(0, 52).addBox(-2.2F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F),
            PartPose.offsetAndRotation(-2.66F, 13.0F, 0.0F, 0.06F, 0.0F, 0.04F)
                .scaled(1.21F, 1.0F, 1.28F)
        );
        rightLeg.addOrReplaceChild(
            "right_cloven_hoof",
            CubeListBuilder.create()
                .texOffs(18, 52).addBox(-2.5F, 0.0F, -3.0F, 5.0F, 3.0F, 5.0F)
                .texOffs(40, 52).addBox(-2.3F, 1.0F, -4.0F, 1.7F, 2.0F, 2.0F)
                .texOffs(40, 52).addBox(0.7F, 1.0F, -4.0F, 1.7F, 2.0F, 2.0F),
            PartPose.offset(0.0F, 8.0F, 0.0F)
        );
        final PartDefinition leftLeg = root.addOrReplaceChild(
            "left_leg",
            CubeListBuilder.create().texOffs(0, 52).mirror().addBox(-1.8F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F),
            PartPose.offsetAndRotation(2.66F, 13.0F, 0.0F, 0.06F, 0.0F, -0.04F)
                .scaled(1.21F, 1.0F, 1.28F)
        );
        leftLeg.addOrReplaceChild(
            "left_cloven_hoof",
            CubeListBuilder.create().texOffs(18, 52).mirror()
                .addBox(-2.5F, 0.0F, -3.0F, 5.0F, 3.0F, 5.0F)
                .texOffs(40, 52).addBox(-2.4F, 1.0F, -4.0F, 1.7F, 2.0F, 2.0F)
                .texOffs(40, 52).addBox(0.6F, 1.0F, -4.0F, 1.7F, 2.0F, 2.0F),
            PartPose.offset(0.0F, 8.0F, 0.0F)
        );
        torso.addOrReplaceChild(
            "right_pointed_tasset",
            CubeListBuilder.create().texOffs(50, 52).addBox(-3.0F, 0.0F, -1.0F, 3.0F, 8.0F, 2.0F),
            PartPose.offsetAndRotation(-0.4F, 5.0F, -2.4F, 0.06F, 0.0F, 0.06F)
        );
        torso.addOrReplaceChild(
            "left_pointed_tasset",
            CubeListBuilder.create().texOffs(50, 52).mirror().addBox(0.0F, 0.0F, -1.0F, 3.0F, 8.0F, 2.0F),
            PartPose.offsetAndRotation(0.4F, 5.0F, -2.4F, 0.06F, 0.0F, -0.06F)
        );
        torso.addOrReplaceChild(
            "back_spines",
            CubeListBuilder.create()
                .texOffs(64, 52).addBox(-1.0F, -3.5F, -1.0F, 2.0F, 4.5F, 2.0F)
                .texOffs(74, 52).addBox(-0.7F, -2.5F, -0.7F, 1.4F, 3.0F, 1.4F),
            PartPose.offsetAndRotation(0.0F, 2.5F, 2.6F, -0.55F, 0.0F, 0.0F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        hornedHead.yRot += state.yRot * Mth.DEG_TO_RAD;
        hornedHead.xRot += state.xRot * Mth.DEG_TO_RAD;
        final float pace = state.walkAnimationPos * 0.6662F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F) * 1.25F;
        rightLeg.xRot += Mth.cos(pace) * stride;
        leftLeg.xRot += Mth.cos(pace + Mth.PI) * stride;
        rightArm.xRot += Mth.cos(pace + Mth.PI) * stride * 0.72F;
        leftArm.xRot += Mth.cos(pace) * stride * 0.72F;
        infernalTorso.yRot += Mth.sin(state.ageInTicks * 0.08F) * 0.025F;
        rightPointedTasset.xRot += Mth.cos(pace) * stride * 0.09F;
        leftPointedTasset.xRot += Mth.cos(pace + Mth.PI) * stride * 0.09F;
        backSpines.xRot += Mth.sin(state.ageInTicks * 0.06F) * 0.02F;
        final float strike = Mth.clamp(state.attackProgress, 0.0F, 1.0F);
        rightArm.xRot -= strike * 1.18F;
        rightArm.yRot -= strike * 0.42F;
        leftArm.xRot -= strike * 1.02F;
        leftArm.yRot += strike * 0.42F;
        rightEmberClaw.xRot -= strike * 0.32F;
        rightEmberClaw.yRot -= strike * 0.16F;
        leftEmberClaw.xRot -= strike * 0.32F;
        leftEmberClaw.yRot += strike * 0.16F;
        infernalTorso.xRot += strike * 0.22F;
        if (state.aggressive) {
            infernalTorso.xRot += 0.18F;
            goldEyeBand.xRot -= 0.08F;
            rightUprightHorn.zRot -= 0.025F;
            leftUprightHorn.zRot += 0.025F;
        }
    }

    public static void extractRenderState(
        final InfernalHierarchyEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.aggressive = entity.isAggressive();
        state.attackProgress = state.aggressive ? 1.0F : 0.0F;
    }

    public static final class State extends LivingEntityRenderState {
        public float attackProgress;
        public boolean aggressive;
    }
}
