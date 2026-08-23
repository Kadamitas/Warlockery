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

public final class DreamrootModel extends EntityModel<DreamrootModel.State>
    implements ArmedModel<DreamrootModel.State> {
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 64;

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart crown;
    private final ModelPart petalTierInner;
    private final ModelPart petalTierMiddle;
    private final ModelPart petalTierOuter;
    private final ModelPart rightHindLeg;
    private final ModelPart rightTasselDistal;
    private final ModelPart leftHindLeg;
    private final ModelPart leftTasselDistal;
    private final ModelPart rightArm;
    private final ModelPart rightOuterTassel;
    private final ModelPart leftArm;
    private final ModelPart leftOuterTassel;

    public DreamrootModel(final ModelPart root) {
        super(root);
        head = root.getChild("head");
        body = root.getChild("body");
        crown = root.getChild("crown");
        petalTierInner = crown.getChild("petal_tier_inner");
        petalTierMiddle = petalTierInner.getChild("petal_tier_middle");
        petalTierOuter = petalTierMiddle.getChild("petal_tier_outer");
        rightHindLeg = root.getChild("right_hind_leg");
        rightTasselDistal = rightHindLeg.getChild("right_tassel_distal");
        leftHindLeg = root.getChild("left_hind_leg");
        leftTasselDistal = leftHindLeg.getChild("left_tassel_distal");
        rightArm = root.getChild("right_arm");
        rightOuterTassel = rightArm.getChild("right_outer_tassel");
        leftArm = root.getChild("left_arm");
        leftOuterTassel = leftArm.getChild("left_outer_tassel");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -4.0F, -3.0F, 6.0F, 6.0F, 6.0F),
            PartPose.offset(0.0F, 8.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "body",
            CubeListBuilder.create()
                .texOffs(24, 0).addBox(-2.5F, -1.0F, -2.5F, 5.0F, 10.0F, 5.0F)
                .texOffs(44, 0).addBox(-3.5F, 5.0F, -3.0F, 7.0F, 6.0F, 6.0F),
            PartPose.offset(0.0F, 9.0F, 0.0F)
        );
        final PartDefinition bloom = root.addOrReplaceChild(
            "crown",
            CubeListBuilder.create()
                .texOffs(68, 48).addBox(-2.0F, -4.0F, -2.0F, 4.0F, 4.0F, 4.0F)
                .texOffs(68, 48).addBox(-2.0F, -7.0F, -2.0F, 4.0F, 4.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.08F, 0.0F, 0.0F)
        );
        bloom.addOrReplaceChild(
            "left_bloom_spire",
            CubeListBuilder.create().texOffs(84, 0).addBox(0.0F, -0.5F, -1.5F, 10.0F, 1.0F, 3.0F),
            PartPose.offsetAndRotation(-2.0F, -1.0F, 0.0F, 0.0F, -0.12F, -1.15F)
        );
        bloom.addOrReplaceChild(
            "right_bloom_spire",
            CubeListBuilder.create().texOffs(84, 0).addBox(-10.0F, -0.5F, -1.5F, 10.0F, 1.0F, 3.0F),
            PartPose.offsetAndRotation(2.0F, -1.0F, 0.0F, 0.0F, 0.12F, 1.15F)
        );
        bloom.addOrReplaceChild(
            "front_bloom_spire",
            CubeListBuilder.create().texOffs(84, 0).addBox(-1.5F, -0.5F, -10.0F, 3.0F, 1.0F, 10.0F),
            PartPose.offsetAndRotation(0.0F, -1.0F, -2.0F, -1.15F, 0.0F, 0.0F)
        );
        bloom.addOrReplaceChild(
            "rear_bloom_spire",
            CubeListBuilder.create().texOffs(84, 0).addBox(-1.5F, -0.5F, 0.0F, 3.0F, 1.0F, 10.0F),
            PartPose.offsetAndRotation(0.0F, -1.0F, 2.0F, 1.15F, 0.0F, 0.0F)
        );
        final PartDefinition inner = bloom.addOrReplaceChild(
            "petal_tier_inner",
            CubeListBuilder.create().texOffs(84, 0)
                .addBox(-5.0F, -0.5F, -1.5F, 10.0F, 1.0F, 3.0F)
                .addBox(-1.5F, -0.45F, -5.0F, 3.0F, 1.0F, 10.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.18F, 0.18F, 0.12F)
        );
        final PartDefinition middle = inner.addOrReplaceChild(
            "petal_tier_middle",
            CubeListBuilder.create().texOffs(84, 16)
                .addBox(-6.0F, -0.5F, -1.0F, 12.0F, 1.0F, 2.0F)
                .addBox(-1.0F, -0.45F, -6.0F, 2.0F, 1.0F, 12.0F),
            PartPose.offsetAndRotation(0.0F, -1.2F, 0.0F, 0.32F, -0.52F, -0.18F)
        );
        middle.addOrReplaceChild(
            "petal_tier_outer",
            CubeListBuilder.create().texOffs(84, 32)
                .addBox(-7.0F, -0.5F, -1.0F, 14.0F, 1.0F, 2.0F)
                .addBox(-1.0F, -0.45F, -7.0F, 2.0F, 1.0F, 14.0F),
            PartPose.offsetAndRotation(0.0F, 1.2F, 0.0F, -0.26F, 0.35F, 0.16F)
        );

        addTassel(root, "right_hind_leg", "right_tassel_distal", -3.8F, 11.65F, 0.28F, false);
        addTassel(root, "left_hind_leg", "left_tassel_distal", 3.8F, 11.65F, -0.32F, true);
        addTassel(root, "right_arm", "right_outer_tassel", -6.5F, 11.0F, 0.5F, false);
        addTassel(root, "left_arm", "left_outer_tassel", 6.5F, 11.5F, -0.56F, true);
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private static void addTassel(
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
            CubeListBuilder.create().texOffs(0, 24).mirror(mirror)
                .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 7.0F, 2.0F),
            PartPose.offsetAndRotation(x, y, 0.0F, 0.15F, 0.0F, zRot)
        );
        final PartDefinition distal = proximal.addOrReplaceChild(
            distalName,
            CubeListBuilder.create().texOffs(8, 24).mirror(mirror)
                .addBox(-0.75F, 0.0F, -0.75F, 1.5F, 4.0F, 1.5F),
            PartPose.offsetAndRotation(0.0F, 5.75F, 0.0F, -0.35F, 0.12F, zRot * 0.75F)
        );
        distal.addOrReplaceChild(
            distalName + "_tip",
            CubeListBuilder.create().texOffs(8, 24).mirror(mirror)
                .addBox(-0.75F, 0.0F, -0.75F, 1.5F, 4.0F, 1.5F),
            PartPose.offsetAndRotation(0.0F, 3.5F, 0.0F, -0.24F, -0.12F, -zRot * 0.42F)
        );
        if (proximalName.endsWith("hind_leg")) {
            distal.addOrReplaceChild(
                proximalName + "_root_fork",
                CubeListBuilder.create().texOffs(8, 24).mirror(mirror)
                    .addBox(-0.75F, 0.0F, -0.75F, 1.5F, 4.0F, 1.5F),
                PartPose.offsetAndRotation(0.0F, 3.0F, -0.5F, -1.18F, mirror ? -0.42F : 0.42F, mirror ? -0.18F : 0.18F)
            );
        }
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
        petalTierInner.yRot += Mth.sin(state.ageInTicks * 0.03F) * 0.035F;
        petalTierMiddle.yRot -= Mth.sin(state.ageInTicks * 0.027F + 0.7F) * 0.04F;
        petalTierOuter.zRot += Mth.sin(state.ageInTicks * 0.024F + 1.2F) * 0.03F;
    }

    @Override
    public void translateToHand(final State state, final HumanoidArm arm, final PoseStack poseStack) {
        (arm == HumanoidArm.LEFT ? leftArm : rightArm).translateAndRotate(poseStack);
    }

    public static final class State extends ArmedEntityRenderState {
    }
}
