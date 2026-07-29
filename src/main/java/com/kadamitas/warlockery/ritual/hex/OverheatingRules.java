package com.kadamitas.warlockery.ritual.hex;

public final class OverheatingRules {
    public static final float HOT_TEMPERATURE = 1.0F;

    private OverheatingRules() {
    }

    public static boolean shouldBurn(
        final boolean taggedHotBiome,
        final float baseTemperature,
        final boolean wet,
        final boolean fireResistant
    ) {
        return (taggedHotBiome || baseTemperature >= HOT_TEMPERATURE) && !wet && !fireResistant;
    }
}
