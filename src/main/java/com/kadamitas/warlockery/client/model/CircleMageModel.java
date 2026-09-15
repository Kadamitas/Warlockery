package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.CircleMageEntity;
import com.mojang.blaze3d.vertex.PoseStack;
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

/**
 * A player-proportioned robed scholar. The left 64x64 of the atlas is the standard player skin
 * layout (head, hat overlay, body, arms, legs and all their outer layers); the right half holds the
 * islands of the modeled wide-brim wizard hat. The Circle Magic book is rendered in the left hand
 * by the renderer's item layer.
 */
public final class CircleMageModel extends EntityModel<CircleMageModel.State>
    implements ArmedModel<CircleMageModel.State> {
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 64;

    private final ModelPart head;
    private final ModelPart hood;
    private final ModelPart hatBrim;
    private final ModelPart hatCone1;
    private final ModelPart hatCone2;
    private final ModelPart hatCone3;
    private final ModelPart hatTip;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public CircleMageModel(final ModelPart root) {
        super(root);
        head = root.getChild("head");
        hood = head.getChild("hood");
        hatBrim = head.getChild("hat_brim");
        hatCone1 = hatBrim.getChild("hat_cone_1");
        hatCone2 = hatCone1.getChild("hat_cone_2");
        hatCone3 = hatCone2.getChild("hat_cone_3");
        hatTip = hatCone3.getChild("hat_tip");
        body = root.getChild("body");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();

        // Vanilla player-skin islands so the atlas can be authored like a 64x64 skin.
        final PartDefinition head = root.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
            PartPose.ZERO
        );
        head.addOrReplaceChild(
            "hood",
            CubeListBuilder.create().texOffs(32, 0)
                .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.5F)),
            PartPose.ZERO
        );

        // Modeled wizard hat: a wide brim and a tapered cone of shrinking boxes. Its islands sit in
        // the right half of the atlas (x >= 64), outside the standard skin layout.
        final PartDefinition hatBrim = head.addOrReplaceChild(
            "hat_brim",
            CubeListBuilder.create().texOffs(64, 0).addBox(-5.5F, -1.0F, -5.5F, 11.0F, 1.0F, 11.0F),
            PartPose.offsetAndRotation(0.0F, -8.5F, 0.0F, -0.06F, 0.0F, 0.04F)
        );
        final PartDefinition hatCone1 = hatBrim.addOrReplaceChild(
            "hat_cone_1",
            CubeListBuilder.create().texOffs(108, 0).addBox(-2.5F, -4.0F, -2.5F, 5.0F, 4.0F, 5.0F),
            PartPose.offset(0.0F, -1.0F, 0.0F)
        );
        final PartDefinition hatCone2 = hatCone1.addOrReplaceChild(
            "hat_cone_2",
            CubeListBuilder.create().texOffs(64, 16).addBox(-1.5F, -4.0F, -1.5F, 3.0F, 4.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, 0.05F, 0.0F, 0.12F)
        );
        final PartDefinition hatCone3 = hatCone2.addOrReplaceChild(
            "hat_cone_3",
            CubeListBuilder.create().texOffs(76, 16).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, 0.08F, 0.0F, 0.22F)
        );
        hatCone3.addOrReplaceChild(
            "hat_tip",
            CubeListBuilder.create().texOffs(84, 16).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, 0.12F, 0.0F, 0.42F)
        );

        // Base limbs plus the skin's outer layers, so the atlas renders as a skin editor shows it.
        final CubeDeformation outer = new CubeDeformation(0.25F);
        final PartDefinition body = root.addOrReplaceChild(
            "body",
            CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F),
            PartPose.ZERO
        );
        body.addOrReplaceChild(
            "jacket",
            CubeListBuilder.create().texOffs(16, 32).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, outer),
            PartPose.ZERO
        );
        final PartDefinition rightArm = root.addOrReplaceChild(
            "right_arm",
            CubeListBuilder.create().texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offset(-5.0F, 2.0F, 0.0F)
        );
        rightArm.addOrReplaceChild(
            "right_sleeve",
            CubeListBuilder.create().texOffs(40, 32).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer),
            PartPose.ZERO
        );
        final PartDefinition leftArm = root.addOrReplaceChild(
            "left_arm",
            CubeListBuilder.create().texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offset(5.0F, 2.0F, 0.0F)
        );
        leftArm.addOrReplaceChild(
            "left_sleeve",
            CubeListBuilder.create().texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer),
            PartPose.ZERO
        );
        final PartDefinition rightLeg = root.addOrReplaceChild(
            "right_leg",
            CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offset(-1.9F, 12.0F, 0.0F)
        );
        rightLeg.addOrReplaceChild(
            "right_pants",
            CubeListBuilder.create().texOffs(0, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer),
            PartPose.ZERO
        );
        final PartDefinition leftLeg = root.addOrReplaceChild(
            "left_leg",
            CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offset(1.9F, 12.0F, 0.0F)
        );
        leftLeg.addOrReplaceChild(
            "left_pants",
            CubeListBuilder.create().texOffs(0, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer),
            PartPose.ZERO
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    public static void extractRenderState(
        final CircleMageEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.activity = Activity.valueOf(entity.presentationActivity().name());
        state.focusPrepared = entity.presentationFocusPrepared();
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;
        final float pace = state.walkAnimationPos * 0.6662F;
        final float stride = Math.min(state.walkAnimationSpeed, 1.0F);
        rightLeg.xRot = Mth.cos(pace) * 1.2F * stride;
        leftLeg.xRot = Mth.cos(pace + Mth.PI) * 1.2F * stride;
        rightArm.xRot = Mth.cos(pace + Mth.PI) * 1.0F * stride;
        leftArm.xRot = Mth.cos(pace) * 1.0F * stride;
        // A quiet idle sway so the arms never read as a stiff T-pose.
        final float breath = Mth.sin(state.ageInTicks * 0.067F) * 0.05F;
        rightArm.zRot = 0.06F + breath;
        leftArm.zRot = -0.06F - breath;
        hatTip.zRot += Mth.sin(state.ageInTicks * 0.09F) * 0.06F;

        if (state.focusPrepared && state.activity != Activity.WITHDRAWING) {
            // The book is out: keep it held up at the chest.
            leftArm.xRot = -0.75F;
            leftArm.yRot = 0.25F;
        }
        if (state.activity == Activity.STUDYING) {
            // Both hands raised to the open book, chin dropped to read it.
            head.xRot += 0.45F;
            leftArm.xRot = -1.15F;
            leftArm.yRot = 0.42F;
            leftArm.zRot = -0.12F;
            rightArm.xRot = -1.0F;
            rightArm.yRot = -0.48F;
            rightArm.zRot = 0.1F;
            rightArm.xRot += Mth.sin(state.ageInTicks * 0.11F) * 0.04F;
        } else if (state.activity == Activity.DEFENDING) {
            // Casting hand thrust forward, book braced against the chest.
            head.xRot -= 0.1F;
            rightArm.xRot = -1.55F;
            rightArm.yRot = -0.15F;
            rightArm.zRot = 0.02F;
            leftArm.xRot = -0.85F;
            leftArm.yRot = 0.55F;
            leftArm.zRot = -0.05F;
            body.yRot = -0.12F;
        } else if (state.activity == Activity.WITHDRAWING) {
            // Turning away, arms swept back and hat tucked low.
            body.xRot = 0.14F;
            head.xRot -= 0.18F;
            rightArm.xRot = 0.55F;
            rightArm.zRot = 0.32F;
            leftArm.xRot = 0.55F;
            leftArm.zRot = -0.32F;
            hatBrim.xRot -= 0.1F;
        }
    }

    @Override
    public void translateToHand(final State state, final HumanoidArm arm, final PoseStack poseStack) {
        final ModelPart part = arm == HumanoidArm.LEFT ? leftArm : rightArm;
        part.translateAndRotate(poseStack);
        poseStack.translate(arm == HumanoidArm.LEFT ? 0.03F : -0.03F, 0.05F, 0.0F);
    }

    public enum Activity {
        IDLE,
        FOLLOWING,
        DEFENDING,
        STUDYING,
        WITHDRAWING
    }

    /** Armed so the renderer's item layer can put the Circle Magic book in the left hand. */
    public static final class State extends ArmedEntityRenderState {
        public Activity activity = Activity.IDLE;
        public boolean focusPrepared;
    }
}
