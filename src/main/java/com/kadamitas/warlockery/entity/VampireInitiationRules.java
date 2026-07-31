package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.transformation.SupernaturalForm;
import net.minecraft.world.InteractionHand;

public final class VampireInitiationRules {
    private VampireInitiationRules() {
    }

    public static Status assess(final boolean hasMatriarchBlood, final SupernaturalForm currentForm) {
        if (!hasMatriarchBlood) {
            return Status.MISSING_MATRIARCH_BLOOD;
        }
        if (currentForm != SupernaturalForm.NONE) {
            return Status.TRANSFORMATION_BLOCKED;
        }
        return Status.READY;
    }

    public static InteractionHand preferredHand(final boolean mainHandOffering, final boolean offHandOffering) {
        return mainHandOffering || !offHandOffering ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    public enum Status {
        MISSING_MATRIARCH_BLOOD,
        TRANSFORMATION_BLOCKED,
        READY
    }
}
