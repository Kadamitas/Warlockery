package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.transformation.SupernaturalProgression;

public final class ProgressionTokenRules {
    public static final int MAX_LEVEL = SupernaturalProgression.MAX_LEVEL;

    private ProgressionTokenRules() {
    }

    public static int next(final int current) {
        return current >= MAX_LEVEL ? 0 : Math.max(0, current) + 1;
    }

    public static UtilityDecision diagnose(final boolean creative, final int nextLevel) {
        if (!creative) {
            return UtilityDecision.failure("creative_required");
        }
        return UtilityDecision.success(nextLevel == 0 ? "cleared" : "level_changed");
    }
}
