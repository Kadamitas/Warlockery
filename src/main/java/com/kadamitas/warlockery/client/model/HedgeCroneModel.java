package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.HedgeCroneEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public final class HedgeCroneModel extends EntityModel<HedgeCroneModel.State> {
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart burdenFrame;
    private final ModelPart mortar;
    private final ModelPart pestle;
    private final ModelPart wardBundle;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public HedgeCroneModel(final ModelPart root) {
        super(root);
        final ModelPart pelvis = root.getChild("hunched_pelvis");
        body = pelvis.getChild("body");
        head = body.getChild("head");
        burdenFrame = body.getChild("one_sided_root_arch");
        mortar = body.getChild("shallow_stone_bowl");
        wardBundle = body.getChild("ward_bundle");
        rightArm = pelvis.getChild("right_arm");
        leftArm = pelvis.getChild("left_arm");
        pestle = rightArm.getChild("short_ward_pestle");
        rightLeg = pelvis.getChild("right_bent_leg");
        leftLeg = pelvis.getChild("left_bent_leg");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition pelvis = root.addOrReplaceChild(
            "hunched_pelvis",
            CubeListBuilder.create().texOffs(0, 40)
                .addBox(-5.0F, -2.0F, -3.0F, 10.0F, 11.0F, 6.0F),
            PartPose.offsetAndRotation(0.0F, 20.0F, 0.5F, 0.05F, 0.0F, 0.0F)
        );
        final PartDefinition body = pelvis.addOrReplaceChild(
            "body",
            CubeListBuilder.create().texOffs(32, 40)
                .addBox(-4.0F, 0.0F, -2.5F, 8.0F, 11.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, -12.0F, -0.6F, 0.18F, 0.0F, 0.0F)
        );
        final PartDefinition head = body.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.5F, -7.0F, -3.5F, 7.0F, 7.0F, 7.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, -0.8F, 0.12F, 0.0F, 0.0F)
        );
        head.addOrReplaceChild(
            "gray_hair_cap",
            CubeListBuilder.create().texOffs(0, 20)
                .addBox(-3.5F, -7.0F, -3.5F, 7.0F, 3.0F, 7.0F, new CubeDeformation(0.16F)),
            PartPose.ZERO
        );
        head.addOrReplaceChild(
            "gray_back_hair",
            CubeListBuilder.create().texOffs(28, 20)
                .addBox(-3.5F, -5.0F, 3.2F, 7.0F, 7.0F, 1.0F),
            PartPose.ZERO
        );
        head.addOrReplaceChild(
            "right_gray_lock",
            CubeListBuilder.create().texOffs(44, 20)
                .addBox(-4.0F, -5.0F, -3.8F, 2.0F, 6.0F, 1.0F),
            PartPose.ZERO
        );
        head.addOrReplaceChild(
            "left_gray_lock",
            CubeListBuilder.create().texOffs(50, 20)
                .addBox(2.0F, -5.0F, -3.8F, 2.0F, 6.0F, 1.0F),
            PartPose.ZERO
        );
        head.addOrReplaceChild(
            "crooked_nose",
            CubeListBuilder.create().texOffs(56, 20)
                .addBox(-1.0F, -1.0F, -2.0F, 2.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, -3.0F, -3.2F, 0.15F, 0.0F, 0.0F)
        );
        body.addOrReplaceChild(
            "shawl",
            CubeListBuilder.create().texOffs(64, 20)
                .addBox(-4.5F, -0.5F, -3.0F, 9.0F, 5.0F, 6.0F, new CubeDeformation(0.1F)),
            PartPose.ZERO
        );
        body.addOrReplaceChild(
            "one_sided_root_arch",
            CubeListBuilder.create().texOffs(48, 60)
                .addBox(-0.5F, -6.0F, -0.5F, 1.0F, 7.0F, 1.0F)
                .texOffs(100, 60).addBox(-0.5F, -6.0F, -0.5F, 4.0F, 1.0F, 1.0F),
            PartPose.offsetAndRotation(-3.0F, 1.0F, 3.0F, -0.1F, 0.0F, -0.16F)
        );
        body.addOrReplaceChild(
            "shallow_stone_bowl",
            CubeListBuilder.create().texOffs(56, 60)
                .addBox(-2.0F, -1.0F, -1.5F, 4.0F, 2.0F, 3.0F),
            PartPose.offsetAndRotation(4.0F, 7.0F, -1.4F, 0.12F, -0.1F, -0.08F)
        );
        body.addOrReplaceChild(
            "ward_bundle",
            CubeListBuilder.create().texOffs(76, 60)
                .addBox(-1.0F, -2.5F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(-4.0F, 7.0F, 1.5F, -0.12F, 0.0F, 0.12F)
        );
        final PartDefinition rightArm = pelvis.addOrReplaceChild(
            "right_arm",
            CubeListBuilder.create().texOffs(0, 60)
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 12.0F, 3.0F),
            PartPose.offsetAndRotation(-5.5F, -10.0F, 0.0F, -0.1F, 0.0F, 0.12F)
        );
        rightArm.addOrReplaceChild(
            "short_ward_pestle",
            CubeListBuilder.create().texOffs(72, 60)
                .addBox(-0.5F, -1.0F, -0.5F, 1.0F, 6.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, 8.0F, -1.8F, 0.2F, 0.0F, 0.2F)
        );
        pelvis.addOrReplaceChild(
            "left_arm",
            CubeListBuilder.create().texOffs(12, 60)
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 12.0F, 3.0F),
            PartPose.offsetAndRotation(5.5F, -10.0F, 0.0F, -0.1F, 0.0F, -0.12F)
        );
        pelvis.addOrReplaceChild(
            "right_bent_leg",
            CubeListBuilder.create().texOffs(24, 60)
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F),
            PartPose.offset(-2.0F, 8.0F, 0.0F)
        );
        pelvis.addOrReplaceChild(
            "left_bent_leg",
            CubeListBuilder.create().texOffs(36, 60)
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F),
            PartPose.offset(2.0F, 8.0F, 0.0F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    public static void extractRenderState(
        final HedgeCroneEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.activity = Activity.valueOf(entity.presentationActivity().name());
        state.wardPrepared = entity.presentationWardPrepared();
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot += state.xRot * Mth.DEG_TO_RAD;
        final float pace = state.walkAnimationPos * 0.55F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F);
        rightLeg.xRot += Mth.cos(pace) * 0.45F * stride;
        leftLeg.xRot += Mth.cos(pace + Mth.PI) * 0.45F * stride;
        rightArm.xRot += Mth.cos(pace + Mth.PI) * 0.25F * stride;
        leftArm.xRot += Mth.cos(pace) * 0.25F * stride;
        burdenFrame.zRot += Mth.sin(state.ageInTicks * 0.04F) * 0.025F;
        wardBundle.visible = state.wardPrepared;
        if (state.activity == Activity.WARNING) {
            head.yRot += Mth.sin(state.ageInTicks * 0.32F) * 0.16F;
            rightArm.xRot = -0.75F;
            leftArm.xRot = -0.55F;
        } else if (state.activity == Activity.CASTING) {
            rightArm.xRot = -1.35F;
            rightArm.yRot = -0.45F;
            leftArm.xRot = -1.1F;
            leftArm.yRot = 0.6F;
            pestle.zRot += Mth.sin(state.ageInTicks * 0.3F) * 0.22F;
            mortar.xRot += 0.18F;
        } else if (state.activity == Activity.PREPARING) {
            rightArm.xRot = -0.9F;
            leftArm.xRot = -1.25F;
            wardBundle.yRot = state.ageInTicks * 0.025F;
        } else if (state.activity == Activity.WITHDRAWING) {
            body.xRot += 0.16F;
            burdenFrame.xRot -= 0.1F;
        }
    }

    public enum Activity {
        IDLE,
        WARNING,
        CASTING,
        PREPARING,
        WITHDRAWING,
        RETURNING
    }

    public static final class State extends LivingEntityRenderState {
        public Activity activity = Activity.IDLE;
        public boolean wardPrepared;
    }
}
