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

/**
 * The vanilla villager rig (identical body, arm, leg and hat part names, boxes and UVs to
 * {@code VillagerModel}) carrying a werewolf head instead of the villager head: a squarer skull,
 * a muzzle with a lower jaw, a brow wedge and a pair of wolf ears. Keeping the body geometry
 * vanilla lets the native profession and biome clothing textures line up unchanged; the clothing
 * meshes leave the skull bare so the biome skin never paints a villager face over the wolf.
 */
public final class LycanVillagerModel extends EntityModel<LycanVillagerModel.State>
    implements VillagerLikeModel<LycanVillagerModel.State> {
    /** The fur atlas is 128 wide: vanilla villager islands on the left half, the wolf head islands on the right. */
    public static final int TEXTURE_WIDTH = 128;
    /** Native clothing textures are the vanilla 64x64 sheets, so the clothing meshes keep the vanilla size. */
    public static final int CLOTHING_TEXTURE_WIDTH = 64;
    public static final int TEXTURE_HEIGHT = 64;

    private final ModelPart head;
    private final ModelPart ears;
    private final ModelPart body;
    private final ModelPart arms;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public LycanVillagerModel(final ModelPart root) {
        super(root);
        head = root.getChild("head");
        ears = head.getChild("ears");
        body = root.getChild("body");
        arms = root.getChild("arms");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        return createLayer(true, true);
    }

    public static LayerDefinition createBodyLayerNoHat() {
        return createLayer(false, true);
    }

    /** Clothing meshes for the native villager texture layers: vanilla body and hat, bare skull. */
    public static LayerDefinition createClothingLayer() {
        return createLayer(true, false);
    }

    public static LayerDefinition createClothingLayerNoHat() {
        return createLayer(false, false);
    }

    private static LayerDefinition createLayer(final boolean includeHat, final boolean includeWolfHead) {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();

        // Werewolf skull in the vanilla head slot: an 8x8x8 box whose islands sit inside the vanilla
        // 8x10x8 head footprint at texOffs(0,0), so the vanilla hat and hat rim still fit around it.
        final CubeListBuilder skull = includeWolfHead
            ? CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -9.0F, -4.0F, 8.0F, 8.0F, 8.0F)
            : CubeListBuilder.create();
        final PartDefinition head = root.addOrReplaceChild("head", skull, PartPose.ZERO);
        final CubeListBuilder hatGeometry = includeHat
            ? CubeListBuilder.create().texOffs(32, 0)
                .addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, new CubeDeformation(0.51F))
            : CubeListBuilder.create();
        final PartDefinition hat = head.addOrReplaceChild("hat", hatGeometry, PartPose.ZERO);
        final CubeListBuilder rimGeometry = includeHat
            ? CubeListBuilder.create().texOffs(30, 47)
                .addBox(-8.0F, -8.0F, -6.0F, 16.0F, 16.0F, 1.0F)
            : CubeListBuilder.create();
        hat.addOrReplaceChild("hat_rim", rimGeometry, PartPose.rotation(-Mth.HALF_PI, 0.0F, 0.0F));

        // Muzzle, lower jaw and brow wedge live on the right half of the atlas (u >= 64), clear of every
        // vanilla villager island including the hat rim's back face, which reaches u = 64.
        head.addOrReplaceChild(
            "muzzle",
            includeWolfHead
                ? CubeListBuilder.create().texOffs(64, 0).addBox(-2.5F, -1.5F, -3.0F, 5.0F, 3.0F, 3.0F)
                : CubeListBuilder.create(),
            PartPose.offset(0.0F, -4.0F, -4.0F)
        );
        head.addOrReplaceChild(
            "lower_jaw",
            includeWolfHead
                ? CubeListBuilder.create().texOffs(64, 8).addBox(-2.0F, -0.5F, -2.5F, 4.0F, 1.5F, 3.0F)
                : CubeListBuilder.create(),
            PartPose.offsetAndRotation(0.0F, -2.0F, -4.0F, 0.12F, 0.0F, 0.0F)
        );
        head.addOrReplaceChild(
            "brow_wedge",
            includeWolfHead
                ? CubeListBuilder.create().texOffs(64, 16).addBox(-3.0F, -1.0F, -1.0F, 6.0F, 2.0F, 2.0F)
                : CubeListBuilder.create(),
            PartPose.offset(0.0F, -7.5F, -4.0F)
        );

        // Wolf ears on a shared pivot at the crown; their islands sit in the atlas pocket (28..40, 38..47).
        final PartDefinition ears = head.addOrReplaceChild(
            "ears",
            CubeListBuilder.create(),
            PartPose.offset(0.0F, -9.0F, 0.0F)
        );
        ears.addOrReplaceChild(
            "right_ear",
            includeWolfHead
                ? CubeListBuilder.create().texOffs(28, 38).addBox(-1.0F, -4.0F, -0.5F, 2.0F, 4.0F, 1.0F)
                : CubeListBuilder.create(),
            PartPose.offsetAndRotation(-2.5F, 0.0F, -0.5F, -0.18F, 0.0F, -0.22F)
        );
        ears.addOrReplaceChild(
            "left_ear",
            includeWolfHead
                ? CubeListBuilder.create().texOffs(34, 38).mirror().addBox(-1.0F, -4.0F, -0.5F, 2.0F, 4.0F, 1.0F)
                : CubeListBuilder.create(),
            PartPose.offsetAndRotation(2.5F, 0.0F, -0.5F, -0.18F, 0.0F, 0.22F)
        );

        // Vanilla villager torso, jacket, folded arms and legs.
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
        root.addOrReplaceChild(
            "arms",
            CubeListBuilder.create().texOffs(44, 22)
                .addBox(-8.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F)
                .texOffs(44, 22).mirror().addBox(4.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F)
                .texOffs(40, 38).addBox(-4.0F, 2.0F, -2.0F, 8.0F, 4.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 3.0F, -1.0F, -0.75F, 0.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "right_leg",
            CubeListBuilder.create().texOffs(0, 22)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offset(-2.0F, 12.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "left_leg",
            CubeListBuilder.create().texOffs(0, 22).mirror()
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offset(2.0F, 12.0F, 0.0F)
        );
        return LayerDefinition.create(mesh, includeWolfHead ? TEXTURE_WIDTH : CLOTHING_TEXTURE_WIDTH, TEXTURE_HEIGHT);
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
        ears.zRot = Mth.sin(state.ageInTicks * 0.07F) * 0.025F;
        if (state.activity == Activity.MOON_WATCH) {
            head.xRot -= 0.42F;
            head.yRot += Mth.sin(state.ageInTicks * 0.05F) * 0.15F;
            ears.xRot = -0.12F;
        } else if (state.activity == Activity.WARNING) {
            head.xRot -= 0.18F;
            arms.xRot = -1.0F;
            ears.xRot = 0.35F;
        } else if (state.activity == Activity.DEFENDING) {
            body.xRot = 0.12F;
            arms.xRot = -1.22F;
            head.xRot -= 0.14F;
            ears.xRot = 0.5F;
        } else if (state.activity == Activity.WITHDRAWING) {
            body.xRot = 0.18F;
            ears.xRot = 0.3F;
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
