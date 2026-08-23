package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.InfernalHierarchyEntity;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.Intent;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public final class EmberhornArchfiendModel extends EntityModel<EmberhornArchfiendModel.State> {
    public static final int TEXTURE_WIDTH = 192;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart guardedHeartChest;
    private final ModelPart exposedDemonHeart;
    private final ModelPart heartCore;
    private final ModelPart heartCrown;
    private final ModelPart rightGuardRib;
    private final ModelPart leftGuardRib;
    private final ModelPart archfiendHead;
    private final ModelPart moltenJaw;
    private final ModelPart rightBranchedEmberhorn;
    private final ModelPart leftBranchedEmberhorn;
    private final ModelPart rightGuardArm;
    private final ModelPart rightMagmaFist;
    private final ModelPart leftGuardArm;
    private final ModelPart leftMagmaFist;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart rightHeartPauldron;
    private final ModelPart leftHeartPauldron;
    private final ModelPart backRibMantle;

    public EmberhornArchfiendModel(final ModelPart root) {
        super(root);
        guardedHeartChest = root.getChild("guarded_heart_chest");
        exposedDemonHeart = guardedHeartChest.getChild("exposed_demon_heart");
        heartCore = exposedDemonHeart.getChild("heart_core");
        heartCrown = exposedDemonHeart.getChild("heart_crown");
        rightGuardRib = guardedHeartChest.getChild("right_guard_rib");
        leftGuardRib = guardedHeartChest.getChild("left_guard_rib");
        archfiendHead = guardedHeartChest.getChild("archfiend_head");
        moltenJaw = archfiendHead.getChild("molten_jaw");
        rightBranchedEmberhorn = archfiendHead.getChild("right_branched_emberhorn");
        leftBranchedEmberhorn = archfiendHead.getChild("left_branched_emberhorn");
        rightGuardArm = root.getChild("right_guard_arm");
        rightMagmaFist = rightGuardArm.getChild("right_magma_fist");
        leftGuardArm = root.getChild("left_guard_arm");
        leftMagmaFist = leftGuardArm.getChild("left_magma_fist");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
        rightHeartPauldron = root.getChild("right_heart_pauldron");
        leftHeartPauldron = root.getChild("left_heart_pauldron");
        backRibMantle = root.getChild("back_rib_mantle");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition chest = root.addOrReplaceChild(
            "guarded_heart_chest",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 11.0F, 3.0F)
                .texOffs(28, 0).addBox(-7.0F, -5.0F, -3.0F, 14.0F, 3.0F, 6.0F)
                .texOffs(70, 0).addBox(-4.0F, 4.0F, -2.5F, 8.0F, 3.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, 7.0F, 0.0F, 0.05F, 0.0F, 0.0F)
        );
        final PartDefinition heart = chest.addOrReplaceChild(
            "exposed_demon_heart",
            CubeListBuilder.create().texOffs(98, 0).addBox(-3.0F, -3.0F, -2.0F, 6.0F, 6.0F, 4.0F),
            PartPose.offset(0.0F, 0.5F, -3.5F)
        );
        heart.addOrReplaceChild(
            "heart_core",
            CubeListBuilder.create().texOffs(120, 0).addBox(-1.5F, -1.5F, -1.0F, 3.0F, 3.0F, 2.0F),
            PartPose.offset(0.0F, 0.2F, -2.0F)
        );
        heart.addOrReplaceChild(
            "heart_crown",
            CubeListBuilder.create()
                .texOffs(132, 0).addBox(-3.0F, -3.0F, -1.0F, 2.0F, 4.0F, 2.0F)
                .texOffs(142, 0).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F)
                .texOffs(152, 0).addBox(1.0F, -3.0F, -1.0F, 2.0F, 4.0F, 2.0F),
            PartPose.offset(0.0F, -2.0F, 0.0F)
        );
        final PartDefinition rightRib = chest.addOrReplaceChild(
            "right_guard_rib",
            CubeListBuilder.create()
                .texOffs(0, 18).addBox(-2.0F, -4.5F, -1.5F, 2.0F, 9.0F, 3.0F)
                .texOffs(12, 18).addBox(-3.0F, -2.5F, -2.0F, 3.0F, 6.0F, 4.0F),
            PartPose.offsetAndRotation(-3.2F, 0.2F, -3.0F, 0.0F, 0.12F, -0.18F)
        );
        rightRib.addOrReplaceChild(
            "right_upper_guard_rib",
            CubeListBuilder.create().texOffs(0, 18)
                .addBox(-2.0F, -4.5F, -1.5F, 2.0F, 9.0F, 3.0F),
            PartPose.offsetAndRotation(-1.2F, -0.8F, -0.8F, -0.12F, 0.28F, -0.22F)
        );
        rightRib.addOrReplaceChild(
            "right_lower_guard_rib",
            CubeListBuilder.create().texOffs(12, 18)
                .addBox(-3.0F, -2.5F, -2.0F, 3.0F, 6.0F, 4.0F),
            PartPose.offsetAndRotation(-1.0F, 2.8F, 0.8F, 0.16F, 0.34F, -0.18F)
        );
        final PartDefinition leftRib = chest.addOrReplaceChild(
            "left_guard_rib",
            CubeListBuilder.create().texOffs(0, 18).mirror()
                .addBox(0.0F, -4.5F, -1.5F, 2.0F, 9.0F, 3.0F)
                .texOffs(12, 18).addBox(0.0F, -2.5F, -2.0F, 3.0F, 6.0F, 4.0F),
            PartPose.offsetAndRotation(3.2F, 0.2F, -3.0F, 0.0F, -0.12F, 0.18F)
        );
        leftRib.addOrReplaceChild(
            "left_upper_guard_rib",
            CubeListBuilder.create().texOffs(0, 18).mirror()
                .addBox(0.0F, -4.5F, -1.5F, 2.0F, 9.0F, 3.0F),
            PartPose.offsetAndRotation(1.2F, -0.8F, -0.8F, -0.12F, -0.28F, 0.22F)
        );
        leftRib.addOrReplaceChild(
            "left_lower_guard_rib",
            CubeListBuilder.create().texOffs(12, 18).mirror()
                .addBox(0.0F, -2.5F, -2.0F, 3.0F, 6.0F, 4.0F),
            PartPose.offsetAndRotation(1.0F, 2.8F, 0.8F, 0.16F, -0.34F, 0.18F)
        );
        final PartDefinition head = chest.addOrReplaceChild(
            "archfiend_head",
            CubeListBuilder.create().texOffs(28, 18).addBox(-4.0F, -5.0F, -3.5F, 8.0F, 6.0F, 7.0F),
            PartPose.offset(0.0F, -5.0F, -0.3F)
        );
        head.addOrReplaceChild(
            "molten_jaw",
            CubeListBuilder.create().texOffs(58, 18).addBox(-3.2F, -0.5F, -4.0F, 6.4F, 3.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, -0.2F, -1.0F, 0.12F, 0.0F, 0.0F)
        );
        final PartDefinition rightHorn = head.addOrReplaceChild(
            "right_branched_emberhorn",
            CubeListBuilder.create()
                .texOffs(80, 18).addBox(-7.0F, -2.0F, -1.5F, 7.0F, 3.0F, 3.0F)
                .texOffs(102, 18).addBox(-4.0F, -6.0F, -1.2F, 4.0F, 5.0F, 2.5F)
                .texOffs(116, 18).addBox(-2.0F, -5.0F, -1.0F, 2.0F, 4.0F, 2.0F),
            PartPose.offsetAndRotation(-3.2F, -4.0F, 0.0F, 0.08F, 0.22F, 0.24F)
        );
        rightHorn.addOrReplaceChild(
            "right_outer_horn_branch",
            CubeListBuilder.create().texOffs(102, 18)
                .addBox(-4.0F, -2.5F, -1.2F, 4.0F, 5.0F, 2.5F),
            PartPose.offsetAndRotation(-6.0F, -0.8F, 1.4F, -0.18F, 0.42F, 0.36F)
        );
        rightHorn.addOrReplaceChild(
            "right_crown_horn_branch",
            CubeListBuilder.create().texOffs(116, 18)
                .addBox(-2.0F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F),
            PartPose.offsetAndRotation(-3.8F, -3.4F, -1.8F, 0.28F, -0.38F, 0.46F)
        );
        final PartDefinition leftHorn = head.addOrReplaceChild(
            "left_branched_emberhorn",
            CubeListBuilder.create().texOffs(80, 18).mirror()
                .addBox(0.0F, -2.0F, -1.5F, 7.0F, 3.0F, 3.0F)
                .texOffs(102, 18).addBox(0.0F, -6.0F, -1.2F, 4.0F, 5.0F, 2.5F)
                .texOffs(116, 18).addBox(0.0F, -5.0F, -1.0F, 2.0F, 4.0F, 2.0F),
            PartPose.offsetAndRotation(3.2F, -4.0F, 0.0F, 0.08F, -0.22F, -0.24F)
        );
        leftHorn.addOrReplaceChild(
            "left_outer_horn_branch",
            CubeListBuilder.create().texOffs(102, 18).mirror()
                .addBox(0.0F, -2.5F, -1.2F, 4.0F, 5.0F, 2.5F),
            PartPose.offsetAndRotation(6.0F, -0.8F, 1.4F, -0.18F, -0.42F, -0.36F)
        );
        leftHorn.addOrReplaceChild(
            "left_crown_horn_branch",
            CubeListBuilder.create().texOffs(116, 18).mirror()
                .addBox(0.0F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F),
            PartPose.offsetAndRotation(3.8F, -3.4F, -1.8F, 0.28F, 0.38F, -0.46F)
        );

        final PartDefinition rightArm = root.addOrReplaceChild(
            "right_guard_arm",
            CubeListBuilder.create().texOffs(0, 42).addBox(-4.0F, -2.0F, -3.0F, 5.0F, 10.0F, 6.0F),
            PartPose.offsetAndRotation(-8.5F, 7.0F, -1.8F, 0.18F, 0.12F, 0.20F)
        );
        rightArm.addOrReplaceChild(
            "right_magma_forearm",
            CubeListBuilder.create().texOffs(0, 42)
                .addBox(-4.0F, -2.0F, -3.0F, 5.0F, 10.0F, 6.0F),
            PartPose.offsetAndRotation(-0.6F, 5.0F, -1.6F, -0.18F, 0.18F, 0.10F)
        );
        rightArm.addOrReplaceChild(
            "right_magma_fist",
            CubeListBuilder.create()
                .texOffs(24, 42).addBox(-4.5F, -1.0F, -4.0F, 7.0F, 7.0F, 8.0F)
                .texOffs(56, 42).addBox(-5.5F, 0.5F, -3.0F, 2.0F, 5.0F, 6.0F),
            PartPose.offset(-1.0F, 8.0F, 0.0F)
        );
        final PartDefinition leftArm = root.addOrReplaceChild(
            "left_guard_arm",
            CubeListBuilder.create().texOffs(0, 42).mirror().addBox(-1.0F, -2.0F, -3.0F, 5.0F, 10.0F, 6.0F),
            PartPose.offsetAndRotation(8.5F, 7.0F, 1.8F, 0.18F, -0.12F, -0.20F)
        );
        leftArm.addOrReplaceChild(
            "left_magma_forearm",
            CubeListBuilder.create().texOffs(0, 42).mirror()
                .addBox(-1.0F, -2.0F, -3.0F, 5.0F, 10.0F, 6.0F),
            PartPose.offsetAndRotation(0.6F, 5.0F, 1.6F, -0.18F, -0.18F, -0.10F)
        );
        leftArm.addOrReplaceChild(
            "left_magma_fist",
            CubeListBuilder.create().texOffs(24, 42).mirror()
                .addBox(-2.5F, -1.0F, -4.0F, 7.0F, 7.0F, 8.0F)
                .texOffs(56, 42).addBox(3.5F, 0.5F, -3.0F, 2.0F, 5.0F, 6.0F),
            PartPose.offset(1.0F, 8.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "right_heart_pauldron",
            CubeListBuilder.create().texOffs(74, 42).addBox(-6.0F, -2.0F, -3.5F, 7.0F, 4.0F, 7.0F),
            PartPose.offsetAndRotation(-6.5F, 5.0F, 0.0F, 0.0F, 0.0F, 0.12F)
        );
        root.addOrReplaceChild(
            "left_heart_pauldron",
            CubeListBuilder.create().texOffs(74, 42).mirror().addBox(-1.0F, -2.0F, -3.5F, 7.0F, 4.0F, 7.0F),
            PartPose.offsetAndRotation(6.5F, 5.0F, 0.0F, 0.0F, 0.0F, -0.12F)
        );
        final PartDefinition rightLeg = root.addOrReplaceChild(
            "right_leg",
            CubeListBuilder.create().texOffs(0, 66).addBox(-3.5F, 0.0F, -3.0F, 6.0F, 8.0F, 6.0F),
            PartPose.offsetAndRotation(-3.5F, 13.0F, 0.0F, 0.02F, 0.0F, 0.04F)
        );
        final PartDefinition rightHoof = rightLeg.addOrReplaceChild(
            "right_obsidian_hoof",
            CubeListBuilder.create().texOffs(26, 66).addBox(-4.0F, 0.0F, -4.5F, 7.0F, 3.0F, 7.0F),
            PartPose.offset(0.0F, 8.0F, 0.0F)
        );
        rightHoof.addOrReplaceChild(
            "right_inner_cloven_toe",
            CubeListBuilder.create().texOffs(132, 0)
                .addBox(-1.0F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F),
            PartPose.offsetAndRotation(-1.2F, 1.2F, -3.6F, -1.05F, -0.12F, 0.0F)
        );
        rightHoof.addOrReplaceChild(
            "right_outer_cloven_toe",
            CubeListBuilder.create().texOffs(132, 0)
                .addBox(-1.0F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F),
            PartPose.offsetAndRotation(-3.4F, 1.2F, -3.2F, -1.05F, 0.18F, 0.0F)
        );
        final PartDefinition leftLeg = root.addOrReplaceChild(
            "left_leg",
            CubeListBuilder.create().texOffs(0, 66).mirror().addBox(-2.5F, 0.0F, -3.0F, 6.0F, 8.0F, 6.0F),
            PartPose.offsetAndRotation(3.5F, 13.0F, 0.0F, 0.02F, 0.0F, -0.04F)
        );
        final PartDefinition leftHoof = leftLeg.addOrReplaceChild(
            "left_obsidian_hoof",
            CubeListBuilder.create().texOffs(26, 66).mirror().addBox(-3.0F, 0.0F, -4.5F, 7.0F, 3.0F, 7.0F),
            PartPose.offset(0.0F, 8.0F, 0.0F)
        );
        leftHoof.addOrReplaceChild(
            "left_inner_cloven_toe",
            CubeListBuilder.create().texOffs(132, 0).mirror()
                .addBox(-1.0F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F),
            PartPose.offsetAndRotation(1.2F, 1.2F, -3.6F, -1.05F, 0.12F, 0.0F)
        );
        leftHoof.addOrReplaceChild(
            "left_outer_cloven_toe",
            CubeListBuilder.create().texOffs(132, 0).mirror()
                .addBox(-1.0F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F),
            PartPose.offsetAndRotation(3.4F, 1.2F, -3.2F, -1.05F, -0.18F, 0.0F)
        );
        root.addOrReplaceChild(
            "back_rib_mantle",
            CubeListBuilder.create()
                .texOffs(56, 66).addBox(-7.0F, -4.0F, -1.0F, 14.0F, 3.0F, 3.0F)
                .texOffs(98, 66).addBox(-5.0F, -1.0F, -0.5F, 10.0F, 7.0F, 2.0F)
                .texOffs(126, 66).addBox(-1.5F, -6.0F, -1.0F, 3.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 7.0F, 3.0F, -0.1F, 0.0F, 0.0F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        archfiendHead.yRot += state.yRot * Mth.DEG_TO_RAD;
        archfiendHead.xRot += state.xRot * Mth.DEG_TO_RAD;
        final float pace = state.walkAnimationPos * 0.52F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F) * 0.85F;
        rightLeg.xRot += Mth.cos(pace) * stride;
        leftLeg.xRot += Mth.cos(pace + Mth.PI) * stride;
        rightGuardArm.xRot += Mth.cos(pace + Mth.PI) * stride * 0.45F;
        leftGuardArm.xRot += Mth.cos(pace) * stride * 0.45F;
        guardedHeartChest.y += Mth.sin(state.ageInTicks * 0.06F) * 0.1F;
        exposedDemonHeart.y += Mth.sin(state.ageInTicks * 0.18F) * 0.16F;
        heartCore.z -= Mth.sin(state.ageInTicks * 0.18F) * 0.08F;
        heartCrown.xRot += Mth.sin(state.ageInTicks * 0.09F) * 0.025F;
        backRibMantle.xRot += Mth.sin(state.ageInTicks * 0.07F) * 0.025F;
        final float eruption = Mth.clamp(state.eruptionProgress, 0.0F, 1.0F);
        rightGuardArm.xRot -= eruption * 1.22F;
        leftGuardArm.xRot -= eruption * 1.22F;
        rightGuardArm.yRot -= eruption * 0.5F;
        leftGuardArm.yRot += eruption * 0.5F;
        rightMagmaFist.xRot -= eruption * 0.22F;
        leftMagmaFist.xRot -= eruption * 0.22F;
        rightGuardRib.zRot -= eruption * 0.24F;
        leftGuardRib.zRot += eruption * 0.24F;
        moltenJaw.xRot += eruption * 0.34F;
        rightHeartPauldron.zRot -= eruption * 0.08F;
        leftHeartPauldron.zRot += eruption * 0.08F;
        rightBranchedEmberhorn.zRot -= eruption * 0.025F;
        leftBranchedEmberhorn.zRot += eruption * 0.025F;
        if (state.aggressive) guardedHeartChest.xRot += 0.12F;
    }

    public static void extractRenderState(
        final InfernalHierarchyEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.aggressive = entity.isAggressive();
        state.eruptionProgress = entity.presentationIntent() == Intent.EMBER_FRONT ? 1.0F : 0.0F;
    }

    public static final class State extends LivingEntityRenderState {
        public float eruptionProgress;
        public boolean aggressive;
    }
}
