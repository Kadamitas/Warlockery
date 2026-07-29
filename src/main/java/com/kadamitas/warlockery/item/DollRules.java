package com.kadamitas.warlockery.item;

public final class DollRules {
    public static final int DURABILITY_REPAIRED_PER_CHARGE = 2;

    private DollRules() {
    }

    public static boolean isLethal(final float health, final float finalDamage) {
        return finalDamage >= health;
    }

    public static boolean needsRepair(final int damage, final int maxDamage) {
        return maxDamage > 0 && damage > 0;
    }

    public static int repairedDamage(final int damage) {
        return Math.max(0, damage - DURABILITY_REPAIRED_PER_CHARGE);
    }
}
