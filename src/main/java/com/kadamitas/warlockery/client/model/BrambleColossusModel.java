package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.BrambleColossusEntity;
import com.kadamitas.warlockery.entity.BrambleColossusRules;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public final class BrambleColossusModel extends EntityModel<BrambleColossusModel.State> {
    public static final int TEXTURE_WIDTH = 256;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart siegeBody;
    private final ModelPart vaultedCarapace;
    private final ModelPart highBloomBulb;
    private final ModelPart lowBloomBulb;
    private final ModelPart wedgeHead;
    private final ModelPart leftThornOutrigger;
    private final ModelPart rightThornOutrigger;
    private final ModelPart leftForePost;
    private final ModelPart leftSweepHook;
    private final ModelPart rightForePost;
    private final ModelPart rightSweepHook;
    private final ModelPart leftMidStabilizer;
    private final ModelPart leftMidToe;
    private final ModelPart rightMidStabilizer;
    private final ModelPart rightMidToe;
    private final ModelPart leftRearHaunch;
    private final ModelPart leftDragFoot;
    private final ModelPart rightRearHaunch;
    private final ModelPart rightDragFoot;
    private final ModelPart leftVineRib;
    private final ModelPart rightVineRib;

    public BrambleColossusModel(final ModelPart root) {
        super(root);
        siegeBody = root.getChild("siege_body");
        vaultedCarapace = siegeBody.getChild("vaulted_carapace");
        highBloomBulb = vaultedCarapace.getChild("high_bloom_bulb");
        lowBloomBulb = vaultedCarapace.getChild("low_bloom_bulb");
        wedgeHead = siegeBody.getChild("wedge_head");
        leftThornOutrigger = siegeBody.getChild("left_thorn_outrigger");
        rightThornOutrigger = siegeBody.getChild("right_thorn_outrigger");
        leftVineRib = siegeBody.getChild("left_vine_rib");
        rightVineRib = siegeBody.getChild("right_vine_rib");
        leftForePost = root.getChild("left_fore_post");
        leftSweepHook = leftForePost.getChild("left_sweep_hook");
        rightForePost = root.getChild("right_fore_post");
        rightSweepHook = rightForePost.getChild("right_sweep_hook");
        leftMidStabilizer = root.getChild("left_mid_stabilizer");
        leftMidToe = leftMidStabilizer.getChild("left_mid_toe");
        rightMidStabilizer = root.getChild("right_mid_stabilizer");
        rightMidToe = rightMidStabilizer.getChild("right_mid_toe");
        leftRearHaunch = root.getChild("left_rear_haunch");
        leftDragFoot = leftRearHaunch.getChild("left_drag_foot");
        rightRearHaunch = root.getChild("right_rear_haunch");
        rightDragFoot = rightRearHaunch.getChild("right_drag_foot");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition body = root.addOrReplaceChild(
            "siege_body",
            CubeListBuilder.create().texOffs(0, 0).addBox(-10.0F, -5.0F, -12.0F, 20.0F, 10.0F, 24.0F),
            PartPose.offsetAndRotation(0.0F, 6.947012F, 1.5F, 0.02F, 0.0F, 0.0F)
        );
        final PartDefinition carapace = body.addOrReplaceChild(
            "vaulted_carapace",
            CubeListBuilder.create().texOffs(90, 0).addBox(-12.0F, -5.0F, -13.0F, 24.0F, 7.0F, 26.0F),
            PartPose.offsetAndRotation(0.0F, -4.5F, 1.5F, 0.05F, 0.0F, 0.0F)
        );
        carapace.addOrReplaceChild("front_crown_thorn", CubeListBuilder.create().texOffs(192, 0).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 8.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -4.0F, -8.0F, -0.28F, 0.0F, 0.0F));
        carapace.addOrReplaceChild("left_crown_thorn", CubeListBuilder.create().texOffs(210, 0).addBox(-1.5F, -7.0F, -1.5F, 3.0F, 7.0F, 3.0F), PartPose.offsetAndRotation(7.0F, -3.0F, 1.0F, -0.12F, 0.0F, 0.34F));
        carapace.addOrReplaceChild("right_crown_thorn", CubeListBuilder.create().texOffs(224, 0).addBox(-1.5F, -6.0F, -1.5F, 3.0F, 6.0F, 3.0F), PartPose.offsetAndRotation(-7.0F, -3.0F, 2.0F, -0.08F, 0.0F, -0.38F));
        carapace.addOrReplaceChild(
            "high_bloom_bulb",
            CubeListBuilder.create()
                .texOffs(202, 84).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F)
                .texOffs(202, 84).addBox(-5.0F, -7.0F, -3.5F, 8.0F, 10.0F, 8.0F),
            PartPose.offsetAndRotation(4.0F, -5.0F, 3.0F, -0.1F, 0.2F, 0.1F)
        );
        carapace.addOrReplaceChild(
            "low_bloom_bulb",
            CubeListBuilder.create()
                .texOffs(0, 108).addBox(-3.0F, -7.0F, -3.0F, 6.0F, 7.0F, 6.0F)
                .texOffs(0, 108).addBox(-2.5F, -5.5F, -4.0F, 6.0F, 7.0F, 6.0F),
            PartPose.offsetAndRotation(-6.0F, -3.0F, -2.0F, 0.08F, -0.22F, -0.14F)
        );
        final PartDefinition head = body.addOrReplaceChild(
            "wedge_head",
            CubeListBuilder.create().texOffs(0, 36).addBox(-6.0F, -4.0F, -9.0F, 12.0F, 8.0F, 14.0F),
            PartPose.offsetAndRotation(0.0F, 1.0F, -14.0F, 0.2F, 0.0F, 0.0F)
        );
        head.addOrReplaceChild(
            "pale_sap_seam",
            CubeListBuilder.create().texOffs(54, 36).addBox(-2.0F, -3.0F, -1.0F, 4.0F, 6.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, -0.2F, -9.0F, 0.0F, 0.0F, 0.12F)
        );
        body.addOrReplaceChild(
            "underslung_sap_core",
            CubeListBuilder.create().texOffs(54, 36).addBox(-2.0F, -3.0F, -1.0F, 4.0F, 6.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 4.0F, -2.0F, 0.18F, 0.0F, -0.08F)
        );
        final PartDefinition leftOutrigger = body.addOrReplaceChild(
            "left_thorn_outrigger",
            CubeListBuilder.create().texOffs(70, 36).addBox(-1.5F, -2.0F, -2.0F, 3.0F, 4.0F, 14.0F),
            PartPose.offsetAndRotation(11.5F, -1.0F, -1.0F, 0.18F, 0.88F, 0.78F)
        );
        leftOutrigger.addOrReplaceChild("left_outrigger_hook", CubeListBuilder.create().texOffs(106, 36).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 9.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 11.0F, 0.55F, 0.0F, -0.64F));
        final PartDefinition rightOutrigger = body.addOrReplaceChild(
            "right_thorn_outrigger",
            CubeListBuilder.create().texOffs(120, 36).addBox(-1.5F, -2.0F, -2.0F, 3.0F, 4.0F, 14.0F),
            PartPose.offsetAndRotation(-11.5F, -1.0F, -1.0F, 0.18F, -0.84F, -0.76F)
        );
        rightOutrigger.addOrReplaceChild("right_outrigger_hook", CubeListBuilder.create().texOffs(156, 36).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 8.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 11.0F, 0.5F, 0.0F, 0.66F));
        body.addOrReplaceChild("left_vine_rib", CubeListBuilder.create().texOffs(170, 36).addBox(-2.0F, -2.0F, -10.0F, 4.0F, 4.0F, 20.0F), PartPose.offsetAndRotation(10.5F, -2.0F, 1.0F, 0.0F, 0.0F, 0.34F));
        body.addOrReplaceChild("right_vine_rib", CubeListBuilder.create().texOffs(152, 84).addBox(-2.0F, -2.0F, -10.0F, 4.0F, 4.0F, 20.0F), PartPose.offsetAndRotation(-10.5F, -2.0F, 1.0F, 0.0F, 0.0F, -0.34F));

        final PartDefinition leftFore = root.addOrReplaceChild(
            "left_fore_post",
            CubeListBuilder.create().texOffs(0, 62).addBox(-3.5F, 0.0F, -4.0F, 7.0F, 9.0F, 8.0F),
            PartPose.offsetAndRotation(10.5F, 9.947012F, -8.5F, -0.14F, 0.0F, -0.12F)
        );
        final PartDefinition leftHook = leftFore.addOrReplaceChild(
            "left_sweep_hook",
            CubeListBuilder.create().texOffs(32, 62).addBox(-3.0F, 0.0F, -7.0F, 6.0F, 4.0F, 12.0F),
            PartPose.offsetAndRotation(0.0F, 8.0F, -1.0F, 0.06F, 0.18F, 0.0F)
        );
        leftHook.addOrReplaceChild("left_fore_thorn", CubeListBuilder.create().texOffs(70, 62).addBox(-1.5F, -5.0F, -1.5F, 3.0F, 5.0F, 3.0F), PartPose.offsetAndRotation(2.0F, 1.0F, -3.0F, -0.3F, 0.0F, 0.45F));
        final PartDefinition rightFore = root.addOrReplaceChild(
            "right_fore_post",
            CubeListBuilder.create().texOffs(84, 62).addBox(-3.5F, 0.0F, -4.0F, 7.0F, 9.0F, 8.0F),
            PartPose.offsetAndRotation(-10.5F, 9.947012F, -8.5F, -0.14F, 0.0F, 0.12F)
        );
        final PartDefinition rightHook = rightFore.addOrReplaceChild(
            "right_sweep_hook",
            CubeListBuilder.create().texOffs(116, 62).addBox(-3.0F, 0.0F, -7.0F, 6.0F, 4.0F, 12.0F),
            PartPose.offsetAndRotation(0.0F, 8.0F, -1.0F, 0.06F, -0.18F, 0.0F)
        );
        rightHook.addOrReplaceChild("right_fore_thorn", CubeListBuilder.create().texOffs(154, 62).addBox(-1.5F, -5.0F, -1.5F, 3.0F, 5.0F, 3.0F), PartPose.offsetAndRotation(-2.0F, 1.0F, -3.0F, -0.3F, 0.0F, -0.45F));

        final PartDefinition leftMid = root.addOrReplaceChild("left_mid_stabilizer", CubeListBuilder.create().texOffs(168, 62).addBox(-2.0F, 0.0F, -2.5F, 4.0F, 7.0F, 5.0F), PartPose.offsetAndRotation(12.0F, 12.947012F, 2.0F, 0.16F, 0.0F, -0.42F));
        leftMid.addOrReplaceChild("left_mid_toe", CubeListBuilder.create().texOffs(188, 62).addBox(-2.0F, 0.0F, -4.0F, 4.0F, 3.0F, 7.0F), PartPose.offsetAndRotation(0.0F, 7.0F, -0.5F, 0.0F, 0.28F, 0.0F));
        final PartDefinition rightMid = root.addOrReplaceChild("right_mid_stabilizer", CubeListBuilder.create().texOffs(212, 62).addBox(-2.0F, 0.0F, -2.5F, 4.0F, 7.0F, 5.0F), PartPose.offsetAndRotation(-12.0F, 12.947012F, 2.0F, 0.16F, 0.0F, 0.42F));
        rightMid.addOrReplaceChild("right_mid_toe", CubeListBuilder.create().texOffs(232, 62).addBox(-2.0F, 0.0F, -4.0F, 4.0F, 3.0F, 7.0F), PartPose.offsetAndRotation(0.0F, 7.0F, -0.5F, 0.0F, -0.28F, 0.0F));

        final PartDefinition leftRear = root.addOrReplaceChild("left_rear_haunch", CubeListBuilder.create().texOffs(0, 84).addBox(-4.0F, 0.0F, -4.5F, 8.0F, 8.0F, 9.0F), PartPose.offsetAndRotation(7.5F, 11.947012F, 10.5F, 0.2F, 0.0F, -0.14F));
        leftRear.addOrReplaceChild("left_drag_foot", CubeListBuilder.create().texOffs(36, 84).addBox(-3.5F, 0.0F, -6.0F, 7.0F, 3.0F, 10.0F), PartPose.offsetAndRotation(0.0F, 8.0F, 1.0F, 0.0F, 0.12F, 0.0F));
        final PartDefinition rightRear = root.addOrReplaceChild("right_rear_haunch", CubeListBuilder.create().texOffs(72, 84).addBox(-4.0F, 0.0F, -4.5F, 8.0F, 8.0F, 9.0F), PartPose.offsetAndRotation(-7.5F, 11.947012F, 10.5F, 0.2F, 0.0F, 0.14F));
        rightRear.addOrReplaceChild("right_drag_foot", CubeListBuilder.create().texOffs(108, 84).addBox(-3.5F, 0.0F, -6.0F, 7.0F, 3.0F, 10.0F), PartPose.offsetAndRotation(0.0F, 8.0F, 1.0F, 0.0F, -0.12F, 0.0F));
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        wedgeHead.yRot += state.yRot * Mth.DEG_TO_RAD * 0.42F;
        wedgeHead.xRot += state.xRot * Mth.DEG_TO_RAD * 0.38F;
        final float pace = state.walkAnimationPos * 0.48F;
        final float drag = Math.min(state.walkAnimationSpeed, 1.0F) * 0.38F;
        final float leftDrive = Mth.cos(pace) * drag;
        final float rightDrive = Mth.cos(pace + Mth.PI) * drag;
        leftForePost.xRot += leftDrive;
        rightForePost.xRot += rightDrive;
        leftRearHaunch.xRot += rightDrive * 0.72F;
        rightRearHaunch.xRot += leftDrive * 0.72F;
        leftMidStabilizer.xRot += rightDrive * 0.34F;
        rightMidStabilizer.xRot += leftDrive * 0.34F;
        leftDragFoot.xRot -= rightDrive * 0.28F;
        rightDragFoot.xRot -= leftDrive * 0.28F;
        leftMidToe.xRot -= rightDrive * 0.2F;
        rightMidToe.xRot -= leftDrive * 0.2F;
        siegeBody.zRot += Mth.sin(state.ageInTicks * 0.035F) * 0.018F;
        vaultedCarapace.y += Mth.sin(state.ageInTicks * 0.045F) * 0.12F;
        leftThornOutrigger.zRot += Mth.sin(state.ageInTicks * 0.04F) * 0.04F;
        rightThornOutrigger.zRot -= Mth.sin(state.ageInTicks * 0.04F + 0.6F) * 0.04F;
        highBloomBulb.yRot += Mth.sin(state.ageInTicks * 0.025F) * 0.025F;
        lowBloomBulb.yRot -= Mth.sin(state.ageInTicks * 0.027F + 0.6F) * 0.022F;
        if (state.posted) {
            leftForePost.xRot -= 0.12F;
            rightForePost.xRot -= 0.12F;
            leftRearHaunch.xRot += 0.1F;
            rightRearHaunch.xRot += 0.1F;
        }
        final float sweep = Mth.clamp(state.actionProgress, 0.0F, 1.0F);
        if (state.displaying) {
            siegeBody.xRot -= 0.12F;
            vaultedCarapace.y -= 0.65F;
            leftForePost.xRot -= 0.64F * sweep;
            leftForePost.x += 5.0F * sweep;
            leftForePost.yRot -= 1.18F * sweep;
            leftSweepHook.xRot -= 0.38F * sweep;
            leftSweepHook.x += 4.0F * sweep;
            leftSweepHook.yRot -= 0.86F * sweep;
            leftSweepHook.zRot -= 0.44F * sweep;
            rightForePost.xRot += 0.16F * sweep;
            rightSweepHook.zRot += 0.18F * sweep;
            leftVineRib.zRot -= 0.12F * sweep;
            rightVineRib.zRot += 0.12F * sweep;
            highBloomBulb.xRot -= 0.16F * sweep;
            highBloomBulb.y -= 0.45F * sweep;
            highBloomBulb.xScale += 0.42F * sweep;
            highBloomBulb.zScale += 0.24F * sweep;
            lowBloomBulb.zRot -= 0.12F * sweep;
            lowBloomBulb.xScale += 0.08F * sweep;
        }
        final float falter = 1.0F - Math.clamp(state.nerve / 100.0F, 0.0F, 1.0F);
        siegeBody.y += falter * 1.2F;
        leftRearHaunch.zRot += falter * 0.16F;
        rightRearHaunch.zRot -= falter * 0.16F;
    }

    public static void extractRenderState(
        final BrambleColossusEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.posted = entity.presentationPosted();
        state.nerve = entity.presentationNerve();
        state.leg = entity.presentationLeg();
        final BrambleColossusRules.Phase phase = entity.presentationPhase();
        state.displaying = phase == BrambleColossusRules.Phase.DISPLAY;
        state.actionProgress = state.displaying ? 1.0F : entity.getAttackAnim(partialTicks);
    }

    public static final class State extends LivingEntityRenderState {
        public int nerve = 100;
        public int leg;
        public float actionProgress;
        public boolean posted;
        public boolean displaying;
    }
}
