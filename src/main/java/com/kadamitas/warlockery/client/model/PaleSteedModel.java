package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.SpectralSteedEntity;
import com.kadamitas.warlockery.entity.SpectralSteedRules.Gait;
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

/** Tall bone-pale spectral mount with a vertebral mane and ribbon tail. */
public final class PaleSteedModel extends EntityModel<PaleSteedModel.State> {
    public static final int TEXTURE_WIDTH = 256;
    public static final int TEXTURE_HEIGHT = 192;

    private final ModelPart neckBase, neckSpire, skull, mane;
    private final ModelPart leftFront, rightFront, leftFrontCannon, rightFrontCannon;
    private final ModelPart leftHind, rightHind, leftHindHock, rightHindHock;
    private final ModelPart tail, tailMid;

    public PaleSteedModel(final ModelPart root) {
        super(root);
        neckBase = root.getChild("neck_base");
        neckSpire = neckBase.getChild("neck_spire");
        skull = neckSpire.getChild("coffin_skull");
        mane = root.getChild("vertebral_mane");
        leftFront = root.getChild("left_front_strut");
        rightFront = root.getChild("right_front_strut");
        leftFrontCannon = leftFront.getChild("left_front_cannon");
        rightFrontCannon = rightFront.getChild("right_front_cannon");
        leftHind = root.getChild("left_hind_haunch");
        rightHind = root.getChild("right_hind_haunch");
        leftHindHock = leftHind.getChild("left_hind_hock");
        rightHindHock = rightHind.getChild("right_hind_hock");
        tail = root.getChild("ribbon_tail_root");
        tailMid = tail.getChild("ribbon_tail_mid");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("thorax", CubeListBuilder.create().texOffs(0, 22)
            .addBox(-4.0F, -5.8947F, -5.25F, 8.0F, 8.0F, 9.0F),
            PartPose.offsetAndRotation(0.0F, 4.7F, -1.5F, -0.15F, 0.0F, 0.0F)
                .scaled(1.35F, 1.1F, 1.0F));
        root.addOrReplaceChild("croup", CubeListBuilder.create().texOffs(92, 22)
            .addBox(-4.5F, -5.25F, -3.0F, 9.0F, 7.0F, 6.0F),
            PartPose.offset(0.0F, 5.525F, 5.5F).scaled(1.15F, 0.957143F, 1.15F));
        root.addOrReplaceChild("spinal_bridge", CubeListBuilder.create().texOffs(40, 22)
            .addBox(-3.0F, -4.1667F, -4.5F, 6.0F, 5.0F, 9.0F),
            PartPose.offset(0.0F, 5.667F, 2.0F).scaled(1.15F, 1.48F, 1.0F));
        final PartDefinition shoulder = root.addOrReplaceChild(
            "shoulder_keel",
            CubeListBuilder.create().texOffs(76, 0)
                .addBox(-3.0F, -5.0F, -2.5F, 6.0F, 9.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, 4.5F, -6.5F, 0.24F, 0.0F, 0.0F)
                .scaled(1.35F, 1.15F, 1.40F)
        );
        shoulder.addOrReplaceChild(
            "breast_point",
            CubeListBuilder.create().texOffs(110, 90)
                .addBox(-2.0F, -1.5F, -2.0F, 4.0F, 7.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 2.4F, -0.8F, 0.18F, 0.0F, 0.0F)
                .scaled(1.15F, 1.0F, 1.0F)
        );
        final PartDefinition neck = root.addOrReplaceChild("neck_base", CubeListBuilder.create().texOffs(76, 0)
            .addBox(-2.5F, -7.0F, -2.5F, 5.0F, 8.0F, 4.0F, new CubeDeformation(0.75F)),
            PartPose.offsetAndRotation(0.0F, 4.0F, -4.0F, 0.52F, 0.0F, 0.0F));
        final PartDefinition spire = neck.addOrReplaceChild("neck_spire", CubeListBuilder.create().texOffs(96, 0)
            .addBox(-2.0F, -7.0F, -1.725F, 4.0F, 8.0F, 3.0F, new CubeDeformation(0.50F)),
            PartPose.offsetAndRotation(0.0F, -6.5F, -0.8F, 0.05F, 0.0F, 0.0F));
        final PartDefinition skull = spire.addOrReplaceChild("coffin_skull", CubeListBuilder.create().texOffs(0, 0)
            .addBox(-3.0F, -3.5F, -4.0F, 6.0F, 6.0F, 7.0F),
            PartPose.offsetAndRotation(0.0F, -7.2F, -1.8F, -0.25F, 0.0F, 0.0F));
        skull.addOrReplaceChild("long_muzzle", CubeListBuilder.create().texOffs(32, 0)
            .addBox(-2.0F, -1.8F, -4.0F, 4.0F, 3.0F, 4.0F), PartPose.offset(0.0F, 0.4F, -2.5F));
        skull.addOrReplaceChild("left_long_ear", CubeListBuilder.create().texOffs(56, 0)
            .addBox(-0.6F, -5.0F, -0.5F, 1.0F, 5.0F, 1.0F),
            PartPose.offset(2.0F, -1.2F, -1.0F).scaled(1.0F, 2.30F, 1.0F));
        skull.addOrReplaceChild("right_long_ear", CubeListBuilder.create().texOffs(66, 0)
            .addBox(-0.4F, -5.0F, -0.5F, 1.0F, 5.0F, 1.0F),
            PartPose.offset(-2.0F, -1.2F, -1.0F).scaled(1.0F, 2.30F, 1.0F));
        neck.addOrReplaceChild("neck_mane_plate", CubeListBuilder.create().texOffs(0, 90)
            .addBox(-1.5F, -4.0F, -0.5F, 3.0F, 4.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, -3.0F, 2.0F, -0.10F, 0.0F, 0.0F));
        spire.addOrReplaceChild("spire_mane_plate", CubeListBuilder.create().texOffs(14, 90)
            .addBox(-1.5F, -3.0F, -0.5F, 3.0F, 3.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, -3.0F, 1.5F, -0.08F, 0.0F, 0.0F));
        final PartDefinition mane = root.addOrReplaceChild("vertebral_mane", CubeListBuilder.create().texOffs(130, 22)
            .addBox(-1.0F, -2.0F, -5.0F, 2.0F, 2.0F, 7.0F),
            PartPose.offset(0.0F, -0.5F, 2.5F));
        mane.addOrReplaceChild("mane_plate_one", CubeListBuilder.create().texOffs(0, 90)
            .addBox(-1.5F, -4.0F, -0.5F, 3.0F, 4.0F, 1.0F), PartPose.offset(0.0F, 0.0F, -4.0F));
        mane.addOrReplaceChild("mane_plate_two", CubeListBuilder.create().texOffs(14, 90)
            .addBox(-1.5F, -3.0F, -0.5F, 3.0F, 3.0F, 1.0F), PartPose.offset(0.0F, 0.0F, -1.5F));
        mane.addOrReplaceChild("mane_plate_three", CubeListBuilder.create().texOffs(28, 90)
            .addBox(-1.5F, -2.0F, -0.5F, 3.0F, 2.0F, 1.0F), PartPose.offset(0.0F, 0.0F, 1.0F));

        final PartDefinition lf = root.addOrReplaceChild("left_front_strut", CubeListBuilder.create().texOffs(0, 46)
            .addBox(-0.8F, 0.0F, -1.6F, 2.0F, 6.0F, 3.0F),
            PartPose.offsetAndRotation(3.25F, 7.44F, -8.0F, -0.30F, 0.0F, 0.0F)
                .scaled(1.0F, 1.31F, 1.0F));
        final PartDefinition lfc = lf.addOrReplaceChild("left_front_cannon", CubeListBuilder.create().texOffs(18, 46)
            .addBox(-0.6F, 0.0F, -1.5F, 2.0F, 6.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.15F, 0.0F, 0.0F));
        lfc.addOrReplaceChild("left_front_split_hoof", CubeListBuilder.create().texOffs(32, 46)
            .addBox(-0.8F, 0.0F, -3.5F, 3.0F, 2.0F, 4.0F),
            PartPose.offset(0.0F, 6.0F, 0.0F));
        final PartDefinition rf = root.addOrReplaceChild("right_front_strut", CubeListBuilder.create().texOffs(56, 46)
            .addBox(-1.2F, 0.0F, -1.6F, 2.0F, 6.0F, 3.0F),
            PartPose.offsetAndRotation(-3.25F, 7.44F, -7.2F, -0.30F, 0.0F, 0.0F)
                .scaled(1.0F, 1.31F, 1.0F));
        final PartDefinition rfc = rf.addOrReplaceChild("right_front_cannon", CubeListBuilder.create().texOffs(74, 46)
            .addBox(-1.4F, 0.0F, -1.5F, 2.0F, 6.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.15F, 0.0F, 0.0F));
        rfc.addOrReplaceChild("right_front_split_hoof", CubeListBuilder.create().texOffs(88, 46)
            .addBox(-2.2F, 0.0F, -3.5F, 3.0F, 2.0F, 4.0F),
            PartPose.offset(0.0F, 6.0F, 0.0F));
        final PartDefinition lh = root.addOrReplaceChild("left_hind_haunch", CubeListBuilder.create().texOffs(0, 68)
            .addBox(-1.0F, 0.0F, -2.5F, 3.0F, 6.0F, 4.0F),
            PartPose.offsetAndRotation(3.25F, 7.42F, 2.5F, 0.30F, 0.0F, 0.0F)
                .scaled(1.0F, 1.31F, 1.0F));
        final PartDefinition lhh = lh.addOrReplaceChild("left_hind_hock", CubeListBuilder.create().texOffs(22, 68)
            .addBox(-0.6F, 0.0F, -1.5F, 2.0F, 6.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, -0.45F, 0.0F, 0.0F));
        lhh.addOrReplaceChild("left_hind_split_hoof", CubeListBuilder.create().texOffs(36, 68)
            .addBox(-0.8F, 0.0F, -3.0F, 3.0F, 2.0F, 4.0F),
            PartPose.offset(0.0F, 6.0F, 0.0F));
        final PartDefinition rh = root.addOrReplaceChild("right_hind_haunch", CubeListBuilder.create().texOffs(60, 68)
            .addBox(-2.0F, 0.0F, -2.5F, 3.0F, 6.0F, 4.0F),
            PartPose.offsetAndRotation(-3.25F, 7.42F, 5.8F, 0.30F, 0.0F, 0.0F)
                .scaled(1.0F, 1.31F, 1.0F));
        final PartDefinition rhh = rh.addOrReplaceChild("right_hind_hock", CubeListBuilder.create().texOffs(82, 68)
            .addBox(-1.4F, 0.0F, -1.5F, 2.0F, 6.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, -0.45F, 0.0F, 0.0F));
        rhh.addOrReplaceChild("right_hind_split_hoof", CubeListBuilder.create().texOffs(96, 68)
            .addBox(-2.2F, 0.0F, -3.0F, 3.0F, 2.0F, 4.0F),
            PartPose.offset(0.0F, 6.0F, 0.0F));

        final PartDefinition tail = root.addOrReplaceChild("ribbon_tail_root", CubeListBuilder.create().texOffs(42, 90)
            .addBox(-0.5F, -1.0F, 0.0F, 1.0F, 2.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, 3.5F, 6.5F, 0.55F, 0.0F, 0.0F));
        final PartDefinition mid = tail.addOrReplaceChild("ribbon_tail_mid", CubeListBuilder.create().texOffs(54, 90)
            .addBox(-0.5F, -0.7F, 0.0F, 1.0F, 1.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, 2.2F, -2.12F, 0.0F, 0.0F));
        mid.addOrReplaceChild("left_ribbon_fork", CubeListBuilder.create().texOffs(66, 90)
            .addBox(-0.4F, -0.4F, 0.0F, 1.0F, 1.0F, 3.0F),
            PartPose.offset(0.8F, 0.0F, 2.2F));
        mid.addOrReplaceChild("right_ribbon_fork", CubeListBuilder.create().texOffs(78, 90)
            .addBox(-0.6F, -0.4F, 0.0F, 1.0F, 1.0F, 3.0F),
            PartPose.offset(-0.8F, 0.0F, 2.2F));
        final PartDefinition dropOne = mid.addOrReplaceChild("ribbon_tail_drop_one",
            CubeListBuilder.create().texOffs(66, 90)
                .addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 3.0F),
            PartPose.offset(0.0F, 0.0F, 2.2F));
        final PartDefinition dropTwo = dropOne.addOrReplaceChild("ribbon_tail_drop_two",
            CubeListBuilder.create().texOffs(78, 90)
                .addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 3.0F),
            PartPose.offset(0.0F, 0.0F, 2.2F));
        final PartDefinition dropThree = dropTwo.addOrReplaceChild("ribbon_tail_drop_three",
            CubeListBuilder.create().texOffs(66, 90)
                .addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 3.0F),
            PartPose.offset(0.0F, 0.0F, 2.2F));
        final PartDefinition dropFour = dropThree.addOrReplaceChild("ribbon_tail_drop_four",
            CubeListBuilder.create().texOffs(78, 90)
                .addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 3.0F),
            PartPose.offset(0.0F, 0.0F, 2.2F));
        final PartDefinition dropFive = dropFour.addOrReplaceChild("ribbon_tail_drop_five",
            CubeListBuilder.create().texOffs(66, 90)
                .addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 3.0F),
            PartPose.offset(0.0F, 0.0F, 2.2F));
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override public void setupAnim(final State state) {
        super.setupAnim(state);
        skull.yRot += state.yRot * Mth.DEG_TO_RAD;
        skull.xRot += state.xRot * Mth.DEG_TO_RAD;
        final float band = switch (state.gait) { case HALT -> 0.0F; case WALK -> 0.55F; case TROT -> 0.8F; case CANTER -> 1.0F; case SPRINT -> 1.15F; };
        final float phase = state.walkAnimationPos * (0.55F + band * 0.35F);
        final float lift = state.walkAnimationSpeed * band;
        leftFront.xRot += Mth.cos(phase) * lift * 0.82F; rightFront.xRot += Mth.cos(phase + Mth.PI) * lift * 0.82F;
        leftHind.xRot += Mth.cos(phase + Mth.PI) * lift * 0.74F; rightHind.xRot += Mth.cos(phase) * lift * 0.74F;
        leftFrontCannon.xRot -= Math.max(0.0F, Mth.sin(phase)) * lift * 0.55F; rightFrontCannon.xRot -= Math.max(0.0F, -Mth.sin(phase)) * lift * 0.55F;
        leftHindHock.xRot += Mth.sin(phase) * lift * 0.3F; rightHindHock.xRot -= Mth.sin(phase) * lift * 0.3F;
        mane.yScale += Mth.sin(state.ageInTicks * 0.18F) * 0.06F;
        tail.yRot += Mth.sin(state.ageInTicks * 0.13F) * 0.22F; tailMid.yRot -= Mth.sin(state.ageInTicks * 0.17F) * 0.3F;
        if (state.balking) { neckBase.xRot -= 0.45F; neckSpire.xRot += 0.3F; leftFront.xRot -= 1.05F; rightFront.xRot -= 0.32F; }
        if (state.resting) { neckBase.xRot += 0.62F; neckSpire.xRot += 0.35F; }
        if (state.airborne) { leftFront.xRot -= 0.42F; rightFront.xRot -= 0.42F; leftHind.xRot += 0.48F; rightHind.xRot += 0.48F; }
    }

    public static void extractRenderState(final SpectralSteedEntity entity, final State state, final float partialTicks) {
        state.gait = entity.presentationGait();
        state.bond = entity.presentationBond();
        state.fatigue = entity.presentationFatigue();
        state.balking = entity.presentationBalking();
        state.resting = entity.presentationResting();
        state.carrying = entity.isVehicle(); state.airborne = !entity.onGround();
    }

    public static final class State extends LivingEntityRenderState {
        public Gait gait = Gait.HALT;
        public int bond, fatigue;
        public boolean balking, resting, carrying, airborne;
    }
}
