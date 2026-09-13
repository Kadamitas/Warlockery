package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.NaamahCourtRules.Action;
import com.kadamitas.warlockery.entity.NaamahCourtRules.Phase;
import com.kadamitas.warlockery.entity.NaamahEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public final class NaamahModel extends EntityModel<NaamahModel.State> {
    public static final int TEXTURE_WIDTH = 192;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart demonQueen;
    private final ModelPart head;
    private final ModelPart rightHorn;
    private final ModelPart leftHorn;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightSkirt;
    private final ModelPart leftSkirt;
    private final ModelPart hairMantle;
    private final ModelPart tail;
    private final ModelPart tailTip;
    private final ModelPart throatRuby;
    private final ModelPart rightEye;
    private final ModelPart leftEye;

    public NaamahModel(final ModelPart root) {
        super(root);
        demonQueen = root.getChild("demon_queen");
        final ModelPart torso = demonQueen.getChild("torso");
        head = torso.getChild("head");
        rightHorn = head.getChild("right_horn");
        leftHorn = head.getChild("left_horn");
        rightArm = torso.getChild("right_arm");
        leftArm = torso.getChild("left_arm");
        hairMantle = torso.getChild("hair_mantle");
        throatRuby = torso.getChild("throat_ruby");
        final ModelPart face = head.getChild("face");
        rightEye = face.getChild("right_eye");
        leftEye = face.getChild("left_eye");
        final ModelPart gown = demonQueen.getChild("gown");
        rightSkirt = gown.getChild("right_skirt");
        leftSkirt = gown.getChild("left_skirt");
        tail = demonQueen.getChild("demon_tail");
        tailTip = tail.getChild("tail_tip");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition queen = root.addOrReplaceChild(
            "demon_queen",
            CubeListBuilder.create(),
            PartPose.offset(0.0F, 6.0F, 0.0F)
        );
        final PartDefinition torso = queen.addOrReplaceChild(
            "torso",
            CubeListBuilder.create()
                .texOffs(0, 18).addBox(-3.5F, -5.0F, -2.0F, 7.0F, 10.0F, 4.0F)
                .texOffs(24, 18).addBox(-4.0F, -4.5F, -2.5F, 8.0F, 2.0F, 5.0F),
            PartPose.ZERO
        );
        torso.addOrReplaceChild(
            "throat_ruby",
            CubeListBuilder.create().texOffs(52, 18).addBox(-0.75F, -0.75F, -0.5F, 1.5F, 1.5F, 1.0F),
            PartPose.offsetAndRotation(0.0F, -3.6F, -2.4F, 0.0F, 0.0F, 0.7853982F)
        );

        final PartDefinition head = torso.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -7.0F, -3.0F, 7.0F, 7.0F, 6.0F),
            PartPose.offset(0.0F, -5.0F, 0.0F)
        );
        final PartDefinition face = head.addOrReplaceChild(
            "face",
            CubeListBuilder.create().texOffs(84, 0).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F),
            PartPose.offset(0.0F, -3.1F, -3.1F)
        );
        face.addOrReplaceChild(
            "right_eye",
            CubeListBuilder.create().texOffs(98, 0).addBox(-0.5F, -0.5F, -0.4F, 1.0F, 1.0F, 0.8F),
            PartPose.offset(-1.25F, -0.7F, -0.7F)
        );
        face.addOrReplaceChild(
            "left_eye",
            CubeListBuilder.create().texOffs(102, 0).mirror().addBox(-0.5F, -0.5F, -0.4F, 1.0F, 1.0F, 0.8F),
            PartPose.offset(1.25F, -0.7F, -0.7F)
        );
        face.addOrReplaceChild(
            "right_brow",
            CubeListBuilder.create().texOffs(108, 0).addBox(-1.0F, -0.5F, -0.25F, 2.0F, 1.0F, 0.5F),
            PartPose.offsetAndRotation(-1.25F, -1.55F, -0.7F, 0.0F, 0.0F, -0.20F)
        );
        face.addOrReplaceChild(
            "left_brow",
            CubeListBuilder.create().texOffs(108, 0).mirror().addBox(-1.0F, -0.5F, -0.25F, 2.0F, 1.0F, 0.5F),
            PartPose.offsetAndRotation(1.25F, -1.55F, -0.7F, 0.0F, 0.0F, 0.20F)
        );
        face.addOrReplaceChild(
            "nose_ridge",
            CubeListBuilder.create().texOffs(116, 0).addBox(-0.5F, -1.0F, -0.25F, 1.0F, 2.0F, 0.5F),
            PartPose.offset(0.0F, 0.25F, -0.72F)
        );
        face.addOrReplaceChild(
            "severe_mouth",
            CubeListBuilder.create().texOffs(122, 0).addBox(-1.0F, -0.5F, -0.25F, 2.0F, 1.0F, 0.5F),
            PartPose.offset(0.0F, 1.5F, -0.72F)
        );
        head.addOrReplaceChild(
            "hair_crown",
            CubeListBuilder.create().texOffs(28, 0).addBox(-4.0F, -7.5F, -3.3F, 8.0F, 4.0F, 7.0F),
            PartPose.ZERO
        );

        addHorn(head, "right_horn", -3.1F, false);
        addHorn(head, "left_horn", 3.1F, true);

        final PartDefinition rightArm = torso.addOrReplaceChild(
            "right_arm",
            CubeListBuilder.create().texOffs(0, 36).addBox(-2.5F, -1.0F, -1.5F, 3.0F, 11.0F, 3.0F),
            PartPose.offsetAndRotation(-3.8F, -3.5F, 0.0F, 0.0F, 0.0F, 0.08F)
        );
        rightArm.addOrReplaceChild(
            "right_cuff",
            CubeListBuilder.create().texOffs(14, 36).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 3.0F, 4.0F),
            PartPose.offset(-1.0F, 8.8F, 0.0F)
        );
        final PartDefinition leftArm = torso.addOrReplaceChild(
            "left_arm",
            CubeListBuilder.create().texOffs(0, 36).mirror().addBox(-0.5F, -1.0F, -1.5F, 3.0F, 11.0F, 3.0F),
            PartPose.offsetAndRotation(3.8F, -3.5F, 0.0F, 0.0F, 0.0F, -0.08F)
        );
        leftArm.addOrReplaceChild(
            "left_cuff",
            CubeListBuilder.create().texOffs(14, 36).mirror().addBox(-2.0F, -1.0F, -2.0F, 4.0F, 3.0F, 4.0F),
            PartPose.offset(1.0F, 8.8F, 0.0F)
        );

        torso.addOrReplaceChild(
            "hair_mantle",
            CubeListBuilder.create().texOffs(0, 75).addBox(-4.0F, 0.0F, -1.0F, 8.0F, 14.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, -7.5F, 2.8F, 0.06F, 0.0F, 0.0F)
        );

        final PartDefinition gown = queen.addOrReplaceChild(
            "gown",
            CubeListBuilder.create().texOffs(32, 36).addBox(-3.5F, 0.0F, -2.5F, 7.0F, 5.0F, 5.0F),
            PartPose.offset(0.0F, 5.0F, 0.0F)
        );
        gown.addOrReplaceChild(
            "right_skirt",
            CubeListBuilder.create().texOffs(0, 55).addBox(-3.7F, 0.0F, -2.5F, 4.0F, 10.0F, 5.0F),
            PartPose.offsetAndRotation(-0.1F, 4.0F, 0.0F, 0.04F, 0.0F, 0.03F)
        );
        gown.addOrReplaceChild(
            "left_skirt",
            CubeListBuilder.create().texOffs(0, 55).mirror().addBox(-0.3F, 0.0F, -2.5F, 4.0F, 10.0F, 5.0F),
            PartPose.offsetAndRotation(0.1F, 4.0F, 0.0F, -0.04F, 0.0F, -0.03F)
        );
        gown.addOrReplaceChild(
            "royal_front_panel",
            CubeListBuilder.create().texOffs(30, 55).addBox(-1.5F, 0.0F, -0.5F, 3.0F, 10.0F, 1.0F),
            PartPose.offset(0.0F, 4.0F, -2.5F)
        );

        final PartDefinition tail = queen.addOrReplaceChild(
            "demon_tail",
            CubeListBuilder.create().texOffs(58, 55).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 9.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, 7.0F, 2.2F, 0.72F, 0.0F, 0.0F)
        );
        final PartDefinition tailTip = tail.addOrReplaceChild(
            "tail_tip",
            CubeListBuilder.create().texOffs(72, 55).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 7.0F, 0.0F, 0.45F, 0.0F, 0.0F)
        );
        tailTip.addOrReplaceChild(
            "spade",
            CubeListBuilder.create().texOffs(82, 55).addBox(-2.0F, 0.0F, -0.5F, 4.0F, 4.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, 7.0F, 0.0F, 0.0F, 0.0F, 0.7853982F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private static void addHorn(
        final PartDefinition head,
        final String name,
        final float x,
        final boolean mirror
    ) {
        final float direction = mirror ? 1.0F : -1.0F;
        final PartDefinition horn = head.addOrReplaceChild(
            name,
            CubeListBuilder.create().texOffs(60, 0).mirror(mirror)
                .addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(x, -5.8F, 0.2F, -0.20F, 0.0F, direction * 0.62F)
        );
        final PartDefinition middle = horn.addOrReplaceChild(
            "middle",
            CubeListBuilder.create().texOffs(70, 0).mirror(mirror)
                .addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, -4.5F, 0.0F, 0.05F, 0.0F, direction * 0.48F)
        );
        middle.addOrReplaceChild(
            "tip",
            CubeListBuilder.create().texOffs(80, 0).mirror(mirror)
                .addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, -4.5F, 0.0F, 0.08F, 0.0F, direction * 0.36F)
        );
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        head.yRot = state.yRot * Mth.DEG_TO_RAD * 0.82F;
        head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.88F;
        final float time = state.ageInTicks;
        final float poise = Mth.sin(time * 0.07F);
        demonQueen.y += poise * 0.10F;
        hairMantle.xRot += poise * 0.035F;
        tail.yRot += Mth.sin(time * 0.09F) * 0.18F;
        tailTip.yRot += Mth.sin(time * 0.11F + 0.8F) * 0.24F;
        rightHorn.zRot -= poise * 0.012F;
        leftHorn.zRot += poise * 0.012F;

        final float pace = state.walkAnimationPos * 0.58F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F) * 0.34F;
        rightArm.xRot += Mth.cos(pace + Mth.PI) * stride * 0.45F;
        leftArm.xRot += Mth.cos(pace) * stride * 0.45F;
        rightSkirt.xRot += Mth.cos(pace + Mth.PI) * stride * 0.24F;
        leftSkirt.xRot += Mth.cos(pace) * stride * 0.24F;

        final float wave = Mth.clamp(state.courtWaveProgress, 0.0F, 1.0F);
        rightArm.xRot -= wave * 1.1F;
        leftArm.xRot -= wave * 1.1F;
        rightArm.zRot -= wave * 0.82F;
        leftArm.zRot += wave * 0.82F;

        final float surge = Mth.clamp(state.drowningSurgeProgress, 0.0F, 1.0F);
        if (surge > 0.0F) {
            final float strike = 0.55F + 0.45F * Mth.sin(time * 0.45F);
            rightArm.xRot -= surge * (1.25F + strike * 0.95F);
            rightArm.yRot -= surge * 0.48F;
            leftArm.xRot += surge * 0.38F;
            demonQueen.y -= surge * 0.45F;
            tail.xRot -= surge * 0.25F;
        }
        if (state.sovereignRefusal) {
            head.xRot -= 0.12F;
            rightArm.zRot -= 0.18F;
            leftArm.zRot += 0.18F;
        }
        if (state.seaBorne) {
            demonQueen.y -= 0.3F + poise * 0.16F;
            tail.xRot -= 0.18F;
        }
        if (state.gazeMending) {
            final float pulse = 0.15F + (Mth.sin(time * 0.24F) + 1.0F) * 0.10F;
            throatRuby.z -= pulse;
            rightEye.z -= pulse * 0.35F;
            leftEye.z -= pulse * 0.35F;
        }
    }

    public static void extractRenderState(
        final NaamahEntity entity,
        final State state,
        final float partialTicks
    ) {
        final Action action = entity.presentationAction();
        final Phase phase = entity.presentationPhase();
        state.courtWaveProgress = action == Action.COURT_WAVE ? 1.0F : 0.0F;
        state.drowningSurgeProgress = action == Action.DROWNING_SURGE ? 1.0F : 0.0F;
        state.sovereignRefusal = phase == Phase.SOVEREIGN_REFUSAL;
        state.seaBorne = entity.isInWater();
        state.gazeMending = entity.presentationGazeMending();
    }

    public static final class State extends LivingEntityRenderState {
        public float courtWaveProgress;
        public float drowningSurgeProgress;
        public boolean sovereignRefusal;
        public boolean seaBorne;
        public boolean gazeMending;
    }
}
