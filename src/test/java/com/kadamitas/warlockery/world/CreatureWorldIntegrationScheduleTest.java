package com.kadamitas.warlockery.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.LycanPackRules;
import com.kadamitas.warlockery.world.CreatureWorldIntegration.ArmingCandidate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class CreatureWorldIntegrationScheduleTest {
    @Test
    void worldEventsRunOnlyOnTheirConfiguredIntervals() {
        assertTrue(CreatureWorldIntegration.scheduled(0L, 200));
        assertTrue(CreatureWorldIntegration.scheduled(2_400L, 2_400));
        assertTrue(CreatureWorldIntegration.scheduled(4_800L, 2_400));
        assertFalse(CreatureWorldIntegration.scheduled(199L, 200));
        assertFalse(CreatureWorldIntegration.scheduled(2_401L, 2_400));
    }

    @Test
    void armingAccumulatorsAbortAtThirtyTwoRawVisitsAndRetainSixteen() {
        assertEquals(32, CreatureWorldIntegration.MAX_RAW_ARMING_VISITS);
        assertEquals(16, CreatureWorldIntegration.MAX_RETAINED_ARMING);
        assertEquals(LycanPackRules.MAX_RAW_ARMING_VISITS, CreatureWorldIntegration.MAX_RAW_ARMING_VISITS);
        assertEquals(LycanPackRules.MAX_RETAINED_ARMING, CreatureWorldIntegration.MAX_RETAINED_ARMING);
        final List<ArmingCandidate> visited = new ArrayList<>();
        for (int index = 0; index < 32; index++) {
            visited.add(new ArmingCandidate(new UUID(0L, index), 32.0D - index));
        }
        final List<ArmingCandidate> retained = CreatureWorldIntegration.retainNearestToAnchor(visited);
        assertEquals(16, retained.size(), "at most sixteen candidates are retained");
        for (int index = 1; index < retained.size(); index++) {
            assertTrue(retained.get(index - 1).distanceSqr() <= retained.get(index).distanceSqr(),
                "retention orders by squared distance to the selected player");
        }
        assertEquals(new UUID(0L, 31L), retained.get(0).id(), "the nearest candidate is retained first");
    }

    @Test
    void retentionAndTargetTiesResolveByUnsignedUuidOrder() {
        final List<ArmingCandidate> tied = List.of(
            new ArmingCandidate(new UUID(0L, 9L), 4.0D),
            new ArmingCandidate(new UUID(0L, 1L), 4.0D),
            new ArmingCandidate(new UUID(0L, 5L), 4.0D)
        );
        final List<ArmingCandidate> retained = CreatureWorldIntegration.retainNearestToAnchor(tied);
        assertEquals(new UUID(0L, 1L), retained.get(0).id());
        assertEquals(new UUID(0L, 5L), retained.get(1).id());
        assertEquals(new UUID(0L, 9L), retained.get(2).id());
        assertEquals(new UUID(0L, 1L),
            CreatureWorldIntegration.nearestRetainedLycan(tied).orElseThrow(),
            "each Pillager receives the nearest retained lycan with UUID ties");
        assertTrue(CreatureWorldIntegration.nearestRetainedLycan(List.of()).isEmpty(),
            "no retained lycan means no Pillager target selection");
    }
}
