package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class HellhoundCureRulesTest {
    @Test
    void weaknessAndGoldenApplesAreBothRequired() {
        assertEquals(HellhoundCureRules.Diagnostic.NEEDS_WEAKNESS,
            HellhoundCureRules.advance(0, false, true, 4).diagnostic());
        assertEquals(HellhoundCureRules.Diagnostic.NEEDS_GOLDEN_APPLE,
            HellhoundCureRules.advance(0, true, false, 4).diagnostic());
    }

    @Test
    void aSecureCageAcceleratesTheCure() {
        final HellhoundCureRules.Result open = HellhoundCureRules.advance(0, true, true, 0);
        final HellhoundCureRules.Result caged = HellhoundCureRules.advance(0, true, true, 3);
        assertFalse(open.cured());
        assertEquals(1, open.progress());
        assertTrue(caged.cured());
        assertEquals(HellhoundCureRules.REQUIRED_PROGRESS, caged.progress());
    }
}
