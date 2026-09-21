package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.DeathEntity;
import com.kadamitas.warlockery.entity.DeathRules.Phase;
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

public final class DeathModel extends EntityModel<DeathModel.State> implements ArmedModel<DeathModel.State> {
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart reaper;
    private final ModelPart hood;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart scythe;
    private final ModelPart blade;
    private final ModelPart rightRobeTail;
    private final ModelPart leftRobeTail;

    public DeathModel(final ModelPart root) {
        super(root);
        reaper = root.getChild("reaper");
        hood = reaper.getChild("hood");
        rightArm = reaper.getChild("right_arm");
        leftArm = reaper.getChild("left_arm");
        scythe = rightArm.getChild("scythe");
        blade = scythe.getChild("blade");
        final ModelPart robe = reaper.getChild("split_robe");
        rightRobeTail = robe.getChild("right_robe_tail");
        leftRobeTail = robe.getChild("left_robe_tail");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition reaper = root.addOrReplaceChild(
            "reaper",
            CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -2.0F, -2.0F, 6.0F, 10.0F, 4.0F),
            PartPose.offset(0.0F, 6.0F, 0.0F)
        );

        final PartDefinition hood = reaper.addOrReplaceChild(
            "hood",
            CubeListBuilder.create()
                .texOffs(22, 0).addBox(-3.5F, -7.0F, -3.0F, 7.0F, 7.0F, 6.0F)
                .texOffs(72, 50).addBox(-4.5F, -1.5F, -3.5F, 9.0F, 2.0F, 7.0F),
            PartPose.offset(0.0F, -1.5F, -0.2F)
        );
        hood.addOrReplaceChild(
            "skull",
            CubeListBuilder.create().texOffs(50, 0).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 4.0F, 1.0F),
            PartPose.offset(0.0F, -3.1F, -3.2F)
        );
        final PartDefinition jaw = hood.addOrReplaceChild(
            "jaw",
            CubeListBuilder.create().texOffs(72, 0).addBox(-1.5F, -0.5F, -0.5F, 3.0F, 2.0F, 1.0F),
            PartPose.offset(0.0F, -1.0F, -3.2F)
        );
        jaw.addOrReplaceChild(
            "teeth",
            CubeListBuilder.create().texOffs(82, 0).addBox(-1.5F, -0.5F, -0.25F, 3.0F, 1.0F, 0.5F),
            PartPose.offset(0.0F, 0.0F, -0.65F)
        );
        final PartDefinition rightSocket = hood.addOrReplaceChild(
            "right_eye",
            CubeListBuilder.create().texOffs(88, 0).addBox(-1.0F, -1.0F, -0.25F, 2.0F, 2.0F, 0.5F),
            PartPose.offset(-1.25F, -3.5F, -3.8F)
        );
        rightSocket.addOrReplaceChild(
            "pupil",
            CubeListBuilder.create().texOffs(96, 0).addBox(-0.5F, -0.5F, -0.25F, 1.0F, 1.0F, 0.5F),
            PartPose.offset(0.0F, 0.0F, -0.35F)
        );
        final PartDefinition leftSocket = hood.addOrReplaceChild(
            "left_eye",
            CubeListBuilder.create().texOffs(88, 0).mirror().addBox(-1.0F, -1.0F, -0.25F, 2.0F, 2.0F, 0.5F),
            PartPose.offset(1.25F, -3.5F, -3.8F)
        );
        leftSocket.addOrReplaceChild(
            "pupil",
            CubeListBuilder.create().texOffs(96, 0).mirror().addBox(-0.5F, -0.5F, -0.25F, 1.0F, 1.0F, 0.5F),
            PartPose.offset(0.0F, 0.0F, -0.35F)
        );
        hood.addOrReplaceChild(
            "nasal_cavity",
            CubeListBuilder.create().texOffs(102, 0).addBox(-0.5F, -0.5F, -0.25F, 1.0F, 1.0F, 0.5F),
            PartPose.offset(0.0F, -2.1F, -3.85F)
        );

        final PartDefinition rightArm = reaper.addOrReplaceChild(
            "right_arm",
            CubeListBuilder.create().texOffs(0, 18).addBox(-2.5F, -1.0F, -1.5F, 3.0F, 10.0F, 3.0F),
            PartPose.offsetAndRotation(-3.5F, -0.5F, 0.0F, -0.18F, 0.0F, 0.10F)
        );
        rightArm.addOrReplaceChild(
            "skeletal_hand",
            CubeListBuilder.create().texOffs(14, 18).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F),
            PartPose.offset(-1.0F, 9.0F, 0.0F)
        );
        final PartDefinition scythe = rightArm.addOrReplaceChild(
            "scythe",
            CubeListBuilder.create().texOffs(24, 18).addBox(-0.5F, -18.0F, -0.5F, 1.0F, 26.0F, 1.0F),
            PartPose.offsetAndRotation(-1.4F, 8.0F, 0.0F, 0.0F, 0.0F, -0.14F)
        );
        final PartDefinition blade = scythe.addOrReplaceChild(
            "blade",
            CubeListBuilder.create().texOffs(30, 18).addBox(-10.0F, -1.0F, -0.5F, 10.0F, 2.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, -17.5F, 0.0F, 0.0F, 0.0F, -0.24F)
        );
        blade.addOrReplaceChild(
            "blade_tip",
            CubeListBuilder.create().texOffs(54, 18).addBox(-5.0F, -0.5F, -0.5F, 5.0F, 1.0F, 1.0F),
            PartPose.offsetAndRotation(-9.2F, 0.0F, 0.0F, 0.0F, 0.0F, -0.72F)
        );

        final PartDefinition leftArm = reaper.addOrReplaceChild(
            "left_arm",
            CubeListBuilder.create().texOffs(0, 18).mirror().addBox(-0.5F, -1.0F, -1.5F, 3.0F, 10.0F, 3.0F),
            PartPose.offsetAndRotation(3.5F, -0.5F, 0.0F, -0.12F, 0.0F, -0.10F)
        );
        leftArm.addOrReplaceChild(
            "skeletal_hand",
            CubeListBuilder.create().texOffs(14, 18).mirror().addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F),
            PartPose.offset(1.0F, 9.0F, 0.0F)
        );

        final PartDefinition robe = reaper.addOrReplaceChild(
            "split_robe",
            CubeListBuilder.create().texOffs(0, 50).addBox(-3.5F, 0.0F, -2.5F, 7.0F, 5.0F, 5.0F),
            PartPose.offset(0.0F, 8.0F, 0.0F)
        );
        robe.addOrReplaceChild(
            "right_robe_tail",
            CubeListBuilder.create().texOffs(26, 50).addBox(-3.2F, 0.0F, -2.0F, 3.0F, 11.0F, 4.0F),
            PartPose.offsetAndRotation(-0.15F, 4.0F, 0.0F, 0.06F, 0.0F, 0.04F)
        );
        robe.addOrReplaceChild(
            "left_robe_tail",
            CubeListBuilder.create().texOffs(26, 50).mirror().addBox(0.2F, 0.0F, -2.0F, 3.0F, 11.0F, 4.0F),
            PartPose.offsetAndRotation(0.15F, 4.0F, 0.0F, -0.06F, 0.0F, -0.04F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        hood.yRot = state.yRot * Mth.DEG_TO_RAD;
        hood.xRot = state.xRot * Mth.DEG_TO_RAD;
        final float pace = state.walkAnimationPos * 0.52F;
        final float drift = Math.min(state.walkAnimationSpeed, 1.0F) * 0.28F;
        rightRobeTail.xRot += Mth.cos(pace) * drift;
        leftRobeTail.xRot += Mth.cos(pace + Mth.PI) * drift;
        rightArm.xRot += Mth.cos(pace + Mth.PI) * drift * 0.45F;
        leftArm.xRot += Mth.cos(pace) * drift * 0.45F;
        reaper.y += Mth.sin(state.ageInTicks * 0.08F) * 0.18F;

        final float reap = Mth.clamp(state.reapProgress, 0.0F, 1.0F);
        final float attackSwing = Mth.sin(Mth.sqrt(Mth.clamp(state.swingAnimation, 0.0F, 1.0F)) * Mth.PI);
        final float swing = Math.max(reap, attackSwing);
        if (swing > 0.0F) {
            rightArm.xRot -= 0.75F + swing * 1.15F;
            rightArm.yRot -= 0.22F + swing * 0.48F;
            scythe.zRot += 0.28F + swing * 1.05F;
            blade.xRot -= swing * 0.16F;
        }
        if (state.telegraph) {
            rightArm.xRot -= 1.15F;
            rightArm.yRot += 0.42F;
            scythe.zRot -= 0.36F;
            hood.xRot -= 0.12F;
        }
    }

    @Override
    public void translateToHand(final State state, final HumanoidArm arm, final PoseStack poseStack) {
        reaper.translateAndRotate(poseStack);
        (arm == HumanoidArm.LEFT ? leftArm : rightArm).translateAndRotate(poseStack);
    }

    public static void extractRenderState(final DeathEntity entity, final State state, final float partialTicks) {
        final Phase phase = entity.presentationPhase();
        state.telegraph = phase == Phase.TELEGRAPH;
        state.reapProgress = phase == Phase.REAP ? 1.0F : 0.0F;
    }

    public static final class State extends ArmedEntityRenderState {
        public float reapProgress;
        public boolean telegraph;
    }
}
