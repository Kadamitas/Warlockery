package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class DollRulesTest {
    @Test
    void empoweredHexesRequireTwoDistinctGuards() {
        assertFalse(HexGuardRules.hasRequiredGuards(1, 2));
        assertTrue(HexGuardRules.hasRequiredGuards(2, 2));
        assertThrows(IllegalArgumentException.class, () -> HexGuardRules.hasRequiredGuards(2, 0));
    }

    @Test
    void corruptionCapsTargetsAndDollGuardIntercepts() {
        assertEquals(10, DollCorruptionRules.plan(false, 14, 10).dollsToDamage());
        assertTrue(DollCorruptionRules.plan(true, 14, 10).intercepted());
    }

    @Test
    void mendingRepairsWithoutGoingBelowZero() {
        assertFalse(DollRules.needsRepair(0, 100));
        assertTrue(DollRules.needsRepair(10, 100));
        assertEquals(8, DollRules.repairedDamage(10));
        assertEquals(0, DollRules.repairedDamage(1));
    }

    @Test
    void lethalProtectionRestoresTheFullExistingMaximumWithoutExtraHearts() {
        assertFalse(DollRules.isLethal(5.0F, 4.0F));
        assertTrue(DollRules.isLethal(5.0F, 5.0F));
        assertEquals(20.0F, DollRules.restoredHealth(20.0F));
        assertEquals(30.0F, DollRules.restoredHealth(30.0F));
        assertEquals(6.0F, DollRules.restoredHealth(6.0F));
        assertEquals(1.0F, DollRules.restoredHealth(0.0F));
    }

    @Test
    void hexActionsCycleBackToPrick() {
        assertEquals(DollHexAction.SHOVE, DollHexAction.PRICK.next());
        assertEquals(DollHexAction.IGNITE, DollHexAction.SHOVE.next());
        assertEquals(DollHexAction.DROWN, DollHexAction.IGNITE.next());
        assertEquals(DollHexAction.PRICK, DollHexAction.DROWN.next());
    }
}
