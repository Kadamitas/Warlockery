package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.WerewolfHunterEntity;
import com.kadamitas.warlockery.entity.WerewolfHunterRules;
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

public final class WerewolfHunterModel extends EntityModel<WerewolfHunterModel.State>
    implements ArmedModel<WerewolfHunterModel.State> {
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart rightCoatTail;
    private final ModelPart leftCoatTail;
    private final ModelPart crossbowSling;
    private final ModelPart silverBoltCase;

    public WerewolfHunterModel(final ModelPart root) {
        super(root);
        head = root.getChild("head");
        body = root.getChild("body");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
        final ModelPart coat = body.getChild("long_coat");
        rightCoatTail = coat.getChild("right_coat_tail");
        leftCoatTail = coat.getChild("left_coat_tail");
        crossbowSling = body.getChild("crossbow_sling");
        silverBoltCase = body.getChild("silver_bolt_case");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition head = root.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -6.0F, -3.5F, 7.0F, 6.0F, 7.0F),
            PartPose.offset(0.0F, 7.0F, 0.0F)
        );
        head.addOrReplaceChild(
            "right_eye",
            CubeListBuilder.create().texOffs(30, 74).addBox(-0.5F, -0.5F, -0.25F, 1.0F, 1.0F, 0.5F),
            PartPose.offset(-1.35F, -2.9F, -3.65F)
        );
        head.addOrReplaceChild(
            "left_eye",
            CubeListBuilder.create().texOffs(30, 74).mirror().addBox(-0.5F, -0.5F, -0.25F, 1.0F, 1.0F, 0.5F),
            PartPose.offset(1.35F, -2.9F, -3.65F)
        );
        head.addOrReplaceChild(
            "nose",
            CubeListBuilder.create().texOffs(36, 74).addBox(-0.5F, -1.0F, -0.25F, 1.0F, 2.0F, 0.5F),
            PartPose.offset(0.0F, -1.7F, -3.65F)
        );
        head.addOrReplaceChild(
            "mouth",
            CubeListBuilder.create().texOffs(42, 74).addBox(-1.0F, -0.5F, -0.25F, 2.0F, 1.0F, 0.5F),
            PartPose.offset(0.0F, -0.25F, -3.65F)
        );
        head.addOrReplaceChild(
            "right_sideburn",
            CubeListBuilder.create().texOffs(50, 74).addBox(-0.5F, -2.0F, -0.25F, 1.0F, 4.0F, 0.5F),
            PartPose.offset(-3.15F, -2.0F, -3.62F)
        );
        head.addOrReplaceChild(
            "left_sideburn",
            CubeListBuilder.create().texOffs(50, 74).mirror().addBox(-0.5F, -2.0F, -0.25F, 1.0F, 4.0F, 0.5F),
            PartPose.offset(3.15F, -2.0F, -3.62F)
        );
        final PartDefinition hat = head.addOrReplaceChild(
            "broad_brimmed_hat",
            CubeListBuilder.create()
                .texOffs(30, 0).addBox(-6.0F, -1.0F, -6.0F, 12.0F, 1.0F, 12.0F)
                .texOffs(80, 0).addBox(-4.0F, -5.0F, -4.0F, 8.0F, 4.0F, 8.0F),
            PartPose.offsetAndRotation(0.0F, -5.8F, 0.0F, 0.0F, 0.0F, -0.06F)
        );
        hat.addOrReplaceChild(
            "hat_band",
            CubeListBuilder.create().texOffs(0, 94).addBox(-4.25F, -1.0F, -4.25F, 8.5F, 1.0F, 8.5F),
            PartPose.offset(0.0F, -0.8F, 0.0F)
        );

        final PartDefinition body = root.addOrReplaceChild(
            "body",
            CubeListBuilder.create().texOffs(0, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 10.0F, 4.0F),
            PartPose.offset(0.0F, 7.0F, 0.0F)
        );
        final PartDefinition coat = body.addOrReplaceChild(
            "long_coat",
            CubeListBuilder.create()
                .texOffs(26, 16).addBox(-4.5F, -0.5F, -2.5F, 9.0F, 11.0F, 5.0F)
                .texOffs(56, 16).addBox(-1.0F, 0.0F, -2.9F, 2.0F, 10.0F, 1.0F),
            PartPose.ZERO
        );
        coat.addOrReplaceChild(
            "right_lapel",
            CubeListBuilder.create().texOffs(64, 16).addBox(-2.0F, 0.0F, -0.5F, 2.0F, 7.0F, 1.0F),
            PartPose.offsetAndRotation(-0.3F, 0.0F, -2.8F, 0.0F, 0.0F, -0.26F)
        );
        coat.addOrReplaceChild(
            "left_lapel",
            CubeListBuilder.create().texOffs(64, 16).mirror().addBox(0.0F, 0.0F, -0.5F, 2.0F, 7.0F, 1.0F),
            PartPose.offsetAndRotation(0.3F, 0.0F, -2.8F, 0.0F, 0.0F, 0.26F)
        );
        coat.addOrReplaceChild(
            "right_coat_tail",
            CubeListBuilder.create().texOffs(0, 34).addBox(-4.0F, 0.0F, -2.5F, 4.0F, 10.0F, 5.0F),
            PartPose.offsetAndRotation(-0.15F, 9.5F, 0.25F, 0.08F, 0.0F, 0.04F)
        );
        coat.addOrReplaceChild(
            "left_coat_tail",
            CubeListBuilder.create().texOffs(0, 34).mirror().addBox(0.0F, 0.0F, -2.5F, 4.0F, 10.0F, 5.0F),
            PartPose.offsetAndRotation(0.15F, 9.5F, 0.25F, -0.08F, 0.0F, -0.04F)
        );

        final PartDefinition boltCase = body.addOrReplaceChild(
            "silver_bolt_case",
            CubeListBuilder.create().texOffs(30, 34).addBox(-1.5F, -4.0F, -1.5F, 3.0F, 8.0F, 3.0F),
            PartPose.offsetAndRotation(4.1F, 5.0F, 1.3F, 0.0F, 0.0F, -0.16F)
        );
        boltCase.addOrReplaceChild(
            "silver_bolt_fan",
            CubeListBuilder.create().texOffs(44, 34).addBox(-2.0F, -1.0F, -1.5F, 4.0F, 2.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, -4.4F, 0.0F, -0.12F, 0.0F, 0.20F)
        );
        body.addOrReplaceChild(
            "crossbow_sling",
            CubeListBuilder.create().texOffs(60, 34).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 12.0F, 1.0F),
            PartPose.offsetAndRotation(-0.5F, 5.0F, 2.3F, 0.0F, 0.0F, -0.62F)
        );
        body.addOrReplaceChild(
            "field_satchel",
            CubeListBuilder.create().texOffs(66, 34).addBox(-2.0F, -2.5F, -1.5F, 4.0F, 5.0F, 3.0F),
            PartPose.offsetAndRotation(-4.2F, 6.7F, 1.7F, 0.0F, 0.0F, 0.12F)
        );

        addArm(root, "right_arm", -5.0F, false);
        addArm(root, "left_arm", 5.0F, true);
        addLeg(root, "right_leg", -2.1F, false);
        addLeg(root, "left_leg", 2.1F, true);
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private static void addArm(
        final PartDefinition root,
        final String name,
        final float x,
        final boolean mirror
    ) {
        root.addOrReplaceChild(
            name,
            CubeListBuilder.create().texOffs(0, 52).mirror(mirror)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F),
            PartPose.offset(x, 7.5F, 0.0F)
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
            CubeListBuilder.create().texOffs(18, 52).mirror(mirror)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F),
            PartPose.offset(x, 17.0F, 0.0F)
        );
        final String side = name.startsWith("right") ? "right" : "left";
        leg.addOrReplaceChild(
            side + "_hunter_boot",
            CubeListBuilder.create().texOffs(36, 52).mirror(mirror)
                .addBox(-2.0F, 0.0F, -2.5F, 4.0F, 7.0F, 5.0F),
            PartPose.offset(0.0F, 3.0F, -0.2F)
        );
    }

    public static void extractRenderState(
        final WerewolfHunterEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.activity = activityFor(entity.presentationIntent());
        state.chargingCrossbow = entity.isChargingCrossbow();
    }

    private static Activity activityFor(final WerewolfHunterRules.Intent intent) {
        return switch (intent) {
            case IDLE -> Activity.IDLE;
            case PATROL -> Activity.PATROLLING;
            case INVESTIGATE -> Activity.INVESTIGATING;
            case WARN -> Activity.WARNING;
            case ENGAGE -> Activity.ENGAGING;
            case REPOSITION -> Activity.REPOSITIONING;
            case RETREAT -> Activity.RETREATING;
            case RESUPPLY -> Activity.RESUPPLYING;
            case RETURN -> Activity.RETURNING;
        };
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
        rightArm.xRot = Mth.cos(pace + Mth.PI) * 0.55F * stride;
        leftArm.xRot = Mth.cos(pace) * 0.55F * stride;
        rightCoatTail.xRot += Mth.cos(pace + Mth.PI) * stride * 0.24F;
        leftCoatTail.xRot += Mth.cos(pace) * stride * 0.24F;
        crossbowSling.zRot = -0.62F + Mth.sin(state.ageInTicks * 0.05F) * 0.025F;

        if (state.activity == Activity.WARNING) {
            body.yRot = -0.22F;
            rightArm.xRot = -1.0F;
            rightArm.yRot = -0.45F;
            leftArm.xRot = -0.38F;
            head.xRot -= 0.10F;
        } else if (state.activity == Activity.ENGAGING) {
            final float draw = Math.max(
                state.chargingCrossbow ? 1.0F : 0.0F,
                Mth.clamp(state.attackTime, 0.0F, 1.0F)
            );
            rightArm.xRot = -1.35F;
            rightArm.yRot = -0.52F;
            leftArm.xRot = -0.85F - draw * 0.60F;
            leftArm.yRot = 0.78F;
            head.xRot -= 0.14F;
            body.xRot = -0.05F - draw * 0.08F;
        } else if (state.activity == Activity.REPOSITIONING) {
            body.yRot = 0.28F;
            rightArm.zRot = 0.18F;
            leftArm.zRot = -0.18F;
        } else if (state.activity == Activity.RETREATING) {
            body.xRot = 0.18F;
            silverBoltCase.zRot = -0.28F;
            rightCoatTail.xRot += 0.16F;
            leftCoatTail.xRot += 0.16F;
        }
    }

    @Override
    public void translateToHand(final State state, final HumanoidArm arm, final PoseStack poseStack) {
        (arm == HumanoidArm.LEFT ? leftArm : rightArm).translateAndRotate(poseStack);
    }

    public enum Activity {
        IDLE,
        PATROLLING,
        INVESTIGATING,
        WARNING,
        ENGAGING,
        REPOSITIONING,
        RETREATING,
        RESUPPLYING,
        RETURNING
    }

    public static final class State extends ArmedEntityRenderState {
        public Activity activity = Activity.IDLE;
        public boolean chargingCrossbow;
    }
}
