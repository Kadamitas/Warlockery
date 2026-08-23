package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.ParasyticLouseEntity;
import com.kadamitas.warlockery.entity.ParasyticLouseTenancyRules.Phase;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public final class ParasyticLouseModel extends EntityModel<ParasyticLouseModel.State> {
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart head;
    private final ModelPart leftPalp;
    private final ModelPart rightPalp;
    private final ModelPart abdomenFront;
    private final ModelPart abdomenMid;
    private final ModelPart abdomenRear;
    private final ModelPart leftFrontLeg;
    private final ModelPart rightFrontLeg;
    private final ModelPart leftMidLeg;
    private final ModelPart rightMidLeg;
    private final ModelPart leftRearLeg;
    private final ModelPart rightRearLeg;

    public ParasyticLouseModel(final ModelPart root) {
        super(root);
        head = root.getChild("head");
        leftPalp = head.getChild("left_palp");
        rightPalp = head.getChild("right_palp");
        abdomenFront = root.getChild("abdomen_front");
        abdomenMid = abdomenFront.getChild("abdomen_mid");
        abdomenRear = abdomenMid.getChild("abdomen_rear");
        leftFrontLeg = root.getChild("left_front_leg");
        rightFrontLeg = root.getChild("right_front_leg");
        leftMidLeg = root.getChild("left_mid_leg");
        rightMidLeg = root.getChild("right_mid_leg");
        leftRearLeg = root.getChild("left_rear_leg");
        rightRearLeg = root.getChild("right_rear_leg");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -1.5F, -3.0F, 7.0F, 3.0F, 6.0F), PartPose.offset(0.0F, 15.5F, -5.0F));
        head.addOrReplaceChild("left_palp", CubeListBuilder.create().texOffs(84, 16).addBox(-1.0F, -0.5F, -4.0F, 2.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(2.0F, 1.0F, -0.6F, 0.0F, -0.22F, 0.18F));
        head.addOrReplaceChild("right_palp", CubeListBuilder.create().texOffs(96, 16).addBox(-1.0F, -0.5F, -4.0F, 2.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-2.0F, 1.0F, -0.6F, 0.0F, 0.22F, -0.18F));
        root.addOrReplaceChild("thorax", CubeListBuilder.create().texOffs(26, 0).addBox(-4.5F, -2.0F, -4.0F, 9.0F, 4.0F, 8.0F), PartPose.offset(0.0F, 14.5F, -0.5F));
        final PartDefinition abdomenFront = root.addOrReplaceChild(
            "abdomen_front",
            CubeListBuilder.create()
                .texOffs(60, 0).addBox(-5.5F, -2.0F, -5.0F, 11.0F, 4.0F, 10.0F)
                .texOffs(0, 0).addBox(-3.5F, -4.0F, -3.0F, 7.0F, 3.0F, 6.0F),
            PartPose.offset(0.0F, 14.0F, 4.0F)
        );
        final PartDefinition abdomenMid = abdomenFront.addOrReplaceChild("abdomen_mid", CubeListBuilder.create().texOffs(0, 16).addBox(-5.0F, -2.0F, -4.5F, 10.0F, 4.0F, 9.0F), PartPose.offset(0.0F, 1.0F, 5.0F));
        abdomenMid.addOrReplaceChild("abdomen_rear", CubeListBuilder.create().texOffs(38, 16).addBox(-4.5F, -1.5F, -4.0F, 9.0F, 3.0F, 8.0F), PartPose.offset(0.0F, 1.0F, 4.5F));
        root.addOrReplaceChild("feeding_core", CubeListBuilder.create().texOffs(72, 16).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 2.0F, 3.0F), PartPose.offset(0.0F, 16.8F, -5.4F));

        final PartDefinition leftFront = root.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(0, 30).addBox(0.0F, -1.0F, -1.5F, 5.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(3.2F, 15.5F, -4.0F, 0.0F, 0.0F, 0.72F));
        final PartDefinition leftFrontShin = leftFront.addOrReplaceChild("left_front_shin", CubeListBuilder.create().texOffs(0, 40).addBox(0.0F, -1.0F, -1.5F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(4.2F, 0.8F, 0.0F, 0.0F, 0.0F, 0.42F));
        leftFrontShin.addOrReplaceChild("left_front_hook", CubeListBuilder.create().texOffs(84, 16).addBox(0.0F, -0.5F, -2.0F, 2.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(1.3F, 0.4F, 0.0F, 0.0F, 0.0F, 0.18F));
        final PartDefinition rightFront = root.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(16, 30).addBox(-5.0F, -1.0F, -1.5F, 5.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(-3.2F, 15.5F, -4.0F, 0.0F, 0.0F, -0.72F));
        final PartDefinition rightFrontShin = rightFront.addOrReplaceChild("right_front_shin", CubeListBuilder.create().texOffs(12, 40).addBox(-2.0F, -1.0F, -1.5F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(-4.2F, 0.8F, 0.0F, 0.0F, 0.0F, -0.42F));
        rightFrontShin.addOrReplaceChild("right_front_hook", CubeListBuilder.create().texOffs(96, 16).addBox(-2.0F, -0.5F, -2.0F, 2.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-1.3F, 0.4F, 0.0F, 0.0F, 0.0F, -0.18F));

        final PartDefinition leftMid = root.addOrReplaceChild("left_mid_leg", CubeListBuilder.create().texOffs(32, 30).addBox(0.0F, -1.0F, -1.5F, 5.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(4.2F, 15.4F, 1.0F, 0.0F, 0.0F, 0.38F));
        final PartDefinition leftMidShin = leftMid.addOrReplaceChild("left_mid_shin", CubeListBuilder.create().texOffs(24, 40).addBox(0.0F, -1.0F, -1.5F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(4.2F, 0.8F, 0.0F, 0.0F, 0.0F, 0.52F));
        leftMidShin.addOrReplaceChild("left_mid_hook", CubeListBuilder.create().texOffs(84, 16).addBox(0.0F, -0.5F, -2.0F, 2.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(1.3F, 0.4F, 0.0F, 0.0F, 0.0F, 0.18F));
        final PartDefinition rightMid = root.addOrReplaceChild("right_mid_leg", CubeListBuilder.create().texOffs(48, 30).addBox(-5.0F, -1.0F, -1.5F, 5.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(-4.2F, 15.4F, 1.0F, 0.0F, 0.0F, -0.38F));
        final PartDefinition rightMidShin = rightMid.addOrReplaceChild("right_mid_shin", CubeListBuilder.create().texOffs(36, 40).addBox(-2.0F, -1.0F, -1.5F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(-4.2F, 0.8F, 0.0F, 0.0F, 0.0F, -0.52F));
        rightMidShin.addOrReplaceChild("right_mid_hook", CubeListBuilder.create().texOffs(96, 16).addBox(-2.0F, -0.5F, -2.0F, 2.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-1.3F, 0.4F, 0.0F, 0.0F, 0.0F, -0.18F));

        final PartDefinition leftRear = root.addOrReplaceChild("left_rear_leg", CubeListBuilder.create().texOffs(64, 30).addBox(0.0F, -1.0F, -1.5F, 5.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(4.5F, 15.2F, 6.0F, 0.0F, 0.0F, 0.14F));
        final PartDefinition leftRearShin = leftRear.addOrReplaceChild("left_rear_shin", CubeListBuilder.create().texOffs(48, 40).addBox(0.0F, -1.0F, -1.5F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(4.2F, 0.8F, 0.0F, 0.0F, 0.0F, 0.62F));
        leftRearShin.addOrReplaceChild("left_rear_hook", CubeListBuilder.create().texOffs(84, 16).addBox(0.0F, -0.5F, -2.0F, 2.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(1.3F, 0.4F, 0.0F, 0.0F, 0.0F, 0.18F));
        final PartDefinition rightRear = root.addOrReplaceChild("right_rear_leg", CubeListBuilder.create().texOffs(80, 30).addBox(-5.0F, -1.0F, -1.5F, 5.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(-4.5F, 15.2F, 6.0F, 0.0F, 0.0F, -0.14F));
        final PartDefinition rightRearShin = rightRear.addOrReplaceChild("right_rear_shin", CubeListBuilder.create().texOffs(60, 40).addBox(-2.0F, -1.0F, -1.5F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(-4.2F, 0.8F, 0.0F, 0.0F, 0.0F, -0.62F));
        rightRearShin.addOrReplaceChild("right_rear_hook", CubeListBuilder.create().texOffs(96, 16).addBox(-2.0F, -0.5F, -2.0F, 2.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-1.3F, 0.4F, 0.0F, 0.0F, 0.0F, -0.18F));
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        head.yRot = Mth.clamp(state.yRot * ((float) Math.PI / 180.0F), -0.55F, 0.55F);
        head.xRot = Mth.clamp(state.xRot * ((float) Math.PI / 180.0F), -0.4F, 0.45F);
        final float stride = state.walkAnimationSpeed;
        final float wave = Mth.sin(state.walkAnimationPos * 1.1F) * 0.42F * stride;
        leftFrontLeg.yRot -= wave;
        rightFrontLeg.yRot -= wave;
        leftMidLeg.yRot += wave;
        rightMidLeg.yRot += wave;
        leftRearLeg.yRot -= wave;
        rightRearLeg.yRot -= wave;
        abdomenFront.y = 14.0F + Mth.sin(state.ageInTicks * 0.16F) * 0.12F;
        abdomenMid.yRot = Mth.sin(state.ageInTicks * 0.13F) * 0.04F;
        abdomenRear.yRot = -abdomenMid.yRot * 1.4F;
        if (state.feeding || state.phase == Phase.FEED) {
            head.xRot = 0.48F;
            head.y = 16.8F;
            leftPalp.yRot = -0.48F;
            rightPalp.yRot = 0.48F;
            abdomenFront.xRot = -0.26F - Math.min(state.nourishment, 4) * 0.025F;
            abdomenMid.xRot = -0.18F;
            abdomenRear.xRot = -0.12F;
            leftFrontLeg.zRot = 0.72F;
            rightFrontLeg.zRot = -0.72F;
        }
    }

    public static void extractRenderState(final ParasyticLouseEntity entity, final State state, final float partialTicks) {
        state.phase = entity.presentationPhase();
        state.feeding = state.phase == Phase.FEED;
        state.nourishment = entity.presentationNourishment();
    }

    public static final class State extends LivingEntityRenderState {
        public Phase phase = Phase.FREE;
        public boolean feeding;
        public int nourishment;
    }
}
