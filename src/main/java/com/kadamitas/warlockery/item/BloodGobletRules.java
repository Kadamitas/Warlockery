package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.transformation.SupernaturalForm;

public final class BloodGobletRules {
    private BloodGobletRules() {
    }

    public static UtilityDecision drink(final boolean full, final SupernaturalForm form) {
        if (!full) {
            return UtilityDecision.failure("empty");
        }
        if (form != SupernaturalForm.VAMPIRE) {
            return UtilityDecision.failure("initiation_required");
        }
        return UtilityDecision.success("consumed");
    }
}
