package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.BansheeEntity;
import com.kadamitas.warlockery.entity.BansheeRules.Mode;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * An upright spectral woman with a readable face, long hair, separate arms, and a dress
 * that dissolves into three ghostly tails. The authored proportions are expressed directly
 * in the cuboids so no base-pose scaling can flatten or hide the anatomy.
 */
public final class BansheeModel extends EntityModel<BansheeModel.State> {
    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart head;
    private final ModelPart mouth;
    private final ModelPart hairLeft;
    private final ModelPart hairRight;
    private final ModelPart wailFlareLeft;
    private final ModelPart wailFlareRight;
    private final ModelPart shoulderYoke;
    private final ModelPart bodice;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightHand;
    private final ModelPart leftHand;
    private final ModelPart dress;
    private final ModelPart veilLeft;
    private final ModelPart veilRight;
    private final ModelPart bellLowerMass;

    public BansheeModel(final ModelPart root) {
        super(root);
        head = root.getChild("face_head");
        mouth = head.getChild("open_mouth");
        hairLeft = head.getChild("hair_frame_left");
        hairRight = head.getChild("hair_frame_right");
        wailFlareLeft = head.getChild("wail_flare_left");
        wailFlareRight = head.getChild("wail_flare_right");
        shoulderYoke = root.getChild("shoulder_yoke");
        bodice = shoulderYoke.getChild("bodice");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightHand = rightArm.getChild("right_hand");
        leftHand = leftArm.getChild("left_hand");
        dress = root.getChild("layered_dress");
        veilLeft = dress.getChild("veil_left");
        veilRight = dress.getChild("veil_right");
        bellLowerMass = dress.getChild("bell_lower_mass");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();

        final PartDefinition head = root.addOrReplaceChild(
            "face_head",
            CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.5F, -8.0F, -3.5F, 7.0F, 8.0F, 7.0F),
            PartPose.ZERO
        );
        head.addOrReplaceChild(
            "open_mouth",
            CubeListBuilder.create().texOffs(32, 0)
                .addBox(-1.5F, -1.0F, -0.5F, 3.0F, 2.0F, 1.0F),
            PartPose.offset(0.0F, -1.0F, -3.75F)
        );
        head.addOrReplaceChild(
            "hair_crown",
            CubeListBuilder.create().texOffs(42, 0)
                .addBox(-4.0F, -1.5F, -4.0F, 8.0F, 3.0F, 8.0F),
            PartPose.offset(0.0F, -7.0F, 0.0F)
        );
        head.addOrReplaceChild(
            "hair_back",
            CubeListBuilder.create().texOffs(0, 20)
                .addBox(-4.0F, 0.0F, -0.5F, 8.0F, 14.0F, 2.0F),
            PartPose.offset(0.0F, -7.0F, 3.25F)
        );
        head.addOrReplaceChild(
            "hair_frame_left",
            CubeListBuilder.create().texOffs(24, 20)
                .addBox(-1.0F, 0.0F, -1.5F, 2.0F, 13.0F, 3.0F),
            PartPose.offsetAndRotation(-3.75F, -6.5F, -1.25F, 0.03F, 0.0F, 0.04F)
        );
        head.addOrReplaceChild(
            "hair_frame_right",
            CubeListBuilder.create().texOffs(24, 20).mirror()
                .addBox(-1.0F, 0.0F, -1.5F, 2.0F, 13.0F, 3.0F),
            PartPose.offsetAndRotation(3.75F, -6.5F, -1.25F, 0.03F, 0.0F, -0.04F)
        );
        head.addOrReplaceChild(
            "wail_flare_left",
            CubeListBuilder.create().texOffs(36, 20)
                .addBox(-1.0F, 0.0F, -0.5F, 1.0F, 9.0F, 1.0F),
            PartPose.offsetAndRotation(-3.1F, -5.5F, -3.55F, 0.0F, 0.0F, 0.09F)
        );
        head.addOrReplaceChild(
            "wail_flare_right",
            CubeListBuilder.create().texOffs(36, 20).mirror()
                .addBox(0.0F, 0.0F, -0.5F, 1.0F, 9.0F, 1.0F),
            PartPose.offsetAndRotation(3.1F, -5.5F, -3.55F, 0.0F, 0.0F, -0.09F)
        );

        final PartDefinition shoulderYoke = root.addOrReplaceChild(
            "shoulder_yoke",
            CubeListBuilder.create()
                .texOffs(0, 40).addBox(-5.0F, 0.0F, -2.0F, 10.0F, 2.0F, 4.0F)
                .texOffs(80, 0).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 2.0F, 3.0F),
            PartPose.ZERO
        );
        shoulderYoke.addOrReplaceChild(
            "bodice",
            CubeListBuilder.create()
                .texOffs(30, 40).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 7.0F, 4.0F)
                .texOffs(56, 40).addBox(-3.0F, 7.0F, -2.0F, 6.0F, 2.0F, 4.0F),
            PartPose.offset(0.0F, 1.5F, 0.0F)
        );

        final PartDefinition rightArm = root.addOrReplaceChild(
            "right_arm",
            CubeListBuilder.create()
                .texOffs(0, 54).addBox(-2.0F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F)
                .texOffs(14, 54).addBox(-1.5F, 4.0F, -1.0F, 2.0F, 6.0F, 2.0F),
            PartPose.offsetAndRotation(-5.0F, 1.0F, 0.0F, -0.04F, 0.0F, 0.08F)
        );
        rightArm.addOrReplaceChild(
            "right_hand",
            CubeListBuilder.create().texOffs(24, 54)
                .addBox(-1.5F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F),
            PartPose.offset(0.0F, 10.0F, 0.0F)
        );
        final PartDefinition leftArm = root.addOrReplaceChild(
            "left_arm",
            CubeListBuilder.create()
                .texOffs(0, 54).mirror().addBox(-1.0F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F)
                .texOffs(14, 54).mirror().addBox(-0.5F, 4.0F, -1.0F, 2.0F, 6.0F, 2.0F),
            PartPose.offsetAndRotation(5.0F, 1.0F, 0.0F, 0.04F, 0.0F, -0.08F)
        );
        leftArm.addOrReplaceChild(
            "left_hand",
            CubeListBuilder.create().texOffs(24, 54).mirror()
                .addBox(-0.5F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F),
            PartPose.offset(0.0F, 10.0F, 0.0F)
        );

        final PartDefinition dress = root.addOrReplaceChild(
            "layered_dress",
            CubeListBuilder.create().texOffs(0, 68)
                .addBox(-3.0F, 0.0F, -2.0F, 6.0F, 2.0F, 4.0F),
            PartPose.offset(0.0F, 9.5F, 0.0F)
        );
        dress.addOrReplaceChild(
            "upper_skirt",
            CubeListBuilder.create().texOffs(22, 68)
                .addBox(-4.0F, 0.0F, -2.5F, 8.0F, 6.0F, 5.0F),
            PartPose.offset(0.0F, 1.5F, 0.0F)
        );
        dress.addOrReplaceChild(
            "veil_left",
            CubeListBuilder.create().texOffs(0, 82)
                .addBox(-1.0F, 0.0F, -1.0F, 1.0F, 10.0F, 2.0F),
            PartPose.offsetAndRotation(-4.25F, 1.0F, 1.5F, 0.04F, 0.0F, 0.10F)
        );
        dress.addOrReplaceChild(
            "veil_right",
            CubeListBuilder.create().texOffs(0, 82).mirror()
                .addBox(0.0F, 0.0F, -1.0F, 1.0F, 10.0F, 2.0F),
            PartPose.offsetAndRotation(4.25F, 1.0F, 1.5F, -0.04F, 0.0F, -0.10F)
        );
        final PartDefinition bellLowerMass = dress.addOrReplaceChild(
            "bell_lower_mass",
            CubeListBuilder.create().texOffs(50, 68)
                .addBox(-5.0F, 0.0F, -3.0F, 10.0F, 5.0F, 6.0F),
            PartPose.offset(0.0F, 7.0F, 0.0F)
        );
        bellLowerMass.addOrReplaceChild(
            "spectral_train",
            CubeListBuilder.create()
                .texOffs(8, 82).addBox(-3.0F, 0.0F, -2.0F, 6.0F, 5.0F, 4.0F)
                .texOffs(30, 82).addBox(-5.0F, 0.0F, -1.5F, 2.0F, 3.0F, 3.0F)
                .texOffs(30, 82).mirror().addBox(3.0F, 0.0F, -1.5F, 2.0F, 4.0F, 3.0F),
            PartPose.offset(0.0F, 5.0F, 0.0F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        head.yRot += state.yRot * Mth.DEG_TO_RAD;
        head.xRot += state.xRot * Mth.DEG_TO_RAD;

        final float drift = Mth.sin(state.ageInTicks * 0.12F);
        shoulderYoke.zRot += drift * 0.02F;
        bodice.zRot -= drift * 0.012F;
        dress.zRot -= drift * 0.035F;
        veilLeft.zRot += drift * 0.075F;
        veilRight.zRot -= drift * 0.075F;
        bellLowerMass.y += Mth.cos(state.ageInTicks * 0.09F) * 0.15F;
        hairLeft.zRot += drift * 0.035F;
        hairRight.zRot -= drift * 0.035F;
        wailFlareLeft.zRot += drift * 0.06F;
        wailFlareRight.zRot -= drift * 0.06F;
        rightArm.xRot += Mth.cos(state.walkAnimationPos * 0.45F) * state.walkAnimationSpeed * 0.24F;
        leftArm.xRot -= Mth.cos(state.walkAnimationPos * 0.45F) * state.walkAnimationSpeed * 0.24F;

        mouth.visible = state.wailing;
        if (state.wailing) {
            head.xRot -= 0.30F;
            wailFlareLeft.zRot -= 0.40F;
            wailFlareRight.zRot += 0.40F;
            rightArm.zRot -= 1.08F;
            leftArm.zRot += 1.08F;
            rightArm.xRot -= 0.20F;
            leftArm.xRot -= 0.20F;
            rightHand.zRot -= 0.22F;
            leftHand.zRot += 0.22F;
            veilLeft.zRot -= 0.18F;
            veilRight.zRot += 0.18F;
        }
    }

    public static void extractRenderState(final BansheeEntity entity, final State state, final float partialTicks) {
        state.activity = entity.presentationActivity();
        state.pulseSequence = entity.presentationPulseSequence();
        state.wailing = state.activity == Mode.LAMENT || state.activity == Mode.WARNING;
    }

    public static final class State extends LivingEntityRenderState {
        public Mode activity = Mode.VIGIL;
        public int pulseSequence;
        public boolean wailing;
    }
}
