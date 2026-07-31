package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class MagicMirrorParityTest {
    @Test
    void pairedMirrorsAreBoundedAndRequireAClearExit() {
        assertTrue(MagicMirrorRules.canPair(16.0, true, true));
        assertFalse(MagicMirrorRules.canPair(16.01, true, true));
        assertFalse(MagicMirrorRules.canPair(8.0, false, true));
        assertFalse(MagicMirrorRules.canPair(8.0, true, false));
    }

    @Test
    void fairestQueryRewardsVitalityAndArmor() {
        assertTrue(MagicMirrorRules.fairnessScore(20.0F, 20.0F, 0.0F, 10)
            > MagicMirrorRules.fairnessScore(5.0F, 20.0F, 0.0F, 0));
        assertEquals("east", MagicMirrorRules.direction(8.0, 1.0));
        assertEquals("north", MagicMirrorRules.direction(1.0, -8.0));
        assertEquals(500, MagicMirrorRules.REPLICATION_POWER);
    }
}
