package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.HobgoblinEntity;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.Mode;
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

/** A stout caravan penguin whose hood, packs, and broad paddles convey a seasoned traveler. */
public final class HobgoblinModel extends EntityModel<HobgoblinModel.State>
    implements ArmedModel<HobgoblinModel.State> {
    public static final int TEXTURE_WIDTH = 192;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart torso;
    private final ModelPart head;
    private final ModelPart hoodCrown;
    private final ModelPart hoodCape;
    private final ModelPart tailWedge;
    private final ModelPart travelPack;
    private final ModelPart campRoll;
    private final ModelPart lantern;
    private final ModelPart prospectingTool;
    private final ModelPart rightFlipper;
    private final ModelPart rightFlipperTip;
    private final ModelPart leftFlipper;
    private final ModelPart leftFlipperTip;
    private final ModelPart rightLeg;
    private final ModelPart rightFoot;
    private final ModelPart leftLeg;
    private final ModelPart leftFoot;

    public HobgoblinModel(final ModelPart root) {
        super(root);
        torso = root.getChild("torso");
        head = torso.getChild("head");
        hoodCrown = head.getChild("hood_crown");
        hoodCape = torso.getChild("hood_cape");
        tailWedge = torso.getChild("tail_wedge");
        travelPack = torso.getChild("travel_pack");
        campRoll = travelPack.getChild("camp_roll");
        lantern = torso.getChild("side_pack").getChild("lantern");
        prospectingTool = torso.getChild("tool_holster").getChild("prospecting_tool");
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
        final PartDefinition torso = root.addOrReplaceChild(
            "torso",
            CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.5F, -4.0F, -3.5F, 9.0F, 11.0F, 8.0F),
            PartPose.offsetAndRotation(0.0F, 9.2F, 0.3F, 0.1F, 0.0F, 0.0F)
        );
        final PartDefinition head = torso.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(36, 0)
                .addBox(-4.5F, -4.5F, -3.5F, 9.0F, 7.0F, 7.0F),
            PartPose.offsetAndRotation(0.0F, -2.6F, -1.4F, -0.08F, 0.0F, 0.0F)
        );
        head.addOrReplaceChild(
            "beak",
            CubeListBuilder.create().texOffs(68, 0)
                .addBox(-2.5F, -1.0F, -4.0F, 5.0F, 2.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 0.4F, -3.8F, 0.18F, 0.0F, 0.0F)
        );
        head.addOrReplaceChild(
            "hood_crown",
            CubeListBuilder.create().texOffs(92, 0)
                .addBox(-5.0F, -2.0F, -4.0F, 10.0F, 4.0F, 8.0F),
            PartPose.offsetAndRotation(-0.65F, -3.6F, 0.35F, 0.08F, 0.08F, -0.1F)
        );
        head.addOrReplaceChild(
            "scarf_clasp",
            CubeListBuilder.create().texOffs(130, 0)
                .addBox(-1.5F, -1.5F, -1.0F, 3.0F, 3.0F, 2.0F),
            PartPose.offsetAndRotation(0.4F, 2.4F, -3.55F, -0.14F, 0.0F, 0.08F)
        );
        torso.addOrReplaceChild(
            "belly_shield",
            CubeListBuilder.create().texOffs(144, 0)
                .addBox(-3.5F, -5.0F, -1.0F, 7.0F, 10.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, 1.4F, -3.65F, -0.12F, 0.0F, 0.0F)
        );
        torso.addOrReplaceChild(
            "hood_cape",
            CubeListBuilder.create().texOffs(0, 22)
                .addBox(-5.0F, -4.0F, -1.0F, 10.0F, 9.0F, 2.0F),
            PartPose.offsetAndRotation(-0.65F, -0.7F, 4.35F, 0.22F, 0.08F, -0.07F)
        );
        torso.addOrReplaceChild(
            "tail_wedge",
            CubeListBuilder.create().texOffs(26, 22)
                .addBox(-3.0F, 0.0F, -0.5F, 6.0F, 4.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, 4.9F, 4.25F, 0.78F, 0.0F, 0.0F)
        );
        final PartDefinition travelPack = torso.addOrReplaceChild(
            "travel_pack",
            CubeListBuilder.create().texOffs(48, 22)
                .addBox(-4.0F, -4.5F, -1.0F, 8.0F, 9.0F, 4.0F),
            PartPose.offsetAndRotation(1.0F, 1.0F, 4.65F, 0.1F, -0.07F, 0.05F)
        );
        travelPack.addOrReplaceChild(
            "camp_roll",
            CubeListBuilder.create().texOffs(74, 22)
                .addBox(-4.5F, -1.5F, -1.5F, 9.0F, 3.0F, 3.0F),
            PartPose.offsetAndRotation(-0.2F, -4.8F, 1.4F, 0.0F, 0.08F, 0.12F)
        );
        travelPack.addOrReplaceChild(
            "pack_frame",
            CubeListBuilder.create().texOffs(100, 22)
                .addBox(-5.0F, -3.5F, -0.5F, 10.0F, 7.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, 0.2F, 2.8F, 0.0F, 0.0F, 0.0F)
        );
        final PartDefinition sidePack = torso.addOrReplaceChild(
            "side_pack",
            CubeListBuilder.create().texOffs(124, 22)
                .addBox(0.0F, -2.5F, -2.0F, 4.0F, 5.0F, 4.0F),
            PartPose.offsetAndRotation(4.5F, 2.1F, 0.8F, 0.0F, -0.28F, -0.1F)
        );
        sidePack.addOrReplaceChild(
            "lantern",
            CubeListBuilder.create().texOffs(142, 22)
                .addBox(-1.5F, -1.0F, -1.5F, 3.0F, 4.0F, 3.0F),
            PartPose.offsetAndRotation(3.0F, 1.2F, 0.15F, 0.0F, 0.0F, 0.16F)
        );
        final PartDefinition toolHolster = torso.addOrReplaceChild(
            "tool_holster",
            CubeListBuilder.create().texOffs(158, 22)
                .addBox(-2.0F, -3.0F, -1.0F, 3.0F, 6.0F, 2.0F),
            PartPose.offsetAndRotation(-4.4F, 2.1F, 1.2F, 0.1F, 0.25F, 0.16F)
        );
        toolHolster.addOrReplaceChild(
            "prospecting_tool",
            CubeListBuilder.create().texOffs(172, 22)
                .addBox(-1.0F, -4.0F, -1.0F, 2.0F, 8.0F, 2.0F),
            PartPose.offsetAndRotation(-0.6F, -1.5F, 0.0F, 0.0F, 0.0F, -0.38F)
        );

        final PartDefinition rightFlipper = root.addOrReplaceChild(
            "right_flipper",
            CubeListBuilder.create().texOffs(0, 42)
                .addBox(-2.5F, -1.5F, -1.5F, 3.0F, 9.0F, 3.0F),
            PartPose.offsetAndRotation(-5.0F, 8.4F, 0.2F, -0.14F, 0.18F, 0.26F)
        );
        rightFlipper.addOrReplaceChild(
            "right_flipper_tip",
            CubeListBuilder.create().texOffs(18, 42)
                .addBox(-1.5F, 0.0F, -1.5F, 2.0F, 5.0F, 3.0F),
            PartPose.offsetAndRotation(-0.8F, 6.0F, 0.3F, 0.08F, 0.0F, 0.18F)
        );
        final PartDefinition leftFlipper = root.addOrReplaceChild(
            "left_flipper",
            CubeListBuilder.create().texOffs(36, 42)
                .addBox(-0.5F, -1.5F, -1.5F, 3.0F, 9.0F, 3.0F),
            PartPose.offsetAndRotation(5.0F, 8.4F, 0.2F, -0.14F, -0.18F, -0.26F)
        );
        leftFlipper.addOrReplaceChild(
            "left_flipper_tip",
            CubeListBuilder.create().texOffs(54, 42)
                .addBox(-0.5F, 0.0F, -1.5F, 2.0F, 5.0F, 3.0F),
            PartPose.offsetAndRotation(0.8F, 6.0F, 0.3F, 0.08F, 0.0F, -0.18F)
        );
        final PartDefinition rightLeg = root.addOrReplaceChild(
            "right_leg",
            CubeListBuilder.create().texOffs(72, 42)
                .addBox(-1.75F, 0.0F, -1.75F, 3.5F, 5.0F, 3.5F),
            PartPose.offsetAndRotation(-3.15F, 17.1F, 0.8F, -0.08F, 0.0F, 0.16F)
        );
        rightLeg.addOrReplaceChild(
            "right_webbed_foot",
            CubeListBuilder.create().texOffs(88, 42)
                .addBox(-2.75F, 0.0F, -4.5F, 5.5F, 2.0F, 6.0F),
            PartPose.offsetAndRotation(0.0F, 4.55F, -0.6F, 0.1F, 0.16F, 0.0F)
        );
        final PartDefinition leftLeg = root.addOrReplaceChild(
            "left_leg",
            CubeListBuilder.create().texOffs(114, 42)
                .addBox(-1.75F, 0.0F, -1.75F, 3.5F, 5.0F, 3.5F),
            PartPose.offsetAndRotation(3.15F, 17.1F, 0.8F, -0.08F, 0.0F, -0.16F)
        );
        leftLeg.addOrReplaceChild(
            "left_webbed_foot",
            CubeListBuilder.create().texOffs(130, 42)
                .addBox(-2.75F, 0.0F, -4.5F, 5.5F, 2.0F, 6.0F),
            PartPose.offsetAndRotation(0.0F, 4.55F, -0.6F, 0.1F, -0.16F, 0.0F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        head.yRot += state.yRot * Mth.DEG_TO_RAD;
        head.xRot += state.xRot * Mth.DEG_TO_RAD;
        final float pace = state.walkAnimationPos * 0.68F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F);
        final float waddle = Mth.sin(pace) * stride;
        torso.zRot += waddle * 0.16F;
        head.zRot -= waddle * 0.08F;
        rightLeg.xRot += Mth.cos(pace) * stride * 0.66F;
        leftLeg.xRot += Mth.cos(pace + Mth.PI) * stride * 0.66F;
        rightFoot.xRot -= Mth.cos(pace) * stride * 0.16F;
        leftFoot.xRot -= Mth.cos(pace + Mth.PI) * stride * 0.16F;
        rightFlipper.xRot += Mth.cos(pace + Mth.PI) * stride * 0.32F;
        leftFlipper.xRot += Mth.cos(pace) * stride * 0.32F;
        rightFlipperTip.zRot += waddle * 0.06F;
        leftFlipperTip.zRot -= waddle * 0.06F;
        tailWedge.yRot += Mth.sin(state.ageInTicks * 0.12F) * 0.09F;
        travelPack.zRot -= waddle * 0.045F;
        campRoll.yRot += waddle * 0.025F;
        lantern.zRot += Mth.sin(state.ageInTicks * 0.18F) * 0.11F;
        prospectingTool.zRot -= waddle * 0.035F;
        if (state.mode == Mode.DEFEND) {
            torso.xRot += 0.12F;
            head.xRot -= 0.13F;
            rightFlipper.xRot = -1.15F;
            rightFlipper.yRot = -0.42F;
            rightFlipper.zRot += 0.3F;
            leftFlipper.xRot = -0.55F;
            leftFlipper.yRot = 0.28F;
            leftFlipper.zRot -= 0.5F;
            hoodCrown.xRot -= 0.08F;
            hoodCape.xRot += 0.16F;
        } else if (state.mode == Mode.WORK_COMMIT) {
            torso.xRot += 0.2F;
            rightFlipper.xRot = -1.3F;
            leftFlipper.xRot = 0.45F;
            head.xRot += 0.16F;
        } else if (state.mode == Mode.CHILD_PLAY) {
            rightFlipper.zRot += 0.38F;
            leftFlipper.zRot -= 0.38F;
            head.zRot += Mth.sin(state.ageInTicks * 0.3F) * 0.12F;
        }
    }

    @Override
    public void translateToHand(final State state, final HumanoidArm arm, final PoseStack poseStack) {
        final ModelPart flipper = arm == HumanoidArm.LEFT ? leftFlipper : rightFlipper;
        final ModelPart tip = arm == HumanoidArm.LEFT ? leftFlipperTip : rightFlipperTip;
        flipper.translateAndRotate(poseStack);
        tip.translateAndRotate(poseStack);
        poseStack.translate(arm == HumanoidArm.LEFT ? 0.055F : -0.055F, 0.14F, -0.045F);
    }

    public static void extractRenderState(
        final HobgoblinEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.mode = entity.presentationMode();
    }

    public static final class State extends ArmedEntityRenderState {
        public Mode mode = Mode.IDLE;
    }
}
