package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.PoltergeistEntity;
import com.kadamitas.warlockery.entity.PoltergeistRules.Phase;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/** A fully independent embodied household apparition with detached kinetic hands. */
public final class PoltergeistModel extends EntityModel<PoltergeistModel.State> {
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart head;
    private final ModelPart hollowFace;
    private final ModelPart shoulderArch;
    private final ModelPart torso;
    private final ModelPart innerRightArm;
    private final ModelPart innerLeftArm;
    private final ModelPart leftHand;
    private final ModelPart rightHand;
    private final ModelPart tail;
    private final ModelPart orbit;
    private final ModelPart chair;
    private final ModelPart book;
    private final ModelPart bottle;
    private final ModelPart pebble;

    public PoltergeistModel(final ModelPart root) {
        super(root);
        head = root.getChild("apparition_head");
        hollowFace = head.getChild("hollow_face");
        shoulderArch = root.getChild("shoulder_arch");
        torso = root.getChild("ectoplasm_torso");
        innerRightArm = root.getChild("inner_right_spectral_arm");
        innerLeftArm = root.getChild("inner_left_spectral_arm");
        leftHand = root.getChild("left_force_hand");
        rightHand = root.getChild("right_force_hand");
        tail = root.getChild("spiral_tail");
        orbit = root.getChild("object_orbit");
        chair = orbit.getChild("chair");
        book = orbit.getChild("book");
        bottle = orbit.getChild("bottle");
        pebble = orbit.getChild("pebble");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();

        final PartDefinition head = root.addOrReplaceChild(
            "apparition_head",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -4.5F, -3.5F, 8.0F, 7.0F, 7.0F)
                .texOffs(30, 0).addBox(-3.0F, -6.0F, -2.5F, 3.0F, 2.0F, 5.0F)
                .texOffs(46, 0).addBox(0.5F, -5.3F, -2.7F, 3.5F, 2.0F, 5.4F),
            PartPose.offsetAndRotation(0.0F, 7.0F, -0.5F, -0.08F, 0.0F, 0.0F)
        );
        head.addOrReplaceChild(
            "hollow_face",
            CubeListBuilder.create()
                .texOffs(64, 0).addBox(-2.6F, -1.8F, -0.5F, 2.0F, 2.0F, 1.0F)
                .texOffs(72, 0).addBox(0.6F, -1.8F, -0.5F, 2.0F, 2.0F, 1.0F)
                .texOffs(80, 0).addBox(-2.0F, 0.8F, -0.5F, 4.0F, 2.0F, 1.0F),
            PartPose.offset(0.0F, 0.0F, -3.35F)
        );
        root.addOrReplaceChild(
            "shoulder_arch",
            CubeListBuilder.create()
                .texOffs(0, 18).addBox(-5.5F, -2.0F, -2.5F, 11.0F, 2.0F, 5.0F)
                .texOffs(34, 18).addBox(-7.5F, -1.0F, -2.0F, 2.0F, 5.0F, 4.0F)
                .texOffs(48, 18).addBox(5.5F, -1.0F, -2.0F, 2.0F, 5.0F, 4.0F),
            PartPose.offset(0.0F, 10.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "ectoplasm_torso",
            CubeListBuilder.create()
                .texOffs(0, 30).addBox(-3.2F, -1.0F, -2.5F, 6.4F, 7.0F, 5.0F)
                .texOffs(24, 30).addBox(-2.2F, 5.0F, -1.8F, 4.4F, 4.0F, 3.6F),
            PartPose.offset(0.0F, 11.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "inner_right_spectral_arm",
            CubeListBuilder.create()
                .texOffs(42, 30).addBox(-1.5F, 0.0F, -1.0F, 2.0F, 7.0F, 2.0F)
                .texOffs(52, 30).addBox(-1.8F, 6.2F, -1.2F, 2.0F, 3.0F, 2.4F),
            PartPose.offsetAndRotation(-3.7F, 11.0F, -0.5F, -0.22F, 0.0F, 0.25F)
        );
        root.addOrReplaceChild(
            "inner_left_spectral_arm",
            CubeListBuilder.create()
                .texOffs(64, 30).addBox(-0.5F, 0.0F, -1.0F, 2.0F, 7.0F, 2.0F)
                .texOffs(74, 30).addBox(-0.2F, 6.2F, -1.2F, 2.0F, 3.0F, 2.4F),
            PartPose.offsetAndRotation(3.7F, 11.0F, -0.5F, -0.22F, 0.0F, -0.25F)
        );

        final PartDefinition leftForceHand = root.addOrReplaceChild(
            "left_force_hand",
            CubeListBuilder.create().texOffs(0, 46).addBox(-3.0F, -4.0F, -2.0F, 6.0F, 8.0F, 4.0F),
            PartPose.offsetAndRotation(-13.0F, 13.0F, 0.0F, 0.0F, -0.08F, -0.12F)
        );
        leftForceHand.addOrReplaceChild(
            "left_force_thumb",
            CubeListBuilder.create().texOffs(22, 46).addBox(-1.0F, -1.5F, -1.5F, 2.0F, 3.0F, 3.0F),
            PartPose.offsetAndRotation(3.2F, 1.8F, 0.0F, 0.0F, 0.0F, -0.65F)
        );
        leftForceHand.addOrReplaceChild(
            "left_force_index",
            CubeListBuilder.create().texOffs(22, 46).addBox(-1.0F, -1.5F, -1.5F, 2.0F, 3.0F, 3.0F),
            PartPose.offsetAndRotation(-2.2F, -4.6F, 0.0F, 0.0F, 0.0F, 0.20F)
        );
        leftForceHand.addOrReplaceChild(
            "left_force_middle",
            CubeListBuilder.create().texOffs(22, 46).addBox(-1.0F, -1.5F, -1.5F, 2.0F, 3.0F, 3.0F),
            PartPose.offset(0.0F, -5.0F, 0.0F)
        );
        leftForceHand.addOrReplaceChild(
            "left_force_ring",
            CubeListBuilder.create().texOffs(22, 46).addBox(-1.0F, -1.5F, -1.5F, 2.0F, 3.0F, 3.0F),
            PartPose.offsetAndRotation(2.2F, -4.5F, 0.0F, 0.0F, 0.0F, -0.16F)
        );
        leftForceHand.addOrReplaceChild(
            "left_force_little",
            CubeListBuilder.create().texOffs(32, 46).addBox(-1.0F, -1.5F, -1.4F, 2.0F, 3.0F, 2.8F),
            PartPose.offsetAndRotation(-3.2F, 1.4F, 0.0F, 0.0F, 0.0F, 0.58F)
        );

        final PartDefinition rightForceHand = root.addOrReplaceChild(
            "right_force_hand",
            CubeListBuilder.create().texOffs(44, 46).addBox(-3.0F, -4.0F, -2.0F, 6.0F, 8.0F, 4.0F),
            PartPose.offsetAndRotation(13.0F, 13.0F, 0.0F, 0.0F, 0.08F, 0.12F)
        );
        rightForceHand.addOrReplaceChild(
            "right_force_thumb",
            CubeListBuilder.create().texOffs(66, 46).addBox(-1.0F, -1.5F, -1.5F, 2.0F, 3.0F, 3.0F),
            PartPose.offsetAndRotation(-3.2F, 1.8F, 0.0F, 0.0F, 0.0F, 0.65F)
        );
        rightForceHand.addOrReplaceChild(
            "right_force_index",
            CubeListBuilder.create().texOffs(66, 46).addBox(-1.0F, -1.5F, -1.5F, 2.0F, 3.0F, 3.0F),
            PartPose.offsetAndRotation(2.2F, -4.6F, 0.0F, 0.0F, 0.0F, -0.20F)
        );
        rightForceHand.addOrReplaceChild(
            "right_force_middle",
            CubeListBuilder.create().texOffs(66, 46).addBox(-1.0F, -1.5F, -1.5F, 2.0F, 3.0F, 3.0F),
            PartPose.offset(0.0F, -5.0F, 0.0F)
        );
        rightForceHand.addOrReplaceChild(
            "right_force_ring",
            CubeListBuilder.create().texOffs(66, 46).addBox(-1.0F, -1.5F, -1.5F, 2.0F, 3.0F, 3.0F),
            PartPose.offsetAndRotation(-2.2F, -4.5F, 0.0F, 0.0F, 0.0F, 0.16F)
        );
        rightForceHand.addOrReplaceChild(
            "right_force_little",
            CubeListBuilder.create().texOffs(76, 46).addBox(-1.0F, -1.5F, -1.4F, 2.0F, 3.0F, 2.8F),
            PartPose.offsetAndRotation(3.2F, 1.4F, 0.0F, 0.0F, 0.0F, -0.58F)
        );
        root.addOrReplaceChild(
            "spiral_tail",
            CubeListBuilder.create()
                .texOffs(0, 62).addBox(-2.2F, 0.0F, -2.0F, 4.4F, 6.0F, 4.0F)
                .texOffs(18, 62).addBox(-1.4F, 5.0F, -1.4F, 2.8F, 4.0F, 2.8F)
                .texOffs(30, 62).addBox(-0.7F, 8.0F, -0.7F, 1.4F, 2.0F, 1.4F),
            PartPose.offsetAndRotation(0.0F, 14.0F, 0.0F, 0.0F, 0.0F, 0.18F)
        );

        final PartDefinition orbit = root.addOrReplaceChild(
            "object_orbit", CubeListBuilder.create(), PartPose.offset(0.0F, 11.0F, 0.0F)
        );
        orbit.addOrReplaceChild(
            "chair",
            CubeListBuilder.create()
                .texOffs(0, 76).addBox(-2.5F, -2.0F, -1.5F, 5.0F, 2.0F, 3.0F)
                .texOffs(18, 76).addBox(-2.5F, -7.0F, -1.5F, 1.2F, 7.0F, 3.0F)
                .texOffs(28, 76).addBox(1.3F, -7.0F, -1.5F, 1.2F, 7.0F, 3.0F),
            PartPose.offsetAndRotation(-11.0F, 2.0F, 1.0F, 0.2F, 0.2F, -0.3F)
        );
        orbit.addOrReplaceChild(
            "book",
            CubeListBuilder.create().texOffs(40, 76).addBox(-3.0F, -1.0F, -2.0F, 6.0F, 2.0F, 4.0F),
            PartPose.offsetAndRotation(11.0F, -5.0F, -1.0F, 0.45F, 0.25F, 0.2F)
        );
        orbit.addOrReplaceChild(
            "bottle",
            CubeListBuilder.create()
                .texOffs(62, 76).addBox(-1.2F, -3.0F, -1.2F, 2.4F, 5.0F, 2.4F)
                .texOffs(74, 76).addBox(-0.6F, -5.0F, -0.6F, 1.2F, 2.0F, 1.2F),
            PartPose.offsetAndRotation(-10.0F, 8.0F, -1.0F, -0.2F, 0.0F, 0.55F)
        );
        orbit.addOrReplaceChild(
            "pebble",
            CubeListBuilder.create().texOffs(82, 76).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F),
            PartPose.offset(9.5F, 7.0F, 2.0F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        final float whirl = state.ageInTicks * 0.055F;
        final float flicker = Mth.sin(state.ageInTicks * 0.16F);
        orbit.yRot += whirl;
        head.zRot += Mth.sin(state.ageInTicks * 0.14F) * 0.06F;
        hollowFace.zScale = 1.0F + flicker * 0.04F;
        shoulderArch.zRot -= flicker * 0.035F;
        torso.y += flicker * 0.2F;
        tail.zRot += Mth.sin(state.ageInTicks * 0.11F) * 0.16F;
        innerRightArm.xRot += Mth.cos(state.walkAnimationPos * 0.45F) * state.walkAnimationSpeed * 0.22F;
        innerLeftArm.xRot -= Mth.cos(state.walkAnimationPos * 0.45F) * state.walkAnimationSpeed * 0.22F;
        leftHand.y += flicker * 0.5F;
        rightHand.y -= flicker * 0.5F;
        if (state.flinging) {
            orbit.yRot += 0.8F;
            orbit.xScale = 1.2F;
            orbit.zScale = 1.2F;
            hollowFace.xScale = 1.18F;
            hollowFace.yScale = 1.22F;
            innerRightArm.zRot -= 0.45F;
            innerLeftArm.zRot += 0.45F;
            leftHand.xRot -= 0.9F;
            rightHand.xRot -= 0.9F;
            chair.z += 4.0F;
            book.z += 5.0F;
            bottle.z += 3.0F;
            pebble.z += 6.0F;
        }
    }

    public static void extractRenderState(final PoltergeistEntity entity, final State state, final float partialTicks) {
        state.hauntingPhase = entity.presentationPhase();
        state.flinging = state.hauntingPhase == Phase.LIFT || state.hauntingPhase == Phase.THROW;
    }

    public static final class State extends LivingEntityRenderState {
        public Phase hauntingPhase = Phase.LURK;
        public boolean flinging;
    }
}
