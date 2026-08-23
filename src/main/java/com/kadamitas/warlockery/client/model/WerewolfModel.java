package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.LycanPackRules.ActionKind;
import com.kadamitas.warlockery.entity.WerewolfEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerDataHolderRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.HumanoidArm;
import org.jspecify.annotations.Nullable;

/** A broad, upright lycan rig authored only for the disciplined werewolf. */
public final class WerewolfModel extends EntityModel<WerewolfModel.State> {
    public static final int TEXTURE_WIDTH = 192;
    public static final int TEXTURE_HEIGHT = 192;

    private final ModelPart head;
    private final ModelPart chest;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart leftForearm;
    private final ModelPart rightForearm;
    private final ModelPart leftHand;
    private final ModelPart rightHand;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;
    private final ModelPart leftShin;
    private final ModelPart rightShin;
    private final ModelPart tail;
    private final ModelPart tailMid;

    public WerewolfModel(final ModelPart root) {
        super(root);
        chest = root.getChild("chest");
        head = chest.getChild("neck").getChild("head");
        leftArm = root.getChild("left_arm");
        rightArm = root.getChild("right_arm");
        leftForearm = leftArm.getChild("left_forearm");
        rightForearm = rightArm.getChild("right_forearm");
        leftHand = leftForearm.getChild("left_hand");
        rightHand = rightForearm.getChild("right_hand");
        leftLeg = root.getChild("left_leg");
        rightLeg = root.getChild("right_leg");
        leftShin = leftLeg.getChild("left_shin");
        rightShin = rightLeg.getChild("right_shin");
        tail = root.getChild("tail");
        tailMid = tail.getChild("tail_mid");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("pelvis",
            CubeListBuilder.create().texOffs(68, 18)
                .addBox(-4.0F, -2.0F, -2.5F, 8.0F, 5.0F, 5.0F),
            PartPose.offset(0.0F, 13.0F, 0.5F));
        final PartDefinition chest = root.addOrReplaceChild("chest",
            CubeListBuilder.create().texOffs(0, 18)
                .addBox(-5.5F, -4.0F, -3.0F, 11.0F, 9.0F, 6.0F),
            PartPose.offset(0.0F, 7.0F, 0.0F));
        chest.addOrReplaceChild("waist",
            CubeListBuilder.create().texOffs(40, 18).addBox(-3.5F, 0.0F, -2.0F, 7.0F, 5.0F, 4.0F),
            PartPose.offset(0.0F, 4.5F, 0.2F));
        chest.addOrReplaceChild("sternum_keel",
            CubeListBuilder.create().texOffs(0, 18)
                .addBox(-2.0F, -3.0F, -1.0F, 4.0F, 7.0F, 2.0F),
            PartPose.offset(0.0F, 0.0F, -3.0F));
        chest.addOrReplaceChild("trapezius",
            CubeListBuilder.create().texOffs(0, 18)
                .addBox(-5.5F, -4.0F, -3.0F, 11.0F, 9.0F, 6.0F),
            PartPose.offset(0.0F, -3.2F, 0.2F).scaled(1.25F, 0.45F, 0.83F));
        chest.addOrReplaceChild("left_lat",
            CubeListBuilder.create().texOffs(68, 18)
                .addBox(0.0F, -2.5F, -2.0F, 3.0F, 5.0F, 4.0F),
            PartPose.offsetAndRotation(6.0F, 0.4F, 0.4F, 0.0F, 0.0F, -0.16F));
        chest.addOrReplaceChild("right_lat",
            CubeListBuilder.create().texOffs(68, 18)
                .addBox(-3.0F, -2.5F, -2.0F, 3.0F, 5.0F, 4.0F),
            PartPose.offsetAndRotation(-6.0F, 0.4F, 0.4F, 0.0F, 0.0F, 0.16F));
        chest.addOrReplaceChild("left_scapular_facet",
            CubeListBuilder.create().texOffs(68, 18)
                .addBox(0.0F, -2.5F, -2.0F, 3.0F, 5.0F, 4.0F),
            PartPose.offsetAndRotation(4.8F, -0.4F, 0.2F, 0.0F, 0.0F, -0.24F));
        chest.addOrReplaceChild("right_scapular_facet",
            CubeListBuilder.create().texOffs(68, 18)
                .addBox(-3.0F, -2.5F, -2.0F, 3.0F, 5.0F, 4.0F),
            PartPose.offsetAndRotation(-4.8F, -0.4F, 0.2F, 0.0F, 0.0F, 0.24F));
        final PartDefinition neck = chest.addOrReplaceChild("neck",
            CubeListBuilder.create().texOffs(40, 18).addBox(-3.0F, -4.0F, -2.0F, 6.0F, 5.0F, 4.0F),
            PartPose.offset(0.0F, -3.0F, -0.5F));
        final PartDefinition head = neck.addOrReplaceChild("head",
            CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.5F, -6.0F, -3.0F, 7.0F, 7.0F, 6.0F),
            PartPose.offset(0.0F, -3.5F, -0.7F));
        head.addOrReplaceChild("muzzle",
            CubeListBuilder.create().texOffs(32, 0)
                .addBox(-2.5F, -1.5F, -3.5F, 5.0F, 3.0F, 3.5F),
            PartPose.offset(0.0F, -0.8F, -2.8F));
        head.addOrReplaceChild("brow_wedge",
            CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.0F, -1.0F, -1.0F, 6.0F, 2.0F, 2.0F),
            PartPose.offset(0.0F, -3.4F, -3.0F));
        head.addOrReplaceChild("lower_jaw",
            CubeListBuilder.create().texOffs(32, 0)
                .addBox(-2.0F, -0.5F, -3.0F, 4.0F, 1.5F, 3.0F),
            PartPose.offset(0.0F, 0.7F, -2.7F));
        head.addOrReplaceChild("left_ear",
            CubeListBuilder.create().texOffs(56, 0)
                .addBox(-1.0F, -4.0F, -0.5F, 2.0F, 4.0F, 1.0F),
            PartPose.offsetAndRotation(2.5F, -5.0F, -0.2F, -0.18F, 0.0F, 0.18F));
        head.addOrReplaceChild("right_ear",
            CubeListBuilder.create().texOffs(66, 0)
                .addBox(-1.0F, -4.0F, -0.5F, 2.0F, 4.0F, 1.0F),
            PartPose.offsetAndRotation(-2.5F, -5.0F, -0.2F, -0.18F, 0.0F, -0.18F));
        final PartDefinition mane = head.addOrReplaceChild("mane_crown",
            CubeListBuilder.create().texOffs(76, 0)
                .addBox(-2.5F, -1.0F, 0.0F, 5.0F, 4.0F, 2.0F),
            PartPose.offset(0.0F, -4.2F, 3.0F));
        mane.addOrReplaceChild("mane_plate_left",
            CubeListBuilder.create().texOffs(76, 0)
                .addBox(-0.5F, -1.5F, -0.5F, 1.5F, 3.0F, 1.0F),
            PartPose.offsetAndRotation(1.8F, 1.2F, 1.2F, 0.3F, 0.0F, -0.22F));
        mane.addOrReplaceChild("mane_plate_center",
            CubeListBuilder.create().texOffs(76, 0)
                .addBox(-0.75F, -2.0F, -0.5F, 1.5F, 4.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, 1.0F, 1.4F, 0.36F, 0.0F, 0.0F));
        mane.addOrReplaceChild("mane_plate_right",
            CubeListBuilder.create().texOffs(76, 0)
                .addBox(-1.0F, -1.5F, -0.5F, 1.5F, 3.0F, 1.0F),
            PartPose.offsetAndRotation(-1.8F, 1.2F, 1.2F, 0.3F, 0.0F, 0.22F));

        final PartDefinition leftArm = root.addOrReplaceChild("left_arm",
            CubeListBuilder.create().texOffs(0, 40)
                .addBox(0.0F, -1.0F, -1.8F, 4.0F, 6.0F, 4.0F),
            PartPose.offsetAndRotation(5.0F, 7.0F, 0.0F, 0.0F, 0.0F, -0.14F));
        final PartDefinition leftForearm = leftArm.addOrReplaceChild("left_forearm",
            CubeListBuilder.create().texOffs(18, 40)
                .addBox(-0.2F, 0.0F, -1.8F, 4.0F, 8.0F, 4.0F),
            PartPose.offset(0.5F, 4.5F, 0.0F));
        final PartDefinition leftHand = leftForearm.addOrReplaceChild("left_hand",
            CubeListBuilder.create().texOffs(40, 40)
                .addBox(-0.3F, 0.0F, -2.0F, 4.0F, 3.0F, 4.0F),
            PartPose.offset(0.0F, 7.5F, 0.0F));
        leftHand.addOrReplaceChild("left_claw_inner",
            CubeListBuilder.create().texOffs(40, 40).addBox(-0.3F, 0.0F, -2.5F, 1.0F, 1.0F, 3.0F),
            PartPose.offset(0.1F, 2.3F, -1.5F));
        leftHand.addOrReplaceChild("left_claw_middle",
            CubeListBuilder.create().texOffs(40, 40).addBox(-0.3F, 0.0F, -2.8F, 1.0F, 1.0F, 3.0F),
            PartPose.offset(1.4F, 2.3F, -1.5F));
        leftHand.addOrReplaceChild("left_claw_outer",
            CubeListBuilder.create().texOffs(40, 40).addBox(-0.3F, 0.0F, -2.5F, 1.0F, 1.0F, 3.0F),
            PartPose.offset(2.7F, 2.3F, -1.5F));
        final PartDefinition rightArm = root.addOrReplaceChild("right_arm",
            CubeListBuilder.create().texOffs(64, 40)
                .addBox(-4.0F, -1.0F, -1.8F, 4.0F, 6.0F, 4.0F),
            PartPose.offsetAndRotation(-5.0F, 7.0F, 0.0F, 0.0F, 0.0F, 0.14F));
        final PartDefinition rightForearm = rightArm.addOrReplaceChild("right_forearm",
            CubeListBuilder.create().texOffs(82, 40)
                .addBox(-3.8F, 0.0F, -1.8F, 4.0F, 8.0F, 4.0F),
            PartPose.offset(-0.5F, 4.5F, 0.0F));
        final PartDefinition rightHand = rightForearm.addOrReplaceChild("right_hand",
            CubeListBuilder.create().texOffs(104, 40)
                .addBox(-3.7F, 0.0F, -2.0F, 4.0F, 3.0F, 4.0F),
            PartPose.offset(0.0F, 7.5F, 0.0F));
        rightHand.addOrReplaceChild("right_claw_inner",
            CubeListBuilder.create().texOffs(104, 40).addBox(-0.7F, 0.0F, -2.5F, 1.0F, 1.0F, 3.0F),
            PartPose.offset(-0.1F, 2.3F, -1.5F));
        rightHand.addOrReplaceChild("right_claw_middle",
            CubeListBuilder.create().texOffs(104, 40).addBox(-0.7F, 0.0F, -2.8F, 1.0F, 1.0F, 3.0F),
            PartPose.offset(-1.4F, 2.3F, -1.5F));
        rightHand.addOrReplaceChild("right_claw_outer",
            CubeListBuilder.create().texOffs(104, 40).addBox(-0.7F, 0.0F, -2.5F, 1.0F, 1.0F, 3.0F),
            PartPose.offset(-2.7F, 2.3F, -1.5F));

        final PartDefinition leftLeg = root.addOrReplaceChild("left_leg",
            CubeListBuilder.create().texOffs(0, 58).addBox(-1.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F),
            PartPose.offset(2.2F, 14.0F, 0.6F));
        final PartDefinition leftShin = leftLeg.addOrReplaceChild("left_shin",
            CubeListBuilder.create().texOffs(22, 58).addBox(-0.7F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F),
            PartPose.offset(0.2F, 5.0F, 0.0F));
        final PartDefinition leftFoot = leftShin.addOrReplaceChild("left_foot",
            CubeListBuilder.create().texOffs(40, 58).addBox(-1.2F, 0.0F, -4.0F, 4.0F, 2.0F, 6.0F),
            PartPose.offset(0.0F, 3.0F, -0.5F));
        leftFoot.addOrReplaceChild("left_toe_inner",
            CubeListBuilder.create().texOffs(40, 58).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 1.0F, 2.0F),
            PartPose.offset(-0.5F, 0.8F, -3.8F));
        leftFoot.addOrReplaceChild("left_toe_middle",
            CubeListBuilder.create().texOffs(40, 58).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 1.0F, 2.0F),
            PartPose.offset(0.8F, 0.8F, -3.8F));
        leftFoot.addOrReplaceChild("left_toe_outer",
            CubeListBuilder.create().texOffs(40, 58).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 1.0F, 2.0F),
            PartPose.offset(2.0F, 0.8F, -3.8F));
        final PartDefinition rightLeg = root.addOrReplaceChild("right_leg",
            CubeListBuilder.create().texOffs(68, 58).addBox(-3.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F),
            PartPose.offset(-2.2F, 14.0F, 0.6F));
        final PartDefinition rightShin = rightLeg.addOrReplaceChild("right_shin",
            CubeListBuilder.create().texOffs(90, 58).addBox(-2.3F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F),
            PartPose.offset(-0.2F, 5.0F, 0.0F));
        final PartDefinition rightFoot = rightShin.addOrReplaceChild("right_foot",
            CubeListBuilder.create().texOffs(108, 58).addBox(-2.8F, 0.0F, -4.0F, 4.0F, 2.0F, 6.0F),
            PartPose.offset(0.0F, 3.0F, -0.5F));
        rightFoot.addOrReplaceChild("right_toe_inner",
            CubeListBuilder.create().texOffs(108, 58).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 1.0F, 2.0F),
            PartPose.offset(0.5F, 0.8F, -3.8F));
        rightFoot.addOrReplaceChild("right_toe_middle",
            CubeListBuilder.create().texOffs(108, 58).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 1.0F, 2.0F),
            PartPose.offset(-0.8F, 0.8F, -3.8F));
        rightFoot.addOrReplaceChild("right_toe_outer",
            CubeListBuilder.create().texOffs(108, 58).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 1.0F, 2.0F),
            PartPose.offset(-2.0F, 0.8F, -3.8F));

        final PartDefinition tail = root.addOrReplaceChild("tail",
            CubeListBuilder.create().texOffs(0, 74)
                .addBox(-0.5F, -1.0F, 0.0F, 1.0F, 2.0F, 3.5F),
            PartPose.offset(0.0F, 12.5F, 2.2F));
        final PartDefinition tailMid = tail.addOrReplaceChild("tail_mid",
            CubeListBuilder.create().texOffs(14, 74)
                .addBox(-0.5F, -1.0F, 0.0F, 1.0F, 2.0F, 2.5F),
            PartPose.offsetAndRotation(0.0F, 0.0F, 3.0F, -0.12F, 0.18F, 0.0F));
        tailMid.addOrReplaceChild("tail_tip",
            CubeListBuilder.create().texOffs(36, 74)
                .addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, 2.3F, -0.18F, -0.25F, 0.0F));
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        head.yRot += state.yRot * Mth.DEG_TO_RAD;
        head.xRot += state.xRot * Mth.DEG_TO_RAD;
        final float stride = state.walkAnimationPos * 0.68F;
        final float reach = state.walkAnimationSpeed;
        leftArm.xRot += Mth.cos(stride) * reach * 0.62F;
        rightArm.xRot += Mth.cos(stride + Mth.PI) * reach * 0.62F;
        leftLeg.xRot += Mth.cos(stride + Mth.PI) * reach * 0.72F;
        rightLeg.xRot += Mth.cos(stride) * reach * 0.72F;
        leftShin.xRot += Math.max(0.0F, Mth.sin(stride)) * reach * 0.38F;
        rightShin.xRot += Math.max(0.0F, -Mth.sin(stride)) * reach * 0.38F;
        final float breathing = Mth.sin(state.ageInTicks * 0.09F) * 0.025F;
        chest.xScale += breathing;
        tail.yRot += Mth.sin(state.ageInTicks * 0.14F) * 0.12F;
        tailMid.yRot -= Mth.sin(state.ageInTicks * 0.14F) * 0.08F;
        if (state.aggressive) {
            head.xRot -= 0.18F;
            leftHand.zRot -= 0.18F;
            rightHand.zRot += 0.18F;
        }
        if (state.pouncing) {
            chest.xRot += 0.46F;
            leftArm.xRot -= 1.0F;
            rightArm.xRot -= 1.0F;
            leftForearm.xRot -= 0.34F;
            rightForearm.xRot -= 0.34F;
            leftLeg.xRot += 0.62F;
            rightLeg.xRot += 0.62F;
            tail.xRot -= 0.48F;
        }
    }

    /** Returns the complete upper-arm chain used by the transformed player's first-person view. */
    public ModelPart firstPersonArm(final HumanoidArm arm) {
        final ModelPart part = arm == HumanoidArm.RIGHT ? rightArm : leftArm;
        part.resetPose();
        part.setPos(0.0F, 0.0F, 0.0F);
        return part;
    }

    public static void extractRenderState(final WerewolfEntity entity, final State state, final float partialTicks) {
        state.hunger = entity.presentationHunger();
        state.fear = entity.presentationFear();
        state.pouncing = entity.presentationAction() == ActionKind.POUNCE;
        state.aggressive = entity.isAggressive();
        state.airborne = !entity.onGround();
        state.villagerData = entity.transformedVillagerData().orElse(null);
    }

    public static final class State extends LivingEntityRenderState
        implements VillagerDataHolderRenderState {
        public int hunger;
        public int fear;
        public boolean pouncing;
        public boolean aggressive;
        public boolean airborne;
        public @Nullable VillagerData villagerData;

        @Override
        public @Nullable VillagerData getVillagerData() {
            return villagerData;
        }
    }
}
