package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.IllusionZombieEntity;
import com.kadamitas.warlockery.entity.MimicryRules.Phase;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public final class IllusionZombieModel extends EntityModel<IllusionZombieModel.State> {
    public static final int TEXTURE_WIDTH = 64;
    public static final int TEXTURE_HEIGHT = 64;

    private final ModelPart steppedMaskHead;
    private final ModelPart leftFacePlate;
    private final ModelPart rightFacePlate;
    private final ModelPart crownFragment;
    private final ModelPart voidNeck;
    private final ModelPart torsoShell;
    private final ModelPart leftTorsoSlab;
    private final ModelPart rightTorsoSlab;
    private final ModelPart leftBrokenHem;
    private final ModelPart rightBrokenHem;
    private final ModelPart leftShoulder;
    private final ModelPart leftUpperArm;
    private final ModelPart leftForearm;
    private final ModelPart rightShoulder;
    private final ModelPart rightUpperArm;
    private final ModelPart rightForearm;
    private final ModelPart leftLeg;
    private final ModelPart leftFoot;
    private final ModelPart rightLeg;
    private final ModelPart rightFoot;

    public IllusionZombieModel(final ModelPart root) {
        super(root);
        steppedMaskHead = root.getChild("stepped_mask_head");
        leftFacePlate = steppedMaskHead.getChild("left_face_plate");
        rightFacePlate = steppedMaskHead.getChild("right_face_plate");
        crownFragment = steppedMaskHead.getChild("crown_fragment");
        voidNeck = root.getChild("void_neck");
        torsoShell = root.getChild("torso_shell");
        leftTorsoSlab = torsoShell.getChild("left_torso_slab");
        rightTorsoSlab = torsoShell.getChild("right_torso_slab");
        leftBrokenHem = torsoShell.getChild("left_broken_hem");
        rightBrokenHem = torsoShell.getChild("right_broken_hem");
        leftShoulder = root.getChild("left_shoulder");
        leftUpperArm = leftShoulder.getChild("left_upper_arm");
        leftForearm = leftUpperArm.getChild("left_forearm");
        rightShoulder = root.getChild("right_shoulder");
        rightUpperArm = rightShoulder.getChild("right_upper_arm");
        rightForearm = rightUpperArm.getChild("right_forearm");
        leftLeg = root.getChild("left_leg");
        leftFoot = leftLeg.getChild("left_foot");
        rightLeg = root.getChild("right_leg");
        rightFoot = rightLeg.getChild("right_foot");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition head = root.addOrReplaceChild(
            "stepped_mask_head",
            CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -4.0F, -2.5F, 7.0F, 8.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, 4.0F, -0.7F, 0.03F, 0.0F, 0.0F)
        );
        head.addOrReplaceChild(
            "left_face_plate",
            CubeListBuilder.create().texOffs(26, 0).addBox(-3.0F, -3.0F, -0.5F, 3.0F, 6.0F, 1.0F),
            PartPose.offsetAndRotation(-0.3F, 0.4F, -2.55F, 0.0F, 0.0F, -0.08F)
        );
        head.addOrReplaceChild(
            "right_face_plate",
            CubeListBuilder.create().texOffs(26, 0).addBox(0.0F, -3.0F, -0.5F, 3.0F, 6.0F, 1.0F),
            PartPose.offsetAndRotation(0.3F, -0.1F, -2.58F, 0.0F, 0.0F, 0.11F)
        );
        head.addOrReplaceChild(
            "crown_fragment",
            CubeListBuilder.create().texOffs(36, 0).addBox(-2.0F, -2.0F, -2.5F, 4.0F, 2.0F, 5.0F),
            PartPose.offsetAndRotation(0.7F, -4.0F, 0.1F, 0.0F, 0.0F, 0.18F)
        );
        root.addOrReplaceChild(
            "void_neck",
            CubeListBuilder.create().texOffs(42, 44).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 3.0F, 3.0F),
            PartPose.offsetAndRotation(-0.4F, 7.8F, 0.0F, 0.0F, 0.0F, -0.08F)
        );

        final PartDefinition shell = root.addOrReplaceChild(
            "torso_shell",
            CubeListBuilder.create(),
            PartPose.offsetAndRotation(0.0F, 10.0F, 0.2F, 0.04F, 0.0F, 0.0F)
        );
        shell.addOrReplaceChild(
            "left_torso_slab",
            CubeListBuilder.create().texOffs(26, 14).addBox(-4.5F, -0.5F, -2.5F, 5.0F, 10.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, -0.35F, 0.0F, 0.0F, -0.04F)
        );
        shell.addOrReplaceChild(
            "right_torso_slab",
            CubeListBuilder.create().texOffs(26, 14).addBox(-0.5F, -0.5F, -2.5F, 5.0F, 10.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, -0.35F, 0.0F, 0.0F, 0.05F)
        );
        shell.addOrReplaceChild(
            "left_broken_hem",
            CubeListBuilder.create().texOffs(44, 14).addBox(-4.0F, 0.0F, -2.0F, 4.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(-0.2F, 8.0F, -0.2F, 0.0F, 0.0F, 0.09F)
        );
        shell.addOrReplaceChild(
            "right_broken_hem",
            CubeListBuilder.create().texOffs(44, 14).addBox(0.0F, 0.0F, -2.0F, 4.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(0.4F, 7.4F, -0.15F, 0.0F, 0.0F, -0.14F)
        );

        final PartDefinition leftShoulder = root.addOrReplaceChild(
            "left_shoulder",
            CubeListBuilder.create().texOffs(0, 30).addBox(-1.0F, -2.0F, -2.5F, 5.0F, 4.0F, 5.0F),
            PartPose.offsetAndRotation(4.0F, 9.5F, 0.0F, -0.22F, -0.03F, -0.05F)
        );
        final PartDefinition leftUpper = leftShoulder.addOrReplaceChild(
            "left_upper_arm",
            CubeListBuilder.create().texOffs(22, 30).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F),
            PartPose.offsetAndRotation(1.2F, 2.0F, -0.1F, -0.08F, 0.0F, 0.03F)
        );
        leftUpper.addOrReplaceChild(
            "left_forearm",
            CubeListBuilder.create().texOffs(36, 30).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 5.5F, -0.2F, -0.08F, 0.0F, 0.02F)
        );

        final PartDefinition rightShoulder = root.addOrReplaceChild(
            "right_shoulder",
            CubeListBuilder.create().texOffs(0, 30).addBox(-4.0F, -2.0F, -2.5F, 5.0F, 4.0F, 5.0F),
            PartPose.offsetAndRotation(-4.0F, 9.5F, 0.0F, -0.22F, 0.03F, 0.05F)
        );
        final PartDefinition rightUpper = rightShoulder.addOrReplaceChild(
            "right_upper_arm",
            CubeListBuilder.create().texOffs(22, 30).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F),
            PartPose.offsetAndRotation(-1.2F, 2.0F, 0.1F, -0.08F, 0.0F, -0.03F)
        );
        rightUpper.addOrReplaceChild(
            "right_forearm",
            CubeListBuilder.create().texOffs(36, 30).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 5.5F, -0.2F, -0.08F, 0.0F, -0.02F)
        );

        final PartDefinition leftLeg = root.addOrReplaceChild(
            "left_leg",
            CubeListBuilder.create().texOffs(0, 44).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F),
            PartPose.offsetAndRotation(2.3F, 16.0F, 0.2F, 0.0F, 0.08F, 0.0F)
        );
        leftLeg.addOrReplaceChild(
            "left_foot",
            CubeListBuilder.create().texOffs(18, 44).addBox(-2.5F, 0.0F, -4.0F, 5.0F, 3.0F, 6.0F),
            PartPose.offsetAndRotation(0.0F, 5.0F, -0.4F, 0.0F, 0.1F, 0.0F)
        );
        final PartDefinition rightLeg = root.addOrReplaceChild(
            "right_leg",
            CubeListBuilder.create().texOffs(0, 44).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F),
            PartPose.offsetAndRotation(-2.3F, 16.0F, -0.1F, 0.0F, -0.12F, 0.0F)
        );
        rightLeg.addOrReplaceChild(
            "right_foot",
            CubeListBuilder.create().texOffs(18, 44).addBox(-2.5F, 0.0F, -4.0F, 5.0F, 3.0F, 6.0F),
            PartPose.offsetAndRotation(0.0F, 5.0F, 0.2F, 0.0F, -0.08F, 0.0F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        steppedMaskHead.yRot += state.yRot * Mth.DEG_TO_RAD;
        steppedMaskHead.xRot += state.xRot * Mth.DEG_TO_RAD * 0.7F;
        final float pace = state.walkAnimationPos * 0.58F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F);
        leftLeg.xRot += Mth.cos(pace) * stride * 0.72F;
        rightLeg.xRot += Mth.cos(pace + Mth.PI) * stride * 0.72F;
        leftShoulder.xRot += Mth.cos(pace + Mth.PI) * stride * 0.24F;
        rightShoulder.xRot += Mth.cos(pace) * stride * 0.19F;
        leftForearm.zRot += Mth.sin(pace) * stride * 0.05F;
        rightForearm.zRot -= Mth.sin(pace) * stride * 0.04F;
        leftBrokenHem.zRot += Mth.sin(state.ageInTicks * 0.07F) * 0.025F;
        rightBrokenHem.zRot -= Mth.sin(state.ageInTicks * 0.07F + 0.8F) * 0.025F;

        if (state.phase == Phase.ABSORB) {
            final float impact = Math.min(state.acceptedHits, 2) * 0.18F;
            torsoShell.yRot += impact;
            torsoShell.zRot -= impact * 0.45F;
            leftTorsoSlab.x -= impact * 1.8F;
            rightTorsoSlab.x += impact * 1.1F;
            crownFragment.zRot += impact * 0.65F;
        } else if (state.phase == Phase.UNMASK || state.phase == Phase.FADED) {
            leftTorsoSlab.x -= 2.3F;
            leftTorsoSlab.yRot -= 0.55F;
            leftTorsoSlab.zRot -= 0.18F;
            rightTorsoSlab.x += 2.5F;
            rightTorsoSlab.yRot += 0.62F;
            rightTorsoSlab.zRot += 0.14F;
            steppedMaskHead.y -= 0.8F;
            steppedMaskHead.yRot += 0.26F;
            leftFacePlate.x -= 0.65F;
            rightFacePlate.x += 0.8F;
            voidNeck.zScale = 0.55F;
            leftBrokenHem.zRot -= 0.18F;
            rightBrokenHem.zRot += 0.22F;
        }
    }

    public static void extractRenderState(
        final IllusionZombieEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.phase = entity.presentationPhase();
        state.acceptedHits = entity.presentationAcceptedHits();
    }

    public static final class State extends LivingEntityRenderState {
        public Phase phase = Phase.BLENDED;
        public int acceptedHits;
    }
}
