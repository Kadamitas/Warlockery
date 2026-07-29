package com.kadamitas.warlockery.item;

public final class SunlightRules {
    private SunlightRules() {
    }

    public static UtilityDecision collector(
        final boolean chargeable,
        final boolean daytime,
        final boolean skyVisible
    ) {
        if (!chargeable) {
            return UtilityDecision.failure("wrong_chargeable");
        }
        if (!daytime || !skyVisible) {
            return UtilityDecision.failure("missing_sunlight");
        }
        return UtilityDecision.success("charged");
    }

    public static float grenadeDamage(final boolean vulnerable, final float baseDamage) {
        if (baseDamage < 0.0F) {
            throw new IllegalArgumentException("Sun grenade damage must not be negative");
        }
        return vulnerable ? baseDamage * 2.0F : baseDamage;
    }
}
