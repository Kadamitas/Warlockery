package com.kadamitas.warlockery.entity;

public final class DeathCombatRules {
    public static final double MAX_HEALTH = 1_000.0;
    public static final float MAX_INCOMING_DAMAGE = 15.0F;
    public static final float MELEE_HEALTH_FRACTION = 0.15F;

    private DeathCombatRules() {
    }

    public static float meleeDamage(final float targetMaximumHealth) {
        return Math.max(1.0F, targetMaximumHealth * MELEE_HEALTH_FRACTION);
    }

    public static float capIncoming(final float damage) {
        return Math.clamp(damage, 0.0F, MAX_INCOMING_DAMAGE);
    }
}
