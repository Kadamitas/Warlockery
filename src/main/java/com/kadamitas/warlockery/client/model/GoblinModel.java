package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.GoblinEnclaveRules.Intent;
import com.kadamitas.warlockery.entity.GoblinEntity;
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

/** A quick, rockhopper-shaped enclave miner with its tools carried against an avian body. */
public final class GoblinModel extends EntityModel<GoblinModel.State>
    implements ArmedModel<GoblinModel.State> {
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart rightCrest;
    private final ModelPart leftCrest;
    private final ModelPart lampCap;
    private final ModelPart tailWedge;
    private final ModelPart satchel;
    private final ModelPart pickHarness;
    private final ModelPart rightFlipper;
    private final ModelPart rightFlipperTip;
    private final ModelPart leftFlipper;
    private final ModelPart leftFlipperTip;
    private final ModelPart rightLeg;
    private final ModelPart rightFoot;
    private final ModelPart leftLeg;
    private final ModelPart leftFoot;

    public GoblinModel(final ModelPart root) {
        super(root);
        body = root.getChild("body");
        head = body.getChild("head");
        rightCrest = head.getChild("right_crest");
        leftCrest = head.getChild("left_crest");
        lampCap = head.getChild("lamp_cap");
        tailWedge = body.getChild("tail_wedge");
        satchel = body.getChild("satchel");
        pickHarness = body.getChild("pick_harness");
        rightFlipper = root.getChild("right_flipper");
        rightFlipperTip = rightFlipper.getChild("right_flipper_tip");
        leftFlipper = root.getChild("left_flipper");
        leftFlipperTip = leftFlipper.getChild("left_flipper_tip");
        rightLeg = root.getChild("right_leg");
        rightFoot = rightLeg.getChild("right_webbed_foot");
        leftLeg = root.getChild("left_leg");
        leftFoot = leftLeg.getChild("left_webbed_foot");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition body = root.addOrReplaceChild(
            "body",
            CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.5F, -3.5F, -3.0F, 7.0F, 10.0F, 7.0F),
            PartPose.offsetAndRotation(0.0F, 10.5F, 0.0F, 0.035F, 0.0F, 0.0F)
        );
        final PartDefinition head = body.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(32, 0)
                .addBox(-4.0F, -4.0F, -3.3F, 8.0F, 6.0F, 7.0F),
            PartPose.offsetAndRotation(0.0F, -2.7F, -0.9F, -0.08F, 0.0F, 0.0F)
        );
        head.addOrReplaceChild(
            "beak",
            CubeListBuilder.create().texOffs(64, 0)
                .addBox(-2.0F, -1.0F, -4.0F, 4.0F, 2.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, -3.0F, 0.08F, 0.0F, 0.0F)
        );
        head.addOrReplaceChild(
            "right_crest",
            CubeListBuilder.create().texOffs(88, 0)
                .addBox(-4.0F, -1.5F, -1.0F, 4.0F, 3.0F, 2.0F),
            PartPose.offsetAndRotation(-1.8F, -3.25F, 0.0F, -0.15F, 0.1F, -0.72F)
        );
        head.addOrReplaceChild(
            "left_crest",
            CubeListBuilder.create().texOffs(100, 0)
                .addBox(0.0F, -1.5F, -1.0F, 4.0F, 3.0F, 2.0F),
            PartPose.offsetAndRotation(1.8F, -3.25F, 0.0F, -0.15F, -0.1F, 0.72F)
        );
        final PartDefinition lampCap = head.addOrReplaceChild(
            "lamp_cap",
            CubeListBuilder.create().texOffs(0, 20)
                .addBox(-3.0F, -1.0F, -2.5F, 6.0F, 2.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, -4.05F, -0.1F, -0.05F, 0.0F, 0.0F)
        );
        lampCap.addOrReplaceChild(
            "lamp",
            CubeListBuilder.create().texOffs(24, 20)
                .addBox(-1.0F, -1.0F, -2.0F, 2.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, -2.3F, -0.18F, 0.0F, 0.0F)
        );
        body.addOrReplaceChild(
            "belly_keel",
            CubeListBuilder.create().texOffs(34, 20)
                .addBox(-2.5F, -4.0F, -1.0F, 5.0F, 8.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, 1.1F, -3.0F, -0.08F, 0.0F, 0.0F)
        );
        body.addOrReplaceChild(
            "neck_band",
            CubeListBuilder.create().texOffs(50, 20)
                .addBox(-3.5F, -1.0F, -3.5F, 7.0F, 2.0F, 7.0F),
            PartPose.offsetAndRotation(0.0F, -2.4F, 0.0F, 0.03F, 0.0F, 0.0F)
        );
        body.addOrReplaceChild(
            "tail_wedge",
            CubeListBuilder.create().texOffs(0, 34)
                .addBox(-2.5F, 0.0F, -0.5F, 5.0F, 3.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 4.1F, 3.2F, 0.58F, 0.0F, 0.0F)
        );
        final PartDefinition satchel = body.addOrReplaceChild(
            "satchel",
            CubeListBuilder.create().texOffs(22, 34)
                .addBox(-4.0F, -2.5F, -1.5F, 4.0F, 5.0F, 3.0F),
            PartPose.offsetAndRotation(-3.4F, 3.0F, 0.4F, 0.0F, -0.15F, 0.12F)
        );
        satchel.addOrReplaceChild(
            "ore_cluster",
            CubeListBuilder.create().texOffs(38, 34)
                .addBox(-2.5F, -2.2F, -1.5F, 3.0F, 3.0F, 3.0F),
            PartPose.offsetAndRotation(-0.3F, -2.1F, 0.0F, 0.22F, 0.16F, -0.18F)
        );
        final PartDefinition harness = body.addOrReplaceChild(
            "pick_harness",
            CubeListBuilder.create().texOffs(54, 34)
                .addBox(-1.0F, -3.5F, -1.0F, 2.0F, 7.0F, 2.0F),
            PartPose.offsetAndRotation(3.7F, 1.0F, 3.9F, -0.25F, 0.0F, 0.42F)
        );
        harness.addOrReplaceChild(
            "pick_head",
            CubeListBuilder.create().texOffs(64, 34)
                .addBox(-3.5F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, 0.0F, 0.0F, 0.12F)
        );

        final PartDefinition rightFlipper = root.addOrReplaceChild(
            "right_flipper",
            CubeListBuilder.create().texOffs(82, 34)
                .addBox(-2.0F, -1.0F, -1.5F, 2.0F, 7.0F, 4.0F),
            PartPose.offsetAndRotation(-3.1F, 8.8F, -0.1F, -0.12F, 0.08F, 0.48F)
        );
        rightFlipper.addOrReplaceChild(
            "right_flipper_tip",
            CubeListBuilder.create().texOffs(96, 34)
                .addBox(-1.5F, 0.0F, -1.2F, 2.0F, 5.0F, 3.0F),
            PartPose.offsetAndRotation(-0.7F, 5.0F, 0.2F, 0.08F, 0.0F, 0.28F)
        );
        final PartDefinition leftFlipper = root.addOrReplaceChild(
            "left_flipper",
            CubeListBuilder.create().texOffs(0, 50)
                .addBox(0.0F, -1.0F, -1.5F, 2.0F, 7.0F, 4.0F),
            PartPose.offsetAndRotation(3.1F, 8.8F, -0.1F, -0.12F, -0.08F, -0.48F)
        );
        leftFlipper.addOrReplaceChild(
            "left_flipper_tip",
            CubeListBuilder.create().texOffs(14, 50)
                .addBox(-0.5F, 0.0F, -1.2F, 2.0F, 5.0F, 3.0F),
            PartPose.offsetAndRotation(0.7F, 5.0F, 0.2F, 0.08F, 0.0F, -0.28F)
        );
        final PartDefinition rightLeg = root.addOrReplaceChild(
            "right_leg",
            CubeListBuilder.create().texOffs(28, 50)
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F),
            PartPose.offsetAndRotation(-3.0F, 17.5F, 0.5F, -0.08F, 0.0F, -0.08F)
        );
        rightLeg.addOrReplaceChild(
            "right_webbed_foot",
            CubeListBuilder.create().texOffs(42, 50)
                .addBox(-2.0F, 0.0F, -4.0F, 4.0F, 2.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, 3.5F, -0.2F, 0.08F, -0.08F, 0.0F)
        );
        final PartDefinition leftLeg = root.addOrReplaceChild(
            "left_leg",
            CubeListBuilder.create().texOffs(62, 50)
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F),
            PartPose.offsetAndRotation(3.0F, 17.5F, 0.5F, -0.08F, 0.0F, 0.08F)
        );
        leftLeg.addOrReplaceChild(
            "left_webbed_foot",
            CubeListBuilder.create().texOffs(76, 50)
                .addBox(-2.0F, 0.0F, -4.0F, 4.0F, 2.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, 3.5F, -0.2F, 0.08F, 0.08F, 0.0F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        head.yRot += state.yRot * Mth.DEG_TO_RAD;
        head.xRot += state.xRot * Mth.DEG_TO_RAD;
        final float pace = state.walkAnimationPos * 0.82F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F);
        final float waddle = Mth.sin(pace) * stride;
        body.zRot += waddle * 0.13F;
        head.zRot -= waddle * 0.07F;
        rightLeg.xRot += Mth.cos(pace) * stride * 0.78F;
        leftLeg.xRot += Mth.cos(pace + Mth.PI) * stride * 0.78F;
        rightFoot.xRot -= Mth.cos(pace) * stride * 0.18F;
        leftFoot.xRot -= Mth.cos(pace + Mth.PI) * stride * 0.18F;
        rightFlipper.xRot += Mth.cos(pace + Mth.PI) * stride * 0.38F;
        leftFlipper.xRot += Mth.cos(pace) * stride * 0.38F;
        rightFlipperTip.zRot += Mth.sin(state.ageInTicks * 0.14F) * 0.05F;
        leftFlipperTip.zRot -= Mth.sin(state.ageInTicks * 0.14F) * 0.05F;
        tailWedge.yRot += Mth.sin(state.ageInTicks * 0.16F) * 0.12F;
        satchel.zRot -= waddle * 0.05F;
        pickHarness.zRot += waddle * 0.035F;
        if (state.intent == Intent.ASSAULT && state.assaultMember) {
            final float rank = Mth.clamp(state.assaultWave, 1, 5) * 0.025F;
            body.xRot += 0.22F + rank;
            head.xRot -= 0.18F;
            rightFlipper.xRot = -1.05F;
            rightFlipper.zRot += 0.28F;
            leftFlipper.xRot = 0.62F;
            leftFlipper.zRot -= 0.18F;
            rightCrest.zRot -= 0.22F;
            leftCrest.zRot += 0.22F;
            if (state.assaultLeader) {
                lampCap.xRot -= 0.18F;
                leftFlipper.xRot = -1.2F;
                leftFlipper.zRot -= 0.48F;
                head.yRot += Mth.sin(state.ageInTicks * 0.22F) * 0.2F;
            }
        }
    }

    @Override
    public void translateToHand(final State state, final HumanoidArm arm, final PoseStack poseStack) {
        final ModelPart flipper = arm == HumanoidArm.LEFT ? leftFlipper : rightFlipper;
        final ModelPart tip = arm == HumanoidArm.LEFT ? leftFlipperTip : rightFlipperTip;
        flipper.translateAndRotate(poseStack);
        tip.translateAndRotate(poseStack);
        poseStack.translate(arm == HumanoidArm.LEFT ? 0.045F : -0.045F, 0.12F, -0.03F);
    }

    public static void extractRenderState(
        final GoblinEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.intent = entity.presentationIntent();
        state.assaultMember = entity.presentationAssaultMember();
        state.assaultLeader = entity.presentationAssaultLeader();
        state.assaultWave = entity.presentationAssaultWave();
    }

    public static final class State extends ArmedEntityRenderState {
        public Intent intent = Intent.IDLE;
        public boolean assaultMember;
        public boolean assaultLeader;
        public int assaultWave;
    }
}
