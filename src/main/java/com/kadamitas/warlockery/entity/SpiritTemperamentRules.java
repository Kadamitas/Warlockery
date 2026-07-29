package com.kadamitas.warlockery.entity;

public final class SpiritTemperamentRules {
    public static final double FLEE_DISTANCE_SQUARED = 144.0;

    private SpiritTemperamentRules() {
    }

    public static boolean shouldFlee(
        final boolean bound,
        final boolean playerAlive,
        final double distanceSquared
    ) {
        return !bound && playerAlive && distanceSquared <= FLEE_DISTANCE_SQUARED;
    }

    public static boolean canAttack(final boolean bound, final boolean ownerThreatened) {
        return bound && ownerThreatened;
    }
}
