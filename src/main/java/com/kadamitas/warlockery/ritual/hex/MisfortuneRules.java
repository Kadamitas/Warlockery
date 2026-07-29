package com.kadamitas.warlockery.ritual.hex;

import java.util.UUID;

public final class MisfortuneRules {
    public static final int INTERVAL_TICKS = 100;

    private MisfortuneRules() {
    }

    public static int outcomeIndex(final UUID targetId, final long gameTime, final int outcomeCount) {
        if (outcomeCount < 1) {
            throw new IllegalArgumentException("Misfortune requires at least one outcome");
        }
        final long window = Math.floorDiv(gameTime, INTERVAL_TICKS);
        final long mixed = targetId.getMostSignificantBits()
            ^ Long.rotateLeft(targetId.getLeastSignificantBits(), 21)
            ^ window * 0x9E3779B97F4A7C15L;
        return Math.floorMod(Long.hashCode(mixed), outcomeCount);
    }

    public static boolean shouldTrigger(final int tickCount) {
        return Math.floorMod(tickCount, INTERVAL_TICKS) == 0;
    }
}
