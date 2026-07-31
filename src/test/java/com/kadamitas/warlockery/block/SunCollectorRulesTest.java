package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SunCollectorRulesTest {
    @Test
    void dawnToNoonCollectionRequiresAnAdjacentPoweredDetectorAndOpenSky() {
        assertEquals(0, SunCollectorRules.nextStrength(7, 0, 3_000, true));
        assertEquals(0, SunCollectorRules.nextStrength(7, 9, 3_000, false));
        assertEquals(9, SunCollectorRules.nextStrength(7, 9, 3_000, true));
        assertEquals(12, SunCollectorRules.nextStrength(12, 9, 3_000, true));
    }

    @Test
    void gatheredSunlightRemainsAvailableAfterNoonUntilCollected() {
        assertEquals(14, SunCollectorRules.nextStrength(14, 0, 8_000, false));
        assertTrue(SunCollectorRules.canCollect(14));
        assertFalse(SunCollectorRules.canCollect(0));
        assertEquals(10.0F, SunCollectorRules.baseDamage(15));
    }
}
