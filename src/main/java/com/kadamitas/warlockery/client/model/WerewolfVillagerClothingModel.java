package com.kadamitas.warlockery.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.VillagerLikeModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** Clothing-only mesh for temporary full-moon villagers rendered on the werewolf body. */
public final class WerewolfVillagerClothingModel extends EntityModel<WerewolfModel.State>
    implements VillagerLikeModel<WerewolfModel.State> {
    public static final int TEXTURE_WIDTH = 64;
    public static final int TEXTURE_HEIGHT = 64;

    private final ModelPart professionHat;
    private final ModelPart villagerCoat;
    private final ModelPart leftSleeve;
    private final ModelPart rightSleeve;
    private final ModelPart leftForearmSleeve;
    private final ModelPart rightForearmSleeve;
    private final ModelPart leftTrouser;
    private final ModelPart rightTrouser;
    private final ModelPart leftShinTrouser;
    private final ModelPart rightShinTrouser;

    public WerewolfVillagerClothingModel(final ModelPart root) {
        super(root);
        professionHat = root.getChild("profession_hat");
        villagerCoat = root.getChild("villager_coat");
        leftSleeve = root.getChild("left_sleeve");
        rightSleeve = root.getChild("right_sleeve");
        leftForearmSleeve = leftSleeve.getChild("left_forearm_sleeve");
        rightForearmSleeve = rightSleeve.getChild("right_forearm_sleeve");
        leftTrouser = root.getChild("left_trouser");
        rightTrouser = root.getChild("right_trouser");
        leftShinTrouser = leftTrouser.getChild("left_shin_trouser");
        rightShinTrouser = rightTrouser.getChild("right_shin_trouser");
    }

    public static LayerDefinition createBodyLayer(final boolean noHat) {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
            "profession_hat",
            noHat ? CubeListBuilder.create() : CubeListBuilder.create().texOffs(32, 0)
                .addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, new CubeDeformation(0.12F)),
            PartPose.offset(0.0F, 0.5F, -1.2F)
        );
        root.addOrReplaceChild(
            "villager_coat",
            CubeListBuilder.create().texOffs(0, 38)
                .addBox(-6.2F, -4.1F, -3.2F, 12.4F, 14.2F, 6.4F, new CubeDeformation(0.18F)),
            PartPose.offset(0.0F, 7.0F, 0.0F)
        );
        final PartDefinition leftSleeve = root.addOrReplaceChild(
            "left_sleeve",
            CubeListBuilder.create().texOffs(44, 22).mirror()
                .addBox(0.0F, -1.0F, -1.7F, 4.2F, 6.0F, 3.4F, new CubeDeformation(0.12F)),
            PartPose.offset(5.6F, 7.0F, 0.0F)
        );
        leftSleeve.addOrReplaceChild(
            "left_forearm_sleeve",
            CubeListBuilder.create().texOffs(44, 22).mirror()
                .addBox(-0.2F, 0.0F, -1.9F, 4.2F, 8.0F, 3.8F, new CubeDeformation(0.14F)),
            PartPose.offset(0.4F, 4.5F, 0.0F)
        );
        final PartDefinition rightSleeve = root.addOrReplaceChild(
            "right_sleeve",
            CubeListBuilder.create().texOffs(44, 22)
                .addBox(-4.2F, -1.0F, -1.7F, 4.2F, 6.0F, 3.4F, new CubeDeformation(0.12F)),
            PartPose.offset(-5.6F, 7.0F, 0.0F)
        );
        rightSleeve.addOrReplaceChild(
            "right_forearm_sleeve",
            CubeListBuilder.create().texOffs(44, 22)
                .addBox(-4.0F, 0.0F, -1.9F, 4.2F, 8.0F, 3.8F, new CubeDeformation(0.14F)),
            PartPose.offset(-0.4F, 4.5F, 0.0F)
        );
        final PartDefinition leftTrouser = root.addOrReplaceChild(
            "left_trouser",
            CubeListBuilder.create().texOffs(0, 22).mirror()
                .addBox(-1.0F, 0.0F, -2.1F, 4.1F, 6.0F, 4.2F, new CubeDeformation(0.1F)),
            PartPose.offset(2.2F, 14.0F, 0.6F)
        );
        leftTrouser.addOrReplaceChild(
            "left_shin_trouser",
            CubeListBuilder.create().texOffs(0, 22).mirror()
                .addBox(-0.7F, 0.0F, -1.7F, 3.2F, 4.0F, 3.4F, new CubeDeformation(0.12F)),
            PartPose.offset(0.2F, 5.0F, 0.0F)
        );
        final PartDefinition rightTrouser = root.addOrReplaceChild(
            "right_trouser",
            CubeListBuilder.create().texOffs(0, 22)
                .addBox(-3.1F, 0.0F, -2.1F, 4.1F, 6.0F, 4.2F, new CubeDeformation(0.1F)),
            PartPose.offset(-2.2F, 14.0F, 0.6F)
        );
        rightTrouser.addOrReplaceChild(
            "right_shin_trouser",
            CubeListBuilder.create().texOffs(0, 22)
                .addBox(-2.5F, 0.0F, -1.7F, 3.2F, 4.0F, 3.4F, new CubeDeformation(0.12F)),
            PartPose.offset(-0.2F, 5.0F, 0.0F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(final WerewolfModel.State state) {
        super.setupAnim(state);
        professionHat.yRot = state.yRot * Mth.DEG_TO_RAD;
        professionHat.xRot = state.xRot * Mth.DEG_TO_RAD;
        if (state.aggressive) professionHat.xRot -= 0.18F;
        professionHat.xScale = 1.15F;
        professionHat.yScale = 0.82F;
        professionHat.zScale = 1.025F;
        final float stride = state.walkAnimationPos * 0.68F;
        final float reach = state.walkAnimationSpeed;
        leftSleeve.xRot = Mth.cos(stride) * reach * 0.62F;
        rightSleeve.xRot = Mth.cos(stride + Mth.PI) * reach * 0.62F;
        leftTrouser.xRot = Mth.cos(stride + Mth.PI) * reach * 0.72F;
        rightTrouser.xRot = Mth.cos(stride) * reach * 0.72F;
        leftShinTrouser.xRot = Math.max(0.0F, Mth.sin(stride)) * reach * 0.38F;
        rightShinTrouser.xRot = Math.max(0.0F, -Mth.sin(stride)) * reach * 0.38F;
        villagerCoat.xRot = state.pouncing ? 0.46F : 0.0F;
        if (state.pouncing) {
            leftSleeve.xRot -= 1.0F;
            rightSleeve.xRot -= 1.0F;
            leftForearmSleeve.xRot -= 0.34F;
            rightForearmSleeve.xRot -= 0.34F;
            leftTrouser.xRot += 0.62F;
            rightTrouser.xRot += 0.62F;
        }
    }

    @Override
    public void translateToArms(final WerewolfModel.State state, final PoseStack poseStack) {
        root.translateAndRotate(poseStack);
        villagerCoat.translateAndRotate(poseStack);
    }
}
