package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.CircleMageEntity;
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

public final class CircleMageModel extends EntityModel<CircleMageModel.State> {
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart circleFocus;
    private final ModelPart scriptPanel;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public CircleMageModel(final ModelPart root) {
        super(root);
        head = root.getChild("head");
        body = root.getChild("body");
        circleFocus = body.getChild("circle_focus");
        scriptPanel = body.getChild("script_panel");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition head = root.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -6.0F, -3.0F, 7.0F, 6.0F, 6.0F),
            PartPose.offset(0.0F, 7.0F, -0.4F)
        );
        head.addOrReplaceChild(
            "study_visor",
            CubeListBuilder.create().texOffs(28, 0).addBox(-4.0F, -1.5F, -0.75F, 8.0F, 3.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, -3.2F, -3.0F, -0.08F, 0.0F, 0.0F)
        );
        head.addOrReplaceChild(
            "temple_prism",
            CubeListBuilder.create().texOffs(48, 0).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 2.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, -5.7F, 0.0F, 0.0F, 0.35F, 0.0F)
        );

        final PartDefinition body = root.addOrReplaceChild(
            "body",
            CubeListBuilder.create().texOffs(0, 16).addBox(-3.0F, 0.0F, -2.25F, 6.0F, 10.0F, 4.5F),
            PartPose.offset(0.0F, 7.0F, 0.0F)
        );
        final PartDefinition mantle = body.addOrReplaceChild(
            "layered_mantle",
            CubeListBuilder.create().texOffs(24, 16)
                .addBox(-5.0F, -0.5F, -2.75F, 10.0F, 4.0F, 5.5F, new CubeDeformation(0.15F)),
            PartPose.offsetAndRotation(0.0F, 0.5F, 0.0F, 0.0F, 0.0F, -0.03F)
        );
        mantle.addOrReplaceChild(
            "right_broken_ring_shard",
            CubeListBuilder.create().texOffs(48, 0)
                .addBox(-1.5F, -1.0F, -1.5F, 3.0F, 2.0F, 3.0F),
            PartPose.offsetAndRotation(-5.2F, -0.8F, 1.6F, 0.18F, 0.42F, -0.34F)
        );
        mantle.addOrReplaceChild(
            "left_broken_ring_shard",
            CubeListBuilder.create().texOffs(48, 0).mirror()
                .addBox(-1.5F, -1.0F, -1.5F, 3.0F, 2.0F, 3.0F),
            PartPose.offsetAndRotation(5.2F, -0.8F, 1.6F, 0.18F, -0.42F, 0.34F)
        );
        mantle.addOrReplaceChild(
            "rear_broken_ring_shard",
            CubeListBuilder.create().texOffs(48, 0)
                .addBox(-1.5F, -1.0F, -1.5F, 3.0F, 2.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, -1.2F, 3.2F, 0.48F, 0.0F, 0.22F)
        );
        final PartDefinition focus = body.addOrReplaceChild(
            "circle_focus",
            CubeListBuilder.create().texOffs(56, 16)
                .addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F)
                .texOffs(56, 23).addBox(-0.75F, -0.75F, -1.0F, 1.5F, 1.5F, 2.0F),
            PartPose.offsetAndRotation(-1.8F, 5.0F, -3.2F, -0.12F, 0.28F, 0.16F)
        );
        focus.addOrReplaceChild(
            "separate_focus_ring",
            CubeListBuilder.create().texOffs(56, 16)
                .addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F),
            PartPose.offsetAndRotation(1.0F, 0.0F, -0.4F, 0.0F, 0.52F, 0.0F)
        );
        focus.addOrReplaceChild(
            "focus_core",
            CubeListBuilder.create().texOffs(56, 23)
                .addBox(-0.75F, -0.75F, -1.0F, 1.5F, 1.5F, 2.0F),
            PartPose.offset(0.0F, 0.0F, -1.0F)
        );
        final PartDefinition slate = body.addOrReplaceChild(
            "script_panel",
            CubeListBuilder.create().texOffs(72, 16).addBox(-2.5F, -3.5F, -0.4F, 5.0F, 7.0F, 1.0F),
            PartPose.offsetAndRotation(2.0F, 5.2F, -3.0F, -0.08F, -0.24F, -0.12F)
        );
        slate.addOrReplaceChild(
            "right_folding_slate_leaf",
            CubeListBuilder.create().texOffs(72, 16)
                .addBox(-2.5F, -3.5F, -0.4F, 5.0F, 7.0F, 1.0F),
            PartPose.offsetAndRotation(-2.2F, 0.0F, 0.0F, 0.0F, 0.48F, -0.08F)
        );
        slate.addOrReplaceChild(
            "left_folding_slate_leaf",
            CubeListBuilder.create().texOffs(72, 16).mirror()
                .addBox(-2.5F, -3.5F, -0.4F, 5.0F, 7.0F, 1.0F),
            PartPose.offsetAndRotation(2.2F, 0.0F, 0.0F, 0.0F, -0.48F, 0.08F)
        );
        body.addOrReplaceChild(
            "study_sash",
            CubeListBuilder.create().texOffs(88, 16).addBox(-3.5F, -1.0F, -2.5F, 7.0F, 2.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, 0.0F, 0.0F, -0.14F)
        );
        final PartDefinition tunic = body.addOrReplaceChild(
            "split_knee_tunic",
            CubeListBuilder.create().texOffs(88, 16)
                .addBox(-3.5F, -1.0F, -2.5F, 7.0F, 2.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, 8.2F, 0.4F, 0.08F, 0.0F, 0.0F)
        );
        tunic.addOrReplaceChild(
            "right_tunic_panel",
            CubeListBuilder.create().texOffs(32, 36)
                .addBox(-1.75F, 0.0F, -2.0F, 3.5F, 8.0F, 4.0F),
            PartPose.offsetAndRotation(-1.8F, 0.4F, 0.4F, 0.12F, 0.0F, 0.08F)
        );
        tunic.addOrReplaceChild(
            "left_tunic_panel",
            CubeListBuilder.create().texOffs(32, 36).mirror()
                .addBox(-1.75F, 0.0F, -2.0F, 3.5F, 8.0F, 4.0F),
            PartPose.offsetAndRotation(1.8F, 0.4F, 0.8F, 0.12F, 0.0F, -0.08F)
        );

        addArm(root, "right_arm", "right_cuff", -4.8F, false);
        addArm(root, "left_arm", "left_cuff", 4.8F, true);
        addLeg(root, "right_leg", -1.75F, false);
        addLeg(root, "left_leg", 1.75F, true);
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private static void addArm(
        final PartDefinition root,
        final String name,
        final String cuffName,
        final float x,
        final boolean mirror
    ) {
        final PartDefinition arm = root.addOrReplaceChild(
            name,
            CubeListBuilder.create().texOffs(0, 36).mirror(mirror)
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 9.0F, 3.0F),
            PartPose.offsetAndRotation(x, 8.0F, mirror ? -0.6F : -1.2F,
                -0.18F, mirror ? -0.14F : 0.14F, mirror ? -0.14F : 0.14F)
        );
        arm.addOrReplaceChild(
            cuffName,
            CubeListBuilder.create().texOffs(16, 36).mirror(mirror)
                .addBox(-2.0F, -1.5F, -2.0F, 4.0F, 3.0F, 4.0F),
            PartPose.offset(0.0F, 8.0F, 0.0F)
        );
        final String side = mirror ? "left" : "right";
        arm.addOrReplaceChild(
            side + "_forearm",
            CubeListBuilder.create().texOffs(0, 36).mirror(mirror)
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 9.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, 6.0F, -0.8F, -0.55F, 0.0F, mirror ? -0.18F : 0.18F)
        );
        arm.addOrReplaceChild(
            side + "_hand",
            CubeListBuilder.create().texOffs(16, 36).mirror(mirror)
                .addBox(-2.0F, -1.5F, -2.0F, 4.0F, 3.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 10.0F, -1.2F, -0.42F, 0.0F, 0.0F)
        );
    }

    private static void addLeg(
        final PartDefinition root,
        final String name,
        final float x,
        final boolean mirror
    ) {
        final PartDefinition leg = root.addOrReplaceChild(
            name,
            CubeListBuilder.create().texOffs(32, 36).mirror(mirror)
                .addBox(-1.75F, 0.0F, -2.0F, 3.5F, 8.0F, 4.0F),
            PartPose.offset(x, 16.0F, 0.0F)
        );
        leg.addOrReplaceChild(
            (mirror ? "left" : "right") + "_study_boot",
            CubeListBuilder.create().texOffs(16, 36).mirror(mirror)
                .addBox(-2.0F, -1.5F, -2.0F, 4.0F, 3.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 6.5F, -0.8F, 0.08F, 0.0F, 0.0F)
        );
    }

    public static void extractRenderState(
        final CircleMageEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.activity = Activity.valueOf(entity.presentationActivity().name());
        state.focusPrepared = entity.presentationFocusPrepared();
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;
        final float pace = state.walkAnimationPos * 0.6662F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F);
        rightLeg.xRot = Mth.cos(pace) * 0.9F * stride;
        leftLeg.xRot = Mth.cos(pace + Mth.PI) * 0.9F * stride;
        rightArm.xRot = Mth.cos(pace + Mth.PI) * 0.45F * stride;
        leftArm.xRot = Mth.cos(pace) * 0.45F * stride;
        circleFocus.visible = state.focusPrepared;
        scriptPanel.yRot = Mth.sin(state.ageInTicks * 0.06F) * 0.08F;
        if (state.activity == Activity.STUDYING) {
            head.xRot += 0.38F;
            rightArm.xRot = -0.75F;
            rightArm.yRot = -0.28F;
            leftArm.xRot = -0.85F;
            leftArm.yRot = 0.34F;
            circleFocus.yRot = state.ageInTicks * 0.035F;
        } else if (state.activity == Activity.DEFENDING) {
            head.xRot -= 0.12F;
            rightArm.xRot = -1.25F;
            rightArm.yRot = -0.35F;
            leftArm.xRot = -0.9F;
            leftArm.yRot = 0.65F;
            circleFocus.zRot = Mth.sin(state.ageInTicks * 0.25F) * 0.18F;
        } else if (state.activity == Activity.WITHDRAWING) {
            body.xRot = 0.18F;
            rightArm.zRot = 0.3F;
            leftArm.zRot = -0.3F;
        }
    }

    public enum Activity {
        IDLE,
        FOLLOWING,
        DEFENDING,
        STUDYING,
        WITHDRAWING
    }

    public static final class State extends LivingEntityRenderState {
        public Activity activity = Activity.IDLE;
        public boolean focusPrepared;
    }
}
