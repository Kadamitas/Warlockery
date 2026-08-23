package com.kadamitas.warlockery.transformation;

public final class VampireSustenanceRules {
    public static final int NEUTRAL_FOOD_LEVEL = 17;
    public static final int REGENERATION_INTERVAL_TICKS = 80;

    private VampireSustenanceRules() {
    }

    public static boolean updateSanguine(
        final boolean vampire,
        final boolean latched,
        final int blood,
        final int maximumBlood
    ) {
        if (!vampire || maximumBlood <= 0) {
            return false;
        }
        final int reserve = Math.clamp(blood, 0, maximumBlood);
        return latched ? (long) reserve * 10L >= (long) maximumBlood * 9L : reserve == maximumBlood;
    }

    public static Status status(final int blood, final int maximumBlood, final boolean sanguine) {
        if (blood <= 0 || maximumBlood <= 0) {
            return Status.STARVED;
        }
        return sanguine ? Status.SANGUINE : Status.SATED;
    }

    public static boolean shouldRegenerate(
        final boolean naturalRegeneration,
        final boolean sanguine,
        final boolean hurt,
        final long tickCount
    ) {
        return naturalRegeneration && sanguine && hurt && tickCount % REGENERATION_INTERVAL_TICKS == 0;
    }

    public static int regenerationBloodCost(final int maximumBlood) {
        return Math.max(1, Math.ceilDiv(Math.max(0, maximumBlood), 100));
    }

    public enum Status {
        STARVED,
        SATED,
        SANGUINE
    }
}
