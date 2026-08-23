package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.InfernalHierarchyEntity;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.Intent;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.PhaseState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public final class AbyssalRegentModel extends EntityModel<AbyssalRegentModel.State> {
    public static final int TEXTURE_WIDTH = 256;
    public static final int TEXTURE_HEIGHT = 128;

    private final ModelPart tormentBody;
    private final ModelPart voidCage;
    private final ModelPart internalFire;
    private final ModelPart tormentHead;
    private final ModelPart rightNarrowAntenna;
    private final ModelPart leftNarrowAntenna;
    private final ModelPart rightUpperWing;
    private final ModelPart leftUpperWing;
    private final ModelPart rightMiddleWing;
    private final ModelPart leftMiddleWing;
    private final ModelPart rightLowerWing;
    private final ModelPart leftLowerWing;
    private final ModelPart splitVoidShroud;
    private final ModelPart rightVoidTail;
    private final ModelPart leftVoidTail;
    private final ModelPart brokenHaloCage;
    private final ModelPart rightVoidFragment;
    private final ModelPart leftVoidFragment;

    public AbyssalRegentModel(final ModelPart root) {
        super(root);
        tormentBody = root.getChild("torment_body");
        voidCage = tormentBody.getChild("void_cage");
        internalFire = voidCage.getChild("internal_fire");
        tormentHead = tormentBody.getChild("torment_head");
        rightNarrowAntenna = tormentHead.getChild("right_narrow_antenna");
        leftNarrowAntenna = tormentHead.getChild("left_narrow_antenna");
        rightUpperWing = tormentBody.getChild("right_upper_wing");
        leftUpperWing = tormentBody.getChild("left_upper_wing");
        rightMiddleWing = tormentBody.getChild("right_middle_wing");
        leftMiddleWing = tormentBody.getChild("left_middle_wing");
        rightLowerWing = tormentBody.getChild("right_lower_wing");
        leftLowerWing = tormentBody.getChild("left_lower_wing");
        splitVoidShroud = tormentBody.getChild("split_void_shroud");
        rightVoidTail = splitVoidShroud.getChild("right_void_tail");
        leftVoidTail = splitVoidShroud.getChild("left_void_tail");
        brokenHaloCage = tormentBody.getChild("broken_halo_cage");
        rightVoidFragment = tormentBody.getChild("right_void_fragment");
        leftVoidFragment = tormentBody.getChild("left_void_fragment");
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        final PartDefinition body = root.addOrReplaceChild(
            "torment_body",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.5F, -5.5F, -2.5F, 7.0F, 11.0F, 5.0F)
                .texOffs(26, 0).addBox(-2.5F, 5.0F, -2.0F, 5.0F, 5.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.02F, 0.0F, 0.0F)
        );

        final PartDefinition cage = body.addOrReplaceChild(
            "void_cage",
            CubeListBuilder.create()
                .texOffs(46, 0).addBox(-4.5F, -4.5F, -3.2F, 1.5F, 9.0F, 2.0F)
                .texOffs(54, 0).addBox(3.0F, -4.5F, -3.2F, 1.5F, 9.0F, 2.0F)
                .texOffs(62, 0).addBox(-3.0F, -4.5F, -3.4F, 6.0F, 1.5F, 2.0F)
                .texOffs(80, 0).addBox(-3.0F, 3.0F, -3.4F, 6.0F, 1.5F, 2.0F),
            PartPose.offset(0.0F, -0.4F, 0.0F)
        );
        cage.addOrReplaceChild(
            "internal_fire",
            CubeListBuilder.create()
                .texOffs(98, 0).addBox(-2.2F, -3.0F, -1.2F, 4.4F, 6.0F, 2.4F)
                .texOffs(116, 0).addBox(-1.2F, -4.3F, -0.8F, 2.4F, 2.0F, 1.6F),
            PartPose.offset(0.0F, 0.0F, -3.2F)
        );
        cage.addOrReplaceChild(
            "right_fire_rib",
            CubeListBuilder.create().texOffs(128, 0).addBox(-4.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(-1.5F, -1.7F, -2.5F, 0.0F, -0.18F, 0.28F)
        );
        cage.addOrReplaceChild(
            "left_fire_rib",
            CubeListBuilder.create().texOffs(128, 0).mirror().addBox(0.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F),
            PartPose.offsetAndRotation(1.5F, -1.7F, -2.5F, 0.0F, 0.18F, -0.28F)
        );
        body.addOrReplaceChild(
            "abyssal_sternum",
            CubeListBuilder.create().texOffs(144, 0).addBox(-1.0F, -4.5F, -1.0F, 2.0F, 9.0F, 2.0F),
            PartPose.offset(0.0F, 0.0F, -3.7F)
        );

        final PartDefinition head = body.addOrReplaceChild(
            "torment_head",
            CubeListBuilder.create()
                .texOffs(154, 0).addBox(-2.7F, -4.0F, -2.5F, 5.4F, 4.5F, 5.0F)
                .texOffs(176, 0).addBox(-1.8F, -1.8F, -3.1F, 3.6F, 1.0F, 1.0F),
            PartPose.offset(0.0F, -5.0F, -0.2F)
        );
        head.addOrReplaceChild(
            "right_void_eye",
            CubeListBuilder.create().texOffs(190, 0).addBox(-1.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F),
            PartPose.offset(-1.3F, -1.8F, -2.7F)
        );
        head.addOrReplaceChild(
            "left_void_eye",
            CubeListBuilder.create().texOffs(190, 0).mirror().addBox(-1.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F),
            PartPose.offset(1.3F, -1.8F, -2.7F)
        );
        head.addOrReplaceChild(
            "right_narrow_antenna",
            CubeListBuilder.create()
                .texOffs(198, 0).addBox(-0.6F, -7.0F, -0.6F, 1.2F, 7.0F, 1.2F)
                .texOffs(204, 0).addBox(-0.4F, -9.0F, -0.4F, 0.8F, 2.5F, 0.8F),
            PartPose.offsetAndRotation(-1.4F, -3.5F, 0.0F, -0.03F, 0.0F, -0.04F)
        );
        head.addOrReplaceChild(
            "left_narrow_antenna",
            CubeListBuilder.create().texOffs(198, 0).mirror()
                .addBox(-0.6F, -7.0F, -0.6F, 1.2F, 7.0F, 1.2F)
                .texOffs(204, 0).addBox(-0.4F, -9.0F, -0.4F, 0.8F, 2.5F, 0.8F),
            PartPose.offsetAndRotation(1.4F, -3.5F, 0.0F, -0.03F, 0.0F, 0.04F)
        );

        addWing(body, "right_upper_wing", "right_upper_wing_tip", -7.0F, -4.6F, 0.2F, -1.0F, 15.0F, 0.10F, 0, 24);
        addWing(body, "left_upper_wing", "left_upper_wing_tip", 7.0F, -4.6F, 0.2F, 1.0F, 15.0F, 0.10F, 0, 24);
        addWing(body, "right_middle_wing", "right_middle_wing_tip", -5.0F, 0.2F, 1.8F, -1.0F, 12.0F, 0.30F, 80, 24);
        addWing(body, "left_middle_wing", "left_middle_wing_tip", 5.0F, 0.2F, 1.8F, 1.0F, 12.0F, 0.30F, 80, 24);
        addWing(body, "right_lower_wing", "right_lower_wing_tip", -4.2F, 5.0F, 4.8F, -1.0F, 9.0F, 0.54F, 148, 24);
        addWing(body, "left_lower_wing", "left_lower_wing_tip", 4.2F, 5.0F, 4.8F, 1.0F, 9.0F, 0.54F, 148, 24);

        final PartDefinition shroud = body.addOrReplaceChild(
            "split_void_shroud",
            CubeListBuilder.create()
                .texOffs(0, 50).addBox(-3.2F, 0.0F, -2.4F, 6.4F, 4.0F, 4.8F)
                .texOffs(24, 50).addBox(-2.3F, 3.0F, -1.8F, 4.6F, 3.0F, 3.6F),
            PartPose.offset(0.0F, 7.5F, 0.0F)
        );
        shroud.addOrReplaceChild(
            "right_void_tail",
            CubeListBuilder.create()
                .texOffs(44, 50).addBox(-2.8F, 0.0F, -1.8F, 2.8F, 7.0F, 3.6F)
                .texOffs(58, 50).addBox(-2.3F, 6.0F, -1.4F, 2.3F, 4.5F, 2.8F),
            PartPose.offsetAndRotation(-0.2F, 4.0F, 0.0F, 0.04F, 0.0F, 0.08F)
        );
        shroud.addOrReplaceChild(
            "left_void_tail",
            CubeListBuilder.create().texOffs(44, 50).mirror()
                .addBox(0.0F, 0.0F, -1.8F, 2.8F, 7.0F, 3.6F)
                .texOffs(58, 50).addBox(0.0F, 6.0F, -1.4F, 2.3F, 4.5F, 2.8F),
            PartPose.offsetAndRotation(0.2F, 4.0F, 0.0F, -0.04F, 0.0F, -0.08F)
        );
        shroud.addOrReplaceChild(
            "right_shroud_plate",
            CubeListBuilder.create().texOffs(72, 50).addBox(-3.5F, 0.0F, -0.8F, 3.5F, 6.0F, 1.6F),
            PartPose.offsetAndRotation(-0.4F, 1.0F, -2.1F, 0.08F, 0.0F, 0.1F)
        );
        shroud.addOrReplaceChild(
            "left_shroud_plate",
            CubeListBuilder.create().texOffs(72, 50).mirror().addBox(0.0F, 0.0F, -0.8F, 3.5F, 6.0F, 1.6F),
            PartPose.offsetAndRotation(0.4F, 1.0F, -2.1F, -0.08F, 0.0F, -0.1F)
        );

        final PartDefinition halo = body.addOrReplaceChild(
            "broken_halo_cage",
            CubeListBuilder.create()
                .texOffs(90, 50).addBox(-7.0F, -0.7F, -0.7F, 5.5F, 1.4F, 1.4F)
                .texOffs(106, 50).addBox(1.5F, -0.7F, -0.7F, 5.5F, 1.4F, 1.4F)
                .texOffs(122, 50).addBox(-0.7F, -0.7F, -7.0F, 1.4F, 1.4F, 4.8F),
            PartPose.offsetAndRotation(0.0F, -8.4F, 0.8F, 0.1F, 0.18F, 0.0F)
        );
        halo.addOrReplaceChild(
            "halo_spire",
            CubeListBuilder.create().texOffs(138, 50).addBox(-0.8F, -4.5F, -0.8F, 1.6F, 4.5F, 1.6F),
            PartPose.offset(0.0F, 0.0F, 4.6F)
        );
        body.addOrReplaceChild(
            "right_void_fragment",
            CubeListBuilder.create().texOffs(148, 50).addBox(-2.0F, -1.5F, -1.0F, 3.0F, 3.0F, 2.0F),
            PartPose.offsetAndRotation(-11.0F, 1.0F, 2.5F, 0.2F, -0.3F, -0.24F)
        );
        body.addOrReplaceChild(
            "left_void_fragment",
            CubeListBuilder.create().texOffs(148, 50).mirror().addBox(-1.0F, -1.5F, -1.0F, 3.0F, 3.0F, 2.0F),
            PartPose.offsetAndRotation(11.0F, 3.0F, -1.0F, -0.16F, 0.35F, 0.28F)
        );
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private static void addWing(
        final PartDefinition body,
        final String name,
        final String tipName,
        final float x,
        final float y,
        final float z,
        final float side,
        final float length,
        final float descent,
        final int textureX,
        final int textureY
    ) {
        final boolean left = side > 0.0F;
        final float rootX = left ? 0.0F : -length;
        final float ribLength = length * 0.55F;
        final float ribX = left ? length * 0.25F : -(length * 0.8F);
        final PartDefinition wing = body.addOrReplaceChild(
            name,
            CubeListBuilder.create().texOffs(textureX, textureY).mirror(left)
                .addBox(rootX, -2.0F, -1.0F, length, 4.0F, 2.0F)
                .texOffs(textureX + 36, textureY).addBox(ribX, 1.0F, -0.7F, ribLength, 4.0F, 1.4F),
            PartPose.offsetAndRotation(x, y, z, -0.10F, side * 0.30F, side * descent)
        );
        wing.addOrReplaceChild(
            name + "_root_rib",
            CubeListBuilder.create().texOffs(textureX + 36, textureY).mirror(left)
                .addBox(left ? -ribLength : 0.0F, -0.7F, -0.7F, ribLength, 1.4F, 1.4F),
            PartPose.offsetAndRotation(-side * 0.8F, 0.2F, -0.8F, -0.18F, side * 0.20F, -side * 0.08F)
        );
        wing.addOrReplaceChild(
            name + "_fractured_membrane",
            CubeListBuilder.create().texOffs(textureX, textureY).mirror(left)
                .addBox(rootX, -2.0F, -1.0F, length, 4.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, 1.4F, 1.2F, -0.18F, side * 0.32F, side * 0.06F)
        );
        wing.addOrReplaceChild(
            tipName,
            CubeListBuilder.create().texOffs(textureX + 60, textureY).mirror(left)
                .addBox(left ? 0.0F : -(length * 0.48F), -1.0F, -0.5F, length * 0.48F, 2.0F, 1.0F),
            PartPose.offsetAndRotation(side * (length - 1.0F), 0.0F, 0.6F, 0.0F, side * 0.28F, side * 0.2F)
        );
    }

    @Override
    public void setupAnim(final State state) {
        super.setupAnim(state);
        tormentHead.yRot += state.yRot * Mth.DEG_TO_RAD * 0.72F;
        tormentHead.xRot += state.xRot * Mth.DEG_TO_RAD;
        tormentBody.y += Mth.sin(state.ageInTicks * 0.08F) * 0.42F;
        internalFire.z -= Mth.sin(state.ageInTicks * 0.18F) * 0.16F;
        brokenHaloCage.yRot += state.ageInTicks * 0.014F;
        rightVoidFragment.yRot += state.ageInTicks * 0.021F;
        leftVoidFragment.yRot -= state.ageInTicks * 0.018F;
        final float drift = state.walkAnimationPos * 0.28F;
        final float reach = Math.min(state.walkAnimationSpeed, 1.0F) * 0.16F;
        rightUpperWing.zRot -= Mth.sin(drift) * reach;
        leftUpperWing.zRot += Mth.sin(drift) * reach;
        rightMiddleWing.zRot -= Mth.sin(drift + 0.8F) * reach;
        leftMiddleWing.zRot += Mth.sin(drift + 0.8F) * reach;
        rightLowerWing.zRot -= Mth.sin(drift + 1.6F) * reach;
        leftLowerWing.zRot += Mth.sin(drift + 1.6F) * reach;
        rightVoidTail.xRot += Mth.sin(drift) * reach;
        leftVoidTail.xRot += Mth.sin(drift + Mth.PI) * reach;
        final float command = Mth.clamp(state.commandProgress, 0.0F, 1.0F);
        rightUpperWing.zRot -= command * 0.52F;
        leftUpperWing.zRot += command * 0.52F;
        rightMiddleWing.zRot -= command * 0.36F;
        leftMiddleWing.zRot += command * 0.36F;
        rightLowerWing.zRot -= command * 0.22F;
        leftLowerWing.zRot += command * 0.22F;
        voidCage.xRot -= command * 0.16F;
        internalFire.z -= command * 0.42F;
        if (state.phasePulse) {
            brokenHaloCage.xRot += 0.18F;
            rightNarrowAntenna.zRot -= 0.03F;
            leftNarrowAntenna.zRot += 0.03F;
            splitVoidShroud.y += 0.35F;
        }
    }

    public static void extractRenderState(
        final InfernalHierarchyEntity entity,
        final State state,
        final float partialTicks
    ) {
        final Intent intent = entity.presentationIntent();
        state.commandProgress = switch (intent) {
            case COMMAND, FEAR_PULSE, PHASE_TELEGRAPH, PHASE_COMMIT -> 1.0F;
            default -> 0.0F;
        };
        state.phasePulse = entity.presentationPhaseState() != PhaseState.NONE;
    }

    public static final class State extends LivingEntityRenderState {
        public float commandProgress;
        public boolean phasePulse;
    }
}
