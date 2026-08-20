package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The Hollow Decoy's station geometry, which is the half of its behaviour a sealed three-by-three
 * GameTest cell cannot reach: every station is exactly two blocks out from an anchor that must
 * itself be inside the cell, so no live arrival can be observed there. What can be proved is the
 * geometry itself, and specifically the anti-stacking property that is the reason the slot exists.
 */
final class MimicryRuntimeGeometryTest {

    private static final double EPSILON = 1.0E-9D;

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void fourDecoysWithConsecutiveIdsNeverClaimTheSameStationBesideOneAnchor() {
        final List<Vec3> claimed = new ArrayList<>();
        for (int id = 100; id < 100 + MimicryRules.DECOY_STATION_SLOTS; id++) {
            claimed.add(MimicryRuntime.stationOffset(id));
        }
        for (int left = 0; left < claimed.size(); left++) {
            for (int right = left + 1; right < claimed.size(); right++) {
                assertTrue(claimed.get(left).distanceToSqr(claimed.get(right)) > EPSILON,
                    "slots " + left + " and " + right + " resolve to the same station, so two decoys"
                        + " beside one anchor would stand in the same block");
            }
        }
        assertEquals(MimicryRules.DECOY_STATION_SLOTS, claimed.size());
    }

    @Test
    void everyStationSitsExactlyTheDeclaredOffsetOutOnACardinalAndNeverAbove() {
        for (int id = -6; id <= 9; id++) {
            final Vec3 offset = MimicryRuntime.stationOffset(id);
            assertEquals(0.0D, offset.y, EPSILON, "a station is never above or below its anchor");
            assertEquals(
                MimicryRules.DECOY_STATION_OFFSET * MimicryRules.DECOY_STATION_OFFSET,
                offset.x * offset.x + offset.z * offset.z, EPSILON,
                "id " + id + " must stand exactly the declared offset out from the anchor"
            );
            assertTrue(Math.abs(offset.x) < EPSILON || Math.abs(offset.z) < EPSILON,
                "id " + id + " must stand on a cardinal, which is why no station of an anchor inside"
                    + " a three-by-three cell can ever land inside that cell");
        }
    }

    @Test
    void theSlotIsStablePerIdentityAndRepeatsOnlyWithTheDeclaredPeriod() {
        for (int id = -6; id <= 9; id++) {
            assertEquals(MimicryRuntime.stationOffset(id), MimicryRuntime.stationOffset(id),
                "the same decoy must not drift between stations");
            assertEquals(
                MimicryRuntime.stationOffset(id),
                MimicryRuntime.stationOffset(id + MimicryRules.DECOY_STATION_SLOTS),
                "the slot must repeat on exactly the declared period, negative ids included"
            );
        }
    }
}


