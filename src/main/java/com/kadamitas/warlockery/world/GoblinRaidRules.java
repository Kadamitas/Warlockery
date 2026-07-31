package com.kadamitas.warlockery.world;

import net.minecraft.world.Difficulty;

public final class GoblinRaidRules {
    public static final int WAVE_COUNT = 3;
    public static final int CHECK_INTERVAL_TICKS = 20;
    public static final int INTERMISSION_TICKS = 200;
    public static final int RAID_DURATION_TICKS = 12_000;
    public static final int SPAWN_RADIUS = 36;
    public static final int MINIMUM_DELAY_TICKS = 24_000;
    public static final int MAXIMUM_DELAY_TICKS = 72_000;

    private GoblinRaidRules() {
    }

    public static int waveSize(final int wave) {
        if (wave < 1 || wave > WAVE_COUNT) {
            throw new IllegalArgumentException("Goblin raid wave must be between 1 and " + WAVE_COUNT);
        }
        return 1 + wave * 2;
    }

    public static long nextDelay(final long roll) {
        final long range = MAXIMUM_DELAY_TICKS - MINIMUM_DELAY_TICKS + 1L;
        return MINIMUM_DELAY_TICKS + Math.floorMod(roll, range);
    }

    public static boolean canStart(
        final Difficulty difficulty,
        final boolean villagePresent,
        final boolean raidActive,
        final long gameTime,
        final long nextAttempt
    ) {
        return difficulty != Difficulty.PEACEFUL
            && villagePresent
            && !raidActive
            && gameTime >= nextAttempt;
    }
}
