package com.kadamitas.warlockery.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class HellRiftRulesTest {
    @Test
    void riftDrainsEverySecondAndSpawnsEveryThreeSeconds() {
        assertTrue(HellRiftRules.active(99, 100));
        assertFalse(HellRiftRules.active(100, 100));
        assertTrue(HellRiftRules.drainsPower(40));
        assertFalse(HellRiftRules.drainsPower(41));
        assertEquals(101, HellRiftRules.nextSpawn(41));
        assertEquals(200, HellRiftRules.POWER_PER_SECOND);
    }

    @Test
    void remoteRiftsRetainTheCastingCircleAsTheirPowerSource() {
        final HellRiftData.Rift remote = new HellRiftData.Rift(12L, 34L, 9, 1_000L, 40L);
        final HellRiftData.Rift local = new HellRiftData.Rift(12L, 12L, 9, 1_000L, 40L);

        assertEquals(34L, remote.powerCenter());
        assertEquals(local.center(), local.powerCenter());
    }
}
