package com.kadamitas.warlockery.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CreatureWorldIntegrationScheduleTest {
    @Test
    void worldEventsRunOnlyOnTheirConfiguredIntervals() {
        assertTrue(CreatureWorldIntegration.scheduled(0L, 200));
        assertTrue(CreatureWorldIntegration.scheduled(2_400L, 2_400));
        assertTrue(CreatureWorldIntegration.scheduled(4_800L, 2_400));
        assertFalse(CreatureWorldIntegration.scheduled(199L, 200));
        assertFalse(CreatureWorldIntegration.scheduled(2_401L, 2_400));
    }
}
