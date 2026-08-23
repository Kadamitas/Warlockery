package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.IllusionCreeperEntity;
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

public final class IllusionCreeperModel extends EntityModel<IllusionCreeperModel.State> {
    public static final int TEXTURE_WIDTH = 64;
    public static final int TEXTURE_HEIGHT = 64;

    private final ModelPart maskHead;
    private final ModelPart crownLeft;
    private final ModelPart crownRight;
    private final ModelPart leftVoidShutter;
    private final ModelPart centerVoidShutter;
    private final ModelPart rightVoidShutter;
    private final ModelPart neckStack;
    private final ModelPart neckMiddle;
    private final ModelPart neckLower;
    private final ModelPart taperedTrunk;
    private final ModelPart falseFuseSeam;
    private final ModelPart rearRidge;
    private final ModelPart trunkKeystone;
    private final ModelPart frontLeftFoot;
    private final ModelPart frontRightFoot;
    private final ModelPart backLeftFoot;
    private final ModelPart backRightFoot;

    public IllusionCreeperModel(final ModelPart root) {
        super(root);
        maskHead = root.getChild("mask_head");
        crownLeft = maskHead.getChild("crown_left");
        crownRight = maskHead.getChild("crown_right");
        leftVoidShutter = maskHead.getChild("left_void_shutter");
        centerVoidShutter = maskHead.getChild("center_void_shutter");
        rightVoidShutter = maskHead.getChild("right_void_shutter");
        neckStack = root.getChild("neck_stack");
        neckMiddle = neckStack.getChild("neck_middle");
        neckLower = neckMiddle.getChild("neck_lower");
        taperedTrunk = root.getChild("tapered_trunk");
        falseFuseSeam = taperedTrunk.getChild("false_fuse_seam");
        rearRidge = taperedTrunk.getChild("rear_ridge");
        trunkKeystone = taperedTrunk.getChild("trunk_keystone");
        frontLeftFoot = root.getChild("front_left_foot");
        frontRightFoot = root.getChild("front_right_foot");
        backLeftFoot = root.getChild("back_left_foot");
        backRightFoot = root.getChild("back_right_foot");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition mask = root.addOrReplaceChild(
            "mask_head",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -3.0F, -2.5F, 8.0F, 2.0F, 5.0F)
                .texOffs(0, 8).addBox(-4.0F, -1.0F, -2.5F, 2.0F, 6.0F, 5.0F)
                .texOffs(16, 8).addBox(2.0F, -1.0F, -2.5F, 2.0F, 6.0F, 5.0F)
                .texOffs(32, 8).addBox(-2.0F, 3.0F, -2.5F, 4.0F, 2.0F, 5.0F),
            PartPose.offset(0.0F, 4.0F, -0.7F)
        );
        mask.addOrReplaceChild(
            "crown_left",
            CubeListBuilder.create().texOffs(32, 0).addBox(-3.0F, -2.0F, -2.5F, 3.0F, 2.0F, 5.0F),
            PartPose.offsetAndRotation(-2.8F, -3.0F, 0.0F, 0.0F, 0.0F, -0.18F)
        );
        mask.addOrReplaceChild(
            "crown_right",
            CubeListBuilder.create().texOffs(48, 0).addBox(0.0F, -2.0F, -2.5F, 3.0F, 2.0F, 5.0F),
            PartPose.offsetAndRotation(2.8F, -3.0F, 0.0F, 0.0F, 0.0F, 0.31F)
        );
        mask.addOrReplaceChild(
            "left_void_shutter",
            CubeListBuilder.create().texOffs(0, 21).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F),
            PartPose.offsetAndRotation(-2.2F, 0.8F, -2.55F, 0.0F, 0.0F, -0.12F)
        );
        mask.addOrReplaceChild(
            "center_void_shutter",
            CubeListBuilder.create().texOffs(0, 21).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, 0.2F, -2.58F, 0.0F, 0.0F, 0.08F)
        );
        mask.addOrReplaceChild(
            "right_void_shutter",
            CubeListBuilder.create().texOffs(0, 21).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F),
            PartPose.offsetAndRotation(2.2F, 0.9F, -2.55F, 0.0F, 0.0F, 0.17F)
        );

        final PartDefinition neck = root.addOrReplaceChild(
            "neck_stack",
            CubeListBuilder.create().texOffs(8, 21).addBox(-2.5F, 0.0F, -2.0F, 5.0F, 2.0F, 4.0F),
            PartPose.offset(0.0F, 8.0F, 0.0F)
        );
        final PartDefinition middle = neck.addOrReplaceChild(
            "neck_middle",
            CubeListBuilder.create().texOffs(28, 21).addBox(-2.0F, 0.0F, -1.5F, 4.0F, 2.0F, 3.0F),
            PartPose.offsetAndRotation(0.35F, 2.0F, 0.15F, 0.0F, 0.0F, -0.08F)
        );
        middle.addOrReplaceChild(
            "neck_lower",
            CubeListBuilder.create().texOffs(44, 21).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 2.0F, 3.0F),
            PartPose.offsetAndRotation(-0.55F, 2.0F, -0.1F, 0.0F, 0.0F, 0.12F)
        );

        final PartDefinition trunk = root.addOrReplaceChild(
            "tapered_trunk",
            CubeListBuilder.create()
                .texOffs(0, 28).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 5.0F, 6.0F)
                .texOffs(30, 28).addBox(-3.5F, 5.0F, -2.5F, 7.0F, 4.0F, 5.0F)
                .texOffs(0, 40).addBox(-3.0F, 9.0F, -2.0F, 6.0F, 2.0F, 4.0F),
            PartPose.offset(0.0F, 11.0F, 0.0F)
        );
        trunk.addOrReplaceChild(
            "false_fuse_seam",
            CubeListBuilder.create().texOffs(22, 40).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 11.0F, 1.0F),
            PartPose.offsetAndRotation(0.35F, 0.8F, -3.05F, 0.0F, 0.0F, 0.04F)
        );
        trunk.addOrReplaceChild(
            "rear_ridge",
            CubeListBuilder.create().texOffs(28, 40).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F),
            PartPose.offsetAndRotation(-0.55F, 2.0F, 2.55F, 0.0F, 0.0F, -0.07F)
        );
        trunk.addOrReplaceChild(
            "trunk_keystone",
            CubeListBuilder.create().texOffs(56, 21).addBox(-1.5F, -1.5F, -0.5F, 3.0F, 3.0F, 1.0F),
            PartPose.offsetAndRotation(-1.2F, 3.2F, -3.0F, 0.0F, 0.0F, -0.12F)
        );

        root.addOrReplaceChild(
            "front_left_foot",
            CubeListBuilder.create().texOffs(38, 40).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 5.0F, 3.0F),
            PartPose.offsetAndRotation(6.9F, 19.0F, -6.9F, 0.0F, 0.28F, 0.0F)
        );
        root.addOrReplaceChild(
            "front_right_foot",
            CubeListBuilder.create().texOffs(38, 40).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 5.0F, 3.0F),
            PartPose.offsetAndRotation(-6.9F, 19.0F, -2.3F, 0.0F, -0.38F, 0.0F)
        );
        root.addOrReplaceChild(
            "back_left_foot",
            CubeListBuilder.create().texOffs(38, 40).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 5.0F, 3.0F),
            PartPose.offsetAndRotation(2.3F, 19.0F, 2.3F, 0.0F, -0.34F, 0.0F)
        );
        root.addOrReplaceChild(
            "back_right_foot",
            CubeListBuilder.create().texOffs(38, 40).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 5.0F, 3.0F),
            PartPose.offsetAndRotation(-2.3F, 19.0F, 6.9F, 0.0F, 0.23F, 0.0F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        maskHead.yRot += state.yRot * Mth.DEG_TO_RAD;
        maskHead.xRot += state.xRot * Mth.DEG_TO_RAD * 0.65F;
        final float pace = state.walkAnimationPos * 0.72F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F);
        frontLeftFoot.xRot += Mth.cos(pace) * stride * 0.38F;
        backRightFoot.xRot += Mth.cos(pace) * stride * 0.38F;
        frontRightFoot.xRot += Mth.cos(pace + Mth.PI) * stride * 0.38F;
        backLeftFoot.xRot += Mth.cos(pace + Mth.PI) * stride * 0.38F;
        neckMiddle.zRot += Mth.sin(state.ageInTicks * 0.08F) * 0.025F;
        neckLower.zRot -= Mth.sin(state.ageInTicks * 0.08F) * 0.035F;
        rearRidge.zRot += Mth.sin(state.ageInTicks * 0.06F) * 0.025F;

        if (state.phase == Phase.TELL || state.phase == Phase.HOLD) {
            taperedTrunk.xScale = 0.9F;
            taperedTrunk.zScale = 0.92F;
            maskHead.y += 0.65F;
            falseFuseSeam.z -= 0.35F;
            trunkKeystone.z -= 0.2F;
            leftVoidShutter.z -= 0.18F;
            centerVoidShutter.z -= 0.3F;
            rightVoidShutter.z -= 0.18F;
            crownLeft.zRot -= 0.08F;
            crownRight.zRot += 0.08F;
        } else if (state.phase == Phase.COLLAPSE || state.phase == Phase.SPENT) {
            maskHead.y += 3.2F;
            maskHead.xRot += 0.18F;
            neckStack.yScale = 0.48F;
            neckMiddle.yScale = 0.42F;
            neckLower.yScale = 0.36F;
            taperedTrunk.xScale = 0.68F;
            taperedTrunk.zScale = 0.7F;
            taperedTrunk.yScale = 0.78F;
            frontLeftFoot.zRot -= 0.42F;
            backLeftFoot.zRot -= 0.32F;
            frontRightFoot.zRot += 0.46F;
            backRightFoot.zRot += 0.35F;
            falseFuseSeam.z -= 0.55F;
            trunkKeystone.xRot -= 0.45F;
            rearRidge.xRot += 0.24F;
        }
    }

    public static void extractRenderState(
        final IllusionCreeperEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.phase = entity.presentationPhase();
    }

    public static final class State extends LivingEntityRenderState {
        public Phase phase = Phase.LATENT;
    }
}
