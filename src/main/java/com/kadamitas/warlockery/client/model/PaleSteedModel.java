package com.kadamitas.warlockery.client.model;

import com.kadamitas.warlockery.entity.SpectralSteedEntity;
import com.kadamitas.warlockery.entity.SpectralSteedRules.Gait;
import net.minecraft.client.model.animal.equine.AbstractEquineModel;
import net.minecraft.client.model.animal.equine.HorseModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.EquineRenderState;

/** A spectral mount rendered by Minecraft's adult horse anatomy and animation rig. */
public final class PaleSteedModel extends HorseModel {
    public static final int TEXTURE_WIDTH = 64;
    public static final int TEXTURE_HEIGHT = 64;

    public PaleSteedModel(final ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        return LayerDefinition.create(
            AbstractEquineModel.createBodyMesh(CubeDeformation.NONE),
            TEXTURE_WIDTH,
            TEXTURE_HEIGHT
        );
    }

    public static void extractRenderState(
        final SpectralSteedEntity entity,
        final State state,
        final float partialTicks
    ) {
        state.gait = entity.presentationGait();
        state.bond = entity.presentationBond();
        state.fatigue = entity.presentationFatigue();
        state.balking = entity.presentationBalking();
        state.resting = entity.presentationResting();
        state.carrying = entity.isVehicle();
        state.airborne = !entity.onGround();
        state.isRidden = state.carrying;
        state.standAnimation = state.balking ? 1.0F : 0.0F;
        state.eatAnimation = state.resting ? 1.0F : 0.0F;
        state.animateTail = state.gait != Gait.HALT || state.airborne;
    }

    public static final class State extends EquineRenderState {
        public Gait gait = Gait.HALT;
        public int bond;
        public int fatigue;
        public boolean balking;
        public boolean resting;
        public boolean carrying;
        public boolean airborne;
    }
}
