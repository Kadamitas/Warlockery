package com.kadamitas.warlockery.transformation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class WerewolfMoonRulesTest {
    @Test
    void fullMoonForcesWolfFormAtEveryWerewolfLevel() {
        for (int level = 1; level <= WerewolfProgressionRules.MAX_LEVEL; level++) {
            assertTrue(WerewolfMoonRules.forcesWolfForm(level, true, false));
        }
    }

    @Test
    void moonCharmOrWrongMoonPreventsForcedChange() {
        assertFalse(WerewolfMoonRules.forcesWolfForm(10, true, true));
        assertFalse(WerewolfMoonRules.forcesWolfForm(10, false, false));
        assertFalse(WerewolfMoonRules.forcesWolfForm(0, true, false));
        assertThrows(IllegalArgumentException.class, () -> WerewolfMoonRules.forcesWolfForm(11, true, false));
    }
}
