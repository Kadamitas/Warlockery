package com.kadamitas.warlockery.client.model;

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

public final class MandrakeModel extends EntityModel<MandrakeModel.State>
    implements ArmedModel<MandrakeModel.State> {
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 64;

    private final ModelPart head;
    private final ModelPart recessedMouth;
    private final ModelPart body;
    private final ModelPart crown;
    private final ModelPart leafNorth;
    private final ModelPart leafSouth;
    private final ModelPart leafWest;
    private final ModelPart leafEast;
    private final ModelPart leafHigh;
    private final ModelPart rightArm;
    private final ModelPart rightArmDistal;
    private final ModelPart leftArm;
    private final ModelPart leftArmDistal;
    private final ModelPart rightHindLeg;
    private final ModelPart rightRootDistal;
    private final ModelPart leftHindLeg;
    private final ModelPart leftRootDistal;

    public MandrakeModel(final ModelPart root) {
        super(root);
        head = root.getChild("head");
        recessedMouth = head.getChild("recessed_mouth");
        body = root.getChild("body");
        crown = root.getChild("crown");
        leafNorth = crown.getChild("mandrake_leaf_north");
        leafSouth = crown.getChild("mandrake_leaf_south");
        leafWest = crown.getChild("mandrake_leaf_west");
        leafEast = crown.getChild("mandrake_leaf_east");
        leafHigh = crown.getChild("mandrake_leaf_high");
        rightArm = root.getChild("right_arm");
        rightArmDistal = rightArm.getChild("right_arm_distal");
        leftArm = root.getChild("left_arm");
        leftArmDistal = leftArm.getChild("left_arm_distal");
        rightHindLeg = root.getChild("right_hind_leg");
        rightRootDistal = rightHindLeg.getChild("right_root_distal");
        leftHindLeg = root.getChild("left_hind_leg");
        leftRootDistal = leftHindLeg.getChild("left_root_distal");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition head = root.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -4.0F, -3.0F, 6.0F, 4.0F, 6.0F),
            PartPose.offset(0.0F, 11.0F, 0.0F)
        );
        head.addOrReplaceChild(
            "recessed_mouth",
            CubeListBuilder.create()
                .texOffs(0, 12).addBox(-2.0F, -1.0F, -0.5F, 4.0F, 3.0F, 1.0F)
                .texOffs(0, 12).addBox(-2.0F, 1.5F, -0.55F, 4.0F, 3.0F, 1.0F),
            PartPose.offset(0.0F, -1.5F, -2.85F)
        );
        root.addOrReplaceChild(
            "body",
            CubeListBuilder.create()
                .texOffs(24, 0).addBox(-4.0F, -1.0F, -2.5F, 8.0F, 9.0F, 5.0F)
                .texOffs(50, 0).addBox(-4.75F, 1.0F, -2.0F, 2.0F, 6.0F, 4.0F)
                .texOffs(50, 0).addBox(2.75F, 1.0F, -2.0F, 2.0F, 6.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 11.0F, 0.2F, 0.04F, 0.0F, 0.0F)
        );

        final PartDefinition crown = root.addOrReplaceChild(
            "crown",
            CubeListBuilder.create().texOffs(68, 0).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 3.0F, 4.0F),
            PartPose.offset(0.0F, 7.0F, 0.0F)
        );
        addLeafPlane(crown, "mandrake_leaf_north", 0.0F, -0.15F, -1.0F, -0.62F, 0.0F, 0.0F);
        addLeafPlane(crown, "mandrake_leaf_south", 0.0F, 0.0F, 1.0F, 0.55F, 3.14F, 0.0F);
        addLeafPlane(crown, "mandrake_leaf_west", -1.0F, 0.0F, 0.0F, 0.0F, -1.35F, -0.72F);
        addLeafPlane(crown, "mandrake_leaf_east", 1.0F, 0.2F, 0.0F, 0.0F, 1.25F, 0.58F);
        addLeafPlane(crown, "mandrake_leaf_high", 0.0F, -0.4F, 0.0F, 0.0F, 0.35F, 0.18F);
        addLeafPlane(crown, "mandrake_leaf_low_west", -1.2F, 0.5F, 0.6F, -0.18F, -0.8F, -1.0F);
        addLeafPlane(crown, "mandrake_leaf_low_east", 1.2F, 0.4F, -0.5F, 0.16F, 0.85F, 0.94F);
        addLeafPlane(crown, "mandrake_leaf_rear", 0.0F, 0.3F, 1.2F, 0.72F, 3.14F, -0.12F);

        addArm(root, "right_arm", "right_arm_distal", -4.2F, 13.0F, 0.58F, false);
        addArm(root, "left_arm", "left_arm_distal", 4.2F, 13.5F, -0.62F, true);
        addRoot(root, "right_hind_leg", "right_root_distal", -2.6F, 15.85F, 0.24F, false);
        addRoot(root, "left_hind_leg", "left_root_distal", 2.6F, 15.85F, -0.28F, true);
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private static void addLeafPlane(
        final PartDefinition crown,
        final String name,
        final float x,
        final float y,
        final float z,
        final float xRot,
        final float yRot,
        final float zRot
    ) {
        crown.addOrReplaceChild(
            name,
            CubeListBuilder.create().texOffs(84, 0).addBox(-1.5F, -8.0F, -0.5F, 3.0F, 8.0F, 1.0F),
            PartPose.offsetAndRotation(x, y, z, xRot, yRot, zRot)
        );
    }

    private static void addArm(
        final PartDefinition root,
        final String proximalName,
        final String distalName,
        final float x,
        final float y,
        final float zRot,
        final boolean mirror
    ) {
        final PartDefinition proximal = root.addOrReplaceChild(
            proximalName,
            CubeListBuilder.create().texOffs(96, 0).mirror(mirror)
                .addBox(mirror ? 0.0F : -2.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(x, y, 0.0F, 0.0F, 0.0F, zRot)
        );
        proximal.addOrReplaceChild(
            distalName,
            CubeListBuilder.create().texOffs(104, 0).mirror(mirror)
                .addBox(mirror ? 0.0F : -1.5F, 0.0F, -0.75F, 1.5F, 4.0F, 1.5F),
            PartPose.offsetAndRotation(mirror ? 1.5F : -1.5F, 4.0F, 0.0F, 0.0F, 0.0F, zRot * 0.45F)
        );
    }

    private static void addRoot(
        final PartDefinition root,
        final String proximalName,
        final String distalName,
        final float x,
        final float y,
        final float zRot,
        final boolean mirror
    ) {
        final PartDefinition proximal = root.addOrReplaceChild(
            proximalName,
            CubeListBuilder.create().texOffs(112, 0).mirror(mirror)
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 3.0F, 3.0F),
            PartPose.offsetAndRotation(x, y, 0.0F, 0.12F, 0.0F, zRot)
        );
        final PartDefinition distal = proximal.addOrReplaceChild(
            distalName,
            CubeListBuilder.create().texOffs(112, 10).mirror(mirror)
                .addBox(-1.25F, 0.0F, -2.0F, 2.5F, 3.0F, 4.0F),
            PartPose.offsetAndRotation(mirror ? 0.35F : -0.35F, 2.5F, -0.2F, 0.35F, 0.0F, -zRot * 0.5F)
        );
        distal.addOrReplaceChild(
            proximalName + "_inner_toe",
            CubeListBuilder.create().texOffs(104, 0).mirror(mirror)
                .addBox(-0.75F, 0.0F, -0.75F, 1.5F, 4.0F, 1.5F),
            PartPose.offsetAndRotation(mirror ? -0.8F : 0.8F, 2.0F, -1.0F, -1.18F, mirror ? -0.34F : 0.34F, 0.0F)
        );
        distal.addOrReplaceChild(
            proximalName + "_outer_toe",
            CubeListBuilder.create().texOffs(104, 0).mirror(mirror)
                .addBox(-0.75F, 0.0F, -0.75F, 1.5F, 4.0F, 1.5F),
            PartPose.offsetAndRotation(mirror ? 0.8F : -0.8F, 2.0F, -1.0F, -1.18F, mirror ? 0.34F : -0.34F, 0.0F)
        );
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        head.yRot += state.yRot * Mth.DEG_TO_RAD;
        head.xRot += state.xRot * Mth.DEG_TO_RAD;
        final float pace = state.walkAnimationPos * 0.6662F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F) * 1.35F;
        final float rightSwing = Mth.cos(pace) * stride;
        final float leftSwing = Mth.cos(pace + Mth.PI) * stride;
        rightArm.xRot += leftSwing;
        leftArm.xRot += rightSwing;
        rightHindLeg.xRot += rightSwing;
        leftHindLeg.xRot += leftSwing;
        body.zRot += Mth.sin(state.ageInTicks * 0.05F) * 0.025F;
        crown.zRot -= body.zRot * 0.75F;
    }

    @Override
    public void translateToHand(final State state, final HumanoidArm arm, final PoseStack poseStack) {
        (arm == HumanoidArm.LEFT ? leftArm : rightArm).translateAndRotate(poseStack);
    }

    public static final class State extends ArmedEntityRenderState {
    }
}
