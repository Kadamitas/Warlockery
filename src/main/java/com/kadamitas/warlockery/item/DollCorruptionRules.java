package com.kadamitas.warlockery.item;

public final class DollCorruptionRules {
    public static final int LEGACY_MAX_TARGETS = 10;

    private DollCorruptionRules() {
    }

    public static CorruptionPlan plan(
        final boolean guarded,
        final int availableProtectionDolls,
        final int maximumTargets
    ) {
        if (availableProtectionDolls < 0 || maximumTargets < 0) {
            throw new IllegalArgumentException("Corruption counts must be nonnegative");
        }
        final boolean intercepted = guarded && availableProtectionDolls > 0 && maximumTargets > 0;
        return new CorruptionPlan(
            intercepted,
            intercepted ? 0 : Math.min(availableProtectionDolls, maximumTargets)
        );
    }

    public static int destructionWear(final boolean damageable, final int maximumDamage) {
        if (maximumDamage < 0) {
            throw new IllegalArgumentException("Maximum damage must be nonnegative");
        }
        return damageable ? Math.max(1, maximumDamage) : 1;
    }

    public record CorruptionPlan(boolean intercepted, int dollsToDamage) {
        public boolean foundTarget() {
            return intercepted || dollsToDamage > 0;
        }
    }
}
