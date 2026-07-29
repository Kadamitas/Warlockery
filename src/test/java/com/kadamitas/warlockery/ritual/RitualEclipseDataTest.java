package com.kadamitas.warlockery.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RitualEclipseDataTest {
    @Test
    void durationExtendsAndExpiresDeterministically() {
        assertEquals(1_200L, RitualEclipseData.extendedExpiration(0L, 200L, 1_000));
        assertEquals(1_500L, RitualEclipseData.extendedExpiration(1_500L, 300L, 100));
        assertFalse(RitualEclipseData.shouldExpire(1_499L, 1_500L));
        assertTrue(RitualEclipseData.shouldExpire(1_500L, 1_500L));
    }

    @Test
    void priorClockProgressIsRestoredAfterTheEclipse() {
        assertEquals(9_600L, RitualEclipseData.restoreTicks(8_000L, 400L, 2_000L, true));
        assertEquals(8_000L, RitualEclipseData.restoreTicks(8_000L, 400L, 2_000L, false));
    }
}
