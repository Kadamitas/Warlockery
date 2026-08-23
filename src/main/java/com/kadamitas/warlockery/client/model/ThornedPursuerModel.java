package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.ThornedPursuerEntity;
import com.kadamitas.warlockery.entity.ThornedPursuerRules;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public final class ThornedPursuerModel extends EntityModel<ThornedPursuerModel.State> {
    public static final int TEXTURE_WIDTH = 192;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart ritualTorso;
    private final ModelPart seedpodHead;
    private final ModelPart leftGrowthHorn;
    private final ModelPart rightGrowthHorn;
    private final ModelPart highHookedShoulders;
    private final ModelPart leftShoulderHook;
    private final ModelPart rightShoulderHook;
    private final ModelPart leftSickleArm;
    private final ModelPart leftSickleBlade;
    private final ModelPart rightSickleArm;
    private final ModelPart rightSickleBlade;
    private final ModelPart leftDigitigradeLeg;
    private final ModelPart leftRecurvedShin;
    private final ModelPart leftToeCluster;
    private final ModelPart rightDigitigradeLeg;
    private final ModelPart rightRecurvedShin;
    private final ModelPart rightToeCluster;
    private final ModelPart snareTendrils;
    private final ModelPart leftTrailingSnare;
    private final ModelPart leftSnareTip;
    private final ModelPart rightTrailingSnare;
    private final ModelPart rightSnareTip;
    private final ModelPart leftLowerSnare;
    private final ModelPart rightLowerSnare;

    public ThornedPursuerModel(final ModelPart root) {
        super(root);
        ritualTorso = root.getChild("ritual_torso");
        seedpodHead = ritualTorso.getChild("seedpod_head");
        leftGrowthHorn = seedpodHead.getChild("left_growth_horn");
        rightGrowthHorn = seedpodHead.getChild("right_growth_horn");
        highHookedShoulders = ritualTorso.getChild("high_hooked_shoulders");
        leftShoulderHook = highHookedShoulders.getChild("left_shoulder_hook");
        rightShoulderHook = highHookedShoulders.getChild("right_shoulder_hook");
        leftSickleArm = root.getChild("left_sickle_arm");
        leftSickleBlade = leftSickleArm.getChild("left_sickle_blade");
        rightSickleArm = root.getChild("right_sickle_arm");
        rightSickleBlade = rightSickleArm.getChild("right_sickle_blade");
        leftDigitigradeLeg = root.getChild("left_digitigrade_leg");
        leftRecurvedShin = leftDigitigradeLeg.getChild("left_recurved_shin");
        leftToeCluster = leftRecurvedShin.getChild("left_toe_cluster");
        rightDigitigradeLeg = root.getChild("right_digitigrade_leg");
        rightRecurvedShin = rightDigitigradeLeg.getChild("right_recurved_shin");
        rightToeCluster = rightRecurvedShin.getChild("right_toe_cluster");
        snareTendrils = ritualTorso.getChild("snare_tendrils");
        leftTrailingSnare = snareTendrils.getChild("left_trailing_snare");
        leftSnareTip = leftTrailingSnare.getChild("left_snare_tip");
        rightTrailingSnare = snareTendrils.getChild("right_trailing_snare");
        rightSnareTip = rightTrailingSnare.getChild("right_snare_tip");
        leftLowerSnare = snareTendrils.getChild("left_lower_snare");
        rightLowerSnare = snareTendrils.getChild("right_lower_snare");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition torso = root.addOrReplaceChild(
            "ritual_torso",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -7.0F, -3.0F, 8.0F, 12.0F, 6.0F)
                .texOffs(30, 0).addBox(-5.0F, -4.0F, -2.5F, 10.0F, 6.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, 6.730612F, 0.8F, 0.2F, 0.0F, 0.0F)
        );
        torso.addOrReplaceChild(
            "ritual_binding_belt",
            CubeListBuilder.create().texOffs(62, 0).addBox(-5.0F, -1.0F, -3.5F, 10.0F, 2.0F, 7.0F),
            PartPose.offsetAndRotation(0.0F, 3.0F, 0.0F, 0.0F, 0.0F, -0.08F)
        );
        final PartDefinition head = torso.addOrReplaceChild(
            "seedpod_head",
            CubeListBuilder.create().texOffs(98, 0).addBox(-3.5F, -4.0F, -6.0F, 7.0F, 7.0F, 9.0F),
            PartPose.offsetAndRotation(0.0F, -6.5F, -1.8F, -0.1F, 0.0F, 0.0F)
        );
        head.addOrReplaceChild("ember_eye_slit", CubeListBuilder.create().texOffs(132, 0).addBox(-2.0F, -1.0F, -1.0F, 4.0F, 2.0F, 1.0F), PartPose.offset(0.0F, -0.6F, -6.0F));
        final PartDefinition leftHorn = head.addOrReplaceChild(
            "left_growth_horn",
            CubeListBuilder.create().texOffs(144, 0).addBox(-1.0F, -1.5F, -10.0F, 2.0F, 3.0F, 10.0F),
            PartPose.offsetAndRotation(2.6F, -3.4F, 0.0F, 0.48F, -0.55F, 0.32F)
        );
        leftHorn.addOrReplaceChild("left_horn_tip", CubeListBuilder.create().texOffs(170, 0).addBox(-1.0F, -1.0F, -8.0F, 2.0F, 2.0F, 8.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -9.0F, 0.25F, -0.28F, 0.16F));
        final PartDefinition rightHorn = head.addOrReplaceChild(
            "right_growth_horn",
            CubeListBuilder.create().texOffs(0, 20).addBox(-1.0F, -1.5F, -10.0F, 2.0F, 3.0F, 10.0F),
            PartPose.offsetAndRotation(-2.6F, -3.4F, 0.0F, 0.48F, 0.55F, -0.32F)
        );
        rightHorn.addOrReplaceChild("right_horn_tip", CubeListBuilder.create().texOffs(26, 20).addBox(-1.0F, -1.0F, -8.0F, 2.0F, 2.0F, 8.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -9.0F, 0.25F, 0.28F, -0.16F));

        final PartDefinition shoulders = torso.addOrReplaceChild(
            "high_hooked_shoulders",
            CubeListBuilder.create().texOffs(48, 20).addBox(-8.0F, -1.5F, -2.5F, 16.0F, 3.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, -5.2F, 0.4F, 0.0F, 0.0F, -0.04F)
        );
        shoulders.addOrReplaceChild("left_shoulder_hook", CubeListBuilder.create().texOffs(92, 20).addBox(-1.5F, -9.0F, -1.5F, 3.0F, 9.0F, 3.0F), PartPose.offsetAndRotation(7.0F, 0.0F, 0.0F, -0.18F, 0.0F, 0.46F));
        shoulders.addOrReplaceChild("right_shoulder_hook", CubeListBuilder.create().texOffs(106, 20).addBox(-1.5F, -8.0F, -1.5F, 3.0F, 8.0F, 3.0F), PartPose.offsetAndRotation(-7.0F, 0.0F, 0.0F, -0.16F, 0.0F, -0.5F));

        final PartDefinition leftArm = root.addOrReplaceChild(
            "left_sickle_arm",
            CubeListBuilder.create().texOffs(120, 20).addBox(-1.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offsetAndRotation(7.2F, 5.730612F, -0.8F, -0.16F, -0.12F, -0.42F)
        );
        leftArm.addOrReplaceChild(
            "left_sickle_blade",
            CubeListBuilder.create().texOffs(138, 20).addBox(-1.5F, 0.0F, -9.0F, 3.0F, 13.0F, 3.0F),
            PartPose.offsetAndRotation(1.2F, 10.0F, 0.0F, -0.22F, -0.16F, -0.42F)
        );
        final PartDefinition rightArm = root.addOrReplaceChild(
            "right_sickle_arm",
            CubeListBuilder.create().texOffs(152, 20).addBox(-3.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offsetAndRotation(-7.2F, 5.730612F, -0.8F, -0.16F, 0.12F, 0.42F)
        );
        rightArm.addOrReplaceChild(
            "right_sickle_blade",
            CubeListBuilder.create().texOffs(170, 20).addBox(-1.5F, 0.0F, -9.0F, 3.0F, 13.0F, 3.0F),
            PartPose.offsetAndRotation(-1.2F, 10.0F, 0.0F, -0.22F, 0.16F, 0.42F)
        );

        final PartDefinition leftLeg = root.addOrReplaceChild("left_digitigrade_leg", CubeListBuilder.create().texOffs(0, 40).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 8.0F, 5.0F), PartPose.offsetAndRotation(3.8F, 5.270612F, 1.2F, -0.48F, 0.0F, -0.1F));
        final PartDefinition leftShin = leftLeg.addOrReplaceChild("left_recurved_shin", CubeListBuilder.create().texOffs(22, 40).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 7.5F, 2.2F, 0.88F, 0.0F, 0.0F));
        final PartDefinition leftToe = leftShin.addOrReplaceChild("left_toe_cluster", CubeListBuilder.create().texOffs(40, 40).addBox(-3.0F, 0.0F, -7.0F, 6.0F, 3.0F, 10.0F), PartPose.offsetAndRotation(0.0F, 7.5F, -2.0F, -0.42F, -0.08F, 0.0F));
        leftToe.addOrReplaceChild("left_inner_toe", CubeListBuilder.create().texOffs(20, 60).addBox(-1.5F, -8.0F, -1.5F, 3.0F, 8.0F, 3.0F), PartPose.offsetAndRotation(-2.0F, 1.0F, -4.0F, -1.25F, -0.24F, -0.12F));
        leftToe.addOrReplaceChild("left_outer_toe", CubeListBuilder.create().texOffs(20, 60).addBox(-1.5F, -8.0F, -1.5F, 3.0F, 8.0F, 3.0F), PartPose.offsetAndRotation(2.0F, 1.0F, -4.0F, -1.25F, 0.24F, 0.12F));
        final PartDefinition rightLeg = root.addOrReplaceChild("right_digitigrade_leg", CubeListBuilder.create().texOffs(74, 40).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 8.0F, 5.0F), PartPose.offsetAndRotation(-3.8F, 5.270612F, 1.2F, -0.48F, 0.0F, 0.1F));
        final PartDefinition rightShin = rightLeg.addOrReplaceChild("right_recurved_shin", CubeListBuilder.create().texOffs(96, 40).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 7.5F, 2.2F, 0.88F, 0.0F, 0.0F));
        final PartDefinition rightToe = rightShin.addOrReplaceChild("right_toe_cluster", CubeListBuilder.create().texOffs(114, 40).addBox(-3.0F, 0.0F, -7.0F, 6.0F, 3.0F, 10.0F), PartPose.offsetAndRotation(0.0F, 7.5F, -2.0F, -0.42F, 0.08F, 0.0F));
        rightToe.addOrReplaceChild("right_inner_toe", CubeListBuilder.create().texOffs(34, 60).addBox(-1.5F, -7.0F, -1.5F, 3.0F, 7.0F, 3.0F), PartPose.offsetAndRotation(2.0F, 1.0F, -4.0F, -1.25F, 0.24F, 0.12F));
        rightToe.addOrReplaceChild("right_outer_toe", CubeListBuilder.create().texOffs(34, 60).addBox(-1.5F, -7.0F, -1.5F, 3.0F, 7.0F, 3.0F), PartPose.offsetAndRotation(-2.0F, 1.0F, -4.0F, -1.25F, -0.24F, -0.12F));

        final PartDefinition tendrils = torso.addOrReplaceChild(
            "snare_tendrils",
            CubeListBuilder.create().texOffs(148, 40).addBox(-2.5F, -2.0F, -2.5F, 5.0F, 4.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, -0.5F, 3.0F, 0.0F, 0.0F, 0.0F)
        );
        final PartDefinition leftSnare = tendrils.addOrReplaceChild("left_trailing_snare", CubeListBuilder.create().texOffs(170, 40).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 15.0F, 2.0F), PartPose.offsetAndRotation(2.0F, 0.0F, 1.0F, 0.16F, 0.12F, -1.02F));
        leftSnare.addOrReplaceChild("left_snare_tip", CubeListBuilder.create().texOffs(180, 40).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 13.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 14.0F, 0.0F, 0.22F, 0.18F, 0.72F));
        final PartDefinition rightSnare = tendrils.addOrReplaceChild("right_trailing_snare", CubeListBuilder.create().texOffs(0, 60).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 14.0F, 2.0F), PartPose.offsetAndRotation(-2.0F, 0.0F, 1.0F, 0.14F, -0.12F, 1.02F));
        rightSnare.addOrReplaceChild("right_snare_tip", CubeListBuilder.create().texOffs(10, 60).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 12.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 13.0F, 0.0F, 0.22F, -0.18F, -0.72F));
        tendrils.addOrReplaceChild(
            "left_lower_snare",
            CubeListBuilder.create().texOffs(48, 60).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 12.0F, 2.0F),
            PartPose.offsetAndRotation(1.0F, 1.0F, 1.5F, 0.22F, 0.28F, -0.72F)
        );
        tendrils.addOrReplaceChild(
            "right_lower_snare",
            CubeListBuilder.create().texOffs(58, 60).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 12.0F, 2.0F),
            PartPose.offsetAndRotation(-1.0F, 1.0F, 1.5F, 0.2F, -0.28F, 0.72F)
        );
        torso.addOrReplaceChild("left_back_thorn", CubeListBuilder.create().texOffs(20, 60).addBox(-1.5F, -8.0F, -1.5F, 3.0F, 8.0F, 3.0F), PartPose.offsetAndRotation(2.8F, 2.0F, 2.8F, -0.52F, 0.0F, 0.3F));
        torso.addOrReplaceChild("right_back_thorn", CubeListBuilder.create().texOffs(34, 60).addBox(-1.5F, -7.0F, -1.5F, 3.0F, 7.0F, 3.0F), PartPose.offsetAndRotation(-2.8F, 2.0F, 2.8F, -0.56F, 0.0F, -0.34F));
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        seedpodHead.yRot += state.yRot * Mth.DEG_TO_RAD;
        seedpodHead.xRot += state.xRot * Mth.DEG_TO_RAD * 0.82F;
        final float pace = state.walkAnimationPos * 0.74F;
        final float stalk = Math.min(state.walkAnimationSpeed, 1.0F) * 0.74F;
        final float leftStep = Mth.cos(pace) * stalk;
        final float rightStep = Mth.cos(pace + Mth.PI) * stalk;
        leftDigitigradeLeg.xRot += leftStep;
        rightDigitigradeLeg.xRot += rightStep;
        leftRecurvedShin.xRot -= leftStep * 0.56F;
        rightRecurvedShin.xRot -= rightStep * 0.56F;
        leftToeCluster.xRot += leftStep * 0.18F;
        rightToeCluster.xRot += rightStep * 0.18F;
        leftSickleArm.xRot += rightStep * 0.54F;
        rightSickleArm.xRot += leftStep * 0.54F;
        leftSickleBlade.xRot -= rightStep * 0.22F;
        rightSickleBlade.xRot -= leftStep * 0.22F;
        ritualTorso.xRot += stalk * 0.1F;
        leftGrowthHorn.zRot += Mth.sin(state.ageInTicks * 0.035F) * 0.025F;
        rightGrowthHorn.zRot -= Mth.sin(state.ageInTicks * 0.035F + 0.5F) * 0.025F;
        leftTrailingSnare.zRot += Mth.sin(state.ageInTicks * 0.09F) * 0.15F;
        leftSnareTip.zRot += Mth.sin(state.ageInTicks * 0.09F + 0.7F) * 0.24F;
        rightTrailingSnare.zRot -= Mth.sin(state.ageInTicks * 0.085F + 0.4F) * 0.14F;
        rightSnareTip.zRot -= Mth.sin(state.ageInTicks * 0.085F + 1.1F) * 0.22F;
        leftLowerSnare.zRot += Mth.sin(state.ageInTicks * 0.073F + 1.7F) * 0.12F;
        rightLowerSnare.zRot -= Mth.sin(state.ageInTicks * 0.077F + 2.1F) * 0.13F;
        if (state.recovering) {
            ritualTorso.xRot += 0.28F;
            leftDigitigradeLeg.xRot -= 0.38F;
            rightDigitigradeLeg.xRot += 0.18F;
            leftRecurvedShin.xRot += 0.42F;
            rightSickleArm.xRot += 0.36F;
        }
        final float cast = Mth.clamp(state.actionProgress, 0.0F, 1.0F);
        if (state.anchored) {
            rightDigitigradeLeg.xRot += 0.24F;
            rightRecurvedShin.xRot -= 0.32F;
            rightToeCluster.xRot += 0.16F;
        }
        if (state.snaring) {
            ritualTorso.xRot -= 0.18F * cast;
            highHookedShoulders.yRot += 0.18F * cast;
            leftShoulderHook.zRot += 0.14F * cast;
            rightShoulderHook.zRot -= 0.14F * cast;
            leftSickleArm.xRot -= 1.0F * cast;
            leftSickleArm.yRot -= 0.62F * cast;
            leftSickleBlade.xRot -= 0.5F * cast;
            rightSickleArm.xRot -= 0.42F * cast;
            snareTendrils.xRot -= 0.36F * cast;
            leftTrailingSnare.xRot -= 0.68F * cast;
            leftTrailingSnare.yRot -= 0.48F * cast;
            rightTrailingSnare.xRot -= 0.58F * cast;
            rightTrailingSnare.yRot += 0.42F * cast;
            leftSnareTip.xRot -= 0.32F * cast;
            rightSnareTip.xRot -= 0.28F * cast;
            leftLowerSnare.xRot -= 0.46F * cast;
            leftLowerSnare.yRot -= 0.32F * cast;
            rightLowerSnare.xRot -= 0.42F * cast;
            rightLowerSnare.yRot += 0.3F * cast;
        }
    }

    public static void extractRenderState(
        final ThornedPursuerEntity entity,
        final State state,
        final float partialTicks
    ) {
        final ThornedPursuerRules.Phase phase = entity.presentationPhase();
        state.anchored = phase == ThornedPursuerRules.Phase.ANCHORED
            || phase == ThornedPursuerRules.Phase.RECOVER;
        state.recovering = phase == ThornedPursuerRules.Phase.RECOVER;
        state.snaring = phase == ThornedPursuerRules.Phase.SET
            || phase == ThornedPursuerRules.Phase.PRESS;
        state.snareCooldownRemaining = entity.presentationSnareCooldownRemaining();
        state.actionProgress = state.snaring ? 1.0F : entity.getAttackAnim(partialTicks);
    }

    public static final class State extends LivingEntityRenderState {
        public int snareCooldownRemaining;
        public float actionProgress;
        public boolean anchored;
        public boolean recovering;
        public boolean snaring;
    }
}
