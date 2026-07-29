package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.transformation.SupernaturalForm;

public final class WolfsbaneRules {
    private WolfsbaneRules() {
    }

    public static Diagnostic diagnose(final boolean taggedWerewolf, final SupernaturalForm playerForm) {
        return taggedWerewolf || playerForm == SupernaturalForm.WEREWOLF
            ? Diagnostic.LYCANTHROPY_DETECTED
            : Diagnostic.CLEAR;
    }

    public enum Diagnostic {
        CLEAR,
        LYCANTHROPY_DETECTED
    }
}
