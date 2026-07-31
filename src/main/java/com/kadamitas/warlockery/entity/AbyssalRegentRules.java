package com.kadamitas.warlockery.entity;

public final class AbyssalRegentRules {
    public static final double MAX_HEALTH = 500.0D;
    public static final double ATTACK_DAMAGE = 11.0D;
    public static final double ARMOR = 8.0D;

    private AbyssalRegentRules() {
    }

    public static boolean beginsTormentPhase(final double health, final boolean phaseTriggered) {
        if (health < 0.0D || health > MAX_HEALTH) {
            throw new IllegalArgumentException("Abyssal Regent health must be within its combat profile");
        }
        return !phaseTriggered && health <= MAX_HEALTH / 2.0D;
    }
}
