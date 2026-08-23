package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
    void everyStatusPublishesAnOpaqueLabelColor() {
        for (final VampireSustenanceRules.Status status : VampireSustenanceRules.Status.values()) {
            assertEquals(0xFF, VampireBloodHudModel.statusColor(status) >>> 24);
            assertFalse(VampireBloodHudModel.statusKey(status).isBlank());
        }
    }
}
