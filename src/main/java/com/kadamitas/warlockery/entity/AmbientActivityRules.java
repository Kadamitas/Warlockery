package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.AmbientActivityProfile.ActivityType;

public final class AmbientActivityRules {
    public static final int TEMPORARY_HEARTH_TICKS = 1_200;
    public static final int SEARCH_RADIUS = 12;

    private AmbientActivityRules() {
    }

    public static boolean canStart(
        final boolean alive,
        final boolean noAi,
        final boolean inCombat,
        final boolean escapingHazard,
        final boolean passenger
    ) {
        return alive && !noAi && !inCombat && !escapingHazard && !passenger;
    }

    public static boolean shouldCheck(
        final int tickCount,
        final int entityId,
        final int activityOrdinal,
        final int interval
    ) {
        return Math.floorMod(tickCount + entityId * 17 + activityOrdinal * 31, interval) == 0;
    }

    public static boolean passesRareRoll(
        final long gameTime,
        final int entityId,
        final ActivityType type,
        final int denominator
    ) {
        final long mixed = gameTime * 31L + entityId * 0x9E3779B9L + type.ordinal() * 0xC2B2AE35L;
        return Math.floorMod(mixed ^ mixed >>> 16, denominator) == 0;
    }

    public static boolean cooldownElapsed(final long gameTime, final long nextAllowedTime) {
        return gameTime >= nextAllowedTime;
    }

    public static boolean isNight(final long dayTime) {
        final long time = Math.floorMod(dayTime, 24_000L);
        return time >= 13_000L && time <= 23_000L;
    }

    public static boolean isDay(final long dayTime) {
        return !isNight(dayTime);
    }

    public static boolean isColdBiomeId(final String biomeId) {
        final String id = biomeId.toLowerCase(java.util.Locale.ROOT);
        return id.contains("snow") || id.contains("frozen") || id.contains("ice_spikes")
            || id.contains("grove") || id.contains("jagged_peaks") || id.contains("frozen_peaks");
    }
}

