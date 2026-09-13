package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.IllusionCreeperEntity;
import com.kadamitas.warlockery.entity.MimicryRules.Phase;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.monster.creeper.CreeperModel;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;

/** The native Minecraft creeper rig with Warlockery mimic-phase presentation. */
public final class IllusionCreeperModel extends CreeperModel {
    public static final int TEXTURE_WIDTH = 64;
    public static final int TEXTURE_HEIGHT = 32;

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightHindLeg;
    private final ModelPart leftHindLeg;
    private final ModelPart rightFrontLeg;
    private final ModelPart leftFrontLeg;

    public IllusionCreeperModel(final ModelPart root) {
        super(root);
        head = root.getChild("head");
        body = root.getChild("body");
        rightHindLeg = root.getChild("right_hind_leg");
        leftHindLeg = root.getChild("left_hind_leg");
        rightFrontLeg = root.getChild("right_front_leg");
        leftFrontLeg = root.getChild("left_front_leg");
    }

    public static LayerDefinition createBodyLayer() {
        return CreeperModel.createBodyLayer(CubeDeformation.NONE);
    }

    @Override
    public void setupAnim(final CreeperRenderState renderState) {
        super.setupAnim(renderState);
        if (!(renderState instanceof State state)) {
            return;
        }
        if (state.phase == Phase.TELL || state.phase == Phase.HOLD) {
            body.xScale = 1.08F;
            body.zScale = 1.08F;
            head.y -= 0.35F;
        } else if (state.phase == Phase.COLLAPSE || state.phase == Phase.SPENT) {
            head.y += 2.0F;
            body.y += 2.0F;
            body.yScale = 0.72F;
            rightHindLeg.zRot = 0.28F;
            leftHindLeg.zRot = -0.28F;
            rightFrontLeg.zRot = 0.22F;
            leftFrontLeg.zRot = -0.22F;
        }
    }

    public static void extractRenderState(
        final IllusionCreeperEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.phase = entity.presentationPhase();
        state.swelling = switch (state.phase) {
            case TELL -> 0.45F;
            case HOLD -> 0.75F;
            case COLLAPSE -> 0.3F;
            default -> 0.0F;
        };
    }

    public static final class State extends CreeperRenderState {
        public Phase phase = Phase.LATENT;
    }
}
