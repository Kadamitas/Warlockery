package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.CorpseEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public final class CorpseModel extends EntityModel<CorpseModel.State> {
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart head;
    private final ModelPart droppedJaw;
    private final ModelPart reknitTorso;
    private final ModelPart stitchBridge;
    private final ModelPart dragArm;
    private final ModelPart braceArm;
    private final ModelPart stiffLeg;
    private final ModelPart foldedLeg;

    public CorpseModel(final ModelPart root) {
        super(root);
        head = root.getChild("head");
        droppedJaw = head.getChild("dropped_jaw");
        reknitTorso = root.getChild("reknit_torso");
        stitchBridge = reknitTorso.getChild("stitch_bridge");
        dragArm = root.getChild("drag_arm");
        braceArm = root.getChild("brace_arm");
        stiffLeg = root.getChild("stiff_leg");
        foldedLeg = root.getChild("folded_leg");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition head = root.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.0F, -5.0F, -3.0F, 6.0F, 5.0F, 6.0F),
            PartPose.offsetAndRotation(-0.8F, 7.0F, -0.8F, 0.18F, 0.12F, -0.14F)
        );
        head.addOrReplaceChild(
            "dropped_jaw",
            CubeListBuilder.create().texOffs(26, 0)
                .addBox(-2.5F, 0.0F, -2.75F, 5.0F, 2.0F, 5.0F),
            PartPose.offsetAndRotation(0.4F, -0.2F, -0.3F, 0.34F, 0.0F, 0.08F)
        );
        final PartDefinition torso = root.addOrReplaceChild(
            "reknit_torso",
            CubeListBuilder.create().texOffs(0, 16)
                .addBox(-3.5F, 0.0F, -2.5F, 7.0F, 10.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, 7.0F, 0.0F, 0.32F, 0.0F, 0.08F)
        );
        torso.addOrReplaceChild(
            "left_rib_cage",
            CubeListBuilder.create().texOffs(26, 16)
                .addBox(-4.0F, -1.0F, -2.75F, 4.0F, 8.0F, 5.5F),
            PartPose.offsetAndRotation(-0.3F, 1.5F, 0.0F, 0.0F, -0.08F, -0.12F)
        );
        torso.addOrReplaceChild(
            "right_reknit_slab",
            CubeListBuilder.create().texOffs(50, 16)
                .addBox(0.0F, -1.0F, -2.5F, 4.75F, 7.0F, 5.0F),
            PartPose.offsetAndRotation(0.2F, 2.5F, 0.0F, 0.0F, 0.12F, 0.16F)
        );
        torso.addOrReplaceChild(
            "stitch_bridge",
            CubeListBuilder.create().texOffs(74, 16)
                .addBox(-0.75F, -4.5F, -0.5F, 1.5F, 9.0F, 1.0F),
            PartPose.offsetAndRotation(0.6F, 4.5F, -2.8F, 0.0F, 0.0F, 0.32F)
        );
        torso.addOrReplaceChild(
            "grave_binding_bands",
            CubeListBuilder.create().texOffs(0, 16)
                .addBox(-3.5F, 0.0F, -2.5F, 7.0F, 10.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, 0.4F, 0.2F, 0.0F, 0.08F, -0.08F)
        );
        torso.addOrReplaceChild(
            "teal_cohesion_seams",
            CubeListBuilder.create().texOffs(74, 16)
                .addBox(-0.75F, -4.5F, -0.5F, 1.5F, 9.0F, 1.0F),
            PartPose.offsetAndRotation(-1.8F, 4.5F, -2.7F, 0.0F, 0.0F, -0.18F)
        );
        torso.addOrReplaceChild(
            "sternum_ballast",
            CubeListBuilder.create().texOffs(26, 16)
                .addBox(-4.0F, -1.0F, -2.75F, 4.0F, 8.0F, 5.5F),
            PartPose.offsetAndRotation(3.4F, 1.6F, 0.2F, 0.0F, 0.12F, 0.18F)
        );
        torso.addOrReplaceChild(
            "timber_splint",
            CubeListBuilder.create().texOffs(74, 16)
                .addBox(-0.75F, -4.5F, -0.5F, 1.5F, 9.0F, 1.0F),
            PartPose.offsetAndRotation(2.5F, 5.0F, 2.6F, -0.08F, 0.0F, 0.12F)
        );
        final PartDefinition drag = root.addOrReplaceChild(
            "drag_arm",
            CubeListBuilder.create().texOffs(18, 34)
                .addBox(-1.75F, 0.0F, -1.75F, 3.5F, 9.0F, 3.5F),
            PartPose.offsetAndRotation(-4.4F, 7.5F, -0.5F, -0.22F, 0.0F, 0.12F)
        );
        drag.addOrReplaceChild(
            "drag_forearm",
            CubeListBuilder.create().texOffs(18, 34)
                .addBox(-1.75F, 0.0F, -1.75F, 3.5F, 9.0F, 3.5F),
            PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, -0.18F, 0.0F, 0.06F)
        );
        final PartDefinition brace = root.addOrReplaceChild(
            "brace_arm",
            CubeListBuilder.create().texOffs(18, 34)
                .addBox(-1.75F, 0.0F, -1.75F, 3.5F, 9.0F, 3.5F),
            PartPose.offsetAndRotation(4.2F, 8.5F, 0.5F, 0.24F, 0.0F, -0.12F)
        );
        brace.addOrReplaceChild(
            "brace_forearm",
            CubeListBuilder.create().texOffs(18, 34)
                .addBox(-1.75F, 0.0F, -1.75F, 3.5F, 9.0F, 3.5F),
            PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, 0.18F, 0.0F, -0.06F)
        );
        final PartDefinition stiff = root.addOrReplaceChild(
            "stiff_leg",
            CubeListBuilder.create().texOffs(36, 34)
                .addBox(-1.75F, 0.0F, -2.0F, 3.5F, 8.0F, 4.0F),
            PartPose.offsetAndRotation(-1.9F, 16.0F, -0.4F, -0.08F, 0.0F, 0.12F)
        );
        stiff.addOrReplaceChild(
            "stiff_shin",
            CubeListBuilder.create().texOffs(36, 34)
                .addBox(-1.75F, 0.0F, -2.0F, 3.5F, 8.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.24F, 0.0F, -0.08F)
        );
        final PartDefinition folded = root.addOrReplaceChild(
            "folded_leg",
            CubeListBuilder.create().texOffs(56, 34)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F),
            PartPose.offsetAndRotation(2.2F, 16.8F, 0.8F, 0.48F, 0.0F, -0.18F)
        );
        folded.addOrReplaceChild(
            "folded_shin",
            CubeListBuilder.create().texOffs(56, 34)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 4.5F, 2.0F, -0.86F, 0.0F, 0.12F)
        );
        root.addOrReplaceChild(
            "loose_binding",
            CubeListBuilder.create().texOffs(76, 34)
                .addBox(-0.5F, -4.0F, -0.5F, 1.0F, 8.0F, 1.0F),
            PartPose.offsetAndRotation(-4.3F, 14.0F, 2.2F, 0.22F, 0.0F, 0.36F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    public static void extractRenderState(
        final CorpseEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.dormant = entity.isDormant();
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        if (state.dormant) {
            reknitTorso.xRot = 1.1F;
            reknitTorso.zRot = 0.28F;
            head.xRot = 0.82F;
            head.zRot = -0.38F;
            droppedJaw.xRot = 0.62F;
            dragArm.xRot = -0.35F;
            dragArm.zRot = 1.05F;
            braceArm.xRot = -1.1F;
            stiffLeg.xRot = -1.15F;
            foldedLeg.xRot = 1.38F;
            foldedLeg.zRot = -0.48F;
            return;
        }
        head.yRot += state.yRot * Mth.DEG_TO_RAD;
        head.xRot += state.xRot * Mth.DEG_TO_RAD;
        final float pace = state.walkAnimationPos * 0.58F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F);
        stiffLeg.xRot += Mth.cos(pace) * 0.8F * stride;
        foldedLeg.xRot += Mth.cos(pace + Mth.PI) * 0.55F * stride;
        dragArm.xRot += Mth.cos(pace + Mth.PI) * 0.35F * stride;
        braceArm.xRot += Mth.cos(pace) * 0.65F * stride;
        droppedJaw.xRot += Mth.sin(state.ageInTicks * 0.09F) * 0.08F;
        stitchBridge.zRot += Mth.sin(state.ageInTicks * 0.12F) * 0.04F;
    }

    public static final class State extends LivingEntityRenderState {
        public boolean dormant;
    }
}
