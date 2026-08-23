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

public final class VampireModel extends EntityModel<VampireModel.State> {
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 128;
    public static final Identifier MASCULINE_TEXTURE = Identifier.fromNamespaceAndPath(
        "warlockery", "textures/entity/vampire_masculine.png"
    );
    public static final Identifier FEMININE_TEXTURE = Identifier.fromNamespaceAndPath(
        "warlockery", "textures/entity/vampire_feminine.png"
    );

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart masculineVariant;
    private final ModelPart feminineVariant;
    private final ModelPart masculineHair;
    private final ModelPart masculineCollar;
    private final ModelPart masculineCoatTail;
    private final ModelPart feminineHair;
    private final ModelPart feminineBackHair;
    private final ModelPart feminineRightLock;
    private final ModelPart feminineLeftLock;
    private final ModelPart feminineSkirt;

    public VampireModel(final ModelPart root) {
        super(root);
        head = root.getChild("head");
        body = root.getChild("body");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
        masculineVariant = root.getChild("masculine_variant");
        feminineVariant = root.getChild("feminine_variant");
        masculineHair = masculineVariant.getChild("short_hair");
        masculineCollar = masculineVariant.getChild("coat_collar");
        masculineCoatTail = masculineVariant.getChild("coat_tail");
        feminineHair = feminineVariant.getChild("long_hair_cap");
        feminineBackHair = feminineVariant.getChild("back_hair");
        feminineRightLock = feminineVariant.getChild("right_hair_lock");
        feminineLeftLock = feminineVariant.getChild("left_hair_lock");
        feminineSkirt = feminineVariant.getChild("dress_skirt");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition body = root.addOrReplaceChild(
            "body",
            CubeListBuilder.create().texOffs(32, 0)
                .addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F),
            PartPose.offset(0.0F, 8.0F, 0.0F)
        );
        body.addOrReplaceChild(
            "pearl_brooch",
            CubeListBuilder.create().texOffs(92, 52)
                .addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 1.0F),
            PartPose.offset(0.0F, 3.0F, -2.15F)
        );
        root.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
            PartPose.offset(0.0F, 8.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "right_arm",
            CubeListBuilder.create().texOffs(0, 20)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offset(-6.0F, 10.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "left_arm",
            CubeListBuilder.create().texOffs(16, 20)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offset(6.0F, 10.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "right_leg",
            CubeListBuilder.create().texOffs(32, 20)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offset(-2.0F, 20.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "left_leg",
            CubeListBuilder.create().texOffs(48, 20)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offset(2.0F, 20.0F, 0.0F)
        );

        final PartDefinition masculine = root.addOrReplaceChild(
            "masculine_variant",
            CubeListBuilder.create(),
            PartPose.ZERO
        );
        masculine.addOrReplaceChild(
            "short_hair",
            CubeListBuilder.create().texOffs(0, 40)
                .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 3.0F, 8.0F, new CubeDeformation(0.18F)),
            PartPose.offset(0.0F, 8.0F, 0.0F)
        );
        masculine.addOrReplaceChild(
            "coat_collar",
            CubeListBuilder.create().texOffs(32, 40)
                .addBox(-4.5F, 0.0F, -2.5F, 9.0F, 3.0F, 5.0F),
            PartPose.offset(0.0F, 8.0F, 0.0F)
        );
        masculine.addOrReplaceChild(
            "coat_tail",
            CubeListBuilder.create().texOffs(60, 40)
                .addBox(-4.0F, 9.0F, 1.6F, 8.0F, 10.0F, 1.0F),
            PartPose.offset(0.0F, 8.0F, 0.0F)
        );

        final PartDefinition feminine = root.addOrReplaceChild(
            "feminine_variant",
            CubeListBuilder.create(),
            PartPose.ZERO
        );
        feminine.addOrReplaceChild(
            "long_hair_cap",
            CubeListBuilder.create().texOffs(0, 40)
                .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 4.0F, 8.0F, new CubeDeformation(0.18F)),
            PartPose.offset(0.0F, 8.0F, 0.0F)
        );
        feminine.addOrReplaceChild(
            "back_hair",
            CubeListBuilder.create().texOffs(32, 52)
                .addBox(-4.0F, -5.0F, 3.7F, 8.0F, 12.0F, 1.0F),
            PartPose.offset(0.0F, 8.0F, 0.0F)
        );
        feminine.addOrReplaceChild(
            "right_hair_lock",
            CubeListBuilder.create().texOffs(50, 52)
                .addBox(-5.0F, -5.0F, -4.5F, 2.0F, 11.0F, 1.0F),
            PartPose.offset(0.0F, 8.0F, 0.0F)
        );
        feminine.addOrReplaceChild(
            "left_hair_lock",
            CubeListBuilder.create().texOffs(100, 52)
                .addBox(3.0F, -5.0F, -4.5F, 2.0F, 11.0F, 1.0F),
            PartPose.offset(0.0F, 8.0F, 0.0F)
        );
        feminine.addOrReplaceChild(
            "dress_skirt",
            CubeListBuilder.create().texOffs(60, 52)
                .addBox(-5.0F, 8.0F, -2.5F, 10.0F, 12.0F, 5.0F),
            PartPose.offset(0.0F, 8.0F, 0.0F)
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
        masculineVariant.visible = state.variant == Variant.MASCULINE;
        feminineVariant.visible = state.variant == Variant.FEMININE;
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;
        final float pace = state.walkAnimationPos * 0.6F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F) * 0.75F;
        rightLeg.xRot = Mth.cos(pace) * stride;
        leftLeg.xRot = Mth.cos(pace + Mth.PI) * stride;
        rightArm.xRot = Mth.cos(pace + Mth.PI) * stride * 0.55F;
        leftArm.xRot = Mth.cos(pace) * stride * 0.55F;
        body.zRot = Mth.sin(state.ageInTicks * 0.045F) * 0.018F;
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
        copyRotation(head, masculineHair);
        copyRotation(head, feminineHair);
        copyRotation(head, feminineBackHair);
        copyRotation(head, feminineRightLock);
        copyRotation(head, feminineLeftLock);
        copyRotation(body, masculineCollar);
        copyRotation(body, masculineCoatTail);
        copyRotation(body, feminineSkirt);
    }

    private static void copyRotation(final ModelPart source, final ModelPart target) {
        target.xRot = source.xRot;
        target.yRot = source.yRot;
        target.zRot = source.zRot;
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
