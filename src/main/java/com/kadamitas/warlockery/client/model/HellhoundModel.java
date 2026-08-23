package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.HellhoundEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/** Low infernal canid with a forked flame tail and committed-bite pose. */
public final class HellhoundModel extends EntityModel<HellhoundModel.State> {
    public static final int TEXTURE_WIDTH = 256;
    public static final int TEXTURE_HEIGHT = 160;

    private final ModelPart neck;
    private final ModelPart skull;
    private final ModelPart jaw;
    private final ModelPart leftForeleg;
    private final ModelPart rightForeleg;
    private final ModelPart leftHindleg;
    private final ModelPart rightHindleg;
    private final ModelPart leftForeCannon;
    private final ModelPart rightForeCannon;
    private final ModelPart leftHindHock;
    private final ModelPart rightHindHock;
    private final ModelPart tail;
    private final ModelPart tailMid;
    private final ModelPart ridge;

    public HellhoundModel(final ModelPart root) {
        super(root);
        neck = root.getChild("thick_neck");
        skull = neck.getChild("wedge_skull");
        jaw = skull.getChild("lower_jaw");
        leftForeleg = root.getChild("left_foreleg");
        rightForeleg = root.getChild("right_foreleg");
        leftForeCannon = leftForeleg.getChild("left_fore_cannon");
        rightForeCannon = rightForeleg.getChild("right_fore_cannon");
        leftHindleg = root.getChild("left_hindleg");
        rightHindleg = root.getChild("right_hindleg");
        leftHindHock = leftHindleg.getChild("left_hind_hock");
        rightHindHock = rightHindleg.getChild("right_hind_hock");
        tail = root.getChild("flame_tail_root");
        tailMid = tail.getChild("flame_tail_mid");
        ridge = root.getChild("dorsal_ridge");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition chest = root.addOrReplaceChild("ember_chest",
            CubeListBuilder.create().texOffs(0, 20)
                .addBox(-5.0F, -4.5F, -4.5F, 10.0F, 9.0F, 8.0F),
            PartPose.offset(0.0F, 11.5F, -3.0F));
        chest.addOrReplaceChild("left_breast_wedge",
            CubeListBuilder.create().texOffs(0, 20)
                .addBox(0.0F, -3.0F, -3.0F, 3.0F, 6.0F, 6.0F),
            PartPose.offsetAndRotation(6.0F, 0.4F, -0.8F, 0.0F, 0.0F, -0.24F));
        chest.addOrReplaceChild("right_breast_wedge",
            CubeListBuilder.create().texOffs(0, 20)
                .addBox(-3.0F, -3.0F, -3.0F, 3.0F, 6.0F, 6.0F),
            PartPose.offsetAndRotation(-6.0F, 0.4F, -0.8F, 0.0F, 0.0F, 0.24F));
        root.addOrReplaceChild("long_spine",
            CubeListBuilder.create().texOffs(38, 20)
                .addBox(-4.0F, -3.0F, -5.0F, 8.0F, 6.0F, 10.0F),
            PartPose.offset(0.0F, 12.0F, 3.0F));
        root.addOrReplaceChild("cinder_hips",
            CubeListBuilder.create().texOffs(94, 20)
                .addBox(-4.5F, -3.5F, -3.5F, 9.0F, 7.0F, 7.0F),
            PartPose.offset(0.0F, 12.5F, 8.5F));
        final PartDefinition neck = root.addOrReplaceChild("thick_neck",
            CubeListBuilder.create().texOffs(130, 20)
                .addBox(-3.5F, -3.0F, -3.5F, 7.0F, 6.0F, 5.0F),
            PartPose.offsetAndRotation(0.0F, 10.0F, -6.5F, -0.1F, 0.0F, 0.0F));
        final PartDefinition skull = neck.addOrReplaceChild("wedge_skull",
            CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.0F, -3.0F, -5.0F, 8.0F, 6.0F, 6.0F),
            PartPose.offset(0.0F, -0.8F, -3.5F));
        skull.addOrReplaceChild("long_muzzle",
            CubeListBuilder.create().texOffs(36, 0)
                .addBox(-2.8F, -1.8F, -4.0F, 5.6F, 3.0F, 4.0F),
            PartPose.offset(0.0F, 0.2F, -4.2F));
        skull.addOrReplaceChild("lower_jaw",
            CubeListBuilder.create().texOffs(66, 0)
                .addBox(-2.5F, -0.5F, -4.0F, 5.0F, 2.0F, 4.0F),
            PartPose.offset(0.0F, 2.2F, -3.8F));
        skull.addOrReplaceChild("brow_bridge",
            CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.5F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F),
            PartPose.offset(0.0F, -2.0F, -4.6F));
        final PartDefinition leftHorn = skull.addOrReplaceChild("left_ember_horn",
            CubeListBuilder.create().texOffs(92, 0)
                .addBox(-0.8F, -3.0F, -0.8F, 1.5F, 3.0F, 1.5F),
            PartPose.offsetAndRotation(2.8F, -2.3F, -0.2F, -0.7F, 0.0F, 0.18F));
        leftHorn.addOrReplaceChild("left_ember_horn_tip",
            CubeListBuilder.create().texOffs(92, 0)
                .addBox(-0.5F, -2.5F, -0.5F, 1.0F, 2.5F, 1.0F),
            PartPose.offsetAndRotation(0.0F, -2.7F, 0.0F, -0.42F, 0.0F, 0.2F));
        final PartDefinition rightHorn = skull.addOrReplaceChild("right_ember_horn",
            CubeListBuilder.create().texOffs(106, 0)
                .addBox(-0.7F, -3.0F, -0.8F, 1.5F, 3.0F, 1.5F),
            PartPose.offsetAndRotation(-2.8F, -2.3F, -0.2F, -0.7F, 0.0F, -0.18F));
        rightHorn.addOrReplaceChild("right_ember_horn_tip",
            CubeListBuilder.create().texOffs(106, 0)
                .addBox(-0.5F, -2.5F, -0.5F, 1.0F, 2.5F, 1.0F),
            PartPose.offsetAndRotation(0.0F, -2.7F, 0.0F, -0.42F, 0.0F, -0.2F));
        final PartDefinition ridge = root.addOrReplaceChild("dorsal_ridge",
            CubeListBuilder.create().texOffs(38, 20)
                .addBox(-1.0F, -2.0F, -6.0F, 2.0F, 2.0F, 10.0F),
            PartPose.offset(0.0F, 9.0F, 2.5F));
        ridge.addOrReplaceChild("ridge_plate_front",
            CubeListBuilder.create().texOffs(38, 20).addBox(-0.5F, -3.0F, -1.0F, 1.0F, 3.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, -0.8F, -4.5F, -0.18F, 0.0F, 0.0F));
        ridge.addOrReplaceChild("ridge_plate_middle",
            CubeListBuilder.create().texOffs(38, 20).addBox(-0.5F, -2.5F, -1.0F, 1.0F, 2.5F, 2.0F),
            PartPose.offsetAndRotation(0.0F, -0.8F, -0.5F, 0.08F, 0.0F, 0.0F));
        ridge.addOrReplaceChild("ridge_plate_rear",
            CubeListBuilder.create().texOffs(38, 20).addBox(-0.5F, -2.0F, -1.0F, 1.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, -0.8F, 3.0F, 0.24F, 0.0F, 0.0F));

        final PartDefinition lf = root.addOrReplaceChild("left_foreleg",
            CubeListBuilder.create().texOffs(0, 44)
                .addBox(-1.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F),
            PartPose.offset(5.5F, 14.0F, -4.8F));
        final PartDefinition lfc = lf.addOrReplaceChild("left_fore_cannon",
            CubeListBuilder.create().texOffs(20, 44)
                .addBox(-0.7F, 0.0F, -1.3F, 3.0F, 4.0F, 3.0F),
            PartPose.offset(0.0F, 4.0F, 0.0F));
        final PartDefinition leftForePaw = lfc.addOrReplaceChild("left_fore_paw",
            CubeListBuilder.create().texOffs(38, 44)
                .addBox(-1.5F, 0.0F, -3.5F, 5.0F, 2.0F, 5.0F),
            PartPose.offset(0.0F, 4.0F, -0.2F));
        leftForePaw.addOrReplaceChild("left_fore_toe_inner",
            CubeListBuilder.create().texOffs(38, 44).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 1.0F, 2.0F),
            PartPose.offset(-0.6F, 0.8F, -3.3F));
        leftForePaw.addOrReplaceChild("left_fore_toe_middle",
            CubeListBuilder.create().texOffs(38, 44).addBox(-0.5F, 0.0F, -2.5F, 1.0F, 1.0F, 2.5F),
            PartPose.offset(1.0F, 0.8F, -3.3F));
        leftForePaw.addOrReplaceChild("left_fore_toe_outer",
            CubeListBuilder.create().texOffs(38, 44).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 1.0F, 2.0F),
            PartPose.offset(2.6F, 0.8F, -3.3F));
        final PartDefinition rf = root.addOrReplaceChild("right_foreleg",
            CubeListBuilder.create().texOffs(64, 44)
                .addBox(-3.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F),
            PartPose.offset(-5.5F, 14.0F, -4.8F));
        final PartDefinition rfc = rf.addOrReplaceChild("right_fore_cannon",
            CubeListBuilder.create().texOffs(84, 44)
                .addBox(-2.3F, 0.0F, -1.3F, 3.0F, 4.0F, 3.0F),
            PartPose.offset(0.0F, 4.0F, 0.0F));
        final PartDefinition rightForePaw = rfc.addOrReplaceChild("right_fore_paw",
            CubeListBuilder.create().texOffs(102, 44)
                .addBox(-3.5F, 0.0F, -3.5F, 5.0F, 2.0F, 5.0F),
            PartPose.offset(0.0F, 4.0F, -0.2F));
        rightForePaw.addOrReplaceChild("right_fore_toe_inner",
            CubeListBuilder.create().texOffs(102, 44).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 1.0F, 2.0F),
            PartPose.offset(0.6F, 0.8F, -3.3F));
        rightForePaw.addOrReplaceChild("right_fore_toe_middle",
            CubeListBuilder.create().texOffs(102, 44).addBox(-0.5F, 0.0F, -2.5F, 1.0F, 1.0F, 2.5F),
            PartPose.offset(-1.0F, 0.8F, -3.3F));
        rightForePaw.addOrReplaceChild("right_fore_toe_outer",
            CubeListBuilder.create().texOffs(102, 44).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 1.0F, 2.0F),
            PartPose.offset(-2.6F, 0.8F, -3.3F));
        final PartDefinition lh = root.addOrReplaceChild("left_hindleg",
            CubeListBuilder.create().texOffs(0, 62).addBox(-1.0F, 0.0F, -1.8F, 3.0F, 5.0F, 4.0F), PartPose.offset(3.1F, 14.0F, 9.0F));
        final PartDefinition lhh = lh.addOrReplaceChild("left_hind_hock",
            CubeListBuilder.create().texOffs(22, 62).addBox(-0.7F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offset(0.0F, 4.0F, 0.5F));
        lhh.addOrReplaceChild("left_hind_paw",
            CubeListBuilder.create().texOffs(40, 62).addBox(-1.2F, 0.0F, -2.5F, 3.0F, 2.0F, 4.0F), PartPose.offset(0.0F, 4.0F, -0.2F));
        final PartDefinition rh = root.addOrReplaceChild("right_hindleg",
            CubeListBuilder.create().texOffs(66, 62).addBox(-2.0F, 0.0F, -1.8F, 3.0F, 5.0F, 4.0F), PartPose.offset(-3.1F, 14.0F, 9.0F));
        final PartDefinition rhh = rh.addOrReplaceChild("right_hind_hock",
            CubeListBuilder.create().texOffs(88, 62).addBox(-1.3F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offset(0.0F, 4.0F, 0.5F));
        rhh.addOrReplaceChild("right_hind_paw",
            CubeListBuilder.create().texOffs(106, 62).addBox(-1.8F, 0.0F, -2.5F, 3.0F, 2.0F, 4.0F), PartPose.offset(0.0F, 4.0F, -0.2F));

        final PartDefinition tail = root.addOrReplaceChild("flame_tail_root",
            CubeListBuilder.create().texOffs(0, 80)
                .addBox(-0.5F, -1.0F, 0.0F, 1.0F, 2.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 11.5F, 11.5F, 0.35F, 0.0F, 0.0F));
        final PartDefinition mid = tail.addOrReplaceChild("flame_tail_mid",
            CubeListBuilder.create().texOffs(14, 80)
                .addBox(-0.5F, -1.0F, 0.0F, 1.0F, 2.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, 3.8F, 0.3F, 0.0F, 0.0F));
        final PartDefinition leftFork = mid.addOrReplaceChild("left_flame_fork",
            CubeListBuilder.create().texOffs(26, 80)
                .addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 3.0F),
            PartPose.offsetAndRotation(0.6F, 0.0F, 2.8F, 0.24F, 0.34F, 0.0F));
        leftFork.addOrReplaceChild("left_flame_tip",
            CubeListBuilder.create().texOffs(26, 80)
                .addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, 2.7F, -0.18F, 0.2F, 0.0F));
        final PartDefinition rightFork = mid.addOrReplaceChild("right_flame_fork",
            CubeListBuilder.create().texOffs(38, 80)
                .addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 3.0F),
            PartPose.offsetAndRotation(-0.6F, 0.0F, 2.8F, 0.24F, -0.34F, 0.0F));
        rightFork.addOrReplaceChild("right_flame_tip",
            CubeListBuilder.create().texOffs(38, 80)
                .addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, 2.7F, -0.18F, -0.2F, 0.0F));
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        skull.yRot += state.yRot * Mth.DEG_TO_RAD;
        skull.xRot += state.xRot * Mth.DEG_TO_RAD;
        final float pace = state.walkAnimationPos * 0.86F;
        final float drive = state.walkAnimationSpeed;
        leftForeleg.xRot += Mth.cos(pace) * drive * 0.9F;
        rightForeleg.xRot += Mth.cos(pace + Mth.PI) * drive * 0.9F;
        leftHindleg.xRot += Mth.cos(pace + Mth.PI) * drive * 0.9F;
        rightHindleg.xRot += Mth.cos(pace) * drive * 0.9F;
        leftForeCannon.xRot -= Math.max(0.0F, Mth.sin(pace)) * drive * 0.48F;
        rightForeCannon.xRot -= Math.max(0.0F, -Mth.sin(pace)) * drive * 0.48F;
        leftHindHock.xRot += Mth.sin(pace) * drive * 0.36F;
        rightHindHock.xRot -= Mth.sin(pace) * drive * 0.36F;
        tail.yRot += Mth.sin(state.ageInTicks * 0.24F) * 0.2F;
        tailMid.yRot -= Mth.sin(state.ageInTicks * 0.18F) * 0.28F;
        ridge.yScale += Mth.sin(state.ageInTicks * 0.22F) * 0.06F;
        if (state.warning) {
            neck.xRot -= 0.24F;
            skull.xRot += 0.2F;
            tail.yRot += 0.38F;
        }
        if (state.biting) {
            neck.xRot += 0.55F;
            skull.xRot -= 0.42F;
            jaw.xRot += 0.8F;
            leftForeleg.xRot -= 0.48F;
            rightForeleg.xRot -= 0.48F;
        }
        if (state.retreating) {
            tail.xRot += 0.72F;
            skull.yRot += Mth.sin(state.ageInTicks * 0.7F) * 0.2F;
        }
    }

    public static void extractRenderState(final HellhoundEntity entity, final State state, final float partialTicks) {
        state.bound = entity.presentationBound();
        state.warning = entity.presentationWarning();
        state.biting = entity.presentationBiting();
        state.retreating = entity.presentationRetreating();
    }

    public static final class State extends LivingEntityRenderState {
        public boolean bound;
        public boolean biting;
        public boolean warning;
        public boolean retreating;
    }
}
