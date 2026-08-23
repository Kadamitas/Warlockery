package com.kadamitas.warlockery.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.kadamitas.warlockery.entity.LycanVillagerEntity;
import com.kadamitas.warlockery.entity.LycanVillagerRules;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.VillagerLikeModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.util.Mth;

public final class LycanVillagerModel extends EntityModel<LycanVillagerModel.State>
    implements VillagerLikeModel<LycanVillagerModel.State> {
    public static final int TEXTURE_WIDTH = 64;
    public static final int TEXTURE_HEIGHT = 64;

    private final ModelPart head;
    private final ModelPart ears;
    private final ModelPart muzzle;
    private final ModelPart body;
    private final ModelPart arms;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart tail;

    public LycanVillagerModel(final ModelPart root) {
        super(root);
        head = root.getChild("head");
        muzzle = head.getChild("muzzle");
        ears = head.getChild("ears");
        body = root.getChild("body");
        arms = root.getChild("arms");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
        tail = root.getChild("tail");
    }

    public static LayerDefinition createBodyLayer() {
        return createLayer(true);
    }

    public static LayerDefinition createBodyLayerNoHat() {
        return createLayer(false);
    }

    private static LayerDefinition createLayer(final boolean includeHat) {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition head = root.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F),
            PartPose.ZERO
        );
        final PartDefinition muzzle = head.addOrReplaceChild(
            "muzzle",
            CubeListBuilder.create().texOffs(24, 0)
                .addBox(-2.0F, -1.5F, -4.0F, 4.0F, 3.0F, 4.0F),
            PartPose.offset(0.0F, -2.5F, -3.5F)
        );
        muzzle.addOrReplaceChild(
            "lower_wedge_muzzle",
            CubeListBuilder.create().texOffs(24, 0)
                .addBox(-2.0F, -1.5F, -4.0F, 4.0F, 3.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 0.8F, -0.4F, 0.10F, 0.0F, 0.0F)
        );
        final PartDefinition ears = head.addOrReplaceChild(
            "ears",
            CubeListBuilder.create(),
            PartPose.offset(0.0F, -8.5F, 0.0F)
        );
        ears.addOrReplaceChild(
            "right_ear",
            CubeListBuilder.create().texOffs(56, 0)
                .addBox(-1.0F, -3.0F, -0.5F, 2.0F, 3.0F, 1.0F),
            PartPose.offsetAndRotation(-3.5F, 0.0F, 0.0F, 0.0F, 0.0F, -0.26F)
        );
        ears.addOrReplaceChild(
            "left_ear",
            CubeListBuilder.create().texOffs(56, 0).mirror()
                .addBox(-1.0F, -3.0F, -0.5F, 2.0F, 3.0F, 1.0F),
            PartPose.offsetAndRotation(3.5F, 0.6F, 0.0F, 0.0F, 0.0F, 0.34F)
        );
        final CubeListBuilder hatGeometry = includeHat
            ? CubeListBuilder.create().texOffs(32, 0)
                .addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, new CubeDeformation(0.51F))
            : CubeListBuilder.create();
        head.addOrReplaceChild("hat", hatGeometry, PartPose.ZERO);
        final CubeListBuilder rimGeometry = includeHat
            ? CubeListBuilder.create().texOffs(30, 47)
                .addBox(-8.0F, -8.0F, -6.0F, 16.0F, 16.0F, 1.0F)
            : CubeListBuilder.create();
        head.addOrReplaceChild(
            "hat_rim", rimGeometry, PartPose.rotation(-Mth.HALF_PI, 0.0F, 0.0F)
        );

        final PartDefinition body = root.addOrReplaceChild(
            "body",
            CubeListBuilder.create().texOffs(16, 20)
                .addBox(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F),
            PartPose.ZERO
        );
        body.addOrReplaceChild(
            "jacket",
            CubeListBuilder.create().texOffs(0, 38)
                .addBox(-4.0F, 0.0F, -3.0F, 8.0F, 20.0F, 6.0F, new CubeDeformation(0.5F)),
            PartPose.ZERO
        );
        body.addOrReplaceChild(
            "shoulder_ruff",
            CubeListBuilder.create().texOffs(44, 22)
                .addBox(-9.25F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F)
                .texOffs(44, 22).mirror().addBox(5.25F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F)
                .texOffs(40, 38).addBox(-4.0F, 2.0F, -2.0F, 8.0F, 4.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 1.0F, 0.0F, -0.08F, 0.0F, 0.0F)
        );
        body.addOrReplaceChild(
            "tapered_wolf_waist",
            CubeListBuilder.create().texOffs(16, 20)
                .addBox(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F),
            PartPose.offsetAndRotation(0.0F, 7.0F, 0.0F, 0.08F, 0.0F, 0.0F)
        );
        final PartDefinition arms = root.addOrReplaceChild(
            "arms",
            CubeListBuilder.create().texOffs(44, 22)
                .addBox(-8.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F)
                .texOffs(44, 22).mirror().addBox(4.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F)
                .texOffs(40, 38).addBox(-4.0F, 2.0F, -2.0F, 8.0F, 4.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 3.0F, -1.0F, -0.75F, 0.0F, 0.0F)
        );
        arms.addOrReplaceChild(
            "right_wolf_forearm",
            CubeListBuilder.create().texOffs(0, 22)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 10.0F, 4.0F),
            PartPose.offsetAndRotation(-6.0F, -2.0F, 0.0F, -0.10F, 0.0F, 0.08F)
        );
        arms.addOrReplaceChild(
            "left_wolf_forearm",
            CubeListBuilder.create().texOffs(0, 22).mirror()
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 10.0F, 4.0F),
            PartPose.offsetAndRotation(6.0F, -2.0F, 0.0F, -0.10F, 0.0F, -0.08F)
        );
        arms.addOrReplaceChild(
            "right_wolf_claws",
            CubeListBuilder.create().texOffs(56, 0)
                .addBox(-1.0F, -3.0F, -0.5F, 2.0F, 3.0F, 1.0F),
            PartPose.offsetAndRotation(-6.0F, 7.0F, -2.2F, -0.28F, 0.0F, -0.10F)
        );
        arms.addOrReplaceChild(
            "left_wolf_claws",
            CubeListBuilder.create().texOffs(56, 0).mirror()
                .addBox(-1.0F, -3.0F, -0.5F, 2.0F, 3.0F, 1.0F),
            PartPose.offsetAndRotation(6.0F, 7.0F, -2.2F, -0.28F, 0.0F, 0.10F)
        );
        final PartDefinition rightLeg = root.addOrReplaceChild(
            "right_leg",
            CubeListBuilder.create().texOffs(0, 22)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 10.0F, 4.0F)
                .texOffs(40, 48).addBox(-2.25F, 8.0F, -3.5F, 4.5F, 3.0F, 5.5F),
            PartPose.offset(-2.0F, 12.0F, 0.0F)
        );
        addHeavyCalf(rightLeg, "right", false);
        final PartDefinition leftLeg = root.addOrReplaceChild(
            "left_leg",
            CubeListBuilder.create().texOffs(0, 22).mirror()
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 10.0F, 4.0F)
                .texOffs(40, 48).mirror().addBox(-2.25F, 8.0F, -3.5F, 4.5F, 3.0F, 5.5F),
            PartPose.offset(2.0F, 12.0F, 0.0F)
        );
        addHeavyCalf(leftLeg, "left", true);
        root.addOrReplaceChild(
            "tail",
            CubeListBuilder.create().texOffs(32, 48)
                .addBox(-1.5F, -1.0F, 0.0F, 3.0F, 3.0F, 7.0F)
                .texOffs(32, 58).addBox(-1.0F, 0.0F, 6.0F, 2.0F, 2.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 9.0F, 2.5F, 0.72F, 0.0F, 0.0F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private static void addHeavyCalf(
        final PartDefinition leg,
        final String side,
        final boolean mirror
    ) {
        final PartDefinition calf = leg.addOrReplaceChild(
            side + "_heavy_calf",
            CubeListBuilder.create().texOffs(0, 22).mirror(mirror)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 10.0F, 4.0F, new CubeDeformation(0.5F)),
            PartPose.ZERO
        );
        calf.addOrReplaceChild(
            side + "_digitigrade_foot",
            CubeListBuilder.create().texOffs(40, 48).mirror(mirror)
                .addBox(-2.25F, 8.0F, -3.5F, 4.5F, 3.0F, 5.5F, new CubeDeformation(0.25F)),
            PartPose.offsetAndRotation(0.0F, 0.0F, -0.25F, -0.08F, 0.0F, 0.0F)
        );
    }

    public static void extractRenderState(
        final LycanVillagerEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.villagerData = entity.getVillagerData();
        state.isUnhappy = entity.getUnhappyCounter() > 0;
        state.activity = activityFor(entity.presentationIntent());
    }

    private static Activity activityFor(final LycanVillagerRules.Intent intent) {
        return switch (intent) {
            case ROUTINE -> Activity.ROUTINE;
            case BOUNDARY_WATCH -> Activity.BOUNDARY_WATCH;
            case MOON_WATCH -> Activity.MOON_WATCH;
            case GREETING -> Activity.GREETING;
            case RESERVE -> Activity.RESERVE;
            case WARNING -> Activity.WARNING;
            case INTERCEPT -> Activity.INTERCEPTING;
            case DEFEND -> Activity.DEFENDING;
            case WITHDRAW -> Activity.WITHDRAWING;
            case RETURN -> Activity.RETURNING;
        };
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;
        if (state.isUnhappy) {
            head.zRot = 0.3F * Mth.sin(0.45F * state.ageInTicks);
            head.xRot += 0.4F;
        }
        final float pace = state.walkAnimationPos * 0.6662F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F) * 0.7F;
        rightLeg.xRot = Mth.cos(pace) * 1.4F * stride;
        leftLeg.xRot = Mth.cos(pace + Mth.PI) * 1.4F * stride;
        tail.yRot = Mth.sin(state.ageInTicks * 0.11F) * 0.16F;
        ears.zRot = Mth.sin(state.ageInTicks * 0.07F) * 0.025F;
        muzzle.xRot = Mth.sin(state.ageInTicks * 0.08F) * 0.018F;
        if (state.activity == Activity.MOON_WATCH) {
            head.xRot -= 0.42F;
            head.yRot += Mth.sin(state.ageInTicks * 0.05F) * 0.15F;
            tail.xRot = 0.9F;
        } else if (state.activity == Activity.WARNING) {
            head.xRot -= 0.18F;
            arms.xRot = -1.0F;
            tail.yRot = 0.35F;
        } else if (state.activity == Activity.DEFENDING) {
            body.xRot = 0.12F;
            arms.xRot = -1.22F;
            head.xRot -= 0.14F;
        } else if (state.activity == Activity.WITHDRAWING) {
            body.xRot = 0.18F;
            tail.xRot = 0.42F;
        }
    }

    @Override
    public void translateToArms(final State state, final PoseStack poseStack) {
        root().translateAndRotate(poseStack);
        arms.translateAndRotate(poseStack);
    }

    public enum Activity {
        ROUTINE,
        BOUNDARY_WATCH,
        MOON_WATCH,
        GREETING,
        RESERVE,
        WARNING,
        INTERCEPTING,
        DEFENDING,
        WITHDRAWING,
        RETURNING
    }

    public static final class State extends VillagerRenderState {
        public Activity activity = Activity.ROUTINE;
    }
}
