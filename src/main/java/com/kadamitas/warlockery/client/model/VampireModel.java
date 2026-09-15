package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.VampireCourtEntity;
import com.kadamitas.warlockery.entity.VampireCourtRules;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The vampire court wears authored 64x64 player skins on a plain player-proportioned rig, so every
 * skin layer (hat, jacket, sleeves, pants) renders exactly as a skin editor previews it. The court
 * skins are slim-arm skins, so the arms are three pixels wide.
 */
public final class VampireModel extends EntityModel<VampireModel.State> {
    public static final int TEXTURE_WIDTH = 64;
    public static final int TEXTURE_HEIGHT = 64;
    public static final Identifier MASCULINE_TEXTURE = Identifier.fromNamespaceAndPath(
        "warlockery", "textures/entity/vampire_masculine.png"
    );
    public static final Identifier FEMININE_TEXTURE = Identifier.fromNamespaceAndPath(
        "warlockery", "textures/entity/vampire_feminine.png"
    );
    private static final CubeDeformation OUTER_LAYER = new CubeDeformation(0.25F);

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public VampireModel(final ModelPart root) {
        super(root);
        head = root.getChild("head");
        body = root.getChild("body");
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
            CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
            PartPose.ZERO
        );
        head.addOrReplaceChild(
            "hat",
            CubeListBuilder.create().texOffs(32, 0)
                .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.5F)),
            PartPose.ZERO
        );
        final PartDefinition body = root.addOrReplaceChild(
            "body",
            CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F),
            PartPose.ZERO
        );
        body.addOrReplaceChild(
            "jacket",
            CubeListBuilder.create().texOffs(16, 32).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, OUTER_LAYER),
            PartPose.ZERO
        );
        final PartDefinition rightArm = root.addOrReplaceChild(
            "right_arm",
            CubeListBuilder.create().texOffs(40, 16).addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F),
            PartPose.offset(-5.0F, 2.5F, 0.0F)
        );
        rightArm.addOrReplaceChild(
            "right_sleeve",
            CubeListBuilder.create().texOffs(40, 32).addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, OUTER_LAYER),
            PartPose.ZERO
        );
        final PartDefinition leftArm = root.addOrReplaceChild(
            "left_arm",
            CubeListBuilder.create().texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F),
            PartPose.offset(5.0F, 2.5F, 0.0F)
        );
        leftArm.addOrReplaceChild(
            "left_sleeve",
            CubeListBuilder.create().texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, OUTER_LAYER),
            PartPose.ZERO
        );
        final PartDefinition rightLeg = root.addOrReplaceChild(
            "right_leg",
            CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offset(-1.9F, 12.0F, 0.0F)
        );
        rightLeg.addOrReplaceChild(
            "right_pants",
            CubeListBuilder.create().texOffs(0, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, OUTER_LAYER),
            PartPose.ZERO
        );
        final PartDefinition leftLeg = root.addOrReplaceChild(
            "left_leg",
            CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offset(1.9F, 12.0F, 0.0F)
        );
        leftLeg.addOrReplaceChild(
            "left_pants",
            CubeListBuilder.create().texOffs(0, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, OUTER_LAYER),
            PartPose.ZERO
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    public static Variant variantFor(final UUID entityId) {
        Objects.requireNonNull(entityId, "entityId");
        return (entityId.getLeastSignificantBits() & 1L) == 0L ? Variant.MASCULINE : Variant.FEMININE;
    }

    public static Identifier textureFor(final Variant variant) {
        return Objects.requireNonNull(variant, "variant") == Variant.FEMININE
            ? FEMININE_TEXTURE : MASCULINE_TEXTURE;
    }

    public static void extractRenderState(
        final VampireCourtEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.variant = variantFor(entity.getUUID());
        state.activity = activityFor(entity.presentationIntent());
    }

    private static Activity activityFor(final VampireCourtRules.Intent intent) {
        return switch (intent) {
            case UNBOUND, ROOST, VEILED_REST -> Activity.ROOSTING;
            case WATCH, THRESHOLD_GUARD -> Activity.WATCHING;
            case STALK, INTERCEPT -> Activity.STALKING;
            case FEED -> Activity.FEEDING;
            case ASSAULT_LEAD -> Activity.ASSAULT_LEAD;
            case SEEK_SHELTER, WAVERING, RETREAT, RECOVER -> Activity.RECOVERING;
        };
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;
        final float pace = state.walkAnimationPos * 0.6662F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F);
        rightLeg.xRot = Mth.cos(pace) * 1.4F * stride;
        leftLeg.xRot = Mth.cos(pace + Mth.PI) * 1.4F * stride;
        rightArm.xRot = Mth.cos(pace + Mth.PI) * stride;
        leftArm.xRot = Mth.cos(pace) * stride;
        rightArm.zRot = Mth.cos(state.ageInTicks * 0.09F) * 0.05F + 0.05F;
        leftArm.zRot = -Mth.cos(state.ageInTicks * 0.09F) * 0.05F - 0.05F;
        if (state.activity == Activity.STALKING) {
            body.xRot = 0.12F;
            head.xRot -= 0.15F;
            rightArm.xRot -= 0.3F;
            leftArm.xRot -= 0.3F;
        } else if (state.activity == Activity.FEEDING) {
            head.xRot += 0.58F;
            rightArm.xRot = -1.05F;
            rightArm.yRot = -0.35F;
            leftArm.xRot = -1.05F;
            leftArm.yRot = 0.35F;
        } else if (state.activity == Activity.ASSAULT_LEAD) {
            rightArm.xRot = -1.35F;
            rightArm.yRot = -0.55F;
            leftArm.xRot = -0.7F;
            leftArm.yRot = 0.42F;
            head.xRot -= 0.12F;
        } else if (state.activity == Activity.RECOVERING) {
            body.xRot = 0.2F;
            head.xRot += 0.18F;
        }
    }

    public enum Variant {
        MASCULINE,
        FEMININE
    }

    public enum Activity {
        ROOSTING,
        WATCHING,
        STALKING,
        FEEDING,
        ASSAULT_LEAD,
        RECOVERING
    }

    public static final class State extends LivingEntityRenderState {
        public Variant variant = Variant.MASCULINE;
        public Activity activity = Activity.ROOSTING;
    }
}
