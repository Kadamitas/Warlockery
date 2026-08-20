package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.EldritchWatcherRules.CandidateFacts;
import com.kadamitas.warlockery.entity.EldritchWatcherRules.EvidenceType;
import com.kadamitas.warlockery.entity.EldritchWatcherRules.Mode;
import com.kadamitas.warlockery.entity.EldritchWatcherRules.RevelationFacts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class EldritchWatcherRulesTest {
    private static final UUID WATCHER_ID = new UUID(21L, 84L);

    @Test
    void exactApprovedConstantsRemainFixed() {
        assertEquals(20, EldritchWatcherRules.PERCEPTION_INTERVAL_TICKS);
        assertEquals(16, EldritchWatcherRules.MAX_ENTITIES_VISITED);
        assertEquals(8, EldritchWatcherRules.MAX_RETAINED_CANDIDATES);
        assertEquals(8, EldritchWatcherRules.MAX_LINE_OF_SIGHT_CLIPS);
        assertEquals(16, EldritchWatcherRules.PERCEPTION_RADIUS);
        assertEquals(80, EldritchWatcherRules.FOCUS_SCAN_INTERVAL_TICKS);
        assertEquals(128, EldritchWatcherRules.MAX_FOCUS_BLOCK_READS);
        assertEquals(240, EldritchWatcherRules.FOCUS_RETENTION_TICKS);
        assertEquals(20, EldritchWatcherRules.HAZARD_SCAN_INTERVAL_TICKS);
        assertEquals(27, EldritchWatcherRules.MAX_HAZARD_BLOCK_READS);
        assertEquals(24, EldritchWatcherRules.MAX_DESTINATION_CANDIDATES);
        assertEquals(256, EldritchWatcherRules.MAX_SAFETY_BLOCK_READS);
        assertEquals(20, EldritchWatcherRules.MOVEMENT_INTERVAL_TICKS);
        assertEquals(8, EldritchWatcherRules.DESTINATION_RADIUS);
        assertEquals(60, EldritchWatcherRules.DESTINATION_EXPIRY_TICKS);
        assertEquals(3, EldritchWatcherRules.MAX_ROUTE_FAILURES);
        assertEquals(100, EldritchWatcherRules.ROUTE_BACKOFF_TICKS);
        assertEquals(12, EldritchWatcherRules.ANCHOR_HOLD_RADIUS);
        assertEquals(20, EldritchWatcherRules.ANCHOR_CHASE_RADIUS);
        assertEquals(5, EldritchWatcherRules.THRESHOLD_RADIUS);
        assertEquals(0.94D, EldritchWatcherRules.RECIPROCAL_GAZE_DOT);
        assertEquals(2, EldritchWatcherRules.ESCALATION_SAMPLES);
        assertEquals(100, EldritchWatcherRules.SEEN_EVIDENCE_TICKS);
        assertEquals(100, EldritchWatcherRules.LAST_SEEN_TICKS);
        assertEquals(80, EldritchWatcherRules.REPORTED_HARM_TICKS);
        assertEquals(40, EldritchWatcherRules.WARNING_DEDUPE_TICKS);
        assertEquals(12, EldritchWatcherRules.WARNING_RADIUS);
        assertEquals(8, EldritchWatcherRules.MAX_WARNING_VISITS);
        assertEquals(3, EldritchWatcherRules.MAX_WARNING_RECIPIENTS);
        assertEquals(14, EldritchWatcherRules.ATTACK_RANGE);
        assertEquals(20, EldritchWatcherRules.REVELATION_WINDUP_TICKS);
        assertEquals(50, EldritchWatcherRules.REVELATION_RECOVERY_TICKS);
        assertEquals(3.0F, EldritchWatcherRules.REVELATION_DAMAGE);
        assertEquals(100, EldritchWatcherRules.GLOWING_TICKS);
        assertEquals(40, EldritchWatcherRules.DARKNESS_TICKS);
        assertEquals(0.25D, EldritchWatcherRules.WITHDRAW_HEALTH_FRACTION);
        assertEquals(100, EldritchWatcherRules.WITHDRAW_TICKS);
        assertEquals(40, EldritchWatcherRules.LURE_TICKS);
        assertEquals(16, EldritchWatcherRules.LURE_RADIUS);
        assertEquals(20_000L, EldritchWatcherRules.MAX_DEADLINE_HORIZON_TICKS);
    }

    @Test
    void reciprocalGazeRequiresBothThresholdAndLineOfSight() {
        assertTrue(EldritchWatcherRules.reciprocalGaze(0.94D, true));
        assertTrue(EldritchWatcherRules.reciprocalGaze(1.0D, true));
        assertFalse(EldritchWatcherRules.reciprocalGaze(0.9399D, true));
        assertFalse(EldritchWatcherRules.reciprocalGaze(1.0D, false),
            "phasing never bypasses the ordinary sight requirement");
    }

    @Test
    void escalationNeedsTwoSamplesOrDirectHarmAndReportsNeedIndependentSight() {
        assertTrue(EldritchWatcherRules.escalationReady(EvidenceType.DIRECT_HARM, 0, false));
        assertFalse(EldritchWatcherRules.escalationReady(EvidenceType.RECIPROCAL_GAZE, 1, true));
        assertTrue(EldritchWatcherRules.escalationReady(EvidenceType.RECIPROCAL_GAZE, 2, false));
        assertFalse(EldritchWatcherRules.escalationReady(EvidenceType.THRESHOLD_BREACH, 1, true));
        assertTrue(EldritchWatcherRules.escalationReady(EvidenceType.THRESHOLD_BREACH, 2, false));
        assertFalse(EldritchWatcherRules.escalationReady(EvidenceType.REPORTED_HARM, 2, false),
            "reported harm alone never attacks until independently visible");
        assertTrue(EldritchWatcherRules.escalationReady(EvidenceType.REPORTED_HARM, 0, true));
        assertFalse(EldritchWatcherRules.escalationReady(EvidenceType.SEEN, 9, true));
    }

    @Test
    void modePrioritiesFollowTheApprovedOrder() {
        assertEquals(Mode.EXPOSED_WITHDRAWAL,
            EldritchWatcherRules.selectMode(true, false, true, true, true, true, true));
        assertEquals(Mode.INTERCEPTING,
            EldritchWatcherRules.selectMode(false, true, true, true, true, true, true),
            "active direct interception outranks withdrawal completion");
        assertEquals(Mode.EXPOSED_WITHDRAWAL,
            EldritchWatcherRules.selectMode(false, true, false, true, true, true, true));
        assertEquals(Mode.EXTERNAL_LURE,
            EldritchWatcherRules.selectMode(false, false, false, true, true, true, true));
        assertEquals(Mode.OBSERVING,
            EldritchWatcherRules.selectMode(false, false, false, false, true, true, true));
        assertEquals(Mode.FOCUS_INSPECTION,
            EldritchWatcherRules.selectMode(false, false, false, false, false, true, true));
        assertEquals(Mode.QUIET_VIGIL,
            EldritchWatcherRules.selectMode(false, false, false, false, false, false, true));
        assertEquals(Mode.RETURNING,
            EldritchWatcherRules.selectMode(false, false, false, false, false, false, false));
    }

    @Test
    void candidateOrderingIsStableWithUuidTieBreaking() {
        final UUID low = new UUID(0L, 1L);
        final UUID high = new UUID(0L, 2L);
        final CandidateFacts direct = new CandidateFacts(true, false, false, false, false, 100.0D, high);
        final CandidateFacts gaze = new CandidateFacts(false, false, false, false, true, 1.0D, low);
        final CandidateFacts near = new CandidateFacts(false, false, false, false, false, 1.0D, high);
        final CandidateFacts nearTwin = new CandidateFacts(false, false, false, false, false, 1.0D, low);
        final List<CandidateFacts> sorted = List.of(near, gaze, direct, nearTwin).stream()
            .sorted(EldritchWatcherRules.candidateOrder())
            .toList();
        assertEquals(direct, sorted.get(0), "direct threat outranks everything");
        assertEquals(gaze, sorted.get(1), "reciprocal gaze outranks plain distance");
        assertEquals(nearTwin, sorted.get(2), "equal facts fall back to stable UUID order");
        assertEquals(near, sorted.get(3));
    }

    @Test
    void warningCompatibilityPairsSameOwnerOrBothUnbound() {
        final Optional<UUID> owner = Optional.of(new UUID(5L, 6L));
        final Optional<UUID> other = Optional.of(new UUID(5L, 7L));
        assertTrue(EldritchWatcherRules.warningCompatible(owner, owner));
        assertTrue(EldritchWatcherRules.warningCompatible(Optional.empty(), Optional.empty()));
        assertFalse(EldritchWatcherRules.warningCompatible(owner, other));
        assertFalse(EldritchWatcherRules.warningCompatible(owner, Optional.empty()),
            "a bound and unbound Watcher never silently form a faction");
        assertFalse(EldritchWatcherRules.warningCompatible(Optional.empty(), owner));
    }

    @Test
    void revelationRequiresEveryEligibilityFact() {
        final RevelationFacts eligible = new RevelationFacts(
            true, true, true, true, true, true, true, false, false, false
        );
        assertTrue(EldritchWatcherRules.mayStartRevelation(eligible));
        assertFalse(EldritchWatcherRules.mayStartRevelation(new RevelationFacts(
            false, true, true, true, true, true, true, false, false, false)));
        assertFalse(EldritchWatcherRules.mayStartRevelation(new RevelationFacts(
            true, true, true, true, true, true, false, false, false, false)),
            "no attack starts through obstruction");
        assertFalse(EldritchWatcherRules.mayStartRevelation(new RevelationFacts(
            true, true, true, true, true, true, true, true, false, false)));
        assertFalse(EldritchWatcherRules.mayStartRevelation(new RevelationFacts(
            true, true, true, true, true, true, true, false, false, true)));
    }

    @Test
    void lureLosesToHazardActionAndDirectInterception() {
        assertFalse(EldritchWatcherRules.lureOutranked(false, false, false));
        assertTrue(EldritchWatcherRules.lureOutranked(true, false, false));
        assertTrue(EldritchWatcherRules.lureOutranked(false, true, false));
        assertTrue(EldritchWatcherRules.lureOutranked(false, false, true));
    }

    @Test
    void rangeEnvelopesAndWithdrawalThresholdAreExact() {
        assertTrue(EldritchWatcherRules.withinAttackRange(14.0D * 14.0D));
        assertFalse(EldritchWatcherRules.withinAttackRange(14.01D * 14.01D));
        assertTrue(EldritchWatcherRules.withinChaseEnvelope(400.0D, 400.0D));
        assertFalse(EldritchWatcherRules.withinChaseEnvelope(401.0D, 100.0D));
        assertFalse(EldritchWatcherRules.withinChaseEnvelope(100.0D, 401.0D));
        assertTrue(EldritchWatcherRules.thresholdBreach(25.0D));
        assertFalse(EldritchWatcherRules.thresholdBreach(25.01D));
        assertTrue(EldritchWatcherRules.withdrawRequired(0.25D));
        assertFalse(EldritchWatcherRules.withdrawRequired(0.2501D));
    }

    @Test
    void routeFailuresCapAndBackOffAtLeastOneHundredTicks() {
        assertEquals(1, EldritchWatcherRules.routeFailures(0));
        assertEquals(3, EldritchWatcherRules.routeFailures(2));
        assertEquals(3, EldritchWatcherRules.routeFailures(9));
        assertEquals(0L, EldritchWatcherRules.routeBackoffUntil(2, 1_000L));
        assertEquals(1_100L, EldritchWatcherRules.routeBackoffUntil(3, 1_000L));
    }

    @Test
    void deadlinesClampToTheBoundedHorizonAndZeroReadsAsDue() {
        assertEquals(0L, EldritchWatcherRules.clampDeadline(0L, 100L, 500L));
        assertEquals(0L, EldritchWatcherRules.clampDeadline(-5L, 100L, 500L));
        assertEquals(400L, EldritchWatcherRules.clampDeadline(400L, 100L, 500L));
        assertEquals(600L, EldritchWatcherRules.clampDeadline(Long.MAX_VALUE, 100L, 500L));
        assertEquals(100L + EldritchWatcherRules.MAX_DEADLINE_HORIZON_TICKS,
            EldritchWatcherRules.clampDeadline(Long.MAX_VALUE, 100L, Long.MAX_VALUE));
        assertTrue(EldritchWatcherRules.due(0L, 5L));
        assertTrue(EldritchWatcherRules.due(5L, 5L));
        assertFalse(EldritchWatcherRules.due(6L, 5L));
        assertEquals(Long.MAX_VALUE, EldritchWatcherRules.saturatingAdd(Long.MAX_VALUE - 1L, 10L));
    }

    @Test
    void staggeringAndDestinationCandidatesAreDeterministicAndBounded() {
        assertEquals(
            EldritchWatcherRules.stableOffset(WATCHER_ID, 20),
            EldritchWatcherRules.stableOffset(WATCHER_ID, 20)
        );
        assertTrue(EldritchWatcherRules.stableOffset(WATCHER_ID, 20) < 20);
        assertEquals(0, EldritchWatcherRules.stableOffset(WATCHER_ID, 1));
        final List<long[]> offsets = EldritchWatcherRules.destinationOffsets(WATCHER_ID);
        assertEquals(EldritchWatcherRules.MAX_DESTINATION_CANDIDATES, offsets.size());
        for (final long[] offset : offsets) {
            assertTrue(Math.abs(offset[0]) <= EldritchWatcherRules.DESTINATION_RADIUS);
            assertTrue(Math.abs(offset[1]) <= EldritchWatcherRules.DESTINATION_RADIUS);
            assertTrue(Math.abs(offset[2]) <= EldritchWatcherRules.DESTINATION_RADIUS);
        }
        final List<long[]> repeat = EldritchWatcherRules.destinationOffsets(WATCHER_ID);
        for (int index = 0; index < offsets.size(); index++) {
            assertEquals(offsets.get(index)[0], repeat.get(index)[0]);
            assertEquals(offsets.get(index)[1], repeat.get(index)[1]);
            assertEquals(offsets.get(index)[2], repeat.get(index)[2]);
        }
    }

    @Test
    void focusScanAlwaysCoversTheOwnLayerFirstAndRotatesTheRestOfTheEnvelope() {
        final java.util.Set<Integer> secondaries = new java.util.HashSet<>();
        for (long phase = 0; phase < 8; phase++) {
            final int[] layers = EldritchWatcherRules.focusLayers(phase);
            assertEquals(2, layers.length);
            assertEquals(0, layers[0], "the Watcher's own layer is always examined first");
            assertTrue(Math.abs(layers[1]) <= EldritchWatcherRules.FOCUS_VERTICAL_RADIUS);
            assertTrue(layers[1] != 0);
            secondaries.add(layers[1]);
        }
        assertEquals(java.util.Set.of(1, -1, 2, -2, 3, -3, 4, -4), secondaries,
            "every non-zero layer of the envelope is reachable across successive scans");
        assertEquals(EldritchWatcherRules.focusLayers(3L)[1], EldritchWatcherRules.focusLayers(11L)[1],
            "layer rotation is deterministic and periodic");
        final int layerSize = 81;
        final java.util.Set<Integer> starts = new java.util.HashSet<>();
        for (long phase = 0; phase < 16; phase++) {
            final int start = EldritchWatcherRules.focusLayerStart(phase, layerSize);
            assertTrue(start >= 0 && start < layerSize);
            starts.add(start);
        }
        assertTrue(starts.size() > 1, "within-layer starts rotate so partial layers complete across scans");
    }

    @Test
    void ownerGuardEvidenceRequiresBindingFreshnessProximityAndSight() {
        assertEquals(12, EldritchWatcherRules.OWNER_GUARD_RADIUS);
        assertEquals(40, EldritchWatcherRules.ATTRIBUTION_FRESHNESS_TICKS);
        assertTrue(EldritchWatcherRules.ownerGuardEvidence(true, true, true, true, true));
        assertFalse(EldritchWatcherRules.ownerGuardEvidence(false, true, true, true, true),
            "only the Warlockery owner creates the guard relation");
        assertFalse(EldritchWatcherRules.ownerGuardEvidence(true, false, true, true, true),
            "stale attribution never escalates");
        assertFalse(EldritchWatcherRules.ownerGuardEvidence(true, true, false, true, true));
        assertFalse(EldritchWatcherRules.ownerGuardEvidence(true, true, true, false, true),
            "owner harm outside twelve blocks of the anchor is not guard evidence");
        assertFalse(EldritchWatcherRules.ownerGuardEvidence(true, true, true, true, false),
            "line of sight precedes any observed-event response");
    }

    @Test
    void evidenceLifetimesMatchTheApprovedExpiry() {
        assertEquals(EldritchWatcherRules.REPORTED_HARM_TICKS,
            EldritchWatcherRules.evidenceLifetimeTicks(EvidenceType.REPORTED_HARM));
        assertEquals(EldritchWatcherRules.SEEN_EVIDENCE_TICKS,
            EldritchWatcherRules.evidenceLifetimeTicks(EvidenceType.SEEN));
        assertEquals(EldritchWatcherRules.SEEN_EVIDENCE_TICKS,
            EldritchWatcherRules.evidenceLifetimeTicks(EvidenceType.DIRECT_HARM));
    }
}
