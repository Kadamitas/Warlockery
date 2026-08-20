package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.WerewolfHunterRules.Confidence;
import com.kadamitas.warlockery.entity.WerewolfHunterRules.Evidence;
import com.kadamitas.warlockery.entity.WerewolfHunterRules.EvidenceType;
import com.kadamitas.warlockery.entity.WerewolfHunterRules.Intent;
import com.kadamitas.warlockery.entity.WerewolfHunterRules.LaneCandidate;
import com.kadamitas.warlockery.entity.WerewolfHunterRules.ProtectedFacts;
import com.kadamitas.warlockery.entity.WerewolfHunterRules.QuarryCandidate;
import com.kadamitas.warlockery.entity.WerewolfHunterRules.ShotFacts;
import com.kadamitas.warlockery.entity.WerewolfHunterRules.TargetFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class WerewolfHunterRulesTest {
    private static final long NOW = 10_000L;

    @Test
    void approvedConstantsStayExact() {
        assertEquals(4, WerewolfHunterRules.MAX_EVIDENCE_RECORDS);
        assertEquals(6_000L, WerewolfHunterRules.EVENT_QUARRY_TICKS);
        assertEquals(600L, WerewolfHunterRules.DIRECT_ATTACK_TICKS);
        assertEquals(400L, WerewolfHunterRules.WITNESSED_ATTACK_TICKS);
        assertEquals(400L, WerewolfHunterRules.LAST_KNOWN_TICKS);
        assertEquals(24, WerewolfHunterRules.WITNESS_RADIUS);
        assertEquals(40, WerewolfHunterRules.WITNESS_FRESHNESS_TICKS);
        assertEquals(24, WerewolfHunterRules.DEFAULT_SILVER_BOLTS);
        assertEquals(32, WerewolfHunterRules.MAX_SILVER_BOLTS);
        assertEquals(6, WerewolfHunterRules.LOW_RESERVE_BOLTS);
        assertEquals(20, WerewolfHunterRules.DECISION_INTERVAL_TICKS);
        assertEquals(40, WerewolfHunterRules.OBSERVATION_INTERVAL_TICKS);
        assertEquals(100, WerewolfHunterRules.SCHEDULE_INTERVAL_TICKS);
        assertEquals(200, WerewolfHunterRules.FEEDBACK_INTERVAL_TICKS);
        assertEquals(20, WerewolfHunterRules.NAVIGATION_INTERVAL_TICKS);
        assertEquals(20, WerewolfHunterRules.WARN_MINIMUM_TICKS);
        assertEquals(400, WerewolfHunterRules.ENGAGE_TICKS);
        assertEquals(100, WerewolfHunterRules.LOST_SIGHT_TICKS);
        assertEquals(400, WerewolfHunterRules.SEARCH_TICKS);
        assertEquals(12, WerewolfHunterRules.SEARCH_RADIUS);
        assertEquals(4, WerewolfHunterRules.MAX_SEARCH_WAYPOINTS);
        assertEquals(120, WerewolfHunterRules.RETREAT_TICKS);
        assertEquals(0.30F, WerewolfHunterRules.RETREAT_HEALTH_FRACTION);
        assertEquals(10, WerewolfHunterRules.PREFERRED_RANGE_MIN);
        assertEquals(14, WerewolfHunterRules.PREFERRED_RANGE_MAX);
        assertEquals(8, WerewolfHunterRules.LANE_HORIZONTAL_RADIUS);
        assertEquals(4, WerewolfHunterRules.LANE_VERTICAL_RADIUS);
        assertEquals(128, WerewolfHunterRules.MAX_LANE_BLOCK_READS);
        assertEquals(256, WerewolfHunterRules.MAX_SPAWN_BLOCK_READS);
        assertEquals(24, WerewolfHunterRules.OBSERVATION_RADIUS);
        assertEquals(16, WerewolfHunterRules.MAX_RETAINED_CANDIDATES);
        assertEquals(4, WerewolfHunterRules.MAX_LINE_OF_SIGHT_CHECKS);
        assertEquals(3, WerewolfHunterRules.MAX_ROUTE_FAILURES);
        assertEquals(100, WerewolfHunterRules.ROUTE_BACKOFF_TICKS);
        assertEquals(8, WerewolfHunterRules.MAX_HUNT_RECORDS);
        assertEquals(128, WerewolfHunterRules.HUNT_DEDUP_RADIUS);
        assertEquals(6_000L, WerewolfHunterRules.HUNT_RECORD_TICKS);
        assertEquals(200, WerewolfHunterRules.HUNT_CLEANUP_INTERVAL_TICKS);
        assertEquals(2, WerewolfHunterRules.HUNT_PARTICIPANT_CONSTRUCTIONS);
        assertEquals(20_000L, WerewolfHunterRules.MAX_FUTURE_HORIZON_TICKS);
    }

    @Test
    void evidenceCreationUsesTypedLifetimesAndConfidence() {
        for (final EvidenceType type : EvidenceType.values()) {
            final Evidence evidence = WerewolfHunterRules.createEvidence(
                type, Optional.of(new UUID(0L, 1L)), Optional.of(new UUID(0L, 2L)), NOW
            );
            assertEquals(NOW + WerewolfHunterRules.evidenceLifetimeTicks(type), evidence.expiresAt());
            assertEquals(type == EvidenceType.LAST_KNOWN ? Confidence.PROBABLE : Confidence.CONFIRMED,
                evidence.confidence());
            assertTrue(evidence.valid(NOW));
            assertFalse(evidence.valid(evidence.expiresAt()));
        }
    }

    @Test
    void evidenceCapEvictsExpiredThenWeakestThenOldestThenGreatestIdentity() {
        List<Evidence> ledger = new ArrayList<>();
        final Evidence expired = new Evidence(EvidenceType.WITNESSED_ATTACK, Confidence.CONFIRMED,
            Optional.of(new UUID(0L, 1L)), Optional.empty(), Optional.empty(), Optional.empty(),
            NOW - 500L, NOW - 1L, false);
        final Evidence weak = new Evidence(EvidenceType.LAST_KNOWN, Confidence.CLUE,
            Optional.empty(), Optional.of(new UUID(0L, 2L)), Optional.empty(), Optional.empty(),
            NOW - 100L, NOW + 100L, false);
        final Evidence old = new Evidence(EvidenceType.DIRECT_ATTACK, Confidence.CONFIRMED,
            Optional.of(new UUID(0L, 3L)), Optional.empty(), Optional.empty(), Optional.empty(),
            NOW - 300L, NOW + 300L, false);
        final Evidence young = new Evidence(EvidenceType.DIRECT_ATTACK, Confidence.CONFIRMED,
            Optional.of(new UUID(0L, 4L)), Optional.empty(), Optional.empty(), Optional.empty(),
            NOW - 10L, NOW + 300L, false);
        ledger.addAll(List.of(expired, weak, old, young));

        final Evidence incoming = WerewolfHunterRules.createEvidence(
            EvidenceType.WITNESSED_ATTACK, Optional.of(new UUID(0L, 9L)), Optional.empty(), NOW
        );
        List<Evidence> updated = WerewolfHunterRules.recordEvidence(ledger, incoming, NOW);
        assertEquals(4, updated.size());
        assertFalse(updated.contains(expired), "the expired record is evicted first");
        assertTrue(updated.contains(incoming));

        updated = WerewolfHunterRules.recordEvidence(updated, WerewolfHunterRules.createEvidence(
            EvidenceType.WITNESSED_ATTACK, Optional.of(new UUID(0L, 10L)), Optional.empty(), NOW
        ), NOW);
        assertFalse(updated.contains(weak), "the lowest confidence record is evicted next");

        final Evidence incomingClue = WerewolfHunterRules.createEvidence(
            EvidenceType.LAST_KNOWN, Optional.empty(), Optional.of(new UUID(0L, 11L)), NOW
        );
        final List<Evidence> unchanged = WerewolfHunterRules.recordEvidence(updated, incomingClue, NOW);
        assertFalse(unchanged.contains(incomingClue),
            "a weaker incoming clue cannot evict a valid stronger record");
        assertEquals(updated, unchanged);
    }

    @Test
    void evidencePositionRequiresADimensionAndCarriesTheLocus() {
        final Evidence located = WerewolfHunterRules.createEvidence(
            EvidenceType.LAST_KNOWN, Optional.empty(), Optional.of(new UUID(0L, 3L)),
            Optional.of(123456789L), Optional.of("minecraft:overworld"), NOW
        );
        assertEquals(Optional.of(123456789L), located.packedPosition());
        assertEquals(Optional.of("minecraft:overworld"), located.dimension());
        final Evidence orphaned = new Evidence(EvidenceType.LAST_KNOWN, Confidence.PROBABLE,
            Optional.empty(), Optional.of(new UUID(0L, 4L)),
            Optional.of(9L), Optional.empty(), NOW, NOW + 400L, false);
        assertTrue(orphaned.packedPosition().isEmpty(),
            "a position without its dimension is discarded as unsafe coupling");
    }

    @Test
    void evidenceDeduplicatesByTypeAndStableKey() {
        final UUID attacker = new UUID(0L, 7L);
        List<Evidence> ledger = WerewolfHunterRules.recordEvidence(List.of(),
            WerewolfHunterRules.createEvidence(EvidenceType.DIRECT_ATTACK, Optional.of(attacker), Optional.empty(), NOW),
            NOW);
        ledger = WerewolfHunterRules.recordEvidence(ledger,
            WerewolfHunterRules.createEvidence(EvidenceType.DIRECT_ATTACK, Optional.of(attacker), Optional.empty(), NOW + 5L),
            NOW + 5L);
        assertEquals(1, ledger.size(), "one attacker owns one direct-attack record");
        assertEquals(NOW + 5L, ledger.get(0).observedAt());
    }

    @Test
    void quarryPriorityFollowsTheApprovedOrderWithDeterministicTies() {
        final Optional<UUID> event = Optional.of(new UUID(0L, 1L));
        final Optional<UUID> direct = Optional.of(new UUID(0L, 2L));
        final Optional<UUID> current = Optional.of(new UUID(0L, 3L));
        final List<QuarryCandidate> witnessed = List.of(
            new QuarryCandidate(new UUID(0L, 9L), 4.0D),
            new QuarryCandidate(new UUID(0L, 5L), 4.0D),
            new QuarryCandidate(new UUID(0L, 8L), 2.0D)
        );
        assertEquals(event, WerewolfHunterRules.selectQuarry(event, direct, current, witnessed));
        assertEquals(direct, WerewolfHunterRules.selectQuarry(Optional.empty(), direct, current, witnessed));
        assertEquals(current, WerewolfHunterRules.selectQuarry(Optional.empty(), Optional.empty(), current, witnessed));
        assertEquals(Optional.of(new UUID(0L, 8L)),
            WerewolfHunterRules.selectQuarry(Optional.empty(), Optional.empty(), Optional.empty(), witnessed),
            "witnessed attackers resolve nearest first");
        assertEquals(Optional.of(new UUID(0L, 5L)),
            WerewolfHunterRules.selectQuarry(Optional.empty(), Optional.empty(), Optional.empty(),
                witnessed.subList(0, 2)),
            "equal distances resolve by unsigned UUID order");
        assertTrue(WerewolfHunterRules.selectQuarry(
            Optional.empty(), Optional.empty(), Optional.empty(), List.of()).isEmpty());
    }

    @Test
    void identityAloneNeverQualifiesAndProtectionAlwaysExcludes() {
        assertFalse(WerewolfHunterRules.identityAloneQualifies());
        assertFalse(WerewolfHunterRules.eligibleQuarry(new TargetFacts(false, false, false, false)),
            "a Werewolf, Lycan, vampire, villager, golem, or player is never attacked by identity");
        assertTrue(WerewolfHunterRules.eligibleQuarry(new TargetFacts(true, false, false, false)));
        assertTrue(WerewolfHunterRules.eligibleQuarry(new TargetFacts(false, true, false, false)));
        assertTrue(WerewolfHunterRules.eligibleQuarry(new TargetFacts(false, false, true, false)));
        assertFalse(WerewolfHunterRules.eligibleQuarry(new TargetFacts(true, true, true, true)),
            "engine or team protection vetoes every warrant");
    }

    @Test
    void protectedCorridorMatrixCoversEveryApprovedCategory() {
        assertFalse(WerewolfHunterRules.protectedCorridorActor(
            new ProtectedFacts(false, false, false, false, false)));
        assertTrue(WerewolfHunterRules.protectedCorridorActor(
            new ProtectedFacts(true, false, false, false, false)));
        assertTrue(WerewolfHunterRules.protectedCorridorActor(
            new ProtectedFacts(false, true, false, false, false)));
        assertTrue(WerewolfHunterRules.protectedCorridorActor(
            new ProtectedFacts(false, false, true, false, false)));
        assertTrue(WerewolfHunterRules.protectedCorridorActor(
            new ProtectedFacts(false, false, false, true, false)));
        assertTrue(WerewolfHunterRules.protectedCorridorActor(
            new ProtectedFacts(false, false, false, false, true)));
        assertTrue(WerewolfHunterRules.crossfireCancelsShot(1));
        assertFalse(WerewolfHunterRules.crossfireCancelsShot(0));
        assertFalse(WerewolfHunterRules.armorGrantsCommandAuthority());
    }

    @Test
    void ammunitionMathIsFiniteCappedAndRefusable() {
        assertFalse(WerewolfHunterRules.mayCommitRanged(0));
        assertTrue(WerewolfHunterRules.mayCommitRanged(1));
        assertTrue(WerewolfHunterRules.lowReserve(6));
        assertFalse(WerewolfHunterRules.lowReserve(7));
        assertEquals(8, WerewolfHunterRules.acceptedResupply(20, 24));
        assertEquals(0, WerewolfHunterRules.acceptedResupply(20, 32));
        assertEquals(0, WerewolfHunterRules.acceptedResupply(20, 40));
        assertEquals(0, WerewolfHunterRules.acceptedResupply(0, 0));
        assertEquals(32, WerewolfHunterRules.acceptedResupply(64, 0));
        assertEquals(32, WerewolfHunterRules.acceptedResupply(64, -5),
            "malformed negative reserves fill only to the cap");
        assertTrue(WerewolfHunterRules.resupplyRefused(true, false, false, false));
        assertTrue(WerewolfHunterRules.resupplyRefused(false, true, false, false));
        assertTrue(WerewolfHunterRules.resupplyRefused(false, false, true, false));
        assertTrue(WerewolfHunterRules.resupplyRefused(false, false, false, true));
        assertFalse(WerewolfHunterRules.resupplyRefused(false, false, false, false));
    }

    @Test
    void shotValidationRequiresEveryFact() {
        assertTrue(WerewolfHunterRules.mayFire(new ShotFacts(
            true, true, true, true, true, true, true, true, true)));
        for (int missing = 0; missing < 9; missing++) {
            final boolean[] facts = new boolean[9];
            java.util.Arrays.fill(facts, true);
            facts[missing] = false;
            assertFalse(WerewolfHunterRules.mayFire(new ShotFacts(
                facts[0], facts[1], facts[2], facts[3], facts[4],
                facts[5], facts[6], facts[7], facts[8]
            )), "missing fact " + missing + " must veto the shot");
        }
        assertTrue(WerewolfHunterRules.withinPreferredRange(10.0D));
        assertTrue(WerewolfHunterRules.withinPreferredRange(14.0D));
        assertFalse(WerewolfHunterRules.withinPreferredRange(9.9D));
        assertFalse(WerewolfHunterRules.withinPreferredRange(14.1D));
    }

    @Test
    void laneRankingPrefersClearLineBandCoverCostProtectionThenStableOrder() {
        final LaneCandidate best = new LaneCandidate(true, true, 2, 0, 100.0D, 1L);
        final LaneCandidate blocked = new LaneCandidate(false, true, 9, 0, 900.0D, 0L);
        final LaneCandidate outOfBand = new LaneCandidate(true, false, 2, 0, 100.0D, 0L);
        final LaneCandidate lessCover = new LaneCandidate(true, true, 1, 0, 100.0D, 0L);
        final LaneCandidate costlier = new LaneCandidate(true, true, 2, 1, 100.0D, 0L);
        final LaneCandidate nearerProtected = new LaneCandidate(true, true, 2, 0, 25.0D, 0L);
        final LaneCandidate laterOrder = new LaneCandidate(true, true, 2, 0, 100.0D, 2L);
        for (final LaneCandidate worse : List.of(blocked, outOfBand, lessCover, costlier, nearerProtected, laterOrder)) {
            assertTrue(WerewolfHunterRules.laneOrder().compare(best, worse) < 0,
                "the best lane must outrank " + worse);
        }
    }

    @Test
    void scheduleIntentsFollowTheApprovedTable() {
        assertEquals(Intent.WARN,
            WerewolfHunterRules.scheduleIntent(6_000L, true, false, false, false, true));
        assertEquals(Intent.RETREAT,
            WerewolfHunterRules.scheduleIntent(6_000L, false, false, false, true, true));
        assertEquals(Intent.RESUPPLY,
            WerewolfHunterRules.scheduleIntent(6_000L, false, false, true, false, true));
        assertEquals(Intent.INVESTIGATE,
            WerewolfHunterRules.scheduleIntent(15_000L, false, true, false, false, true));
        assertEquals(Intent.PATROL,
            WerewolfHunterRules.scheduleIntent(6_000L, false, false, false, false, true));
        assertEquals(Intent.IDLE,
            WerewolfHunterRules.scheduleIntent(6_000L, false, false, false, false, false),
            "an invalid anchor falls back to idle looking");
    }

    @Test
    void retreatTriggersMatchTheApprovedDoctrine() {
        assertTrue(WerewolfHunterRules.retreatRequired(0.30F, 5, 0, false, false, false));
        assertTrue(WerewolfHunterRules.retreatRequired(1.0F, 0, 0, false, false, false));
        assertTrue(WerewolfHunterRules.retreatRequired(1.0F, 5, 3, false, false, false));
        assertTrue(WerewolfHunterRules.retreatRequired(1.0F, 5, 0, true, false, false));
        assertTrue(WerewolfHunterRules.retreatRequired(1.0F, 5, 0, false, true, false));
        assertTrue(WerewolfHunterRules.retreatRequired(1.0F, 5, 0, false, false, true));
        assertFalse(WerewolfHunterRules.retreatRequired(1.0F, 5, 0, false, false, false));
    }

    @Test
    void routeFailuresBackOffAndCadencesTreatZeroAsDue() {
        assertEquals(1, WerewolfHunterRules.routeFailures(0));
        assertEquals(3, WerewolfHunterRules.routeFailures(3));
        assertEquals(0L, WerewolfHunterRules.routeBackoffUntil(2, NOW));
        assertEquals(NOW + 100L, WerewolfHunterRules.routeBackoffUntil(3, NOW));
        assertTrue(WerewolfHunterRules.decisionDue(0L, NOW), "a never-run decision reads as due");
        assertTrue(WerewolfHunterRules.navigationDue(0L, NOW), "a never-run navigation reads as due");
        assertTrue(WerewolfHunterRules.warningDue(0L, NOW), "a never-warned hunter reads as due");
        assertFalse(WerewolfHunterRules.decisionDue(NOW + 1L, NOW));
        assertTrue(WerewolfHunterRules.warnWaitElapsed(NOW - 20L, NOW, false));
        assertFalse(WerewolfHunterRules.warnWaitElapsed(NOW - 19L, NOW, false));
        assertFalse(WerewolfHunterRules.warnWaitElapsed(0L, NOW, false));
        assertTrue(WerewolfHunterRules.warnWaitElapsed(0L, NOW, true),
            "direct close attack skips the warning wait");
    }

    @Test
    void deadlineClampingBoundsFutureSentinels() {
        assertEquals(0L, WerewolfHunterRules.clampDeadline(0L, NOW, 400L));
        assertEquals(0L, WerewolfHunterRules.clampDeadline(-5L, NOW, 400L));
        assertEquals(NOW + 400L, WerewolfHunterRules.clampDeadline(Long.MAX_VALUE, NOW, 400L));
        assertEquals(NOW + 100L, WerewolfHunterRules.clampDeadline(NOW + 100L, NOW, 400L));
        assertEquals(NOW + WerewolfHunterRules.MAX_FUTURE_HORIZON_TICKS,
            WerewolfHunterRules.clampDeadline(Long.MAX_VALUE, NOW, Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, WerewolfHunterRules.saturatingAdd(Long.MAX_VALUE - 1L, 100L));
    }

    @Test
    void huntManagerDecisionsEnforceCapsDedupAndCleanupCadence() {
        assertTrue(WerewolfHunterRules.mayReserveHunt(7, false));
        assertFalse(WerewolfHunterRules.mayReserveHunt(8, false));
        assertFalse(WerewolfHunterRules.mayReserveHunt(0, true));
        assertTrue(WerewolfHunterRules.withinDedupRadius(128.0D * 128.0D));
        assertFalse(WerewolfHunterRules.withinDedupRadius(128.0D * 128.0D + 1.0D));
        assertTrue(WerewolfHunterRules.huntRecordExpired(NOW, NOW));
        assertFalse(WerewolfHunterRules.huntRecordExpired(NOW + 1L, NOW));
        assertTrue(WerewolfHunterRules.cleanupDue(0L, NOW));
        assertTrue(WerewolfHunterRules.cleanupDue(NOW - 200L, NOW));
        assertFalse(WerewolfHunterRules.cleanupDue(NOW - 199L, NOW));
        assertTrue(WerewolfHunterRules.stageAllowsActivation(WerewolfHunterRules.HuntStage.RESERVED));
        assertTrue(WerewolfHunterRules.stageAllowsActivation(WerewolfHunterRules.HuntStage.PREPARING));
        assertFalse(WerewolfHunterRules.stageAllowsActivation(WerewolfHunterRules.HuntStage.ACTIVE));
        assertFalse(WerewolfHunterRules.stageAllowsActivation(WerewolfHunterRules.HuntStage.CLEANUP));
    }

    @Test
    void stableStaggeringIsDeterministicAndBounded() {
        for (int seed = 0; seed < 8; seed++) {
            final UUID id = new UUID(seed, seed * 31L + 7L);
            final int offset = WerewolfHunterRules.stableOffset(id, 20);
            assertTrue(offset >= 0 && offset < 20);
            assertEquals(offset, WerewolfHunterRules.stableOffset(id, 20));
        }
        final UUID low = new UUID(0L, 1L);
        final UUID high = new UUID(-1L, 0L);
        assertTrue(WerewolfHunterRules.unsignedUuidOrder().compare(low, high) < 0,
            "identity comparison is unsigned and stable");
    }
}
