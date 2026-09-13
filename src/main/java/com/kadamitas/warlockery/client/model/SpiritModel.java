package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.SpiritEntity;
import com.kadamitas.warlockery.entity.SpiritRules.Phase;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public final class SpiritModel extends EntityModel<SpiritModel.State> {
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart spirit;
    private final ModelPart head;
    private final ModelPart torso;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart lowerBody;
    private final ModelPart trailingWisp;
    private final ModelPart wispTip;

    public SpiritModel(final ModelPart root) {
        super(root);
        spirit = root.getChild("spirit");
        head = spirit.getChild("head");
        torso = spirit.getChild("torso");
        rightArm = spirit.getChild("right_arm");
        leftArm = spirit.getChild("left_arm");
        lowerBody = spirit.getChild("lower_body");
        trailingWisp = lowerBody.getChild("trailing_wisp");
        wispTip = trailingWisp.getChild("wisp_tip");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition spirit = root.addOrReplaceChild(
            "spirit",
            CubeListBuilder.create(),
            PartPose.offset(0.0F, 3.0F, 0.0F)
        );
        final PartDefinition head = spirit.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -6.0F, -2.5F, 6.0F, 6.0F, 5.0F),
            PartPose.offset(0.0F, -1.0F, 0.0F)
        );
        head.addOrReplaceChild(
            "face_glow",
            CubeListBuilder.create().texOffs(24, 0).addBox(-2.0F, -2.0F, -0.4F, 4.0F, 4.0F, 0.8F),
            PartPose.offset(0.0F, -3.0F, -2.7F)
        );
        head.addOrReplaceChild(
            "right_spectral_eye",
            CubeListBuilder.create().texOffs(58, 0).addBox(-0.5F, -0.5F, -0.25F, 1.0F, 1.0F, 0.5F),
            PartPose.offset(-1.15F, -3.45F, -3.2F)
        );
        head.addOrReplaceChild(
            "left_spectral_eye",
            CubeListBuilder.create().texOffs(58, 0).mirror().addBox(-0.5F, -0.5F, -0.25F, 1.0F, 1.0F, 0.5F),
            PartPose.offset(1.15F, -3.45F, -3.2F)
        );
        head.addOrReplaceChild(
            "quiet_mouth",
            CubeListBuilder.create().texOffs(64, 0).addBox(-0.5F, -0.5F, -0.25F, 1.0F, 1.0F, 0.5F),
            PartPose.offset(0.0F, -1.9F, -3.2F)
        );
        head.addOrReplaceChild(
            "veil",
            CubeListBuilder.create().texOffs(36, 0).addBox(-3.5F, -1.0F, -1.0F, 7.0F, 7.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, -1.0F, 2.0F, 0.10F, 0.0F, 0.0F)
        );

        spirit.addOrReplaceChild(
            "torso",
            CubeListBuilder.create().texOffs(0, 16).addBox(-3.0F, -3.0F, -2.0F, 6.0F, 9.0F, 4.0F),
            PartPose.offset(0.0F, 2.0F, 0.0F)
        );
        final PartDefinition rightArm = spirit.addOrReplaceChild(
            "right_arm",
            CubeListBuilder.create().texOffs(22, 16).addBox(-1.5F, -1.0F, -1.0F, 2.0F, 9.0F, 2.0F),
            PartPose.offsetAndRotation(-3.0F, 0.0F, 0.0F, -0.12F, 0.0F, 0.12F)
        );
        rightArm.addOrReplaceChild(
            "right_hand",
            CubeListBuilder.create().texOffs(32, 16).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F),
            PartPose.offset(-1.0F, 8.0F, 0.0F)
        );
        final PartDefinition leftArm = spirit.addOrReplaceChild(
            "left_arm",
            CubeListBuilder.create().texOffs(22, 16).mirror().addBox(-0.5F, -1.0F, -1.0F, 2.0F, 9.0F, 2.0F),
            PartPose.offsetAndRotation(3.0F, 0.0F, 0.0F, -0.12F, 0.0F, -0.12F)
        );
        leftArm.addOrReplaceChild(
            "left_hand",
            CubeListBuilder.create().texOffs(32, 16).mirror().addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F),
            PartPose.offset(1.0F, 8.0F, 0.0F)
        );

        final PartDefinition lower = spirit.addOrReplaceChild(
            "lower_body",
            CubeListBuilder.create().texOffs(0, 34).addBox(-3.0F, 0.0F, -2.0F, 6.0F, 6.0F, 4.0F),
            PartPose.offset(0.0F, 8.0F, 0.0F)
        );
        final PartDefinition trailingWisp = lower.addOrReplaceChild(
            "trailing_wisp",
            CubeListBuilder.create().texOffs(22, 34).addBox(-2.0F, 0.0F, -1.5F, 4.0F, 7.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, 5.2F, 0.5F, 0.65F, 0.0F, 0.0F)
        );
        trailingWisp.addOrReplaceChild(
            "wisp_tip",
            CubeListBuilder.create().texOffs(38, 34).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 6.0F, 0.4F, 0.55F, 0.0F, 0.0F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;
        final float hover = Mth.sin(state.ageInTicks * 0.09F);
        spirit.y += hover * 0.32F;
        torso.yRot += Mth.sin(state.ageInTicks * 0.045F) * 0.035F;
        lowerBody.xRot += hover * 0.045F;
        trailingWisp.xRot += Mth.sin(state.ageInTicks * 0.10F + 0.6F) * 0.13F;
        trailingWisp.yRot += hover * 0.07F;
        wispTip.xRot += Mth.sin(state.ageInTicks * 0.13F + 1.2F) * 0.20F;
        wispTip.yRot += Mth.sin(state.ageInTicks * 0.08F) * 0.10F;

        final float flight = Math.min(state.walkAnimationSpeed, 1.0F);
        final float beat = state.walkAnimationPos * 0.55F;
        rightArm.xRot += Mth.cos(beat + Mth.PI) * flight * 0.32F;
        leftArm.xRot += Mth.cos(beat) * flight * 0.32F;
        trailingWisp.xRot += flight * 0.20F;
        trailingWisp.yRot += Mth.cos(beat) * flight * 0.12F;

        if (state.shielding) {
            rightArm.xRot -= 1.25F;
            leftArm.xRot -= 1.25F;
            rightArm.yRot -= 0.35F;
            leftArm.yRot += 0.35F;
            rightArm.zRot -= 0.35F;
            leftArm.zRot += 0.35F;
            lowerBody.xRot -= 0.12F;
        }
    }

    public static void extractRenderState(final SpiritEntity entity, final State state, final float partialTicks) {
        state.guardianPhase = entity.presentationPhase();
        state.shielding = state.guardianPhase == Phase.WARN || state.guardianPhase == Phase.DEFEND;
    }

    public static final class State extends LivingEntityRenderState {
        public Phase guardianPhase = Phase.WANDER;
        public boolean shielding;
    }
}
