package com.kadamitas.warlockery.effect;

final class UndeadMendingRules {
    private static final int BASE_INTERVAL = 50;

    private UndeadMendingRules() {
    }

    static boolean healsThisTick(final int duration, final int amplifier) {
        final int interval = BASE_INTERVAL >> amplifier;
        return interval > 0 ? duration % interval == 0 : true;
    }

    static float healingAmount(final boolean undead, final float health, final float maxHealth) {
        return undead && health < maxHealth ? Math.min(1.0F, maxHealth - health) : 0.0F;
    }
}
