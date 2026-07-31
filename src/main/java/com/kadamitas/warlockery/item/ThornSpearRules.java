package com.kadamitas.warlockery.item;

public final class ThornSpearRules {
    public static final double KNOCKBACK_RESISTANCE = 0.5;
    public static final float GUARD_WOLF_CHANCE = 0.25F;

    private ThornSpearRules() {
    }

    public static boolean summonsGuardWolf(final float roll) {
        return roll >= 0.0F && roll < GUARD_WOLF_CHANCE;
    }

    public static float spiritWorldDamage(final float damage, final boolean spiritWorld) {
        return spiritWorld ? damage * 2.0F : damage;
    }
}
