package com.kadamitas.warlockery.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class RitualInhibitorIntegrationTest {
    @Test
    void voidBrambleSuppressionFeedsTheOrdinaryRitualDiagnostic() {
        assertEquals(0, RitualManager.ritualInhibitorCount(0, false));
        assertEquals(1, RitualManager.ritualInhibitorCount(0, true));
        assertEquals(3, RitualManager.ritualInhibitorCount(2, true));
        assertEquals(1, RitualManager.ritualInhibitorCount(0, false, true));
        assertEquals(4, RitualManager.ritualInhibitorCount(2, true, true));
    }
}
