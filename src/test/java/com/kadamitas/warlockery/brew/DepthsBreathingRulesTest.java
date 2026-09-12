package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class DepthsBreathingRulesTest {
    @Test
    void dryLandDepletesTheReserveDespiteVanillaRestoringAirBetweenUpdates() {
        int reserve = 300;
        for (int second = 1; second < 8; second++) {
            final var breath = DepthsBreathingRules.tick(false, 300, 300, reserve);
            assertFalse(breath.drowning());
            reserve = breath.air();
        }
        final var breath = DepthsBreathingRules.tick(false, 300, 300, reserve);
        assertTrue(breath.drowning());
        assertEquals(0, breath.air());
    }

    @Test
    void submersionRefillsAnExhaustedReserveWithoutDamage() {
        assertEquals(new DepthsBreathingRules.Breath(300, false),
            DepthsBreathingRules.tick(true, 300, -20, 0));
    }

    @Test
    void lowerActualAirIsNotRestoredByAnOlderSavedReserve() {
        assertEquals(new DepthsBreathingRules.Breath(0, true),
            DepthsBreathingRules.tick(false, 300, 20, 300));
    }

    @Test
    void returningToWaterRestoresAFullGracePeriodOnLand() {
        final var inWater = DepthsBreathingRules.tick(true, 600, 0, 0);
        assertEquals(new DepthsBreathingRules.Breath(560, false),
            DepthsBreathingRules.tick(false, 600, 600, inWater.air()));
    }
}
