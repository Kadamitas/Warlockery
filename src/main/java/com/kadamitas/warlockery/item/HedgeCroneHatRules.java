package com.kadamitas.warlockery.item;

public final class HedgeCroneHatRules {
    public static final float EVADE_CHANCE = 0.25F;
    public static final int RESERVE_COST = 8;

    private HedgeCroneHatRules() {
    }

    public static boolean shouldEvade(final boolean infused, final float roll) {
        return infused && roll >= 0.0F && roll < EVADE_CHANCE;
    }
}
