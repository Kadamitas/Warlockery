package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.item.UtilityDecision;

public final class StatueRules {
    private StatueRules() {
    }

    public static UtilityDecision diagnose(
        final StatueProfile.Effect effect,
        final boolean hasHex,
        final boolean hasOffering,
        final boolean active
    ) {
        return switch (effect) {
            case CLEANSE -> hasHex
                ? UtilityDecision.success("cleansing_ready")
                : UtilityDecision.failure("no_hexes");
            case PATRON_BLESSING -> hasOffering
                ? UtilityDecision.success("offering_ready")
                : UtilityDecision.failure("missing_offering");
            case OCCLUDE_RITUALS -> UtilityDecision.success(active ? "occlusion_active" : "occlusion_inactive");
        };
    }

    public static UtilityDecision patron(
        final boolean bound,
        final boolean targetAvailable,
        final boolean hasOffering
    ) {
        if (!bound) {
            return UtilityDecision.failure("missing_binding");
        }
        if (!targetAvailable) {
            return UtilityDecision.failure("missing_bound_target");
        }
        return hasOffering
            ? UtilityDecision.success("offering_ready")
            : UtilityDecision.failure("missing_offering");
    }
}
