package com.kadamitas.warlockery.item;

public final class DollRules {
    public static final int DURABILITY_REPAIRED_PER_CHARGE = 2;
    public static final float PROTECTION_RECOVERY_HEALTH = 10.0F;

    private DollRules() {
    }

    public static boolean isLethal(final float health, final float finalDamage) {
        return finalDamage >= health;
    }

    public static float restoredHealth(final float maximumHealth) {
        return Math.min(PROTECTION_RECOVERY_HEALTH, Math.max(1.0F, maximumHealth));
    }

    public static boolean needsRepair(final int damage, final int maxDamage) {
        return maxDamage > 0 && damage > 0;
    }

    public static int repairedDamage(final int damage) {
        return Math.max(0, damage - DURABILITY_REPAIRED_PER_CHARGE);
    }

    public static boolean canApplyToSelf(final DollAbility ability) {
        return switch (ability) {
            case DollAbility.LethalProtection ignored -> true;
            case DollAbility.Mending ignored -> true;
            case DollAbility.HexGuard ignored -> true;
            case DollAbility.DollGuard ignored -> true;
            case DollAbility.None ignored -> false;
            case DollAbility.ActiveHex ignored -> false;
            case DollAbility.DamageLink ignored -> false;
        };
    }
}
