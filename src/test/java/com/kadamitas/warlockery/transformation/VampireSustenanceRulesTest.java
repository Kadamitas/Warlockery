package com.kadamitas.warlockery.transformation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VampireSustenanceRulesTest {
    @Test
    void sanguineLatchRequiresFullBloodAndUsesHysteresis() {
        assertFalse(VampireSustenanceRules.updateSanguine(false, true, 100, 100));
        assertFalse(VampireSustenanceRules.updateSanguine(true, false, 99, 100));
        assertTrue(VampireSustenanceRules.updateSanguine(true, false, 100, 100));
        assertTrue(VampireSustenanceRules.updateSanguine(true, true, 90, 100));
        assertFalse(VampireSustenanceRules.updateSanguine(true, true, 89, 100));
    }

    @Test
    void statusDistinguishesEmptyMiddleAndLatchedReserve() {
        assertEquals(VampireSustenanceRules.Status.STARVED, VampireSustenanceRules.status(0, 100, false));
        assertEquals(VampireSustenanceRules.Status.SATED, VampireSustenanceRules.status(99, 100, false));
        assertEquals(VampireSustenanceRules.Status.SANGUINE, VampireSustenanceRules.status(90, 100, true));
    }

    @Test
    void regenerationIsDueEveryEightyTicksAndCostsOnePercentRoundedUp() {
        assertEquals(17, VampireSustenanceRules.NEUTRAL_FOOD_LEVEL);
        assertFalse(VampireSustenanceRules.shouldRegenerate(true, true, true, 79));
        assertTrue(VampireSustenanceRules.shouldRegenerate(true, true, true, 80));
        assertFalse(VampireSustenanceRules.shouldRegenerate(false, true, true, 80));
        assertFalse(VampireSustenanceRules.shouldRegenerate(true, false, true, 80));
        assertFalse(VampireSustenanceRules.shouldRegenerate(true, true, false, 80));
        assertEquals(1, VampireSustenanceRules.regenerationBloodCost(0));
        assertEquals(1, VampireSustenanceRules.regenerationBloodCost(100));
        assertEquals(2, VampireSustenanceRules.regenerationBloodCost(101));
        assertEquals(35, VampireSustenanceRules.regenerationBloodCost(3500));
    }
}
