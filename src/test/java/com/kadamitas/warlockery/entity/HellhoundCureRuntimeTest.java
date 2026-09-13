package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

final class HellhoundCureRuntimeTest {
    @Test
    void cureRulesContractIsUnchanged() {
        assertEquals(3, HellhoundCureRules.REQUIRED_PROGRESS);
        final HellhoundCureRules.Result noWeakness =
            HellhoundCureRules.advance(0, false, true, 0);
        assertEquals(HellhoundCureRules.Diagnostic.NEEDS_WEAKNESS, noWeakness.diagnostic());
        final HellhoundCureRules.Result noApple =
            HellhoundCureRules.advance(0, true, false, 0);
        assertEquals(HellhoundCureRules.Diagnostic.NEEDS_GOLDEN_APPLE, noApple.diagnostic());
        final HellhoundCureRules.Result partial =
            HellhoundCureRules.advance(0, true, true, 2);
        assertEquals(1, partial.progress());
        assertEquals(HellhoundCureRules.Diagnostic.PROGRESS, partial.diagnostic());
        final HellhoundCureRules.Result caged =
            HellhoundCureRules.advance(0, true, true, 3);
        assertTrue(caged.cured(), "three sturdy faces complete from one apple");
        final HellhoundCureRules.Result finished =
            HellhoundCureRules.advance(2, true, true, 0);
        assertTrue(finished.cured(), "the third apple completes the cure");
    }
}
