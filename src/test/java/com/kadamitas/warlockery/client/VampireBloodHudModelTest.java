package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.transformation.VampireSustenanceRules;
import org.junit.jupiter.api.Test;

class VampireBloodHudModelTest {
    @Test
    void verticalFillUsesBottomAnchoredPixelRows() {
        assertEquals(0, VampireBloodHudModel.filledHeight(0, 100, 12));
        assertEquals(6, VampireBloodHudModel.filledHeight(50, 100, 12));
        assertEquals(12, VampireBloodHudModel.filledHeight(100, 100, 12));
    }

    @Test
    void pulseIsRestrainedAndOnlyRunsForExtremeStates() {
        assertTrue(VampireBloodHudModel.pulseAlpha(VampireSustenanceRules.Status.STARVED, 10) >= 0.70F);
        assertEquals(1.0F, VampireBloodHudModel.pulseAlpha(VampireSustenanceRules.Status.SATED, 10));
        assertTrue(VampireBloodHudModel.pulseAlpha(VampireSustenanceRules.Status.SANGUINE, 10) <= 1.0F);
        assertFalse(VampireBloodHudModel.statusKey(VampireSustenanceRules.Status.SATED).isBlank());
    }
}
