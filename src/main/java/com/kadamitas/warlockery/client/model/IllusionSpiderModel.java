package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.IllusionSpiderEntity;
import com.kadamitas.warlockery.entity.MimicryRules.Phase;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public final class IllusionSpiderModel extends EntityModel<IllusionSpiderModel.State> {
    public static final int TEXTURE_WIDTH = 64;
    public static final int TEXTURE_HEIGHT = 64;

    private final ModelPart shieldCephalothorax;
    private final ModelPart armoredBrow;
    private final ModelPart leftMandible;
    private final ModelPart rightMandible;
    private final ModelPart sensoryCluster;
    private final ModelPart splitWedgeAbdomen;
    private final ModelPart leftAbdomenWedge;
    private final ModelPart rightAbdomenWedge;
    private final ModelPart frontLeftLeg;
    private final ModelPart middleFrontLeftLeg;
    private final ModelPart middleRearLeftLeg;
    private final ModelPart rearLeftLeg;
    private final ModelPart frontRightLeg;
    private final ModelPart middleFrontRightLeg;
    private final ModelPart middleRearRightLeg;
    private final ModelPart rearRightLeg;
    private final ModelPart frontLeftLegLower;
    private final ModelPart middleFrontLeftLegLower;
    private final ModelPart middleRearLeftLegLower;
    private final ModelPart rearLeftLegLower;
    private final ModelPart frontRightLegLower;
    private final ModelPart middleFrontRightLegLower;
    private final ModelPart middleRearRightLegLower;
    private final ModelPart rearRightLegLower;
    private final ModelPart leftSnareStrand;
    private final ModelPart rightSnareStrand;

    public IllusionSpiderModel(final ModelPart root) {
        super(root);
        shieldCephalothorax = root.getChild("shield_cephalothorax");
        armoredBrow = shieldCephalothorax.getChild("armored_brow");
        leftMandible = shieldCephalothorax.getChild("left_mandible");
        rightMandible = shieldCephalothorax.getChild("right_mandible");
        sensoryCluster = shieldCephalothorax.getChild("sensory_cluster");
        splitWedgeAbdomen = root.getChild("split_wedge_abdomen");
        leftAbdomenWedge = splitWedgeAbdomen.getChild("left_abdomen_wedge");
        rightAbdomenWedge = splitWedgeAbdomen.getChild("right_abdomen_wedge");
        frontLeftLeg = root.getChild("front_left_leg");
        middleFrontLeftLeg = root.getChild("middle_front_left_leg");
        middleRearLeftLeg = root.getChild("middle_rear_left_leg");
        rearLeftLeg = root.getChild("rear_left_leg");
        frontRightLeg = root.getChild("front_right_leg");
        middleFrontRightLeg = root.getChild("middle_front_right_leg");
        middleRearRightLeg = root.getChild("middle_rear_right_leg");
        rearRightLeg = root.getChild("rear_right_leg");
        frontLeftLegLower = frontLeftLeg.getChild("front_left_leg_lower");
        middleFrontLeftLegLower = middleFrontLeftLeg.getChild("middle_front_left_leg_lower");
        middleRearLeftLegLower = middleRearLeftLeg.getChild("middle_rear_left_leg_lower");
        rearLeftLegLower = rearLeftLeg.getChild("rear_left_leg_lower");
        frontRightLegLower = frontRightLeg.getChild("front_right_leg_lower");
        middleFrontRightLegLower = middleFrontRightLeg.getChild("middle_front_right_leg_lower");
        middleRearRightLegLower = middleRearRightLeg.getChild("middle_rear_right_leg_lower");
        rearRightLegLower = rearRightLeg.getChild("rear_right_leg_lower");
        leftSnareStrand = root.getChild("left_snare_strand");
        rightSnareStrand = root.getChild("right_snare_strand");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition shield = root.addOrReplaceChild(
            "shield_cephalothorax",
            CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -2.0F, -4.0F, 10.0F, 4.0F, 8.0F),
            PartPose.offsetAndRotation(0.0F, 7.2F, -3.0F, 0.04F, 0.0F, 0.0F)
        );
        shield.addOrReplaceChild(
            "armored_brow",
            CubeListBuilder.create().texOffs(36, 0).addBox(-5.0F, -1.0F, -1.5F, 10.0F, 2.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, -1.4F, -3.6F, -0.12F, 0.0F, 0.0F)
        );
        shield.addOrReplaceChild(
            "left_mandible",
            CubeListBuilder.create().texOffs(0, 13).addBox(-1.0F, -1.0F, -2.0F, 2.0F, 2.0F, 4.0F),
            PartPose.offsetAndRotation(2.7F, 1.5F, -4.2F, 0.18F, -0.28F, 0.08F)
        );
        shield.addOrReplaceChild(
            "right_mandible",
            CubeListBuilder.create().texOffs(0, 13).addBox(-1.0F, -1.0F, -2.0F, 2.0F, 2.0F, 4.0F),
            PartPose.offsetAndRotation(-2.7F, 1.5F, -4.2F, 0.18F, 0.28F, -0.08F)
        );
        shield.addOrReplaceChild(
            "sensory_cluster",
            CubeListBuilder.create().texOffs(14, 13).addBox(-3.0F, -0.5F, -0.5F, 6.0F, 1.0F, 1.0F),
            PartPose.offsetAndRotation(-0.35F, 0.1F, -4.15F, 0.0F, 0.0F, -0.08F)
        );

        final PartDefinition abdomen = root.addOrReplaceChild(
            "split_wedge_abdomen",
            CubeListBuilder.create().texOffs(0, 16).addBox(-3.0F, -1.5F, -2.5F, 6.0F, 3.0F, 5.0F),
            PartPose.offset(0.0F, 7.2F, 3.0F)
        );
        abdomen.addOrReplaceChild(
            "left_abdomen_wedge",
            CubeListBuilder.create().texOffs(24, 16).addBox(0.0F, -2.0F, -4.0F, 5.0F, 4.0F, 8.0F),
            PartPose.offsetAndRotation(0.35F, -0.1F, 2.2F, 0.0F, -0.11F, -0.08F)
        );
        abdomen.addOrReplaceChild(
            "right_abdomen_wedge",
            CubeListBuilder.create().texOffs(24, 16).addBox(-5.0F, -2.0F, -4.0F, 5.0F, 4.0F, 8.0F),
            PartPose.offsetAndRotation(-0.35F, 0.1F, 2.2F, 0.0F, 0.11F, 0.08F)
        );

        final PartDefinition frontLeft = root.addOrReplaceChild(
            "front_left_leg",
            CubeListBuilder.create().texOffs(0, 30).addBox(0.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(4.15F, 9.95F, -5.2F, 0.0F, 0.72F, 0.85F)
        );
        final PartDefinition frontLeftLower = frontLeft.addOrReplaceChild(
            "front_left_leg_lower",
            CubeListBuilder.create().texOffs(20, 30).addBox(0.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(6.7F, 0.7F, 0.0F, 0.0F, 0.24F, 0.60F)
        );
        frontLeftLower.addOrReplaceChild(
            "front_left_leg_hook",
            CubeListBuilder.create().texOffs(40, 30).addBox(0.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F),
            PartPose.offsetAndRotation(6.7F, 0.7F, 0.0F, 0.0F, 0.0F, -1.45F)
        );

        final PartDefinition middleFrontLeft = root.addOrReplaceChild(
            "middle_front_left_leg",
            CubeListBuilder.create().texOffs(0, 30).addBox(0.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(4.35F, 6.35F, -2.2F, 0.0F, 0.25F, 0.85F)
        );
        final PartDefinition middleFrontLeftLower = middleFrontLeft.addOrReplaceChild(
            "middle_front_left_leg_lower",
            CubeListBuilder.create().texOffs(20, 30).addBox(0.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(6.7F, 0.7F, 0.0F, 0.0F, 0.16F, 0.60F)
        );
        middleFrontLeftLower.addOrReplaceChild(
            "middle_front_left_leg_hook",
            CubeListBuilder.create().texOffs(40, 30).addBox(0.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F),
            PartPose.offsetAndRotation(6.7F, 0.7F, 0.0F, 0.0F, 0.0F, -1.45F)
        );

        final PartDefinition middleRearLeft = root.addOrReplaceChild(
            "middle_rear_left_leg",
            CubeListBuilder.create().texOffs(0, 30).addBox(0.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(4.35F, 6.27F, 0.9F, 0.0F, -0.23F, 0.85F)
        );
        final PartDefinition middleRearLeftLower = middleRearLeft.addOrReplaceChild(
            "middle_rear_left_leg_lower",
            CubeListBuilder.create().texOffs(20, 30).addBox(0.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(6.7F, 0.7F, 0.0F, 0.0F, -0.16F, 0.60F)
        );
        middleRearLeftLower.addOrReplaceChild(
            "middle_rear_left_leg_hook",
            CubeListBuilder.create().texOffs(40, 30).addBox(0.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F),
            PartPose.offsetAndRotation(6.7F, 0.7F, 0.0F, 0.0F, 0.0F, -1.45F)
        );

        final PartDefinition rearLeft = root.addOrReplaceChild(
            "rear_left_leg",
            CubeListBuilder.create().texOffs(0, 30).addBox(0.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(3.6F, 9.6F, 4.0F, 0.0F, -0.7F, 0.85F)
        );
        final PartDefinition rearLeftLower = rearLeft.addOrReplaceChild(
            "rear_left_leg_lower",
            CubeListBuilder.create().texOffs(20, 30).addBox(0.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(6.7F, 0.7F, 0.0F, 0.0F, -0.24F, 0.60F)
        );
        rearLeftLower.addOrReplaceChild(
            "rear_left_leg_hook",
            CubeListBuilder.create().texOffs(40, 30).addBox(0.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F),
            PartPose.offsetAndRotation(6.7F, 0.7F, 0.0F, 0.0F, 0.0F, -1.45F)
        );

        final PartDefinition frontRight = root.addOrReplaceChild(
            "front_right_leg",
            CubeListBuilder.create().texOffs(0, 30).addBox(-7.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(-4.15F, 9.95F, -5.2F, 0.0F, -0.72F, -0.85F)
        );
        final PartDefinition frontRightLower = frontRight.addOrReplaceChild(
            "front_right_leg_lower",
            CubeListBuilder.create().texOffs(20, 30).addBox(-7.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(-6.7F, 0.7F, 0.0F, 0.0F, -0.24F, -0.60F)
        );
        frontRightLower.addOrReplaceChild(
            "front_right_leg_hook",
            CubeListBuilder.create().texOffs(40, 30).addBox(-2.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F),
            PartPose.offsetAndRotation(-6.7F, 0.7F, 0.0F, 0.0F, 0.0F, 1.45F)
        );

        final PartDefinition middleFrontRight = root.addOrReplaceChild(
            "middle_front_right_leg",
            CubeListBuilder.create().texOffs(0, 30).addBox(-7.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(-4.35F, 6.35F, -2.2F, 0.0F, -0.25F, -0.85F)
        );
        final PartDefinition middleFrontRightLower = middleFrontRight.addOrReplaceChild(
            "middle_front_right_leg_lower",
            CubeListBuilder.create().texOffs(20, 30).addBox(-7.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(-6.7F, 0.7F, 0.0F, 0.0F, -0.16F, -0.60F)
        );
        middleFrontRightLower.addOrReplaceChild(
            "middle_front_right_leg_hook",
            CubeListBuilder.create().texOffs(40, 30).addBox(-2.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F),
            PartPose.offsetAndRotation(-6.7F, 0.7F, 0.0F, 0.0F, 0.0F, 1.45F)
        );

        final PartDefinition middleRearRight = root.addOrReplaceChild(
            "middle_rear_right_leg",
            CubeListBuilder.create().texOffs(0, 30).addBox(-7.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(-4.35F, 6.27F, 0.9F, 0.0F, 0.23F, -0.85F)
        );
        final PartDefinition middleRearRightLower = middleRearRight.addOrReplaceChild(
            "middle_rear_right_leg_lower",
            CubeListBuilder.create().texOffs(20, 30).addBox(-7.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(-6.7F, 0.7F, 0.0F, 0.0F, 0.16F, -0.60F)
        );
        middleRearRightLower.addOrReplaceChild(
            "middle_rear_right_leg_hook",
            CubeListBuilder.create().texOffs(40, 30).addBox(-2.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F),
            PartPose.offsetAndRotation(-6.7F, 0.7F, 0.0F, 0.0F, 0.0F, 1.45F)
        );

        final PartDefinition rearRight = root.addOrReplaceChild(
            "rear_right_leg",
            CubeListBuilder.create().texOffs(0, 30).addBox(-7.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(-3.6F, 9.6F, 4.0F, 0.0F, 0.7F, -0.85F)
        );
        final PartDefinition rearRightLower = rearRight.addOrReplaceChild(
            "rear_right_leg_lower",
            CubeListBuilder.create().texOffs(20, 30).addBox(-7.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(-6.7F, 0.7F, 0.0F, 0.0F, 0.24F, -0.60F)
        );
        rearRightLower.addOrReplaceChild(
            "rear_right_leg_hook",
            CubeListBuilder.create().texOffs(40, 30).addBox(-2.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F),
            PartPose.offsetAndRotation(-6.7F, 0.7F, 0.0F, 0.0F, 0.0F, 1.45F)
        );

        root.addOrReplaceChild(
            "left_snare_strand",
            CubeListBuilder.create().texOffs(0, 40).addBox(-7.0F, -0.5F, -0.5F, 14.0F, 1.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, 19.2F, -2.0F, 0.0F, 0.0F, 0.5F)
        );
        root.addOrReplaceChild(
            "right_snare_strand",
            CubeListBuilder.create().texOffs(0, 40).addBox(-7.0F, -0.5F, -0.5F, 14.0F, 1.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, 19.2F, -2.0F, 0.0F, 0.0F, -0.5F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        shieldCephalothorax.yRot += state.yRot * Mth.DEG_TO_RAD * 0.45F;
        shieldCephalothorax.xRot += state.xRot * Mth.DEG_TO_RAD * 0.35F;
        final float pace = state.walkAnimationPos * 0.9F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F);
        frontLeftLeg.zRot += Mth.cos(pace) * stride * 0.28F;
        middleRearLeftLeg.zRot += Mth.cos(pace) * stride * 0.28F;
        middleFrontRightLeg.zRot -= Mth.cos(pace) * stride * 0.28F;
        rearRightLeg.zRot -= Mth.cos(pace) * stride * 0.28F;
        frontRightLeg.zRot -= Mth.cos(pace + Mth.PI) * stride * 0.28F;
        middleRearRightLeg.zRot -= Mth.cos(pace + Mth.PI) * stride * 0.28F;
        middleFrontLeftLeg.zRot += Mth.cos(pace + Mth.PI) * stride * 0.28F;
        rearLeftLeg.zRot += Mth.cos(pace + Mth.PI) * stride * 0.28F;
        splitWedgeAbdomen.y += Mth.sin(state.ageInTicks * 0.09F) * 0.08F;
        leftAbdomenWedge.yRot += Mth.sin(state.ageInTicks * 0.07F) * 0.025F;
        rightAbdomenWedge.yRot -= Mth.sin(state.ageInTicks * 0.07F) * 0.025F;
        sensoryCluster.z -= Mth.sin(state.ageInTicks * 0.16F) * 0.05F;

        final boolean snaring = state.phase == Phase.RESOLVE
            || state.phase == Phase.SNARE
            || state.phase == Phase.BREAK;
        leftSnareStrand.visible = snaring;
        rightSnareStrand.visible = snaring;
        leftSnareStrand.skipDraw = !snaring;
        rightSnareStrand.skipDraw = !snaring;
        final float snareScale = snaring ? 1.0F : 0.0F;
        leftSnareStrand.xScale = snareScale;
        leftSnareStrand.yScale = snareScale;
        leftSnareStrand.zScale = snareScale;
        rightSnareStrand.xScale = snareScale;
        rightSnareStrand.yScale = snareScale;
        rightSnareStrand.zScale = snareScale;
        if (snaring) {
            frontLeftLeg.yRot -= 0.46F;
            middleFrontLeftLeg.yRot -= 0.32F;
            middleRearLeftLeg.yRot += 0.31F;
            rearLeftLeg.yRot += 0.45F;
            frontRightLeg.yRot += 0.46F;
            middleFrontRightLeg.yRot += 0.32F;
            middleRearRightLeg.yRot -= 0.31F;
            rearRightLeg.yRot -= 0.45F;
            frontLeftLegLower.yRot -= 0.18F;
            middleFrontLeftLegLower.yRot -= 0.14F;
            middleRearLeftLegLower.yRot += 0.14F;
            rearLeftLegLower.yRot += 0.18F;
            frontRightLegLower.yRot += 0.18F;
            middleFrontRightLegLower.yRot += 0.14F;
            middleRearRightLegLower.yRot -= 0.14F;
            rearRightLegLower.yRot -= 0.18F;
            armoredBrow.xRot -= 0.12F;
            leftMandible.yRot += 0.35F;
            rightMandible.yRot -= 0.35F;
            splitWedgeAbdomen.y += 0.55F;
        }
    }

    public static void extractRenderState(
        final IllusionSpiderEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.phase = entity.presentationPhase();
    }

    public static final class State extends LivingEntityRenderState {
        public Phase phase = Phase.HIDDEN;
    }
}
