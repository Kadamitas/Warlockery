package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.IronboundSentinelEntity;
import com.kadamitas.warlockery.entity.IronboundSentinelRules.Phase;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public final class IronboundSentinelModel extends EntityModel<IronboundSentinelModel.State> {
    public static final int TEXTURE_WIDTH = 192;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart villageGuardian;
    private final ModelPart guardianHead;
    private final ModelPart leatherGuardHelmet;
    private final ModelPart villageBellCore;
    private final ModelPart foldedDefenseBow;
    private final ModelPart rightGuardArm;
    private final ModelPart rightHammerClamp;
    private final ModelPart leftGuardArm;
    private final ModelPart leftHammerClamp;
    private final ModelPart rightMasonryLeg;
    private final ModelPart leftMasonryLeg;
    private final ModelPart rightAnchorFoot;
    private final ModelPart leftAnchorFoot;
    private final ModelPart rightLeatherPauldron;
    private final ModelPart leftLeatherPauldron;

    public IronboundSentinelModel(final ModelPart root) {
        super(root);
        villageGuardian = root.getChild("village_guardian");
        guardianHead = villageGuardian.getChild("guardian_head");
        leatherGuardHelmet = guardianHead.getChild("leather_guard_helmet");
        villageBellCore = villageGuardian.getChild("village_bell_core");
        foldedDefenseBow = villageGuardian.getChild("folded_defense_bow");
        rightGuardArm = root.getChild("right_guard_arm");
        rightHammerClamp = rightGuardArm.getChild("right_hammer_clamp");
        leftGuardArm = root.getChild("left_guard_arm");
        leftHammerClamp = leftGuardArm.getChild("left_hammer_clamp");
        rightMasonryLeg = root.getChild("right_masonry_leg");
        rightAnchorFoot = rightMasonryLeg.getChild("right_anchor_foot");
        leftMasonryLeg = root.getChild("left_masonry_leg");
        leftAnchorFoot = leftMasonryLeg.getChild("left_anchor_foot");
        rightLeatherPauldron = villageGuardian.getChild("right_leather_pauldron");
        leftLeatherPauldron = villageGuardian.getChild("left_leather_pauldron");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition guardian = root.addOrReplaceChild(
            "village_guardian",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6.5F, -5.0F, -4.0F, 13.0F, 11.0F, 8.0F)
                .texOffs(42, 0).addBox(-5.0F, 5.0F, -3.2F, 10.0F, 4.0F, 6.4F),
            PartPose.offset(0.0F, 8.0F, 0.0F)
        );
        final PartDefinition head = guardian.addOrReplaceChild(
            "guardian_head",
            CubeListBuilder.create()
                .texOffs(74, 0).addBox(-4.5F, -4.0F, -3.5F, 9.0F, 5.0F, 7.0F)
                .texOffs(106, 0).addBox(-3.0F, -1.2F, -4.2F, 6.0F, 1.5F, 1.2F),
            PartPose.offset(0.0F, -5.0F, -0.2F)
        );
        final PartDefinition helmet = head.addOrReplaceChild(
            "leather_guard_helmet",
            CubeListBuilder.create()
                .texOffs(124, 0).addBox(-5.0F, -4.8F, -4.0F, 10.0F, 3.0F, 8.0F)
                .texOffs(150, 0).addBox(-5.5F, -2.2F, -4.4F, 11.0F, 1.5F, 8.8F),
            PartPose.ZERO
        );
        helmet.addOrReplaceChild(
            "iron_brow",
            CubeListBuilder.create().texOffs(0, 22).addBox(-4.0F, -0.8F, -0.8F, 8.0F, 1.6F, 1.6F),
            PartPose.offset(0.0F, -1.4F, -4.0F)
        );
        guardian.addOrReplaceChild(
            "leather_harness",
            CubeListBuilder.create()
                .texOffs(20, 22).addBox(-6.8F, -4.8F, -4.3F, 2.2F, 11.0F, 1.4F)
                .texOffs(30, 22).addBox(4.6F, -4.8F, -4.3F, 2.2F, 11.0F, 1.4F)
                .texOffs(40, 22).addBox(-5.0F, -1.0F, -4.5F, 10.0F, 2.0F, 1.5F),
            PartPose.ZERO
        );
        guardian.addOrReplaceChild(
            "blue_tabard",
            CubeListBuilder.create()
                .texOffs(66, 22).addBox(-3.3F, -1.0F, -0.8F, 6.6F, 9.0F, 1.6F)
                .texOffs(84, 22).addBox(-2.6F, 7.0F, -0.6F, 2.6F, 4.0F, 1.2F)
                .texOffs(94, 22).addBox(0.0F, 7.0F, -0.6F, 2.6F, 4.0F, 1.2F),
            PartPose.offset(0.0F, 1.0F, -4.1F)
        );
        final PartDefinition bellCore = guardian.addOrReplaceChild(
            "village_bell_core",
            CubeListBuilder.create()
                .texOffs(104, 22).addBox(-2.5F, -2.0F, -2.0F, 5.0F, 5.0F, 4.0F)
                .texOffs(124, 22).addBox(-3.4F, 2.3F, -2.8F, 6.8F, 1.5F, 5.6F)
                .texOffs(150, 22).addBox(-0.8F, 3.5F, -0.8F, 1.6F, 2.0F, 1.6F),
            PartPose.offset(0.0F, 0.3F, -5.0F)
        );
        bellCore.addOrReplaceChild(
            "village_bell_clapper",
            CubeListBuilder.create().texOffs(150, 22)
                .addBox(-0.8F, -1.0F, -0.8F, 1.6F, 2.0F, 1.6F),
            PartPose.offset(0.0F, 5.2F, 0.0F)
        );
        final PartDefinition bow = guardian.addOrReplaceChild(
            "folded_defense_bow",
            CubeListBuilder.create()
                .texOffs(160, 22).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 10.0F, 2.0F)
                .texOffs(170, 22).addBox(-4.0F, -0.7F, -0.7F, 8.0F, 1.4F, 1.4F),
            PartPose.offsetAndRotation(5.6F, 0.0F, 3.8F, 0.0F, 0.25F, 0.04F)
        );
        bow.addOrReplaceChild(
            "upper_bow_limb",
            CubeListBuilder.create().texOffs(0, 42).addBox(-0.8F, -6.0F, -0.8F, 1.6F, 6.0F, 1.6F),
            PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, 0.0F, 0.0F, 0.35F)
        );
        bow.addOrReplaceChild(
            "lower_bow_limb",
            CubeListBuilder.create().texOffs(0, 42).addBox(-0.8F, 0.0F, -0.8F, 1.6F, 6.0F, 1.6F),
            PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.0F, 0.0F, -0.35F)
        );
        guardian.addOrReplaceChild(
            "right_leather_pauldron",
            CubeListBuilder.create().texOffs(10, 42).addBox(-6.0F, -2.0F, -3.5F, 7.0F, 4.0F, 7.0F),
            PartPose.offsetAndRotation(-5.8F, -2.7F, 0.0F, 0.0F, 0.0F, -0.1F)
        );
        guardian.addOrReplaceChild(
            "left_leather_pauldron",
            CubeListBuilder.create().texOffs(10, 42).mirror().addBox(-1.0F, -2.0F, -3.5F, 7.0F, 4.0F, 7.0F),
            PartPose.offsetAndRotation(5.8F, -2.7F, 0.0F, 0.0F, 0.0F, 0.1F)
        );
        guardian.addOrReplaceChild(
            "moss_patch",
            CubeListBuilder.create().texOffs(40, 42).addBox(-2.2F, -2.0F, -0.5F, 4.4F, 4.0F, 1.0F),
            PartPose.offsetAndRotation(-3.8F, 3.3F, -4.2F, 0.0F, 0.0F, -0.12F)
        );

        final PartDefinition rightArm = root.addOrReplaceChild(
            "right_guard_arm",
            CubeListBuilder.create()
                .texOffs(52, 42).addBox(-4.0F, -2.0F, -3.0F, 5.0F, 10.0F, 6.0F)
                .texOffs(76, 42).addBox(-5.0F, 4.0F, -2.5F, 3.0F, 6.0F, 5.0F),
            PartPose.offsetAndRotation(-8.6F, 7.0F, 0.0F, 0.02F, 0.0F, 0.08F)
        );
        rightArm.addOrReplaceChild(
            "right_forged_forearm",
            CubeListBuilder.create().texOffs(76, 42)
                .addBox(-3.0F, -1.0F, -2.5F, 3.0F, 6.0F, 5.0F),
            PartPose.offsetAndRotation(-2.0F, 5.0F, 0.0F, 0.0F, 0.0F, 0.08F)
        );
        final PartDefinition rightClamp = rightArm.addOrReplaceChild(
            "right_hammer_clamp",
            CubeListBuilder.create()
                .texOffs(94, 42).addBox(-4.5F, -1.0F, -4.0F, 7.0F, 6.0F, 8.0F)
                .texOffs(124, 42).addBox(-5.8F, 0.0F, -3.0F, 2.0F, 5.0F, 2.0F)
                .texOffs(134, 42).addBox(-5.8F, 0.0F, 1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offset(-1.0F, 8.0F, 0.0F)
        );
        rightClamp.addOrReplaceChild(
            "right_inner_clamp_digit",
            CubeListBuilder.create().texOffs(124, 42)
                .addBox(-1.0F, -2.5F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(-1.0F, 2.4F, -2.4F, -0.16F, 0.0F, 0.0F)
        );
        rightClamp.addOrReplaceChild(
            "right_outer_clamp_digit",
            CubeListBuilder.create().texOffs(134, 42)
                .addBox(-1.0F, -2.5F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(-3.4F, 2.4F, 2.4F, 0.16F, 0.0F, 0.0F)
        );
        final PartDefinition leftArm = root.addOrReplaceChild(
            "left_guard_arm",
            CubeListBuilder.create().texOffs(52, 42).mirror()
                .addBox(-1.0F, -2.0F, -3.0F, 5.0F, 10.0F, 6.0F)
                .texOffs(76, 42).addBox(2.0F, 4.0F, -2.5F, 3.0F, 6.0F, 5.0F),
            PartPose.offsetAndRotation(8.6F, 7.0F, 0.0F, 0.02F, 0.0F, -0.08F)
        );
        leftArm.addOrReplaceChild(
            "left_forged_forearm",
            CubeListBuilder.create().texOffs(76, 42).mirror()
                .addBox(0.0F, -1.0F, -2.5F, 3.0F, 6.0F, 5.0F),
            PartPose.offsetAndRotation(2.0F, 5.0F, 0.0F, 0.0F, 0.0F, -0.08F)
        );
        final PartDefinition leftClamp = leftArm.addOrReplaceChild(
            "left_hammer_clamp",
            CubeListBuilder.create().texOffs(94, 42).mirror()
                .addBox(-2.5F, -1.0F, -4.0F, 7.0F, 6.0F, 8.0F)
                .texOffs(124, 42).addBox(3.8F, 0.0F, -3.0F, 2.0F, 5.0F, 2.0F)
                .texOffs(134, 42).addBox(3.8F, 0.0F, 1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offset(1.0F, 8.0F, 0.0F)
        );
        leftClamp.addOrReplaceChild(
            "left_inner_clamp_digit",
            CubeListBuilder.create().texOffs(124, 42).mirror()
                .addBox(-1.0F, -2.5F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(1.0F, 2.4F, -2.4F, -0.16F, 0.0F, 0.0F)
        );
        leftClamp.addOrReplaceChild(
            "left_outer_clamp_digit",
            CubeListBuilder.create().texOffs(134, 42).mirror()
                .addBox(-1.0F, -2.5F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(3.4F, 2.4F, 2.4F, 0.16F, 0.0F, 0.0F)
        );

        final PartDefinition rightLeg = root.addOrReplaceChild(
            "right_masonry_leg",
            CubeListBuilder.create()
                .texOffs(146, 42).addBox(-3.0F, 0.0F, -3.0F, 5.0F, 7.0F, 6.0F)
                .texOffs(168, 42).addBox(-2.0F, 6.0F, -2.0F, 3.0F, 4.0F, 4.0F),
            PartPose.offset(-3.5F, 14.0F, 0.0F)
        );
        final PartDefinition rightFoot = rightLeg.addOrReplaceChild(
            "right_anchor_foot",
            CubeListBuilder.create().texOffs(0, 66).addBox(-4.0F, 0.0F, -5.0F, 7.0F, 3.0F, 8.0F),
            PartPose.offset(0.0F, 7.0F, 0.0F)
        );
        rightFoot.addOrReplaceChild(
            "right_inner_anchor_toe",
            CubeListBuilder.create().texOffs(124, 42)
                .addBox(-1.0F, -2.5F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(-1.0F, 1.2F, -3.8F, -1.18F, 0.0F, 0.0F)
        );
        rightFoot.addOrReplaceChild(
            "right_outer_anchor_toe",
            CubeListBuilder.create().texOffs(134, 42)
                .addBox(-1.0F, -2.5F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(-3.1F, 1.2F, -3.4F, -1.18F, 0.16F, 0.0F)
        );
        final PartDefinition leftLeg = root.addOrReplaceChild(
            "left_masonry_leg",
            CubeListBuilder.create().texOffs(146, 42).mirror()
                .addBox(-2.0F, 0.0F, -3.0F, 5.0F, 7.0F, 6.0F)
                .texOffs(168, 42).addBox(-1.0F, 6.0F, -2.0F, 3.0F, 4.0F, 4.0F),
            PartPose.offset(3.5F, 14.0F, 0.0F)
        );
        final PartDefinition leftFoot = leftLeg.addOrReplaceChild(
            "left_anchor_foot",
            CubeListBuilder.create().texOffs(0, 66).mirror().addBox(-3.0F, 0.0F, -5.0F, 7.0F, 3.0F, 8.0F),
            PartPose.offset(0.0F, 7.0F, 0.0F)
        );
        leftFoot.addOrReplaceChild(
            "left_inner_anchor_toe",
            CubeListBuilder.create().texOffs(124, 42).mirror()
                .addBox(-1.0F, -2.5F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(1.0F, 1.2F, -3.8F, -1.18F, 0.0F, 0.0F)
        );
        leftFoot.addOrReplaceChild(
            "left_outer_anchor_toe",
            CubeListBuilder.create().texOffs(134, 42).mirror()
                .addBox(-1.0F, -2.5F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(3.1F, 1.2F, -3.4F, -1.18F, -0.16F, 0.0F)
        );
        final PartDefinition spine = root.addOrReplaceChild(
            "masonry_spine",
            CubeListBuilder.create()
                .texOffs(32, 66).addBox(-3.0F, -8.0F, -2.0F, 6.0F, 8.0F, 4.0F)
                .texOffs(56, 66).addBox(-2.0F, -11.0F, -1.5F, 4.0F, 3.0F, 3.0F),
            PartPose.offset(0.0F, 7.0F, 3.5F)
        );
        spine.addOrReplaceChild(
            "back_bell_bracket",
            CubeListBuilder.create().texOffs(74, 66).addBox(-4.0F, -1.0F, -1.5F, 8.0F, 2.0F, 3.0F),
            PartPose.offset(0.0F, -9.5F, 0.0F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        guardianHead.yRot += state.yRot * Mth.DEG_TO_RAD;
        guardianHead.xRot += state.xRot * Mth.DEG_TO_RAD * 0.5F;
        final float pace = state.walkAnimationPos * 0.42F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F) * 0.72F;
        rightMasonryLeg.xRot += Mth.cos(pace) * stride;
        leftMasonryLeg.xRot += Mth.cos(pace + Mth.PI) * stride;
        rightGuardArm.xRot += Mth.cos(pace + Mth.PI) * stride * 0.38F;
        leftGuardArm.xRot += Mth.cos(pace) * stride * 0.38F;
        villageBellCore.zRot += Mth.sin(state.ageInTicks * 0.06F) * 0.055F;
        final float repel = Mth.clamp(state.repelProgress, 0.0F, 1.0F);
        rightGuardArm.xRot -= repel * 1.2F;
        rightGuardArm.yRot -= repel * 0.55F;
        leftGuardArm.xRot -= repel * 0.55F;
        leftGuardArm.yRot += repel * 0.25F;
        rightHammerClamp.xRot -= repel * 0.25F;
        leftHammerClamp.xRot -= repel * 0.12F;
        foldedDefenseBow.zRot += repel * 0.16F;
        if (state.charged) {
            villageBellCore.yRot += state.ageInTicks * 0.012F;
            rightLeatherPauldron.zRot -= 0.03F;
            leftLeatherPauldron.zRot += 0.03F;
            leatherGuardHelmet.xRot -= 0.025F;
        } else {
            villageGuardian.y += 0.7F;
            rightGuardArm.xRot += 0.25F;
            leftGuardArm.xRot += 0.25F;
            rightAnchorFoot.zRot -= 0.03F;
            leftAnchorFoot.zRot += 0.03F;
        }
    }

    public static void extractRenderState(
        final IronboundSentinelEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.charged = entity.presentationCharged();
        final Phase phase = entity.presentationPhase();
        state.repelProgress = phase == Phase.REPEL || phase == Phase.SEIZE ? 1.0F : 0.0F;
    }

    public static final class State extends LivingEntityRenderState {
        public float repelProgress;
        public boolean charged;
    }
}
