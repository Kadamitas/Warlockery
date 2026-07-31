package com.kadamitas.warlockery.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.Difficulty;
import org.junit.jupiter.api.Test;

final class GoblinRaidRulesTest {
    @Test
    void raidsEscalateAcrossThreeDistinctWaves() {
        assertEquals(3, GoblinRaidRules.waveSize(1));
        assertEquals(5, GoblinRaidRules.waveSize(2));
        assertEquals(7, GoblinRaidRules.waveSize(3));
        assertThrows(IllegalArgumentException.class, () -> GoblinRaidRules.waveSize(0));
        assertThrows(IllegalArgumentException.class, () -> GoblinRaidRules.waveSize(4));
    }

    @Test
    void randomizedRaidDelayAlwaysStaysInsideItsBounds() {
        for (final long roll : new long[]{Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE}) {
            final long delay = GoblinRaidRules.nextDelay(roll);
            assertTrue(delay >= GoblinRaidRules.MINIMUM_DELAY_TICKS);
            assertTrue(delay <= GoblinRaidRules.MAXIMUM_DELAY_TICKS);
        }
    }

    @Test
    void raidsRequireARealVillageCooldownAndNonPeacefulDifficulty() {
        assertTrue(GoblinRaidRules.canStart(Difficulty.NORMAL, true, false, 24_000L, 24_000L));
        assertFalse(GoblinRaidRules.canStart(Difficulty.PEACEFUL, true, false, 24_000L, 24_000L));
        assertFalse(GoblinRaidRules.canStart(Difficulty.NORMAL, false, false, 24_000L, 24_000L));
        assertFalse(GoblinRaidRules.canStart(Difficulty.NORMAL, true, true, 24_000L, 24_000L));
        assertFalse(GoblinRaidRules.canStart(Difficulty.NORMAL, true, false, 23_999L, 24_000L));
    }
}
