package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.ForgewardenEntity;
import com.kadamitas.warlockery.entity.GoblinPatronRules.Action;
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

/** A towering emperor-penguin forge patron built around bellows, armor, and unequal flippers. */
public final class ForgewardenModel extends EntityModel<ForgewardenModel.State>
    implements ArmedModel<ForgewardenModel.State> {
    public static final int TEXTURE_WIDTH = 192;
    public static final int TEXTURE_HEIGHT = 160;

    private final ModelPart furnaceBody;
    private final ModelPart head;
    private final ModelPart furnaceBrow;
    private final ModelPart heatedBellyPlate;
    private final ModelPart furnaceCore;
    private final ModelPart tailWedge;
    private final ModelPart shoulderYoke;
    private final ModelPart backBellows;
    private final ModelPart chimneyBank;
    private final ModelPart hangingTongs;
    private final ModelPart rightHammerFlipper;
    private final ModelPart hammerGauntlet;
    private final ModelPart hammerHead;
    private final ModelPart leftWardFlipper;
    private final ModelPart wardPlate;
    private final ModelPart rightLeg;
    private final ModelPart rightFoot;
    private final ModelPart leftLeg;
    private final ModelPart leftFoot;

    public ForgewardenModel(final ModelPart root) {
        super(root);
        furnaceBody = root.getChild("furnace_body");
        head = furnaceBody.getChild("head");
        furnaceBrow = head.getChild("furnace_brow");
        heatedBellyPlate = furnaceBody.getChild("heated_belly_plate");
        furnaceCore = furnaceBody.getChild("furnace_core");
        tailWedge = furnaceBody.getChild("tail_wedge");
        shoulderYoke = furnaceBody.getChild("shoulder_yoke");
        backBellows = furnaceBody.getChild("back_bellows");
        chimneyBank = backBellows.getChild("chimney_bank");
        hangingTongs = furnaceBody.getChild("tool_belt").getChild("hanging_tongs");
        rightHammerFlipper = root.getChild("right_hammer_flipper");
        hammerGauntlet = rightHammerFlipper.getChild("hammer_gauntlet");
        hammerHead = hammerGauntlet.getChild("hammer_head");
        leftWardFlipper = root.getChild("left_ward_flipper");
        wardPlate = leftWardFlipper.getChild("ward_plate");
        rightLeg = root.getChild("right_leg");
        rightFoot = rightLeg.getChild("right_webbed_foot");
        leftLeg = root.getChild("left_leg");
        leftFoot = leftLeg.getChild("left_webbed_foot");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition body = root.addOrReplaceChild(
            "furnace_body",
            CubeListBuilder.create().texOffs(0, 0)
                .addBox(-7.5F, -11.0F, -5.5F, 15.0F, 21.0F, 11.0F),
            PartPose.offsetAndRotation(0.0F, 7.0F, 0.4F, 0.06F, 0.0F, 0.0F)
        );
        final PartDefinition head = body.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(54, 0)
                .addBox(-6.0F, -6.0F, -4.5F, 12.0F, 9.0F, 9.0F),
            PartPose.offsetAndRotation(0.0F, -7.2F, -1.2F, -0.07F, 0.0F, 0.0F)
        );
        head.addOrReplaceChild(
            "beak",
            CubeListBuilder.create().texOffs(98, 0)
                .addBox(-3.5F, -1.5F, -5.0F, 7.0F, 3.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, 0.25F, -4.6F, 0.16F, 0.0F, 0.0F)
        );
        head.addOrReplaceChild(
            "furnace_brow",
            CubeListBuilder.create().texOffs(130, 0)
                .addBox(-6.5F, -2.0F, -5.0F, 13.0F, 4.0F, 10.0F),
            PartPose.offsetAndRotation(0.45F, -4.8F, 0.6F, 0.1F, 0.0F, 0.03F)
        );
        body.addOrReplaceChild(
            "heated_belly_plate",
            CubeListBuilder.create().texOffs(0, 24)
                .addBox(-5.5F, -9.0F, -1.0F, 11.0F, 18.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 0.8F, -5.5F, -0.07F, 0.0F, 0.0F)
        );
        body.addOrReplaceChild(
            "furnace_core",
            CubeListBuilder.create().texOffs(30, 24)
                .addBox(-3.0F, -3.5F, -1.0F, 6.0F, 7.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 4.0F, -6.15F, -0.1F, 0.0F, 0.0F)
        );
        body.addOrReplaceChild(
            "tail_wedge",
            CubeListBuilder.create().texOffs(50, 24)
                .addBox(-3.5F, 0.0F, -1.0F, 7.0F, 6.0F, 8.0F),
            PartPose.offsetAndRotation(0.0F, 6.8F, 5.1F, 0.82F, 0.0F, 0.0F)
        );
        final PartDefinition yoke = body.addOrReplaceChild(
            "shoulder_yoke",
            CubeListBuilder.create().texOffs(82, 24)
                .addBox(-8.5F, -2.0F, -6.0F, 17.0F, 4.0F, 12.0F),
            PartPose.offsetAndRotation(-0.7F, -6.4F, 0.3F, 0.04F, 0.02F, -0.06F)
        );
        yoke.addOrReplaceChild(
            "right_yoke_plate",
            CubeListBuilder.create().texOffs(142, 24)
                .addBox(-5.0F, -2.0F, -3.5F, 6.0F, 5.0F, 7.0F),
            PartPose.offsetAndRotation(-7.6F, -0.4F, -0.8F, 0.03F, 0.0F, -0.34F)
        );
        yoke.addOrReplaceChild(
            "left_yoke_plate",
            CubeListBuilder.create().texOffs(0, 46)
                .addBox(-1.0F, -2.0F, -3.5F, 6.0F, 5.0F, 7.0F),
            PartPose.offsetAndRotation(6.4F, -0.2F, 0.9F, 0.03F, 0.0F, 0.12F)
        );
        final PartDefinition bellows = body.addOrReplaceChild(
            "back_bellows",
            CubeListBuilder.create().texOffs(28, 46)
                .addBox(-6.0F, -5.0F, -1.5F, 12.0F, 10.0F, 4.0F),
            PartPose.offsetAndRotation(0.8F, 0.1F, 5.6F, -0.08F, 0.0F, 0.04F)
        );
        final PartDefinition chimneys = bellows.addOrReplaceChild(
            "chimney_bank",
            CubeListBuilder.create().texOffs(62, 46)
                .addBox(-5.0F, -2.0F, -1.5F, 10.0F, 4.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, -4.8F, 1.4F, 0.0F, 0.0F, 0.0F)
        );
        chimneys.addOrReplaceChild(
            "right_chimney",
            CubeListBuilder.create().texOffs(90, 46)
                .addBox(-1.5F, -6.0F, -1.5F, 3.0F, 7.0F, 3.0F),
            PartPose.offsetAndRotation(-3.1F, -1.4F, 0.0F, -0.08F, 0.0F, -0.08F)
        );
        chimneys.addOrReplaceChild(
            "left_chimney",
            CubeListBuilder.create().texOffs(104, 46)
                .addBox(-1.5F, -5.0F, -1.5F, 3.0F, 6.0F, 3.0F),
            PartPose.offsetAndRotation(3.1F, -1.4F, 0.0F, -0.08F, 0.0F, 0.1F)
        );
        final PartDefinition belt = body.addOrReplaceChild(
            "tool_belt",
            CubeListBuilder.create().texOffs(118, 46)
                .addBox(-8.0F, -1.5F, -5.5F, 16.0F, 3.0F, 11.0F),
            PartPose.offsetAndRotation(0.0F, 5.8F, 0.0F, 0.02F, 0.0F, 0.0F)
        );
        belt.addOrReplaceChild(
            "hanging_tongs",
            CubeListBuilder.create().texOffs(0, 68)
                .addBox(-1.0F, -1.0F, -1.0F, 2.0F, 9.0F, 2.0F),
            PartPose.offsetAndRotation(5.8F, 1.2F, 0.8F, 0.06F, 0.0F, -0.16F)
        );
        belt.addOrReplaceChild(
            "belt_buckle",
            CubeListBuilder.create().texOffs(12, 68)
                .addBox(-2.0F, -1.5F, -1.0F, 4.0F, 3.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, -5.6F, 0.0F, 0.0F, 0.0F)
        );

        final PartDefinition hammerFlipper = root.addOrReplaceChild(
            "right_hammer_flipper",
            CubeListBuilder.create().texOffs(28, 68)
                .addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offsetAndRotation(-8.8F, 3.0F, 0.0F, -0.12F, 0.14F, 0.24F)
        );
        final PartDefinition gauntlet = hammerFlipper.addOrReplaceChild(
            "hammer_gauntlet",
            CubeListBuilder.create().texOffs(52, 68)
                .addBox(-3.0F, -1.0F, -2.5F, 5.0F, 6.0F, 5.0F),
            PartPose.offsetAndRotation(-0.8F, 8.8F, 0.0F, 0.06F, 0.0F, 0.18F)
        );
        gauntlet.addOrReplaceChild(
            "hammer_head",
            CubeListBuilder.create().texOffs(80, 68)
                .addBox(-5.0F, -3.0F, -3.5F, 10.0F, 6.0F, 7.0F),
            PartPose.offsetAndRotation(-0.4F, 4.6F, 0.0F, 0.0F, 0.0F, 0.04F)
        );
        final PartDefinition wardFlipper = root.addOrReplaceChild(
            "left_ward_flipper",
            CubeListBuilder.create().texOffs(116, 68)
                .addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offsetAndRotation(8.8F, 3.2F, 0.0F, -0.12F, -0.14F, -0.22F)
        );
        wardFlipper.addOrReplaceChild(
            "ward_plate",
            CubeListBuilder.create().texOffs(140, 68)
                .addBox(-1.5F, -2.0F, -2.5F, 4.0F, 8.0F, 5.0F),
            PartPose.offsetAndRotation(0.8F, 8.0F, 0.1F, 0.06F, 0.0F, -0.12F)
        );
        final PartDefinition rightLeg = root.addOrReplaceChild(
            "right_leg",
            CubeListBuilder.create().texOffs(0, 90)
                .addBox(-2.75F, 0.0F, -2.75F, 5.5F, 7.0F, 5.5F),
            PartPose.offsetAndRotation(-5.0F, 13.92F, 0.8F, -0.04F, 0.0F, 0.1F)
        );
        rightLeg.addOrReplaceChild(
            "right_webbed_foot",
            CubeListBuilder.create().texOffs(24, 90)
                .addBox(-3.5F, 0.0F, -6.0F, 7.0F, 2.0F, 8.0F),
            PartPose.offsetAndRotation(0.0F, 6.45F, -0.7F, 0.1F, 0.15F, 0.0F)
        );
        final PartDefinition leftLeg = root.addOrReplaceChild(
            "left_leg",
            CubeListBuilder.create().texOffs(62, 90)
                .addBox(-2.75F, 0.0F, -2.75F, 5.5F, 7.0F, 5.5F),
            PartPose.offsetAndRotation(5.0F, 13.92F, 0.8F, -0.04F, 0.0F, -0.1F)
        );
        leftLeg.addOrReplaceChild(
            "left_webbed_foot",
            CubeListBuilder.create().texOffs(86, 90)
                .addBox(-3.5F, 0.0F, -6.0F, 7.0F, 2.0F, 8.0F),
            PartPose.offsetAndRotation(0.0F, 6.45F, -0.7F, 0.1F, -0.15F, 0.0F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        head.yRot += state.yRot * Mth.DEG_TO_RAD;
        head.xRot += state.xRot * Mth.DEG_TO_RAD;
        final float pace = state.walkAnimationPos * 0.52F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F);
        final float waddle = Mth.sin(pace) * stride;
        furnaceBody.zRot += waddle * 0.11F;
        head.zRot -= waddle * 0.045F;
        rightLeg.xRot += Mth.cos(pace) * stride * 0.48F;
        leftLeg.xRot += Mth.cos(pace + Mth.PI) * stride * 0.48F;
        rightFoot.xRot -= Mth.cos(pace) * stride * 0.11F;
        leftFoot.xRot -= Mth.cos(pace + Mth.PI) * stride * 0.11F;
        rightHammerFlipper.xRot += Mth.cos(pace + Mth.PI) * stride * 0.18F;
        leftWardFlipper.xRot += Mth.cos(pace) * stride * 0.18F;
        tailWedge.yRot += Mth.sin(state.ageInTicks * 0.085F) * 0.065F;
        hangingTongs.zRot += Mth.sin(state.ageInTicks * 0.13F) * 0.08F;
        backBellows.zScale += Mth.sin(state.ageInTicks * 0.09F) * 0.025F;
        chimneyBank.xRot += Mth.sin(state.ageInTicks * 0.055F) * 0.012F;
        furnaceCore.zScale += Mth.sin(state.ageInTicks * 0.22F) * 0.035F;
        if (state.action == Action.HAMMER_COMMIT) {
            final float commit = Mth.clamp(state.actionProgress, 0.0F, 1.0F);
            furnaceBody.xRot += commit * 0.15F;
            head.xRot -= commit * 0.14F;
            rightHammerFlipper.xRot = -2.15F * commit;
            rightHammerFlipper.yRot = -0.22F * commit;
            rightHammerFlipper.zRot += 0.46F * commit;
            hammerGauntlet.xRot -= 0.55F * commit;
            hammerHead.zRot += 0.3F * commit;
            leftWardFlipper.xRot = -0.48F * commit;
            leftWardFlipper.zRot -= 0.38F * commit;
        } else if (state.action == Action.FORGE_SURGE) {
            final float surge = Mth.clamp(state.actionProgress, 0.0F, 1.0F);
            heatedBellyPlate.zScale += surge * 0.08F;
            furnaceCore.zScale += surge * 0.18F;
            backBellows.zScale += surge * 0.15F;
            rightHammerFlipper.zRot += surge * 0.36F;
            leftWardFlipper.zRot -= surge * 0.36F;
            furnaceBrow.xRot -= surge * 0.12F;
        } else if (state.action == Action.WARD_STANCE || state.action == Action.INTERPOSE) {
            leftWardFlipper.xRot = -1.28F;
            leftWardFlipper.yRot = 0.34F;
            leftWardFlipper.zRot -= 0.55F;
            wardPlate.yRot += 0.22F;
            rightHammerFlipper.xRot = -0.42F;
            head.xRot -= 0.12F;
        }
    }

    @Override
    public void translateToHand(final State state, final HumanoidArm arm, final PoseStack poseStack) {
        if (arm == HumanoidArm.LEFT) {
            leftWardFlipper.translateAndRotate(poseStack);
            wardPlate.translateAndRotate(poseStack);
            poseStack.translate(0.08F, 0.18F, -0.06F);
        } else {
            rightHammerFlipper.translateAndRotate(poseStack);
            hammerGauntlet.translateAndRotate(poseStack);
            hammerHead.translateAndRotate(poseStack);
            poseStack.translate(-0.1F, 0.12F, -0.05F);
        }
    }

    public static void extractRenderState(
        final ForgewardenEntity entity,
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
