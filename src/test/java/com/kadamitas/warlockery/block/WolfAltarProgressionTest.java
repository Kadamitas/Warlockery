package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class WolfAltarProgressionTest {
    @Test
    void incompleteTrialReportsItsFirstMissingCondition() {
        assertEquals(
            "missing_wolf_head",
            UtilityDeviceRules.wolfAltar(false, false, false, 9).diagnostic()
        );
        assertEquals(
            "missing_offering",
            UtilityDeviceRules.wolfAltar(true, false, true, 9).diagnostic()
        );
        assertEquals(
            "moon_required",
            UtilityDeviceRules.wolfAltar(true, true, false, 9).diagnostic()
        );
    }

    @Test
    void finalTrialAwardsExactlyOneHorn() {
        final var progression = UtilityDeviceRules.advanceWolf(9);

        assertEquals(10, progression.level());
        assertTrue(progression.advanced());
        assertTrue(progression.hornEarned());
    }

    @Test
    void completedPathCannotDuplicateItsReward() {
        final var progression = UtilityDeviceRules.advanceWolf(10);

        assertEquals(10, progression.level());
        assertFalse(progression.advanced());
        assertFalse(progression.hornEarned());
        assertEquals(
            "path_complete",
            UtilityDeviceRules.wolfAltar(false, false, false, 10).diagnostic()
        );
    }

    @Test
    void corruptedLevelsAreBounded() {
        assertEquals(1, UtilityDeviceRules.advanceWolf(-40).level());
        assertEquals(10, UtilityDeviceRules.advanceWolf(200).level());
    }
}
