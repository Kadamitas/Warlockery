package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.SpectreEntity;
import com.kadamitas.warlockery.entity.SpectreRules.Phase;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/** Independent near-invisible dread manifestation; its reach is a warning pose, never an attack. */
public final class SpectreModel extends EntityModel<SpectreModel.State> {
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart cowl;
    private final ModelPart innerFace;
    private final ModelPart mantle;
    private final ModelPart shroud;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightForearm;
    private final ModelPart leftForearm;
    private final ModelPart rightTip;
    private final ModelPart leftTip;
    private final ModelPart leftTail;
    private final ModelPart rightTail;

    public SpectreModel(final ModelPart root) {
        super(root);
        cowl = root.getChild("empty_cowl");
        innerFace = cowl.getChild("obscured_inner_face");
        mantle = root.getChild("hooked_mantle");
        shroud = root.getChild("manifestation_shroud");
        rightArm = root.getChild("right_reaching_arm");
        leftArm = root.getChild("left_reaching_arm");
        rightForearm = rightArm.getChild("right_reaching_forearm");
        leftForearm = leftArm.getChild("left_reaching_forearm");
        rightTip = rightForearm.getChild("right_cold_touch_tip");
        leftTip = leftForearm.getChild("left_cold_touch_tip");
        leftTail = root.getChild("fork_tail_left");
        rightTail = root.getChild("fork_tail_right");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition cowl = root.addOrReplaceChild(
            "empty_cowl",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -6.0F, -4.0F, 8.0F, 8.0F, 8.0F)
                .texOffs(32, 0).addBox(-5.0F, -4.0F, -3.0F, 10.0F, 4.0F, 6.0F),
            PartPose.offsetAndRotation(0.0F, 7.0F, -1.0F, -0.12F, 0.0F, 0.0F)
        );
        cowl.addOrReplaceChild(
            "obscured_inner_face",
            CubeListBuilder.create()
                .texOffs(64, 0).addBox(-2.5F, -2.0F, -0.5F, 5.0F, 4.0F, 1.0F)
                .texOffs(78, 0).addBox(-2.0F, -0.8F, -0.65F, 1.2F, 1.0F, 0.5F)
                .texOffs(84, 0).addBox(0.8F, -0.8F, -0.65F, 1.2F, 1.0F, 0.5F),
            PartPose.offset(0.0F, -1.0F, -4.0F)
        );
        final PartDefinition mantle = root.addOrReplaceChild(
            "hooked_mantle",
            CubeListBuilder.create().texOffs(0, 20).addBox(-8.0F, -2.0F, -3.0F, 16.0F, 6.0F, 6.0F),
            PartPose.offsetAndRotation(0.0F, 9.5F, 0.0F, 0.12F, 0.0F, 0.0F)
        );
        mantle.addOrReplaceChild(
            "mantle_left_hook",
            CubeListBuilder.create().texOffs(92, 20).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 5.0F, 3.0F),
            PartPose.offsetAndRotation(-8.0F, 1.5F, 0.0F, -0.12F, 0.0F, 0.22F)
        );
        mantle.addOrReplaceChild(
            "mantle_right_hook",
            CubeListBuilder.create().texOffs(104, 20).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 5.0F, 3.0F),
            PartPose.offsetAndRotation(8.0F, 1.5F, 0.0F, -0.12F, 0.0F, -0.22F)
        );
        mantle.addOrReplaceChild(
            "mantle_back_layer",
            CubeListBuilder.create().texOffs(44, 20).addBox(-10.0F, -1.0F, -2.0F, 20.0F, 3.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, 2.5F, -0.16F, 0.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "manifestation_shroud",
            CubeListBuilder.create()
                .texOffs(0, 34).addBox(-3.0F, -4.0F, -1.2F, 2.0F, 7.0F, 2.4F)
                .texOffs(10, 34).addBox(1.0F, -3.0F, -1.2F, 2.0F, 6.0F, 2.4F)
                .texOffs(20, 34).addBox(-1.0F, 2.0F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 13.0F, -1.8F, 0.08F, 0.0F, 0.0F)
        );

        final PartDefinition right = root.addOrReplaceChild(
            "right_reaching_arm",
            CubeListBuilder.create().texOffs(32, 34).addBox(-2.0F, 0.0F, -2.0F, 3.0F, 11.0F, 4.0F),
            PartPose.offsetAndRotation(-7.0F, 10.0F, 0.0F, -0.22F, 0.0F, 0.14F)
        );
        final PartDefinition rightForearm = right.addOrReplaceChild(
            "right_reaching_forearm",
            CubeListBuilder.create().texOffs(48, 34).addBox(-1.6F, 0.0F, -1.5F, 2.5F, 4.0F, 3.0F),
            PartPose.offsetAndRotation(-0.35F, 8.5F, 0.0F, -0.48F, 0.0F, -0.08F)
        );
        rightForearm.addOrReplaceChild(
            "right_cold_touch_tip",
            CubeListBuilder.create()
                .texOffs(62, 34).addBox(-1.5F, 0.0F, -1.0F, 2.0F, 4.5F, 2.0F)
                .texOffs(72, 34).addBox(-1.0F, 3.5F, -0.65F, 1.2F, 3.0F, 1.3F),
            PartPose.offsetAndRotation(-0.2F, 3.5F, 0.0F, -0.42F, 0.0F, -0.10F)
        );
        final PartDefinition left = root.addOrReplaceChild(
            "left_reaching_arm",
            CubeListBuilder.create().texOffs(82, 34).addBox(-1.0F, 0.0F, -2.0F, 3.0F, 11.0F, 4.0F),
            PartPose.offsetAndRotation(7.0F, 10.0F, 0.0F, -0.22F, 0.0F, -0.14F)
        );
        final PartDefinition leftForearm = left.addOrReplaceChild(
            "left_reaching_forearm",
            CubeListBuilder.create().texOffs(98, 34).addBox(-0.9F, 0.0F, -1.5F, 2.5F, 4.0F, 3.0F),
            PartPose.offsetAndRotation(0.35F, 8.5F, 0.0F, -0.48F, 0.0F, 0.08F)
        );
        leftForearm.addOrReplaceChild(
            "left_cold_touch_tip",
            CubeListBuilder.create()
                .texOffs(0, 50).addBox(-0.5F, 0.0F, -1.0F, 2.0F, 4.5F, 2.0F)
                .texOffs(10, 50).addBox(-0.2F, 3.5F, -0.65F, 1.2F, 3.0F, 1.3F),
            PartPose.offsetAndRotation(0.2F, 3.5F, 0.0F, -0.42F, 0.0F, 0.10F)
        );
        root.addOrReplaceChild(
            "fork_tail_left",
            CubeListBuilder.create()
                .texOffs(20, 50).addBox(-3.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F)
                .texOffs(38, 50).addBox(-2.0F, 6.0F, -1.2F, 2.0F, 5.0F, 2.4F),
            PartPose.offsetAndRotation(-1.5F, 14.0F, 0.0F, 0.0F, 0.0F, 0.2F)
        );
        root.addOrReplaceChild(
            "fork_tail_right",
            CubeListBuilder.create()
                .texOffs(50, 50).addBox(-1.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F)
                .texOffs(68, 50).addBox(0.0F, 6.0F, -1.2F, 2.0F, 5.0F, 2.4F),
            PartPose.offsetAndRotation(1.5F, 14.0F, 0.0F, 0.0F, 0.0F, -0.24F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        cowl.yRot += state.yRot * Mth.DEG_TO_RAD;
        cowl.xRot += state.xRot * Mth.DEG_TO_RAD;
        final float stalk = Mth.sin(state.ageInTicks * 0.1F);
        mantle.zRot += stalk * 0.035F;
        shroud.y += Mth.cos(state.ageInTicks * 0.13F) * 0.3F;
        shroud.yRot += stalk * 0.08F;
        innerFace.xScale = 1.0F + stalk * 0.035F;
        leftTail.zRot -= stalk * 0.08F;
        rightTail.zRot += stalk * 0.08F;
        rightArm.xRot += Mth.cos(state.walkAnimationPos * 0.55F) * state.walkAnimationSpeed * 0.3F;
        leftArm.xRot -= Mth.cos(state.walkAnimationPos * 0.55F) * state.walkAnimationSpeed * 0.3F;
        if (state.manifesting) {
            mantle.xRot += 0.35F;
            mantle.xScale = 1.12F;
            cowl.xRot -= 0.22F;
            shroud.xScale = 1.3F;
            shroud.zScale = 1.2F;
            rightArm.xRot -= 0.45F;
            leftArm.xRot -= 0.45F;
            rightArm.zRot += 0.62F;
            leftArm.zRot -= 0.62F;
            rightForearm.xRot -= 0.22F;
            leftForearm.xRot -= 0.22F;
            rightTip.xRot -= 0.48F;
            leftTip.xRot -= 0.48F;
            leftTail.zRot += 0.32F;
            rightTail.zRot -= 0.32F;
        }
    }

    public static void extractRenderState(final SpectreEntity entity, final State state, final float partialTicks) {
        state.huntPhase = entity.presentationPhase();
        state.manifesting = state.huntPhase == Phase.MANIFEST || state.huntPhase == Phase.DREAD;
    }

    public static final class State extends LivingEntityRenderState {
        public Phase huntPhase = Phase.DRIFT;
        public boolean manifesting;
    }
}
