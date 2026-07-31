package com.kadamitas.warlockery.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class LegacySiteIndexTest {
    @Test
    void indexesCircleCentersWithoutScanningWorldVolumes() {
        final LegacySiteIndex index = new LegacySiteIndex();
        index.registerCircle(new BlockPos(16, 70, 16));
        index.registerCircle(new BlockPos(256, 70, 256));

        assertEquals(
            List.of(new BlockPos(16, 70, 16)),
            index.nearbyCircles(new BlockPos(0, 64, 0), 64.0)
        );
    }

    @Test
    void indexesCampsAndVillageDiscoveryRegions() {
        final LegacySiteIndex index = new LegacySiteIndex();
        final long region = LegacyStructureRules.regionKey(180, -20);

        assertFalse(index.containsCamp(region));
        assertFalse(index.scannedVillageRegion(region));
        index.registerCamp(region);
        index.markVillageRegionScanned(region);

        assertTrue(index.containsCamp(region));
        assertTrue(index.scannedVillageRegion(region));
    }

    @Test
    void returnsNearestIndexedVillageBell() {
        final LegacySiteIndex index = new LegacySiteIndex();
        index.registerBell(new BlockPos(20, 64, 0));
        index.registerBell(new BlockPos(8, 64, 0));

        assertEquals(new BlockPos(8, 64, 0), index.nearestBell(new BlockPos(0, 64, 0), 32.0).orElseThrow());
    }
}
