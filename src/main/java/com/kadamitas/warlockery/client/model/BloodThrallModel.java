package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.VampireCourtEntity;
import com.kadamitas.warlockery.entity.VampireCourtRules.AssaultRole;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public final class BloodThrallModel extends EntityModel<BloodThrallModel.State> {
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart head;
    private final ModelPart narrowRibcage;
    private final ModelPart shellRestraint;
    private final ModelPart pearlCage;
    private final ModelPart coralSeal;
    private final ModelPart rightLongArm;
    private final ModelPart leftLongArm;
    private final ModelPart rightCrouchedLeg;
    private final ModelPart leftCrouchedLeg;

    public BloodThrallModel(final ModelPart root) {
        super(root);
        head = root.getChild("head");
        narrowRibcage = root.getChild("narrow_ribcage");
        shellRestraint = narrowRibcage.getChild("shell_restraint");
        pearlCage = narrowRibcage.getChild("pearl_cage");
        coralSeal = narrowRibcage.getChild("coral_seal");
        rightLongArm = root.getChild("right_long_arm");
        leftLongArm = root.getChild("left_long_arm");
        rightCrouchedLeg = root.getChild("right_crouched_leg");
        leftCrouchedLeg = root.getChild("left_crouched_leg");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.5F, -7.0F, -3.5F, 7.0F, 7.0F, 7.0F),
            PartPose.offsetAndRotation(0.0F, 8.0F, -0.4F, 0.08F, 0.0F, 0.0F)
        );
        final PartDefinition ribcage = root.addOrReplaceChild(
            "narrow_ribcage",
            CubeListBuilder.create().texOffs(28, 0)
                .addBox(-3.0F, 0.0F, -2.0F, 6.0F, 12.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, 0.08F, 0.0F, 0.0F)
        );
        ribcage.addOrReplaceChild(
            "shell_restraint",
            CubeListBuilder.create().texOffs(48, 0)
                .addBox(-4.0F, -1.0F, -2.5F, 8.0F, 2.0F, 5.0F, new CubeDeformation(0.08F)),
            PartPose.offset(0.0F, 1.2F, 0.0F)
        );
        ribcage.addOrReplaceChild(
            "pearl_cage",
            CubeListBuilder.create().texOffs(76, 0)
                .addBox(-3.0F, -0.5F, -0.5F, 6.0F, 1.0F, 1.0F),
            PartPose.offset(0.0F, 5.5F, -2.0F)
        );
        ribcage.addOrReplaceChild(
            "coral_seal",
            CubeListBuilder.create().texOffs(90, 0)
                .addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 1.0F),
            PartPose.offset(2.0F, 8.5F, -2.1F)
        );
        root.addOrReplaceChild(
            "right_long_arm",
            CubeListBuilder.create().texOffs(0, 20)
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 12.0F, 3.0F),
            PartPose.offsetAndRotation(-4.5F, 9.0F, 0.0F, 0.0F, 0.0F, 0.04F)
        );
        root.addOrReplaceChild(
            "left_long_arm",
            CubeListBuilder.create().texOffs(12, 20)
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 12.0F, 3.0F),
            PartPose.offsetAndRotation(4.5F, 9.0F, 0.0F, 0.0F, 0.0F, -0.04F)
        );
        root.addOrReplaceChild(
            "right_crouched_leg",
            CubeListBuilder.create().texOffs(24, 20)
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 12.0F, 3.0F),
            PartPose.offset(-1.6F, 20.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "left_crouched_leg",
            CubeListBuilder.create().texOffs(36, 20)
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 12.0F, 3.0F),
            PartPose.offset(1.6F, 20.0F, 0.0F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    public static void extractRenderState(
        final VampireCourtEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.activity = activityFor(entity.presentationIntent(), entity.presentationAssaultRole());
    }

    private static Activity activityFor(
        final com.kadamitas.warlockery.entity.VampireCourtRules.Intent intent,
        final AssaultRole role
    ) {
        if (role == AssaultRole.BOUND_GUARD) {
            if (intent == com.kadamitas.warlockery.entity.VampireCourtRules.Intent.WAVERING) {
                return Activity.WAVERING;
            }
            if (intent == com.kadamitas.warlockery.entity.VampireCourtRules.Intent.STALK
                || intent == com.kadamitas.warlockery.entity.VampireCourtRules.Intent.FEED
                || intent == com.kadamitas.warlockery.entity.VampireCourtRules.Intent.ASSAULT_LEAD) {
                return Activity.ATTACKING;
            }
            return Activity.BOUND_GUARD;
        }
        return switch (intent) {
            case THRESHOLD_GUARD, INTERCEPT -> Activity.BOUND_GUARD;
            case WAVERING -> Activity.WAVERING;
            case STALK, FEED, ASSAULT_LEAD -> Activity.ATTACKING;
            default -> Activity.UNBOUND;
        };
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot += state.xRot * Mth.DEG_TO_RAD;
        final float pace = state.walkAnimationPos * 0.72F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F);
        rightCrouchedLeg.xRot += Mth.cos(pace) * 0.55F * stride;
        leftCrouchedLeg.xRot += Mth.cos(pace + Mth.PI) * 0.55F * stride;
        rightLongArm.xRot += Mth.cos(pace + Mth.PI) * 0.55F * stride;
        leftLongArm.xRot += Mth.cos(pace) * 0.55F * stride;
        narrowRibcage.yRot = Mth.sin(state.ageInTicks * 0.045F) * 0.04F;
        pearlCage.zRot = Mth.sin(state.ageInTicks * 0.08F) * 0.025F;
        if (state.activity == Activity.BOUND_GUARD) {
            head.xRot += 0.14F;
            rightLongArm.xRot = -0.8F;
            leftLongArm.xRot = -0.8F;
            shellRestraint.zRot = 0.08F;
        } else if (state.activity == Activity.WAVERING) {
            head.zRot = Mth.sin(state.ageInTicks * 0.25F) * 0.22F;
            narrowRibcage.zRot = 0.12F;
            rightLongArm.zRot += 0.18F;
            leftLongArm.zRot -= 0.12F;
            coralSeal.yRot = 0.3F;
        } else if (state.activity == Activity.ATTACKING) {
            rightLongArm.xRot = -1.35F;
            leftLongArm.xRot = -0.35F;
            head.xRot -= 0.18F;
        }
    }

    public enum Activity {
        UNBOUND,
        BOUND_GUARD,
        WAVERING,
        ATTACKING
    }

    public static final class State extends LivingEntityRenderState {
        public Activity activity = Activity.UNBOUND;
    }
}
