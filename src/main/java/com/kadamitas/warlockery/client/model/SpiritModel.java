package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.SpiritEntity;
import com.kadamitas.warlockery.entity.SpiritRules.Phase;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/** Independent warding spirit built around a warm golden essence rather than humanoid anatomy. */
public final class SpiritModel extends EntityModel<SpiritModel.State> {
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart head;
    private final ModelPart torso;
    private final ModelPart sparkCore;
    private final ModelPart sparkCrown;
    private final ModelPart sparkTrail;
    private final ModelPart haloLeft;
    private final ModelPart haloRight;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightPalm;
    private final ModelPart leftPalm;
    private final ModelPart leftTail;
    private final ModelPart rightTail;

    public SpiritModel(final ModelPart root) {
        super(root);
        head = root.getChild("mask_head");
        torso = root.getChild("solid_torso");
        sparkCore = torso.getChild("golden_spirit_spark_core");
        sparkCrown = sparkCore.getChild("spark_crown");
        sparkTrail = sparkCore.getChild("spark_trail");
        haloLeft = root.getChild("halo_left");
        haloRight = root.getChild("halo_right");
        rightArm = root.getChild("right_guard_arm");
        leftArm = root.getChild("left_guard_arm");
        rightPalm = rightArm.getChild("right_guard_forearm").getChild("right_ward_palm");
        leftPalm = leftArm.getChild("left_guard_forearm").getChild("left_ward_palm");
        leftTail = root.getChild("vapor_tail_left");
        rightTail = root.getChild("vapor_tail_right");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
            "mask_head",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.5F, -4.0F, -3.0F, 7.0F, 7.0F, 6.0F)
                .texOffs(26, 0).addBox(-2.0F, -1.0F, -3.3F, 4.0F, 2.0F, 1.0F),
            PartPose.offset(0.0F, 7.0F, -1.0F)
        );
        final PartDefinition torso = root.addOrReplaceChild(
            "solid_torso",
            CubeListBuilder.create()
                .texOffs(0, 16).addBox(-4.0F, -2.0F, -3.0F, 8.0F, 10.0F, 6.0F)
                .texOffs(28, 16).addBox(-2.5F, 7.0F, -2.0F, 5.0F, 6.0F, 4.0F)
                .texOffs(28, 16).addBox(-5.5F, -1.0F, -2.0F, 5.0F, 6.0F, 4.0F)
                .texOffs(28, 16).addBox(0.5F, -1.0F, -2.0F, 5.0F, 6.0F, 4.0F),
            PartPose.offset(0.0F, 10.0F, 0.0F)
        );
        final PartDefinition core = torso.addOrReplaceChild(
            "golden_spirit_spark_core",
            CubeListBuilder.create()
                .texOffs(48, 16).addBox(-1.8F, -2.0F, -0.8F, 3.6F, 4.0F, 1.6F)
                .texOffs(60, 16).addBox(-1.1F, -2.8F, -0.6F, 2.2F, 1.4F, 1.2F)
                .texOffs(70, 16).addBox(-1.1F, 1.4F, -0.6F, 2.2F, 1.4F, 1.2F),
            PartPose.offset(0.0F, 1.5F, -3.0F)
        );
        core.addOrReplaceChild(
            "spark_crown",
            CubeListBuilder.create()
                .texOffs(80, 16).addBox(-1.8F, -1.5F, -0.5F, 1.0F, 2.0F, 1.0F)
                .texOffs(86, 16).addBox(-0.5F, -2.2F, -0.5F, 1.0F, 2.7F, 1.0F)
                .texOffs(92, 16).addBox(0.8F, -1.5F, -0.5F, 1.0F, 2.0F, 1.0F),
            PartPose.offset(0.0F, -3.0F, 0.0F)
        );
        core.addOrReplaceChild(
            "spark_trail",
            CubeListBuilder.create()
                .texOffs(98, 16).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 1.0F, 1.0F)
                .texOffs(104, 16).addBox(0.8F, 1.5F, -0.4F, 0.8F, 0.8F, 0.8F)
                .texOffs(110, 16).addBox(-1.3F, 2.7F, -0.3F, 0.6F, 0.6F, 0.6F),
            PartPose.offset(0.0F, 2.8F, 0.0F)
        );

        final PartDefinition haloLeft = root.addOrReplaceChild(
            "halo_left", CubeListBuilder.create(), PartPose.offsetAndRotation(-10.5F, 7.0F, 1.0F, 0.0F, 0.0F, -0.08F)
        );
        haloLeft.addOrReplaceChild(
            "halo_left_upper",
            CubeListBuilder.create().texOffs(48, 0).addBox(-4.0F, -2.0F, -2.0F, 8.0F, 4.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, -1.0F, 0.0F, 0.0F, 0.0F, -0.18F)
        );
        haloLeft.addOrReplaceChild(
            "halo_left_lower",
            CubeListBuilder.create().texOffs(48, 10).addBox(-1.5F, -3.5F, -1.5F, 3.0F, 7.0F, 3.0F),
            PartPose.offsetAndRotation(-2.0F, 2.0F, 0.0F, 0.0F, 0.0F, 0.24F)
        );
        final PartDefinition haloRight = root.addOrReplaceChild(
            "halo_right", CubeListBuilder.create(), PartPose.offsetAndRotation(10.5F, 7.0F, 1.0F, 0.0F, 0.0F, 0.08F)
        );
        haloRight.addOrReplaceChild(
            "halo_right_upper",
            CubeListBuilder.create().texOffs(72, 0).addBox(-4.0F, -2.0F, -2.0F, 8.0F, 4.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.18F)
        );
        haloRight.addOrReplaceChild(
            "halo_right_lower",
            CubeListBuilder.create().texOffs(72, 10).addBox(-1.5F, -3.5F, -1.5F, 3.0F, 7.0F, 3.0F),
            PartPose.offsetAndRotation(2.0F, 2.0F, 0.0F, 0.0F, 0.0F, -0.24F)
        );
        final PartDefinition right = root.addOrReplaceChild(
            "right_guard_arm",
            CubeListBuilder.create().texOffs(0, 34).addBox(-3.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(-1.5F)),
            PartPose.offsetAndRotation(-8.0F, 12.0F, 0.0F, -0.25F, 0.0F, 0.55F)
        );
        final PartDefinition rightForearm = right.addOrReplaceChild(
            "right_guard_forearm",
            CubeListBuilder.create().texOffs(0, 34).addBox(-3.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(-1.5F)),
            PartPose.offsetAndRotation(-0.5F, 5.0F, 0.0F, 0.0F, 0.0F, -0.85F)
        );
        rightForearm.addOrReplaceChild(
            "right_ward_palm",
            CubeListBuilder.create().texOffs(16, 34).addBox(-3.0F, -1.0F, -3.0F, 4.0F, 5.0F, 6.0F),
            PartPose.offsetAndRotation(-0.5F, 5.0F, 0.0F, 0.0F, 0.0F, -0.12F)
        );
        final PartDefinition left = root.addOrReplaceChild(
            "left_guard_arm",
            CubeListBuilder.create().texOffs(36, 34).addBox(-1.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(-1.5F)),
            PartPose.offsetAndRotation(8.0F, 12.0F, 0.0F, -0.25F, 0.0F, -0.55F)
        );
        final PartDefinition leftForearm = left.addOrReplaceChild(
            "left_guard_forearm",
            CubeListBuilder.create().texOffs(36, 34).addBox(-1.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(-1.5F)),
            PartPose.offsetAndRotation(0.5F, 5.0F, 0.0F, 0.0F, 0.0F, 0.85F)
        );
        leftForearm.addOrReplaceChild(
            "left_ward_palm",
            CubeListBuilder.create().texOffs(52, 34).addBox(-1.0F, -1.0F, -3.0F, 4.0F, 5.0F, 6.0F),
            PartPose.offsetAndRotation(0.5F, 5.0F, 0.0F, 0.0F, 0.0F, 0.12F)
        );
        root.addOrReplaceChild(
            "vapor_tail_left",
            CubeListBuilder.create()
                .texOffs(76, 34).addBox(-2.0F, 0.0F, -1.5F, 3.0F, 5.0F, 3.0F)
                .texOffs(88, 34).addBox(-1.5F, 4.0F, -1.0F, 2.0F, 4.0F, 2.0F),
            PartPose.offsetAndRotation(-1.0F, 18.0F, 0.0F, 0.0F, 0.0F, 0.2F)
        );
        root.addOrReplaceChild(
            "vapor_tail_right",
            CubeListBuilder.create()
                .texOffs(98, 34).addBox(-1.0F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F)
                .texOffs(110, 34).addBox(-0.5F, 3.0F, -1.0F, 2.0F, 4.0F, 2.0F),
            PartPose.offsetAndRotation(1.0F, 18.0F, 0.0F, 0.0F, 0.0F, -0.24F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        head.yRot += state.yRot * Mth.DEG_TO_RAD;
        head.xRot += state.xRot * Mth.DEG_TO_RAD;
        final float calm = Mth.sin(state.ageInTicks * 0.08F);
        torso.y += calm * 0.25F;
        sparkCore.yScale = 1.0F + Mth.sin(state.ageInTicks * 0.2F) * 0.08F;
        sparkCore.xScale = 1.0F + Mth.cos(state.ageInTicks * 0.18F) * 0.06F;
        sparkCrown.yRot += state.ageInTicks * 0.025F;
        sparkTrail.zRot += calm * 0.12F;
        haloLeft.zRot -= calm * 0.035F;
        haloRight.zRot += calm * 0.035F;
        leftTail.zRot += calm * 0.08F;
        rightTail.zRot -= calm * 0.08F;
        rightArm.xRot += Mth.cos(state.walkAnimationPos * 0.5F) * state.walkAnimationSpeed * 0.22F;
        leftArm.xRot -= Mth.cos(state.walkAnimationPos * 0.5F) * state.walkAnimationSpeed * 0.22F;
        if (state.shielding) {
            haloLeft.zRot -= 0.65F;
            haloRight.zRot += 0.65F;
            rightArm.xRot -= 1.05F;
            leftArm.xRot -= 1.05F;
            rightArm.zRot -= 0.28F;
            leftArm.zRot += 0.28F;
            rightPalm.xScale = 1.2F;
            rightPalm.yScale = 1.2F;
            leftPalm.xScale = 1.2F;
            leftPalm.yScale = 1.2F;
            sparkCore.xScale *= 1.25F;
            sparkCore.yScale *= 1.25F;
            sparkCrown.yScale = 1.2F;
        }
    }

    public static void extractRenderState(final SpiritEntity entity, final State state, final float partialTicks) {
        state.guardianPhase = entity.presentationPhase();
        state.shielding = state.guardianPhase == Phase.WARN || state.guardianPhase == Phase.DEFEND;
    }

    public static final class State extends LivingEntityRenderState {
        public Phase guardianPhase = Phase.WANDER;
        public boolean shielding;
    }
}
