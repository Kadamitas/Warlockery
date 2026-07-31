package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class MandrakeHarvestRulesTest {
    @Test
    void daylightHarvestsWakeMandrakesMoreOftenThanNightHarvests() {
        assertTrue(MandrakeHarvestRules.awakens(false, 0.20F));
        assertTrue(MandrakeHarvestRules.awakens(true, 0.20F));
        assertTrue(MandrakeHarvestRules.awakens(false, 0.50F));
        assertFalse(MandrakeHarvestRules.awakens(true, 0.50F));
        assertFalse(MandrakeHarvestRules.awakens(false, 0.90F));
        assertFalse(MandrakeHarvestRules.awakens(true, 0.90F));
    }

    @Test
    void invalidRandomRollsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> MandrakeHarvestRules.awakens(false, -0.01F));
        assertThrows(IllegalArgumentException.class, () -> MandrakeHarvestRules.awakens(true, 1.0F));
    }
}
