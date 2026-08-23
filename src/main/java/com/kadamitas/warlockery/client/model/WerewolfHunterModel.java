package com.kadamitas.warlockery.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.kadamitas.warlockery.entity.WerewolfHunterEntity;
import com.kadamitas.warlockery.entity.WerewolfHunterRules;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
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
            "half_brim_hood",
            CubeListBuilder.create().texOffs(30, 0)
                .addBox(-4.25F, -6.75F, -4.0F, 8.5F, 7.0F, 8.0F, new CubeDeformation(0.15F))
                .texOffs(64, 0).addBox(-5.5F, -1.0F, -5.5F, 11.0F, 1.0F, 7.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.09F)
        );
        final PartDefinition body = root.addOrReplaceChild(
            "body",
            CubeListBuilder.create().texOffs(0, 20).addBox(-4.0F, 0.0F, -2.5F, 8.0F, 11.0F, 5.0F),
            PartPose.offset(0.0F, 7.0F, 0.0F)
        );
        final PartDefinition coat = body.addOrReplaceChild(
            "split_field_coat",
            CubeListBuilder.create().texOffs(30, 20)
                .addBox(-5.0F, -1.0F, -3.0F, 10.0F, 13.0F, 6.0F, new CubeDeformation(0.12F))
                .texOffs(64, 20).addBox(-4.5F, 10.0F, -2.75F, 4.0F, 8.0F, 5.5F)
                .texOffs(84, 20).addBox(0.5F, 10.0F, -2.75F, 4.0F, 8.0F, 5.5F),
            PartPose.offset(0.0F, 0.0F, 0.0F)
        );
        coat.addOrReplaceChild(
            "right_rear_coat_panel",
            CubeListBuilder.create().texOffs(64, 20)
                .addBox(-2.0F, 0.0F, -2.75F, 4.0F, 8.0F, 5.5F),
            PartPose.offsetAndRotation(-3.0F, 10.0F, 1.0F, 0.10F, 0.0F, 0.08F)
        );
        coat.addOrReplaceChild(
            "center_rear_coat_panel",
            CubeListBuilder.create().texOffs(64, 20)
                .addBox(-2.0F, 0.0F, -2.75F, 4.0F, 8.0F, 5.5F),
            PartPose.offsetAndRotation(0.0F, 10.0F, 3.0F, 0.16F, 0.0F, 0.0F)
        );
        coat.addOrReplaceChild(
            "left_rear_coat_panel",
            CubeListBuilder.create().texOffs(84, 20)
                .addBox(-2.0F, 0.0F, -2.75F, 4.0F, 8.0F, 5.5F),
            PartPose.offsetAndRotation(3.0F, 10.0F, 5.0F, 0.22F, 0.0F, -0.08F)
        );
        final PartDefinition boltCase = body.addOrReplaceChild(
            "silver_bolt_case",
            CubeListBuilder.create().texOffs(0, 42)
                .addBox(-1.5F, -5.0F, -1.5F, 3.0F, 10.0F, 3.0F)
                .texOffs(14, 42).addBox(-2.0F, -5.5F, -2.0F, 4.0F, 1.0F, 4.0F),
            PartPose.offsetAndRotation(4.6F, 6.0F, 1.5F, 0.0F, 0.0F, -0.2F)
        );
        boltCase.addOrReplaceChild(
            "silver_bolt_fan",
            CubeListBuilder.create().texOffs(14, 42)
                .addBox(-2.0F, -5.5F, -2.0F, 4.0F, 1.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, -0.12F, 0.0F, 0.28F)
        );
        body.addOrReplaceChild(
            "crossbow_sling",
            CubeListBuilder.create().texOffs(32, 42)
                .addBox(-0.75F, -7.0F, -0.5F, 1.5F, 14.0F, 1.0F),
            PartPose.offsetAndRotation(-0.5F, 5.0F, 2.8F, 0.0F, 0.0F, -0.62F)
        );
        body.addOrReplaceChild(
            "field_satchel",
            CubeListBuilder.create().texOffs(68, 42)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F),
            PartPose.offsetAndRotation(-4.4F, 7.0F, 3.8F, 0.10F, 0.0F, 0.16F)
        );
        body.addOrReplaceChild(
            "raised_shoulder_guard",
            CubeListBuilder.create().texOffs(48, 42)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 10.0F, 4.0F),
            PartPose.offsetAndRotation(4.3F, -1.8F, 0.0F, -0.12F, 0.0F, -0.18F)
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
            CubeListBuilder.create().texOffs(48, 42).mirror(mirror)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 10.0F, 4.0F),
            PartPose.offset(x, 8.0F, 0.0F)
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
            CubeListBuilder.create().texOffs(68, 42).mirror(mirror)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F),
            PartPose.offset(x, 17.0F, 0.0F)
        );
        final String side = name.startsWith("right") ? "right" : "left";
        leg.addOrReplaceChild(
            side + "_hunter_boot",
            CubeListBuilder.create().texOffs(68, 42).mirror(mirror)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.5F)),
            PartPose.offsetAndRotation(0.0F, 3.0F, 0.0F, -0.08F, 0.0F, 0.0F)
        );
    }

    public static void extractRenderState(
        final WerewolfHunterEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.activity = activityFor(entity.presentationIntent());
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
        crossbowSling.zRot = -0.62F + Mth.sin(state.ageInTicks * 0.05F) * 0.02F;
        if (state.activity == Activity.WARNING) {
            body.yRot = -0.22F;
            rightLeg.zRot = 0.2F;
            leftLeg.zRot = -0.08F;
            rightArm.xRot = -0.95F;
            rightArm.yRot = -0.45F;
            leftArm.xRot = -0.35F;
        } else if (state.activity == Activity.ENGAGING) {
            final float draw = 0.8F + state.attackTime * 0.5F;
            rightArm.xRot = -1.35F;
            rightArm.yRot = -0.48F;
            leftArm.xRot = -draw;
            leftArm.yRot = 0.72F;
            head.xRot -= 0.12F;
        } else if (state.activity == Activity.REPOSITIONING) {
            body.yRot = 0.28F;
            rightArm.zRot = 0.18F;
            leftArm.zRot = -0.18F;
        } else if (state.activity == Activity.RETREATING) {
            body.xRot = 0.18F;
            silverBoltCase.zRot = -0.28F;
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
    }
}
