package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.ArcaneMob;
import net.minecraft.client.model.animal.frog.FrogModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.FrogRenderState;

public final class ToadModel extends FrogModel {
    public static final int TEXTURE_WIDTH = 48;
    public static final int TEXTURE_HEIGHT = 48;

    private final ModelPart body;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;

    public ToadModel(final ModelPart root) {
        super(root);
        body = this.root.getChild("body");
        leftArm = body.getChild("left_arm");
        rightArm = body.getChild("right_arm");
        leftLeg = this.root.getChild("left_leg");
        rightLeg = this.root.getChild("right_leg");
    }

    public static LayerDefinition createBodyLayer() {
        return FrogModel.createBodyLayer();
    }

    @Override
    public void setupAnim(final FrogRenderState renderState) {
        super.setupAnim(renderState);
        if (!(renderState instanceof State state)) {
            return;
        }
        if (state.hopping) {
            body.xRot = -0.14F;
            leftArm.xRot = -0.85F;
            rightArm.xRot = -0.85F;
            leftLeg.xRot = 0.9F;
            rightLeg.xRot = 0.9F;
        }
        if (state.leaping) {
            body.xRot = -0.28F;
            leftArm.xRot = -1.15F;
            rightArm.xRot = -1.15F;
            leftLeg.xRot = 1.25F;
            rightLeg.xRot = 1.25F;
        }
    }

    public static void extractRenderState(final ArcaneMob entity, final State state, final float partialTicks) {
        final double verticalSpeed = entity.getDeltaMovement().y;
        state.hopping = !entity.onGround() || Math.abs(verticalSpeed) > 0.08D;
        state.leaping = verticalSpeed > 0.12D;
    }

    public static final class State extends FrogRenderState {
        public boolean hopping;
        public boolean leaping;
    }
}
