package com.kadamitas.warlockery.util;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClock;

/**
 * Keeps a fixture's clock changes from escaping into the rest of the run.
 *
 * <p>{@code GameTestHelper.setTime} moves the level clock, which is shared by every fixture in the
 * run. Nothing restores it, so once one fixture sets night, every fixture that happens to run
 * afterwards also runs at night. Batch order is decided by a hash map and therefore differs
 * between JVM runs, so which fixtures see day and which see night changes from run to run and any
 * fixture whose mobs care about daylight becomes intermittent through no fault of its own.</p>
 */
public final class GameTestWorldClock {
    private GameTestWorldClock() {
    }

    /**
     * Records the clock as it stands now and restores it when the test finishes, pass or fail.
     * Call once, before the first {@code setTime} of a fixture that moves the clock.
     */
    public static void restoreAfterTest(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final Holder<WorldClock> clock = level.dimensionType().defaultClock().orElseThrow();
        final long original = level.clockManager().getTotalTicks(clock);
        GameTestCleanup.add(helper, passed -> level.clockManager().setTotalTicks(clock, original));
    }
}
