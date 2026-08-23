package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.FeralLycanEntity;
import com.kadamitas.warlockery.entity.LycanPackRules;
import com.kadamitas.warlockery.entity.LycanPackRules.ActionKind;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/** A narrow, forward-reaching lycan rig authored only for the feral variant. */
public final class FeralLycanModel extends EntityModel<FeralLycanModel.State> {
    public static final int TEXTURE_WIDTH = 192;
    public static final int TEXTURE_HEIGHT = 160;

    private final ModelPart ribcage;
    private final ModelPart skull;
    private final ModelPart leftReach;
    private final ModelPart rightReach;
    private final ModelPart leftCarpal;
    private final ModelPart rightCarpal;
    private final ModelPart leftHaunch;
    private final ModelPart rightHaunch;
    private final ModelPart leftHock;
    private final ModelPart rightHock;
    private final ModelPart crookedTail;
    private final ModelPart crookedMid;
    private final ModelPart wildRuff;

    public FeralLycanModel(final ModelPart root) {
        super(root);
        ribcage = root.getChild("ribcage");
        skull = ribcage.getChild("throat").getChild("skull");
        leftReach = root.getChild("left_reach");
        rightReach = root.getChild("right_reach");
        leftCarpal = leftReach.getChild("left_carpal");
        rightCarpal = rightReach.getChild("right_carpal");
        leftHaunch = root.getChild("left_haunch");
        rightHaunch = root.getChild("right_haunch");
        leftHock = leftHaunch.getChild("left_hock");
        rightHock = rightHaunch.getChild("right_hock");
        crookedTail = root.getChild("crooked_tail");
        crookedMid = crookedTail.getChild("crooked_mid");
        wildRuff = root.getChild("wild_ruff");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition ribcage = root.addOrReplaceChild("ribcage",
            CubeListBuilder.create().texOffs(0, 18).addBox(-4.0F, -4.0F, -2.5F, 8.0F, 10.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, 7.0F, 0.8F, 0.30F, 0.0F, 0.0F));
        ribcage.addOrReplaceChild("abdomen",
            CubeListBuilder.create().texOffs(30, 18).addBox(-2.5F, 0.0F, -1.8F, 5.0F, 6.0F, 3.0F),
            PartPose.offset(0.0F, 5.2F, 0.5F));
        ribcage.addOrReplaceChild("hips",
            CubeListBuilder.create().texOffs(54, 18).addBox(-3.0F, -1.5F, -2.0F, 6.0F, 4.0F, 4.0F),
            PartPose.offset(0.0F, 7.0F, 0.8F));
        ribcage.addOrReplaceChild("rib_keel",
            CubeListBuilder.create().texOffs(0, 18)
                .addBox(-1.5F, -3.0F, -1.0F, 3.0F, 7.0F, 2.0F),
            PartPose.offset(0.0F, 0.5F, -2.4F));
        ribcage.addOrReplaceChild("sunken_flank",
            CubeListBuilder.create().texOffs(30, 18)
                .addBox(-2.0F, -1.0F, -1.5F, 4.0F, 5.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, 5.0F, 0.6F, -0.22F, 0.0F, 0.0F));
        final PartDefinition throat = ribcage.addOrReplaceChild("throat",
            CubeListBuilder.create().texOffs(80, 18).addBox(-2.0F, -4.0F, -1.5F, 4.0F, 5.0F, 3.0F),
            PartPose.offset(0.0F, -3.0F, -1.0F));
        final PartDefinition skull = throat.addOrReplaceChild("skull",
            CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -5.0F, -3.0F, 6.0F, 6.0F, 6.0F),
            PartPose.offset(0.0F, -3.5F, -1.0F));
        skull.addOrReplaceChild("long_muzzle",
            CubeListBuilder.create().texOffs(30, 0).addBox(-2.0F, -1.5F, -5.0F, 4.0F, 3.0F, 5.0F),
            PartPose.offset(0.0F, -0.4F, -2.7F));
        skull.addOrReplaceChild("lower_jaw",
            CubeListBuilder.create().texOffs(30, 0)
                .addBox(-1.7F, -0.5F, -4.0F, 3.4F, 1.5F, 4.0F),
            PartPose.offset(0.0F, 0.8F, -2.6F));
        skull.addOrReplaceChild("broken_brow",
            CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2.5F, -1.0F, -1.0F, 5.0F, 1.5F, 2.0F),
            PartPose.offsetAndRotation(0.0F, -3.4F, -2.8F, 0.0F, 0.0F, -0.08F));
        skull.addOrReplaceChild("torn_left_ear",
            CubeListBuilder.create().texOffs(56, 0).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
            PartPose.offset(2.0F, -4.2F, 0.0F));
        skull.addOrReplaceChild("torn_right_ear",
            CubeListBuilder.create().texOffs(66, 0).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F),
            PartPose.offset(-2.2F, -4.4F, 0.5F));

        final PartDefinition ruff = root.addOrReplaceChild("wild_ruff",
            CubeListBuilder.create().texOffs(76, 0).addBox(-3.0F, -2.0F, -2.0F, 6.0F, 4.0F, 2.0F),
            PartPose.offset(0.0F, 5.0F, 2.3F));
        ruff.addOrReplaceChild("left_ruff",
            CubeListBuilder.create().texOffs(96, 0).addBox(0.0F, -1.0F, -1.0F, 5.0F, 3.0F, 2.0F),
            PartPose.offsetAndRotation(3.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.28F));
        ruff.addOrReplaceChild("right_ruff",
            CubeListBuilder.create().texOffs(112, 0).addBox(-5.0F, -1.0F, -1.0F, 5.0F, 3.0F, 2.0F),
            PartPose.offsetAndRotation(-3.0F, 0.5F, 0.0F, 0.0F, 0.0F, 0.28F));
        ruff.addOrReplaceChild("high_ruff",
            CubeListBuilder.create().texOffs(76, 0)
                .addBox(-2.0F, -4.0F, -1.0F, 4.0F, 4.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, -1.2F, 0.4F, 0.36F, 0.0F, 0.0F));
        ruff.addOrReplaceChild("left_broken_ruff",
            CubeListBuilder.create().texOffs(96, 0)
                .addBox(0.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(7.0F, 1.0F, 0.2F, 0.0F, 0.0F, -0.46F));
        ruff.addOrReplaceChild("right_broken_ruff",
            CubeListBuilder.create().texOffs(112, 0)
                .addBox(-4.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(-7.0F, 1.4F, 0.2F, 0.0F, 0.0F, 0.46F));

        final PartDefinition leftReach = root.addOrReplaceChild("left_reach",
            CubeListBuilder.create().texOffs(0, 40)
                .addBox(0.0F, -1.0F, -1.2F, 3.0F, 7.0F, 2.0F),
            PartPose.offsetAndRotation(5.4F, 6.0F, -0.4F, 0.0F, 0.0F, -0.18F));
        final PartDefinition leftCarpal = leftReach.addOrReplaceChild("left_carpal",
            CubeListBuilder.create().texOffs(14, 40)
                .addBox(-0.2F, 0.0F, -1.4F, 4.0F, 8.0F, 3.0F),
            PartPose.offset(0.5F, 5.3F, 0.0F));
        final PartDefinition leftRake = leftCarpal.addOrReplaceChild("left_rake",
            CubeListBuilder.create().texOffs(32, 40)
                .addBox(-0.4F, 0.0F, -3.0F, 4.0F, 2.0F, 5.0F),
            PartPose.offset(0.0F, 7.3F, -0.3F));
        leftRake.addOrReplaceChild("left_claw_inner",
            CubeListBuilder.create().texOffs(32, 40).addBox(-0.4F, 0.0F, -3.0F, 1.0F, 1.0F, 3.0F),
            PartPose.offset(0.2F, 1.4F, -2.5F));
        leftRake.addOrReplaceChild("left_claw_middle",
            CubeListBuilder.create().texOffs(32, 40).addBox(-0.4F, 0.0F, -3.5F, 1.0F, 1.0F, 3.5F),
            PartPose.offset(1.8F, 1.4F, -2.5F));
        leftRake.addOrReplaceChild("left_claw_outer",
            CubeListBuilder.create().texOffs(32, 40).addBox(-0.4F, 0.0F, -3.0F, 1.0F, 1.0F, 3.0F),
            PartPose.offset(3.4F, 1.4F, -2.5F));
        final PartDefinition rightReach = root.addOrReplaceChild("right_reach",
            CubeListBuilder.create().texOffs(52, 40)
                .addBox(-3.0F, -1.0F, -1.2F, 3.0F, 7.0F, 2.0F),
            PartPose.offsetAndRotation(-5.4F, 6.0F, -0.4F, 0.0F, 0.0F, 0.18F));
        final PartDefinition rightCarpal = rightReach.addOrReplaceChild("right_carpal",
            CubeListBuilder.create().texOffs(66, 40)
                .addBox(-3.8F, 0.0F, -1.4F, 4.0F, 8.0F, 3.0F),
            PartPose.offset(-0.5F, 5.3F, 0.0F));
        final PartDefinition rightRake = rightCarpal.addOrReplaceChild("right_rake",
            CubeListBuilder.create().texOffs(84, 40)
                .addBox(-3.6F, 0.0F, -3.0F, 4.0F, 2.0F, 5.0F),
            PartPose.offset(0.0F, 7.3F, -0.3F));
        rightRake.addOrReplaceChild("right_claw_inner",
            CubeListBuilder.create().texOffs(84, 40).addBox(-0.6F, 0.0F, -3.0F, 1.0F, 1.0F, 3.0F),
            PartPose.offset(-0.2F, 1.4F, -2.5F));
        rightRake.addOrReplaceChild("right_claw_middle",
            CubeListBuilder.create().texOffs(84, 40).addBox(-0.6F, 0.0F, -3.5F, 1.0F, 1.0F, 3.5F),
            PartPose.offset(-1.8F, 1.4F, -2.5F));
        rightRake.addOrReplaceChild("right_claw_outer",
            CubeListBuilder.create().texOffs(84, 40).addBox(-0.6F, 0.0F, -3.0F, 1.0F, 1.0F, 3.0F),
            PartPose.offset(-3.4F, 1.4F, -2.5F));

        final PartDefinition leftHaunch = root.addOrReplaceChild("left_haunch",
            CubeListBuilder.create().texOffs(0, 58).addBox(-0.5F, 0.0F, -1.7F, 3.0F, 7.0F, 3.0F),
            PartPose.offset(2.0F, 13.872355F, 0.8F));
        final PartDefinition leftHock = leftHaunch.addOrReplaceChild("left_hock",
            CubeListBuilder.create().texOffs(18, 58).addBox(-0.3F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F),
            PartPose.offsetAndRotation(0.2F, 5.0F, 0.0F, -0.48F, 0.0F, 0.0F));
        final PartDefinition leftPaw = leftHock.addOrReplaceChild("left_paw",
            CubeListBuilder.create().texOffs(32, 58).addBox(-0.8F, 0.0F, -4.0F, 3.0F, 2.0F, 6.0F),
            PartPose.offset(0.0F, 3.0F, -0.5F));
        leftPaw.addOrReplaceChild("left_toe_inner",
            CubeListBuilder.create().texOffs(32, 58).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 1.0F, 2.0F),
            PartPose.offset(-0.2F, 0.8F, -3.8F));
        leftPaw.addOrReplaceChild("left_toe_middle",
            CubeListBuilder.create().texOffs(32, 58).addBox(-0.5F, 0.0F, -2.4F, 1.0F, 1.0F, 2.4F),
            PartPose.offset(0.7F, 0.8F, -3.8F));
        leftPaw.addOrReplaceChild("left_toe_outer",
            CubeListBuilder.create().texOffs(32, 58).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 1.0F, 2.0F),
            PartPose.offset(1.6F, 0.8F, -3.8F));
        final PartDefinition rightHaunch = root.addOrReplaceChild("right_haunch",
            CubeListBuilder.create().texOffs(58, 58).addBox(-2.5F, 0.0F, -1.7F, 3.0F, 7.0F, 3.0F),
            PartPose.offset(-2.0F, 13.872355F, 0.8F));
        final PartDefinition rightHock = rightHaunch.addOrReplaceChild("right_hock",
            CubeListBuilder.create().texOffs(76, 58).addBox(-1.7F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F),
            PartPose.offsetAndRotation(-0.2F, 5.0F, 0.0F, -0.48F, 0.0F, 0.0F));
        final PartDefinition rightPaw = rightHock.addOrReplaceChild("right_paw",
            CubeListBuilder.create().texOffs(90, 58).addBox(-2.2F, 0.0F, -4.0F, 3.0F, 2.0F, 6.0F),
            PartPose.offset(0.0F, 3.0F, -0.5F));
        rightPaw.addOrReplaceChild("right_toe_inner",
            CubeListBuilder.create().texOffs(90, 58).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 1.0F, 2.0F),
            PartPose.offset(0.2F, 0.8F, -3.8F));
        rightPaw.addOrReplaceChild("right_toe_middle",
            CubeListBuilder.create().texOffs(90, 58).addBox(-0.5F, 0.0F, -2.4F, 1.0F, 1.0F, 2.4F),
            PartPose.offset(-0.7F, 0.8F, -3.8F));
        rightPaw.addOrReplaceChild("right_toe_outer",
            CubeListBuilder.create().texOffs(90, 58).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 1.0F, 2.0F),
            PartPose.offset(-1.6F, 0.8F, -3.8F));

        final PartDefinition tail = root.addOrReplaceChild("crooked_tail",
            CubeListBuilder.create().texOffs(0, 76)
                .addBox(-0.5F, -1.0F, 0.0F, 1.0F, 2.0F, 3.5F),
            PartPose.offset(0.0F, 13.0F, 2.2F));
        final PartDefinition mid = tail.addOrReplaceChild("crooked_mid",
            CubeListBuilder.create().texOffs(14, 76)
                .addBox(-0.5F, -0.8F, 0.0F, 1.0F, 2.0F, 2.5F),
            PartPose.offsetAndRotation(0.0F, -0.2F, 3.0F, -0.18F, 0.28F, 0.0F));
        mid.addOrReplaceChild("crooked_tip",
            CubeListBuilder.create().texOffs(36, 76)
                .addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 2.0F),
            PartPose.offsetAndRotation(0.5F, 0.0F, 2.3F, 0.2F, -0.45F, 0.0F));
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        skull.yRot += state.yRot * Mth.DEG_TO_RAD;
        skull.xRot += state.xRot * Mth.DEG_TO_RAD;
        final float scuttle = state.walkAnimationPos * 0.92F;
        final float speed = state.walkAnimationSpeed;
        leftReach.xRot += Mth.cos(scuttle) * speed * 0.84F;
        rightReach.xRot += Mth.cos(scuttle + Mth.PI) * speed * 0.84F;
        leftHaunch.xRot += Mth.cos(scuttle + Mth.PI) * speed * 0.94F;
        rightHaunch.xRot += Mth.cos(scuttle) * speed * 0.94F;
        leftCarpal.xRot += Mth.sin(scuttle) * speed * 0.31F;
        rightCarpal.xRot -= Mth.sin(scuttle) * speed * 0.31F;
        leftHock.xRot -= Math.max(0.0F, Mth.sin(scuttle)) * speed * 0.52F;
        rightHock.xRot -= Math.max(0.0F, -Mth.sin(scuttle)) * speed * 0.52F;
        wildRuff.zRot += Mth.sin(state.ageInTicks * 0.17F) * 0.045F;
        crookedTail.yRot += Mth.sin(state.ageInTicks * 0.21F) * 0.26F;
        crookedMid.yRot -= Mth.cos(state.ageInTicks * 0.18F) * 0.22F;
        if (state.panicked) {
            skull.yRot += Mth.sin(state.ageInTicks * 0.9F) * 0.16F;
            wildRuff.xScale += 0.12F;
        }
        if (state.bounding) {
            ribcage.xRot += 0.58F;
            leftReach.xRot -= 1.08F;
            rightReach.xRot -= 0.82F;
            leftHaunch.xRot += 0.88F;
            rightHaunch.xRot += 0.65F;
            crookedTail.xRot -= 0.62F;
        }
    }

    public static void extractRenderState(final FeralLycanEntity entity, final State state, final float partialTicks) {
        final ActionKind action = entity.presentationAction();
        state.hunger = entity.presentationHunger();
        state.fear = entity.presentationFear();
        state.bounding = action == ActionKind.POUNCE || action == ActionKind.HARRY;
        state.panicked = action == ActionKind.RETREAT
            || state.fear >= LycanPackRules.PANIC_FEAR;
    }

    public static final class State extends LivingEntityRenderState {
        public int hunger;
        public int fear;
        public boolean bounding;
        public boolean panicked;
    }
}
