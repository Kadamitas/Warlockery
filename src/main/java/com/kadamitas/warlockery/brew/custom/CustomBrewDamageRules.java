package com.kadamitas.warlockery.brew.custom;

public final class CustomBrewDamageRules {
    public static final float STANDARD_DAMAGE_CAP = 20.0F;
    public static final float ENGINE_DAMAGE_CAP = 2_048.0F;

    private CustomBrewDamageRules() {
    }

    public static float instantDamage(
        final int amplifier,
        final double distanceScale,
        final boolean uncapped
    ) {
        final double scale = Math.clamp(distanceScale, 0.0, 1.0);
        final double raw = Math.scalb(6.0, Math.clamp(amplifier, 0, 30)) * scale;
        final float cap = uncapped ? ENGINE_DAMAGE_CAP : STANDARD_DAMAGE_CAP;
        return (float) Math.clamp(raw, 0.0, cap);
    }
}
