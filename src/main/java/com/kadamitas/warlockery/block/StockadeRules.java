package com.kadamitas.warlockery.block;

public final class StockadeRules {
    private StockadeRules() {
    }

    public static boolean shouldImpale(
        final boolean living,
        final boolean alive,
        final boolean spectator,
        final boolean immune,
        final double fallDistance
    ) {
        return living && alive && !spectator && !immune && fallDistance > 0.5F;
    }

    public static float damage(final double fallDistance) {
        return (float) Math.clamp(2.0 + Math.max(0.0, fallDistance), 2.0, 10.0);
    }
}
