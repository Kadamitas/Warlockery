package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.WerewolfShape;
import com.kadamitas.warlockery.transformation.WolfMouthItemPresentation;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public final class WolfFormRenderState extends WolfRenderState {
    private final ItemStackRenderState mouthItem = new ItemStackRenderState();
    private boolean fallFlying;
    private float fallFlyingScale;
    private boolean applyFlyingYRotation;
    private float flyingYRotation;
    private float swimAmount;
    private boolean visuallySwimming;
    private WolfMouthItemPresentation.MouthPose mouthPose = WolfMouthItemPresentation.resolve(
        SupernaturalForm.WEREWOLF,
        WerewolfShape.WOLF,
        true,
        true
    ).pose();

    public ItemStackRenderState mouthItem() {
        return mouthItem;
    }

    public WolfMouthItemPresentation.MouthPose mouthPose() {
        return mouthPose;
    }

    void prepareMouthItem(final WolfMouthItemPresentation.MouthPose pose) {
        mouthItem.clear();
        mouthPose = pose;
    }

    void applyAvatarPose(final AvatarRenderState avatarState) {
        fallFlying = avatarState.isFallFlying;
        fallFlyingScale = avatarState.fallFlyingScale();
        applyFlyingYRotation = avatarState.shouldApplyFlyingYRot;
        flyingYRotation = avatarState.flyingYRot;
        swimAmount = avatarState.swimAmount;
        visuallySwimming = avatarState.isVisuallySwimming;
    }

    boolean fallFlying() {
        return fallFlying;
    }

    float fallFlyingScale() {
        return fallFlyingScale;
    }

    boolean applyFlyingYRotation() {
        return applyFlyingYRotation;
    }

    float flyingYRotation() {
        return flyingYRotation;
    }

    float swimAmount() {
        return swimAmount;
    }

    boolean visuallySwimming() {
        return visuallySwimming;
    }
}
