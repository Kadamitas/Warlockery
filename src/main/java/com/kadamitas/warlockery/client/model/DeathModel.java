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

    private final ModelPart appointmentShroud;
    private final ModelPart voidHood;
    private final ModelPart rightSleeve;
    private final ModelPart leftSleeve;
    private final ModelPart ironScythe;
    private final ModelPart splitRobe;
    private final ModelPart rightRobeTail;
    private final ModelPart leftRobeTail;
    private final ModelPart outerRobe;

    public DeathModel(final ModelPart root) {
        super(root);
        appointmentShroud = root.getChild("appointment_shroud");
        voidHood = appointmentShroud.getChild("void_hood");
        rightSleeve = appointmentShroud.getChild("right_sleeve");
        leftSleeve = appointmentShroud.getChild("left_sleeve");
        ironScythe = rightSleeve.getChild("iron_scythe");
        splitRobe = appointmentShroud.getChild("split_robe");
        rightRobeTail = splitRobe.getChild("right_robe_tail");
        leftRobeTail = splitRobe.getChild("left_robe_tail");
        outerRobe = appointmentShroud.getChild("outer_robe");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition shroud = root.addOrReplaceChild(
            "appointment_shroud",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -2.0F, -2.5F, 8.0F, 10.0F, 5.0F)
                .texOffs(28, 0).addBox(-5.0F, -1.5F, -3.0F, 10.0F, 3.0F, 6.0F),
            PartPose.offset(0.0F, 6.0F, 0.0F)
        );
        final PartDefinition hood = shroud.addOrReplaceChild(
            "void_hood",
            CubeListBuilder.create().texOffs(60, 0).addBox(-4.5F, -6.5F, -3.5F, 9.0F, 8.0F, 7.0F),
            PartPose.offset(0.0F, -1.0F, -0.2F)
        );
        hood.addOrReplaceChild(
            "skull_face",
            CubeListBuilder.create().texOffs(92, 0).addBox(-3.0F, -2.5F, -0.5F, 6.0F, 5.0F, 1.0F),
            PartPose.offset(0.0F, -1.3F, -3.5F)
        );
        hood.addOrReplaceChild(
            "right_blue_eye",
            CubeListBuilder.create().texOffs(110, 0).addBox(-0.75F, -0.5F, -0.5F, 1.5F, 1.0F, 1.0F),
            PartPose.offset(-1.5F, -1.6F, -4.1F)
        );
        hood.addOrReplaceChild(
            "left_blue_eye",
            CubeListBuilder.create().texOffs(116, 0).addBox(-0.75F, -0.5F, -0.5F, 1.5F, 1.0F, 1.0F),
            PartPose.offset(1.5F, -1.6F, -4.1F)
        );
        hood.addOrReplaceChild(
            "hourglass_crown",
            CubeListBuilder.create().texOffs(116, 8).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F),
            PartPose.offset(0.0F, -6.2F, 0.0F)
        );
        shroud.addOrReplaceChild(
            "right_mantle",
            CubeListBuilder.create().texOffs(0, 24).addBox(-5.0F, -1.5F, -2.5F, 5.0F, 3.0F, 5.0F),
            PartPose.offsetAndRotation(-4.5F, 0.0F, 0.0F, 0.0F, 0.0F, -0.16F)
        );
        shroud.addOrReplaceChild(
            "left_mantle",
            CubeListBuilder.create().texOffs(0, 24).mirror().addBox(0.0F, -1.5F, -2.5F, 5.0F, 3.0F, 5.0F),
            PartPose.offsetAndRotation(4.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.16F)
        );
        final PartDefinition rightSleeve = shroud.addOrReplaceChild(
            "right_sleeve",
            CubeListBuilder.create().texOffs(24, 24).addBox(-3.0F, -1.0F, -2.0F, 4.0F, 11.0F, 4.0F),
            PartPose.offsetAndRotation(-5.5F, 1.0F, 0.0F, -0.08F, 0.0F, 0.12F)
        );
        final PartDefinition scythe = rightSleeve.addOrReplaceChild(
            "iron_scythe",
            CubeListBuilder.create().texOffs(48, 24).addBox(-0.5F, -17.0F, -0.5F, 1.0F, 25.0F, 1.0F),
            PartPose.offsetAndRotation(-2.0F, 8.0F, 0.0F, 0.0F, 0.0F, -0.1F)
        );
        final PartDefinition blade = scythe.addOrReplaceChild(
            "crescent_blade",
            CubeListBuilder.create().texOffs(56, 24).addBox(-8.0F, -1.0F, -0.5F, 8.0F, 2.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, -16.5F, 0.0F, 0.0F, 0.0F, -0.18F)
        );
        blade.addOrReplaceChild(
            "blade_tip",
            CubeListBuilder.create().texOffs(76, 24).addBox(-5.0F, -0.75F, -0.5F, 5.0F, 1.5F, 1.0F),
            PartPose.offsetAndRotation(-7.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.55F)
        );
        final PartDefinition leftSleeve = shroud.addOrReplaceChild(
            "left_sleeve",
            CubeListBuilder.create().texOffs(24, 24).mirror().addBox(-1.0F, -1.0F, -2.0F, 4.0F, 11.0F, 4.0F),
            PartPose.offsetAndRotation(5.5F, 1.0F, 0.0F, -0.08F, 0.0F, -0.12F)
        );
        leftSleeve.addOrReplaceChild(
            "appointment_lantern",
            CubeListBuilder.create().texOffs(80, 24).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 5.0F, 3.0F),
            PartPose.offset(1.0F, 9.0F, 0.0F)
        );
        shroud.addOrReplaceChild(
            "outer_robe",
            CubeListBuilder.create().texOffs(60, 50).addBox(-5.5F, 0.0F, -3.5F, 11.0F, 7.0F, 7.0F),
            PartPose.offset(0.0F, 7.0F, 0.0F)
        );
        final PartDefinition robe = shroud.addOrReplaceChild(
            "split_robe",
            CubeListBuilder.create().texOffs(90, 24).addBox(-5.0F, 0.0F, -3.5F, 10.0F, 6.0F, 7.0F),
            PartPose.offset(0.0F, 12.0F, 0.0F)
        );
        robe.addOrReplaceChild(
            "right_robe_tail",
            CubeListBuilder.create().texOffs(0, 50).addBox(-5.5F, 0.0F, -3.0F, 5.5F, 10.0F, 6.0F),
            PartPose.offsetAndRotation(-0.2F, 4.0F, 0.0F, 0.02F, 0.0F, 0.04F)
        );
        robe.addOrReplaceChild(
            "left_robe_tail",
            CubeListBuilder.create().texOffs(30, 50).addBox(0.0F, 0.0F, -3.0F, 5.5F, 10.0F, 6.0F),
            PartPose.offsetAndRotation(0.2F, 4.0F, 0.0F, -0.02F, 0.0F, -0.04F)
        );
        shroud.addOrReplaceChild(
            "timestamp_spine",
            CubeListBuilder.create().texOffs(116, 50).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 7.0F, 1.0F),
            PartPose.offset(0.0F, 3.0F, 3.0F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        voidHood.yRot = state.yRot * Mth.DEG_TO_RAD;
        voidHood.xRot = state.xRot * Mth.DEG_TO_RAD;
        final float drift = Math.min(state.walkAnimationSpeed, 1.0F) * 0.22F;
        final float pace = state.walkAnimationPos * 0.48F;
        rightRobeTail.xRot = Mth.cos(pace) * drift;
        leftRobeTail.xRot = Mth.cos(pace + Mth.PI) * drift;
        outerRobe.xRot = Mth.sin(state.ageInTicks * 0.04F) * 0.025F;
        rightSleeve.xRot += Mth.cos(pace + Mth.PI) * drift * 0.5F;
        leftSleeve.xRot += Mth.cos(pace) * drift * 0.5F;
        final float reap = Mth.clamp(state.reapProgress, 0.0F, 1.0F);
        rightSleeve.xRot -= reap * 1.4F;
        rightSleeve.yRot -= reap * 0.55F;
        ironScythe.zRot += reap * 0.95F;
        if (state.telegraph) {
            appointmentShroud.y -= 0.45F;
        }
    }

    @Override
    public void translateToHand(final State state, final HumanoidArm arm, final PoseStack poseStack) {
        appointmentShroud.translateAndRotate(poseStack);
        (arm == HumanoidArm.LEFT ? leftSleeve : rightSleeve).translateAndRotate(poseStack);
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
