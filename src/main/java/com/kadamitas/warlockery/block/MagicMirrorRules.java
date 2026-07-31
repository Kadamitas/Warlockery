package com.kadamitas.warlockery.block;

public final class MagicMirrorRules {
    public static final int MAX_PAIR_DISTANCE = 16;
    public static final int REPLICATION_POWER = 500;

    private MagicMirrorRules() {
    }

    public static boolean canPair(final double distance, final boolean sameDimension, final boolean exitClear) {
        return sameDimension && exitClear && distance > 0.0 && distance <= MAX_PAIR_DISTANCE;
    }

    public static double fairnessScore(
        final float health,
        final float maximumHealth,
        final float absorption,
        final int armor
    ) {
        final double vitality = maximumHealth <= 0.0F ? 0.0 : health / maximumHealth;
        return vitality * 20.0 + absorption + armor * 0.5;
    }

    public static String direction(final double horizontal, final double vertical) {
        if (Math.abs(horizontal) > Math.abs(vertical)) {
            return horizontal < 0.0 ? "west" : "east";
        }
        return vertical < 0.0 ? "north" : "south";
    }
}
