package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.ArcaneMob;
import net.minecraft.client.model.animal.feline.AdultCatModel;
import net.minecraft.client.model.animal.feline.AdultFelineModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.FelineRenderState;

public final class FamiliarCatModel extends AdultFelineModel<FamiliarCatModel.State> {
    public static final int TEXTURE_WIDTH = 64;
    public static final int TEXTURE_HEIGHT = 32;

    public FamiliarCatModel(final ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        return LayerDefinition.create(
            AdultFelineModel.createBodyMesh(CubeDeformation.NONE),
            TEXTURE_WIDTH,
            TEXTURE_HEIGHT
        ).apply(AdultCatModel.CAT_TRANSFORMER);
    }

    @Override
    public void setupAnim(final State state) {
        state.isCrouching = state.isCrouching || state.stalking;
        super.setupAnim(state);
        if (state.airborne) {
            leftFrontLeg.xRot = -1.1F;
            rightFrontLeg.xRot = -1.1F;
            leftHindLeg.xRot = 0.75F;
            rightHindLeg.xRot = 0.75F;
            tail1.xRot = 1.15F;
            tail2.xRot = 1.55F;
        }
    }

    public static void extractRenderState(final ArcaneMob entity, final State state, final float partialTicks) {
        state.stalking = entity.isAggressive() || entity.getTarget() != null;
        state.isCrouching = state.stalking;
        state.isSprinting = entity.isSprinting();
        state.airborne = !entity.onGround();
    }

    public static final class State extends FelineRenderState {
        public boolean stalking;
        public boolean airborne;
    }
}
