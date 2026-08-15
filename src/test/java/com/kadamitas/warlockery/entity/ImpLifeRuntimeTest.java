package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ImpLifeRuntime.FollowBand;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class ImpLifeRuntimeTest {
    private static final UUID IMP_ID = new UUID(77L, 31L);

    @Test
    void countersStartAtZeroAndExposeEveryBudgetChannel() {
        final ImpLifeRuntime.Counters counters = new ImpLifeRuntime.Counters();
        assertEquals(0L, counters.blockReads());
        assertEquals(0L, counters.lineOfSightChecks());
        assertEquals(0L, counters.observationScans());
        assertEquals(0L, counters.curiosityScans());
        assertEquals(0L, counters.navigationRequests());
        assertEquals(0L, counters.laneSearches());
        assertEquals(0L, counters.scoutLegsCompleted());
        assertEquals(0L, counters.reportsDelivered());
        assertEquals(0L, counters.ordersAccepted());
        assertEquals(0L, counters.ordersCleared());
        assertEquals(0L, counters.hazardInterruptions());
        assertEquals(0L, counters.windupCancellations());
        assertEquals(0L, counters.shotsFired());
        assertEquals(0L, counters.meleeAttempts());
        assertEquals(0L, counters.releases());
    }

    @Test
    void followBandsSplitAtEightAndTwentyFourBlocks() {
        assertEquals(FollowBand.ORBIT, ImpLifeRuntime.followBand(7.9));
        assertEquals(FollowBand.PATH, ImpLifeRuntime.followBand(8.0));
        assertEquals(FollowBand.PATH, ImpLifeRuntime.followBand(24.0));
        assertEquals(FollowBand.RECOVER, ImpLifeRuntime.followBand(24.1));
    }

    @Test
    void watchEnvelopeCoversThreeToSixBlocks() {
        assertFalse(ImpLifeRuntime.watchWithinEnvelope(2.9));
        assertTrue(ImpLifeRuntime.watchWithinEnvelope(3.0));
        assertTrue(ImpLifeRuntime.watchWithinEnvelope(6.0));
        assertFalse(ImpLifeRuntime.watchWithinEnvelope(6.1));
    }

    @Test
    void curiositySamplingIsDeterministicBoundedAndInsideTheApprovedRadii() {
        final List<BlockPos> offsets = ImpLifeRuntime.curiositySampleOffsets(IMP_ID);
        assertEquals(offsets, ImpLifeRuntime.curiositySampleOffsets(IMP_ID),
            "sampling is deterministic per identity");
        assertTrue(offsets.size() <= ImpLifeRules.CURIOSITY_READ_BUDGET,
            "the deterministic sample never exceeds the ninety-six read budget");
        assertTrue(offsets.size() >= 32, "the sample still covers a useful volume");
        for (final BlockPos offset : offsets) {
            assertTrue(Math.abs(offset.getX()) <= ImpLifeRules.CURIOSITY_HORIZONTAL_RADIUS);
            assertTrue(Math.abs(offset.getZ()) <= ImpLifeRules.CURIOSITY_HORIZONTAL_RADIUS);
            assertTrue(Math.abs(offset.getY()) <= ImpLifeRules.CURIOSITY_VERTICAL_RADIUS);
        }
        assertFalse(offsets.equals(ImpLifeRuntime.curiositySampleOffsets(new UUID(5L, 999L)))
                && ImpLifeRules.stableOffset(IMP_ID, offsets.size())
                    == ImpLifeRules.stableOffset(new UUID(5L, 999L), offsets.size()),
            "different identities may rotate the deterministic sample");
    }

    @Test
    void laneCandidatesAreSixDeterministicOffsets() {
        final List<BlockPos> lanes = ImpLifeRuntime.laneOffsets(IMP_ID);
        assertEquals(ImpLifeRules.LANE_CANDIDATES, lanes.size());
        assertEquals(lanes, ImpLifeRuntime.laneOffsets(IMP_ID), "lane candidates are deterministic");
        for (final BlockPos lane : lanes) {
            final double horizontal = Math.sqrt(lane.getX() * (double) lane.getX()
                + lane.getZ() * (double) lane.getZ());
            assertTrue(horizontal <= ImpLifeRules.TOO_FAR_RANGE,
                "every lane stays inside the sixteen-block combat envelope");
            assertTrue(lane.getY() >= 0 && lane.getY() <= 4,
                "lane altitude offsets stay small and positive");
        }
    }

    @Test
    void scoutLegsAdvanceMonotonicallyToTheReturnLeg() {
        assertEquals(1, ImpLifeRuntime.nextScoutLeg(0));
        assertEquals(2, ImpLifeRuntime.nextScoutLeg(1));
        assertEquals(3, ImpLifeRuntime.nextScoutLeg(2));
        assertEquals(3, ImpLifeRuntime.nextScoutLeg(3), "the leg counter saturates at the return leg");
    }

    @Test
    void followRecoveryCandidatesStayWithinFourBlocksOfTheOwner() {
        final List<BlockPos> candidates = ImpLifeRuntime.followRecoveryOffsets(IMP_ID);
        assertEquals(ImpLifeRules.WAYPOINT_CANDIDATES, candidates.size());
        assertEquals(candidates, ImpLifeRuntime.followRecoveryOffsets(IMP_ID));
        for (final BlockPos candidate : candidates) {
            assertTrue(Math.abs(candidate.getX()) <= ImpLifeRules.FOLLOW_RECOVERY_RADIUS);
            assertTrue(Math.abs(candidate.getY()) <= 2);
            assertTrue(Math.abs(candidate.getZ()) <= ImpLifeRules.FOLLOW_RECOVERY_RADIUS);
        }
    }
}
