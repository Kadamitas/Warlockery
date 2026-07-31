package com.kadamitas.warlockery.block;

public final class MandrakeHarvestRules {
    public static final float DAY_AWAKENING_CHANCE = 0.75F;
    public static final float NIGHT_AWAKENING_CHANCE = 0.25F;

    private MandrakeHarvestRules() {
    }

    public static boolean awakens(final boolean night, final float roll) {
        if (roll < 0.0F || roll >= 1.0F) {
            throw new IllegalArgumentException("Mandrake awakening rolls must be in [0, 1)");
        }
        return roll < (night ? NIGHT_AWAKENING_CHANCE : DAY_AWAKENING_CHANCE);
    }
}
