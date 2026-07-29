package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.transformation.SupernaturalForm;

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

    public enum Status {
        MISSING_MATRIARCH_BLOOD,
        TRANSFORMATION_BLOCKED,
        READY
    }
}
