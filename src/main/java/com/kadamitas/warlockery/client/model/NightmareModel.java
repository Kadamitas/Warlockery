package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.SpectralSteedEntity;
import com.kadamitas.warlockery.entity.SpectralSteedRules.Gait;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/** Crouched dream-dark mount with a branching crest and three-pronged chain tail. */
public final class NightmareModel extends EntityModel<NightmareModel.State> {
    public static final int TEXTURE_WIDTH = 256;
    public static final int TEXTURE_HEIGHT = 224;

    private final ModelPart neck, skull, jaw, crest;
    private final ModelPart leftFront, rightFront, leftFrontFetlock, rightFrontFetlock;
    private final ModelPart leftRear, rightRear, leftRearFetlock, rightRearFetlock;
    private final ModelPart tailAnchor, tailLink, tailEnd;

    public NightmareModel(final ModelPart root) {
        super(root);
        neck = root.getChild("arched_neck"); skull = neck.getChild("war_skull"); jaw = skull.getChild("lower_jaw");
        crest = root.getChild("broken_crest");
        leftFront = root.getChild("left_front_pillar"); rightFront = root.getChild("right_front_pillar");
        leftFrontFetlock = leftFront.getChild("left_front_fetlock"); rightFrontFetlock = rightFront.getChild("right_front_fetlock");
        leftRear = root.getChild("left_rear_drive"); rightRear = root.getChild("right_rear_drive");
        leftRearFetlock = leftRear.getChild("left_rear_fetlock"); rightRearFetlock = rightRear.getChild("right_rear_fetlock");
        tailAnchor = root.getChild("chain_tail_anchor"); tailLink = tailAnchor.getChild("chain_tail_link"); tailEnd = tailLink.getChild("chain_tail_end");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("shoulder_yoke", CubeListBuilder.create().texOffs(0, 22)
            .addBox(-5.0F, -3.5F, -5.0F, 10.0F, 7.0F, 8.0F), PartPose.offset(0.0F, 11.0F, -4.0F));
        root.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(44, 22)
            .addBox(-4.0F, -3.0F, -6.0F, 8.0F, 6.0F, 12.0F), PartPose.offset(0.0F, 11.5F, 2.5F));
        root.addOrReplaceChild("croup_block", CubeListBuilder.create().texOffs(102, 22)
            .addBox(-4.5F, -3.5F, -3.5F, 9.0F, 7.0F, 7.0F), PartPose.offset(0.0F, 11.0F, 8.5F));
        final PartDefinition neck = root.addOrReplaceChild("arched_neck", CubeListBuilder.create().texOffs(144, 22)
            .addBox(-3.0F, -7.0F, -2.5F, 6.0F, 8.0F, 5.0F), PartPose.offset(0.0F, 9.5F, -7.0F));
        final PartDefinition skull = neck.addOrReplaceChild("war_skull", CubeListBuilder.create().texOffs(0, 0)
            .addBox(-3.5F, -3.0F, -5.0F, 7.0F, 6.0F, 7.0F), PartPose.offset(0.0F, -5.8F, -2.0F));
        skull.addOrReplaceChild("lower_jaw", CubeListBuilder.create().texOffs(40, 0)
            .addBox(-2.5F, -0.5F, -5.0F, 5.0F, 2.0F, 6.0F), PartPose.offset(0.0F, 2.2F, -3.5F));
        final PartDefinition leftHorn = skull.addOrReplaceChild("left_obsidian_horn", CubeListBuilder.create().texOffs(98, 0)
            .addBox(-0.7F, -3.0F, -0.7F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(2.7F, -2.2F, 0.0F, -0.55F, 0.0F, 0.2F));
        leftHorn.addOrReplaceChild("left_obsidian_horn_tip", CubeListBuilder.create().texOffs(98, 0)
            .addBox(-0.5F, -2.5F, -0.5F, 1.0F, 2.5F, 1.0F), PartPose.offsetAndRotation(0.0F, -2.7F, 0.0F, -0.45F, 0.0F, 0.18F));
        final PartDefinition rightHorn = skull.addOrReplaceChild("right_obsidian_horn", CubeListBuilder.create().texOffs(112, 0)
            .addBox(-0.3F, -3.0F, -0.7F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(-2.7F, -2.2F, 0.0F, -0.55F, 0.0F, -0.2F));
        rightHorn.addOrReplaceChild("right_obsidian_horn_tip", CubeListBuilder.create().texOffs(112, 0)
            .addBox(-0.5F, -2.5F, -0.5F, 1.0F, 2.5F, 1.0F), PartPose.offsetAndRotation(0.0F, -2.7F, 0.0F, -0.45F, 0.0F, -0.18F));

        final PartDefinition crest = root.addOrReplaceChild("broken_crest", CubeListBuilder.create().texOffs(144, 22)
            .addBox(-1.0F, -2.0F, -5.0F, 2.0F, 2.0F, 8.0F), PartPose.offset(0.0F, 7.0F, -1.0F));
        final PartDefinition crestOne = crest.addOrReplaceChild("crest_shard_one", CubeListBuilder.create().texOffs(0, 94)
            .addBox(-1.0F, -10.0F, -0.5F, 2.0F, 10.0F, 1.0F), PartPose.offsetAndRotation(-1.0F, 0.0F, -4.2F, -0.24F, 0.0F, -0.12F));
        crestOne.addOrReplaceChild("crest_tip_one", CubeListBuilder.create().texOffs(0, 94)
            .addBox(-0.7F, -7.0F, -0.5F, 1.4F, 7.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -9.4F, 0.0F, -0.16F, 0.0F, -0.08F));
        final PartDefinition crestTwo = crest.addOrReplaceChild("crest_shard_two", CubeListBuilder.create().texOffs(16, 94)
            .addBox(-1.0F, -9.0F, -0.5F, 2.0F, 9.0F, 1.0F), PartPose.offsetAndRotation(0.7F, 0.0F, -2.2F, 0.12F, 0.0F, 0.1F));
        crestTwo.addOrReplaceChild("crest_tip_two", CubeListBuilder.create().texOffs(16, 94)
            .addBox(-0.7F, -8.0F, -0.5F, 1.4F, 8.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -8.4F, 0.0F, 0.1F, 0.0F, 0.06F));
        final PartDefinition crestThree = crest.addOrReplaceChild("crest_shard_three", CubeListBuilder.create().texOffs(30, 94)
            .addBox(-1.0F, -9.0F, -0.5F, 2.0F, 9.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -0.2F, -0.08F, 0.0F, 0.0F));
        crestThree.addOrReplaceChild("crest_tip_three", CubeListBuilder.create().texOffs(30, 94)
            .addBox(-0.7F, -9.0F, -0.5F, 1.4F, 9.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -11.4F, 0.0F, -0.12F, 0.0F, -0.08F));
        final PartDefinition crestFour = crest.addOrReplaceChild("crest_shard_four", CubeListBuilder.create().texOffs(0, 94)
            .addBox(-1.0F, -10.0F, -0.5F, 2.0F, 10.0F, 1.0F), PartPose.offsetAndRotation(-0.7F, 0.0F, 1.5F, 0.22F, 0.0F, -0.1F));
        crestFour.addOrReplaceChild("crest_tip_four", CubeListBuilder.create().texOffs(0, 94)
            .addBox(-0.7F, -7.0F, -0.5F, 1.4F, 7.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -9.4F, 0.0F, 0.18F, 0.0F, -0.06F));
        final PartDefinition crestFive = crest.addOrReplaceChild("crest_shard_five", CubeListBuilder.create().texOffs(16, 94)
            .addBox(-1.0F, -8.0F, -0.5F, 2.0F, 8.0F, 1.0F), PartPose.offsetAndRotation(0.8F, 0.0F, 2.8F, 0.34F, 0.0F, 0.12F));
        crestFive.addOrReplaceChild("crest_tip_five", CubeListBuilder.create().texOffs(16, 94)
            .addBox(-0.7F, -6.0F, -0.5F, 1.4F, 6.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -7.4F, 0.0F, 0.28F, 0.0F, 0.08F));

        final PartDefinition lf = root.addOrReplaceChild("left_front_pillar", CubeListBuilder.create().texOffs(0, 48)
            .addBox(-1.0F, 0.0F, -1.6F, 3.0F, 6.0F, 3.0F), PartPose.offset(3.5F, 13.0F, -5.0F));
        final PartDefinition lff = lf.addOrReplaceChild("left_front_fetlock", CubeListBuilder.create().texOffs(22, 48)
            .addBox(-0.7F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offset(0.0F, 5.0F, 0.0F));
        lff.addOrReplaceChild("left_front_cloven", CubeListBuilder.create().texOffs(40, 48)
            .addBox(-1.2F, 0.0F, -2.8F, 3.0F, 2.0F, 4.0F), PartPose.offset(0.0F, 4.0F, -0.2F));
        final PartDefinition rf = root.addOrReplaceChild("right_front_pillar", CubeListBuilder.create().texOffs(64, 48)
            .addBox(-2.0F, 0.0F, -1.6F, 3.0F, 6.0F, 3.0F), PartPose.offset(-3.5F, 13.0F, -5.0F));
        final PartDefinition rff = rf.addOrReplaceChild("right_front_fetlock", CubeListBuilder.create().texOffs(86, 48)
            .addBox(-1.3F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offset(0.0F, 5.0F, 0.0F));
        rff.addOrReplaceChild("right_front_cloven", CubeListBuilder.create().texOffs(104, 48)
            .addBox(-1.8F, 0.0F, -2.8F, 3.0F, 2.0F, 4.0F), PartPose.offset(0.0F, 4.0F, -0.2F));
        final PartDefinition lr = root.addOrReplaceChild("left_rear_drive", CubeListBuilder.create().texOffs(0, 70)
            .addBox(-1.2F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F), PartPose.offset(3.2F, 13.0F, 9.0F));
        final PartDefinition lrf = lr.addOrReplaceChild("left_rear_fetlock", CubeListBuilder.create().texOffs(26, 70)
            .addBox(-0.7F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offset(0.0F, 5.0F, 0.4F));
        lrf.addOrReplaceChild("left_rear_hook", CubeListBuilder.create().texOffs(26, 70)
            .addBox(-0.5F, -0.5F, 0.0F, 1.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.3F, 2.0F, 0.8F, -0.7F, 0.0F, 0.0F));
        lrf.addOrReplaceChild("left_rear_cloven", CubeListBuilder.create().texOffs(42, 70)
            .addBox(-1.2F, 0.0F, -2.5F, 3.0F, 2.0F, 4.0F), PartPose.offset(0.0F, 4.0F, -0.2F));
        final PartDefinition rr = root.addOrReplaceChild("right_rear_drive", CubeListBuilder.create().texOffs(68, 70)
            .addBox(-2.8F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F), PartPose.offset(-3.2F, 13.0F, 9.0F));
        final PartDefinition rrf = rr.addOrReplaceChild("right_rear_fetlock", CubeListBuilder.create().texOffs(94, 70)
            .addBox(-1.3F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offset(0.0F, 5.0F, 0.4F));
        rrf.addOrReplaceChild("right_rear_hook", CubeListBuilder.create().texOffs(94, 70)
            .addBox(-0.5F, -0.5F, 0.0F, 1.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(-0.3F, 2.0F, 0.8F, -0.7F, 0.0F, 0.0F));
        rrf.addOrReplaceChild("right_rear_cloven", CubeListBuilder.create().texOffs(110, 70)
            .addBox(-1.8F, 0.0F, -2.5F, 3.0F, 2.0F, 4.0F), PartPose.offset(0.0F, 4.0F, -0.2F));

        final PartDefinition anchor = root.addOrReplaceChild("chain_tail_anchor", CubeListBuilder.create().texOffs(42, 94)
            .addBox(-0.5F, -1.0F, 0.0F, 1.0F, 2.0F, 3.5F), PartPose.offsetAndRotation(0.0F, 10.0F, 11.0F, 0.38F, 0.0F, 0.0F));
        final PartDefinition link = anchor.addOrReplaceChild("chain_tail_link", CubeListBuilder.create().texOffs(54, 94)
            .addBox(-0.5F, -1.0F, 0.0F, 1.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 3.2F, 0.3F, 0.0F, 0.0F));
        final PartDefinition end = link.addOrReplaceChild("chain_tail_end", CubeListBuilder.create().texOffs(66, 94)
            .addBox(-0.5F, -0.7F, 0.0F, 1.0F, 1.0F, 2.5F), PartPose.offsetAndRotation(0.0F, 0.0F, 2.8F, 0.24F, 0.0F, 0.0F));
        final PartDefinition leftBarb = end.addOrReplaceChild("left_ember_barb", CubeListBuilder.create().texOffs(78, 94)
            .addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 2.8F), PartPose.offsetAndRotation(0.8F, -0.4F, 2.2F, -0.55F, 0.62F, 0.0F));
        leftBarb.addOrReplaceChild("left_ember_branch_tip", CubeListBuilder.create().texOffs(78, 94)
            .addBox(-0.4F, -0.4F, 0.0F, 0.8F, 0.8F, 1.2F), PartPose.offsetAndRotation(0.0F, 0.0F, 2.6F, -0.28F, 0.25F, 0.0F));
        final PartDefinition centerBarb = end.addOrReplaceChild("center_ember_barb", CubeListBuilder.create().texOffs(92, 94)
            .addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 2.8F), PartPose.offsetAndRotation(0.0F, 0.3F, 2.2F, 0.24F, 0.0F, 0.0F));
        centerBarb.addOrReplaceChild("center_ember_branch_tip", CubeListBuilder.create().texOffs(92, 94)
            .addBox(-0.4F, -0.4F, 0.0F, 0.8F, 0.8F, 1.2F), PartPose.offsetAndRotation(0.0F, 0.0F, 2.6F, 0.18F, 0.0F, 0.0F));
        final PartDefinition rightBarb = end.addOrReplaceChild("right_ember_barb", CubeListBuilder.create().texOffs(106, 94)
            .addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 2.8F), PartPose.offsetAndRotation(-0.8F, -0.4F, 2.2F, -0.55F, -0.62F, 0.0F));
        rightBarb.addOrReplaceChild("right_ember_branch_tip", CubeListBuilder.create().texOffs(106, 94)
            .addBox(-0.4F, -0.4F, 0.0F, 0.8F, 0.8F, 1.2F), PartPose.offsetAndRotation(0.0F, 0.0F, 2.6F, -0.28F, -0.25F, 0.0F));
        final PartDefinition leftShadow = root.addOrReplaceChild("left_after_shadow", CubeListBuilder.create().texOffs(120, 94)
            .addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(3.2F, 15.0F, 9.0F, -0.4F, 0.35F, 0.0F));
        leftShadow.addOrReplaceChild("left_after_shadow_tip", CubeListBuilder.create().texOffs(120, 94)
            .addBox(-0.4F, -0.4F, 0.0F, 0.8F, 0.8F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 3.7F, -0.22F, 0.2F, 0.0F));
        final PartDefinition rightShadow = root.addOrReplaceChild("right_after_shadow", CubeListBuilder.create().texOffs(134, 94)
            .addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-3.2F, 15.5F, 9.0F, -0.4F, -0.35F, 0.0F));
        rightShadow.addOrReplaceChild("right_after_shadow_tip", CubeListBuilder.create().texOffs(134, 94)
            .addBox(-0.4F, -0.4F, 0.0F, 0.8F, 0.8F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 3.7F, -0.22F, -0.2F, 0.0F));
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override public void setupAnim(final State state) {
        super.setupAnim(state);
        skull.yRot += state.yRot * Mth.DEG_TO_RAD; skull.xRot += state.xRot * Mth.DEG_TO_RAD;
        final float power = switch (state.gait) { case HALT -> 0.0F; case WALK -> 0.5F; case TROT -> 0.72F; case CANTER -> 0.96F; case SPRINT -> 1.22F; };
        final float phase = state.walkAnimationPos * (0.72F + power * 0.32F); final float stride = state.walkAnimationSpeed * power;
        leftFront.xRot += Mth.cos(phase) * stride; rightFront.xRot += Mth.cos(phase + Mth.PI) * stride;
        leftRear.xRot += Mth.cos(phase + Mth.PI) * stride * 0.9F; rightRear.xRot += Mth.cos(phase) * stride * 0.9F;
        leftFrontFetlock.xRot -= Math.max(0.0F, Mth.sin(phase)) * stride * 0.52F; rightFrontFetlock.xRot -= Math.max(0.0F, -Mth.sin(phase)) * stride * 0.52F;
        leftRearFetlock.xRot += Mth.sin(phase) * stride * 0.38F; rightRearFetlock.xRot -= Mth.sin(phase) * stride * 0.38F;
        crest.zRot += Mth.sin(state.ageInTicks * 0.19F) * 0.06F;
        tailAnchor.yRot += Mth.sin(state.ageInTicks * 0.21F) * 0.25F; tailLink.yRot -= Mth.sin(state.ageInTicks * 0.17F) * 0.34F; tailEnd.zRot += Mth.cos(state.ageInTicks * 0.23F) * 0.16F;
        if (state.warning) { neck.xRot -= 0.58F; skull.xRot += 0.28F; jaw.xRot += 0.72F; crest.yScale += 0.3F; leftFront.xRot -= 0.62F; rightFront.xRot -= 0.62F; tailEnd.yScale += 0.32F; }
        if (state.balking) { neck.yRot += Mth.sin(state.ageInTicks * 0.6F) * 0.18F; }
        if (state.airborne) { leftFront.xRot -= 0.52F; rightFront.xRot -= 0.52F; leftRear.xRot += 0.58F; rightRear.xRot += 0.58F; }
    }

    public static void extractRenderState(final SpectralSteedEntity entity, final State state, final float partialTicks) {
        state.gait = entity.presentationGait();
        state.bond = entity.presentationBond();
        state.fatigue = entity.presentationFatigue();
        state.balking = entity.presentationBalking();
        state.resting = entity.presentationResting();
        state.carrying = entity.isVehicle(); state.airborne = !entity.onGround();
        state.warning = entity.presentationWarning();
    }

    public static final class State extends LivingEntityRenderState {
        public Gait gait = Gait.HALT;
        public int bond, fatigue;
        public boolean balking, resting, carrying, airborne, warning;
    }
}
