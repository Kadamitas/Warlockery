package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.GoblinPatronRules.Action;
import com.kadamitas.warlockery.entity.StonebrokerEntity;
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

/** A broad king-penguin patron whose crystal mantle and ledger carry its broker identity. */
public final class StonebrokerModel extends EntityModel<StonebrokerModel.State>
    implements ArmedModel<StonebrokerModel.State> {
    public static final int TEXTURE_WIDTH = 192;
    public static final int TEXTURE_HEIGHT = 160;

    private final ModelPart brokerBody;
    private final ModelPart head;
    private final ModelPart appraisalLens;
    private final ModelPart tailWedge;
    private final ModelPart geodeMantle;
    private final ModelPart crownCrystal;
    private final ModelPart ledger;
    private final ModelPart ledgerCover;
    private final ModelPart quiver;
    private final ModelPart rightFlipper;
    private final ModelPart rightFeatherFan;
    private final ModelPart leftFlipper;
    private final ModelPart leftFeatherFan;
    private final ModelPart rightLeg;
    private final ModelPart rightFoot;
    private final ModelPart leftLeg;
    private final ModelPart leftFoot;

    public StonebrokerModel(final ModelPart root) {
        super(root);
        brokerBody = root.getChild("broker_body");
        head = brokerBody.getChild("head");
        appraisalLens = head.getChild("appraisal_lens");
        tailWedge = brokerBody.getChild("tail_wedge");
        geodeMantle = brokerBody.getChild("geode_mantle");
        crownCrystal = geodeMantle.getChild("crown_crystal");
        ledger = brokerBody.getChild("ledger");
        ledgerCover = ledger.getChild("ledger_cover");
        quiver = brokerBody.getChild("quiver");
        rightFlipper = root.getChild("right_flipper");
        rightFeatherFan = rightFlipper.getChild("right_feather_fan");
        leftFlipper = root.getChild("left_flipper");
        leftFeatherFan = leftFlipper.getChild("left_feather_fan");
        rightLeg = root.getChild("right_leg");
        rightFoot = rightLeg.getChild("right_webbed_foot");
        leftLeg = root.getChild("left_leg");
        leftFoot = leftLeg.getChild("left_webbed_foot");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition body = root.addOrReplaceChild(
            "broker_body",
            CubeListBuilder.create().texOffs(0, 0)
                .addBox(-7.0F, -9.0F, -5.0F, 14.0F, 19.0F, 10.0F),
            PartPose.offsetAndRotation(0.0F, 7.7F, 0.5F, 0.08F, 0.0F, 0.0F)
        );
        final PartDefinition head = body.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(50, 0)
                .addBox(-6.0F, -6.0F, -4.5F, 12.0F, 9.0F, 9.0F),
            PartPose.offsetAndRotation(0.0F, -5.7F, -1.5F, -0.08F, 0.0F, 0.0F)
        );
        head.addOrReplaceChild(
            "beak",
            CubeListBuilder.create().texOffs(94, 0)
                .addBox(-3.5F, -1.5F, -5.0F, 7.0F, 3.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, 0.3F, -4.5F, 0.16F, 0.0F, 0.0F)
        );
        head.addOrReplaceChild(
            "appraisal_lens",
            CubeListBuilder.create().texOffs(124, 0)
                .addBox(-1.0F, -1.5F, -1.0F, 2.0F, 3.0F, 1.0F),
            PartPose.offsetAndRotation(3.2F, -0.65F, -4.6F, -0.04F, 0.0F, 0.12F)
        );
        head.addOrReplaceChild(
            "lens_chain",
            CubeListBuilder.create().texOffs(132, 0)
                .addBox(-0.5F, -0.5F, -0.5F, 1.0F, 6.0F, 1.0F),
            PartPose.offsetAndRotation(3.8F, 0.0F, -4.1F, 0.0F, 0.0F, -0.16F)
        );
        body.addOrReplaceChild(
            "belly_keel",
            CubeListBuilder.create().texOffs(138, 0)
                .addBox(-5.0F, -8.0F, -1.0F, 10.0F, 16.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, 1.2F, -5.25F, -0.12F, 0.0F, 0.0F)
        );
        body.addOrReplaceChild(
            "broker_collar",
            CubeListBuilder.create().texOffs(0, 24)
                .addBox(-7.5F, -1.5F, -5.5F, 15.0F, 3.0F, 11.0F),
            PartPose.offsetAndRotation(0.0F, -5.8F, 0.0F, 0.08F, 0.0F, 0.0F)
        );
        body.addOrReplaceChild(
            "tail_wedge",
            CubeListBuilder.create().texOffs(54, 24)
                .addBox(-3.5F, 0.0F, -1.0F, 7.0F, 5.0F, 7.0F),
            PartPose.offsetAndRotation(0.0F, 6.8F, 4.6F, 0.8F, 0.0F, 0.0F)
        );
        final PartDefinition mantle = body.addOrReplaceChild(
            "geode_mantle",
            CubeListBuilder.create().texOffs(84, 24)
                .addBox(-7.5F, -1.5F, -5.5F, 15.0F, 3.0F, 11.0F),
            PartPose.offsetAndRotation(-0.7F, -4.6F, 0.6F, 0.08F, 0.02F, -0.06F)
        );
        mantle.addOrReplaceChild(
            "right_crystal",
            CubeListBuilder.create().texOffs(138, 24)
                .addBox(-2.0F, -6.0F, -2.0F, 4.0F, 7.0F, 4.0F),
            PartPose.offsetAndRotation(-7.9F, -1.2F, 0.5F, -0.25F, 0.12F, -0.62F)
        );
        mantle.addOrReplaceChild(
            "left_crystal",
            CubeListBuilder.create().texOffs(158, 24)
                .addBox(-2.0F, -5.0F, -2.0F, 4.0F, 6.0F, 4.0F),
            PartPose.offsetAndRotation(7.3F, -0.6F, 1.2F, -0.18F, -0.08F, 0.54F)
        );
        mantle.addOrReplaceChild(
            "crown_crystal",
            CubeListBuilder.create().texOffs(0, 42)
                .addBox(-2.5F, -7.0F, -2.5F, 5.0F, 8.0F, 5.0F),
            PartPose.offsetAndRotation(1.2F, -1.8F, 2.6F, -0.1F, 0.0F, 0.82F)
        );
        final PartDefinition ledger = body.addOrReplaceChild(
            "ledger",
            CubeListBuilder.create().texOffs(22, 42)
                .addBox(-1.5F, -5.0F, -4.0F, 3.0F, 10.0F, 8.0F),
            PartPose.offsetAndRotation(0.0F, 1.3F, -5.8F, 0.08F, 1.5708F, 0.0F)
        );
        ledger.addOrReplaceChild(
            "ledger_cover",
            CubeListBuilder.create().texOffs(46, 42)
                .addBox(-1.0F, -5.5F, -4.5F, 1.0F, 11.0F, 9.0F),
            PartPose.offsetAndRotation(1.55F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F)
        );
        ledger.addOrReplaceChild(
            "ledger_clasp",
            CubeListBuilder.create().texOffs(68, 42)
                .addBox(-2.0F, -1.0F, -1.5F, 4.0F, 2.0F, 3.0F),
            PartPose.offsetAndRotation(1.7F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F)
        );
        final PartDefinition quiver = body.addOrReplaceChild(
            "quiver",
            CubeListBuilder.create().texOffs(84, 42)
                .addBox(-2.5F, -5.0F, -2.0F, 5.0F, 10.0F, 4.0F),
            PartPose.offsetAndRotation(6.0F, 0.3F, 4.6F, -0.32F, -0.08F, 0.28F)
        );
        quiver.addOrReplaceChild(
            "bolt_cluster",
            CubeListBuilder.create().texOffs(104, 42)
                .addBox(-2.5F, -7.0F, -1.5F, 5.0F, 8.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, -4.3F, 0.0F, 0.04F, 0.0F, 0.0F)
        );

        final PartDefinition rightFlipper = root.addOrReplaceChild(
            "right_flipper",
            CubeListBuilder.create().texOffs(124, 42)
                .addBox(-2.5F, -2.0F, -2.0F, 3.0F, 11.0F, 4.0F),
            PartPose.offsetAndRotation(-8.0F, 5.0F, 0.0F, -0.14F, 0.18F, 0.3F)
        );
        rightFlipper.addOrReplaceChild(
            "right_feather_fan",
            CubeListBuilder.create().texOffs(146, 42)
                .addBox(-2.5F, 0.0F, -1.5F, 4.0F, 6.0F, 3.0F),
            PartPose.offsetAndRotation(-1.2F, 7.0F, 0.3F, 0.08F, 0.0F, 0.4F)
        );
        final PartDefinition leftFlipper = root.addOrReplaceChild(
            "left_flipper",
            CubeListBuilder.create().texOffs(0, 62)
                .addBox(-0.5F, -2.0F, -2.0F, 3.0F, 11.0F, 4.0F),
            PartPose.offsetAndRotation(8.0F, 5.0F, 0.0F, -0.14F, -0.18F, -0.3F)
        );
        leftFlipper.addOrReplaceChild(
            "left_feather_fan",
            CubeListBuilder.create().texOffs(22, 62)
                .addBox(-1.5F, 0.0F, -1.5F, 4.0F, 6.0F, 3.0F),
            PartPose.offsetAndRotation(1.2F, 7.0F, 0.3F, 0.08F, 0.0F, -0.4F)
        );
        final PartDefinition rightLeg = root.addOrReplaceChild(
            "right_leg",
            CubeListBuilder.create().texOffs(44, 62)
                .addBox(-2.5F, 0.0F, -2.5F, 5.0F, 6.0F, 5.0F),
            PartPose.offsetAndRotation(-4.7F, 14.88F, 0.8F, -0.04F, 0.0F, 0.12F)
        );
        rightLeg.addOrReplaceChild(
            "right_webbed_foot",
            CubeListBuilder.create().texOffs(66, 62)
                .addBox(-3.5F, 0.0F, -5.5F, 7.0F, 2.0F, 7.0F),
            PartPose.offsetAndRotation(0.0F, 5.5F, -0.7F, 0.1F, 0.14F, 0.0F)
        );
        final PartDefinition leftLeg = root.addOrReplaceChild(
            "left_leg",
            CubeListBuilder.create().texOffs(100, 62)
                .addBox(-2.5F, 0.0F, -2.5F, 5.0F, 6.0F, 5.0F),
            PartPose.offsetAndRotation(4.7F, 14.88F, 0.8F, -0.04F, 0.0F, -0.12F)
        );
        leftLeg.addOrReplaceChild(
            "left_webbed_foot",
            CubeListBuilder.create().texOffs(122, 62)
                .addBox(-3.5F, 0.0F, -5.5F, 7.0F, 2.0F, 7.0F),
            PartPose.offsetAndRotation(0.0F, 5.5F, -0.7F, 0.1F, -0.14F, 0.0F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        head.yRot += state.yRot * Mth.DEG_TO_RAD;
        head.xRot += state.xRot * Mth.DEG_TO_RAD;
        final float pace = state.walkAnimationPos * 0.56F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F);
        final float waddle = Mth.sin(pace) * stride;
        brokerBody.zRot += waddle * 0.13F;
        head.zRot -= waddle * 0.055F;
        rightLeg.xRot += Mth.cos(pace) * stride * 0.52F;
        leftLeg.xRot += Mth.cos(pace + Mth.PI) * stride * 0.52F;
        rightFoot.xRot -= Mth.cos(pace) * stride * 0.12F;
        leftFoot.xRot -= Mth.cos(pace + Mth.PI) * stride * 0.12F;
        rightFlipper.xRot += Mth.cos(pace + Mth.PI) * stride * 0.22F;
        leftFlipper.xRot += Mth.cos(pace) * stride * 0.22F;
        rightFeatherFan.zRot += waddle * 0.04F;
        leftFeatherFan.zRot -= waddle * 0.04F;
        tailWedge.yRot += Mth.sin(state.ageInTicks * 0.09F) * 0.075F;
        crownCrystal.zRot += Mth.sin(state.ageInTicks * 0.045F) * 0.018F;
        appraisalLens.zRot += Mth.sin(state.ageInTicks * 0.12F) * 0.035F;
        quiver.zRot -= waddle * 0.03F;
        if (state.action == Action.LEDGER_VOLLEY) {
            final float commit = Mth.clamp(state.actionProgress, 0.0F, 1.0F);
            brokerBody.xRot += commit * 0.12F;
            head.xRot -= commit * 0.16F;
            ledger.yRot -= commit * 0.7F;
            ledger.xRot -= commit * 0.35F;
            ledgerCover.yRot += commit * 0.95F;
            rightFlipper.xRot = -1.22F * commit;
            rightFlipper.yRot = -0.4F * commit;
            rightFlipper.zRot += 0.38F * commit;
            rightFeatherFan.zRot += 0.3F * commit;
            leftFlipper.xRot = -0.58F * commit;
            leftFlipper.yRot = 0.25F * commit;
            geodeMantle.zRot += Mth.sin(state.ageInTicks * 0.24F) * 0.025F * commit;
        } else if (state.action == Action.QUIET_LEDGER || state.action == Action.APPRAISE_CONTEXT) {
            ledger.yRot -= 0.35F;
            rightFlipper.xRot = -0.58F;
            head.yRot += 0.18F;
        }
    }

    @Override
    public void translateToHand(final State state, final HumanoidArm arm, final PoseStack poseStack) {
        final ModelPart flipper = arm == HumanoidArm.LEFT ? leftFlipper : rightFlipper;
        final ModelPart fan = arm == HumanoidArm.LEFT ? leftFeatherFan : rightFeatherFan;
        flipper.translateAndRotate(poseStack);
        fan.translateAndRotate(poseStack);
        poseStack.translate(arm == HumanoidArm.LEFT ? 0.075F : -0.075F, 0.16F, -0.055F);
    }

    public static void extractRenderState(
        final StonebrokerEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.action = entity.presentationAction();
        state.actionProgress = state.action == Action.IDLE ? 0.0F : 1.0F;
    }

    public static final class State extends ArmedEntityRenderState {
        public Action action = Action.IDLE;
        public float actionProgress;
    }
}
