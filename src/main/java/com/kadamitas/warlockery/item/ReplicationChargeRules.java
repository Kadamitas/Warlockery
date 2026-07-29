package com.kadamitas.warlockery.item;

public final class ReplicationChargeRules {
    private ReplicationChargeRules() {
    }

    public static UtilityDecision diagnose(final boolean target, final boolean spawnSpace) {
        if (!target) {
            return UtilityDecision.failure("missing_reflection");
        }
        return spawnSpace ? UtilityDecision.success("duplicate_released") : UtilityDecision.failure("blocked");
    }
}
