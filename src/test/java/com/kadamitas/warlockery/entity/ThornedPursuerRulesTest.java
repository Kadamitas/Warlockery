package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ThornedPursuerRulesTest {
    @Test
    void constantsPinTheApprovedBoundedContract() {
        assertEquals(40, ThornedPursuerRules.QUARRY_SCAN_CADENCE);
        assertEquals(16.0D, ThornedPursuerRules.QUARRY_SCAN_RADIUS);
        assertEquals(8, ThornedPursuerRules.MAX_SCAN_VISITS);
        assertEquals(2, ThornedPursuerRules.MAX_SCAN_SIGHT_RAYS);
        assertEquals(1_200, ThornedPursuerRules.EPISODE_BUDGET);
        assertEquals(4, ThornedPursuerRules.TRAIL_CAPACITY);
        assertEquals(32.0D * 32.0D, ThornedPursuerRules.RETENTION_DISTANCE_SQR);
        assertEquals(48.0D * 48.0D, ThornedPursuerRules.LEASH_DISTANCE_SQR);
        assertEquals(9.0D, ThornedPursuerRules.HOLD_DISTANCE_SQR);
        assertEquals(40, ThornedPursuerRules.ATTRIBUTION_FRESHNESS_TICKS);
        assertEquals(2, ThornedPursuerRules.MAX_ESCORTS);
        assertEquals(20_000, ThornedPursuerRules.BOUNDED_FUTURE_SENTINEL);
    }

    @Test
    void phaseTransitionsRespectTelegraphsAndInclusiveBoundaries() {
        assertFalse(ThornedPursuerRules.bayElapsed(39));
        assertTrue(ThornedPursuerRules.bayElapsed(40));
        assertTrue(ThornedPursuerRules.mayEnterSet(9.0D, true, 0, false));
        assertFalse(ThornedPursuerRules.mayEnterSet(Math.nextUp(9.0D), true, 0, false));
        assertFalse(ThornedPursuerRules.mayEnterSet(9.0D, false, 0, false));
        assertFalse(ThornedPursuerRules.mayEnterSet(9.0D, true, 1, false));
        assertTrue(ThornedPursuerRules.holdMayCommit(20, true, true, 9.0D));
        assertFalse(ThornedPursuerRules.holdMayCommit(19, true, true, 9.0D));
        assertTrue(ThornedPursuerRules.recoverComplete(4.0D, 0, 0));
        assertTrue(ThornedPursuerRules.recoverComplete(100.0D, 3, 0));
        assertTrue(ThornedPursuerRules.recoverComplete(100.0D, 0, 400));
    }

    @Test
    void scheduledBreakArbitrationHasOneClosedPrecedenceOrder() {
        assertTrue(ThornedPursuerRules.scheduledBreakReason(new ThornedPursuerRules.BreakFacts(
            false, false, false, false, false)).isEmpty());
        assertEquals(ThornedPursuerRules.BreakReason.BUDGET,
            ThornedPursuerRules.scheduledBreakReason(new ThornedPursuerRules.BreakFacts(
                true, true, true, true, true)).orElseThrow());
        assertEquals(ThornedPursuerRules.BreakReason.LEASH_EXCEEDED,
            ThornedPursuerRules.scheduledBreakReason(new ThornedPursuerRules.BreakFacts(
                false, true, true, true, true)).orElseThrow());
        assertEquals(ThornedPursuerRules.BreakReason.QUARRY_OUT_OF_RETENTION,
            ThornedPursuerRules.scheduledBreakReason(new ThornedPursuerRules.BreakFacts(
                false, false, true, true, true)).orElseThrow());
        assertEquals(ThornedPursuerRules.BreakReason.TRAIL_EXPIRED,
            ThornedPursuerRules.scheduledBreakReason(new ThornedPursuerRules.BreakFacts(
                false, false, false, true, true)).orElseThrow());
        assertEquals(ThornedPursuerRules.BreakReason.ROUTE_FAILED,
            ThornedPursuerRules.scheduledBreakReason(new ThornedPursuerRules.BreakFacts(
                false, false, false, false, true)).orElseThrow());
    }

    @Test
    void quarryReleaseClassificationCoversEveryImmediateCancellationReason() {
        assertEquals(ThornedPursuerRules.BreakReason.QUARRY_DIMENSION,
            ThornedPursuerRules.immediateReleaseReason(new ThornedPursuerRules.ReleaseFacts(
                false, false, false, false, false)).orElseThrow());
        assertEquals(ThornedPursuerRules.BreakReason.QUARRY_UNLOADED,
            ThornedPursuerRules.immediateReleaseReason(new ThornedPursuerRules.ReleaseFacts(
                true, false, false, false, false)).orElseThrow());
        assertEquals(ThornedPursuerRules.BreakReason.QUARRY_REMOVED,
            ThornedPursuerRules.immediateReleaseReason(new ThornedPursuerRules.ReleaseFacts(
                true, true, true, true, false)).orElseThrow());
        assertEquals(ThornedPursuerRules.BreakReason.QUARRY_DEAD,
            ThornedPursuerRules.immediateReleaseReason(new ThornedPursuerRules.ReleaseFacts(
                true, true, false, false, false)).orElseThrow());
        assertEquals(ThornedPursuerRules.BreakReason.QUARRY_ILLEGAL,
            ThornedPursuerRules.immediateReleaseReason(new ThornedPursuerRules.ReleaseFacts(
                true, true, true, false, false)).orElseThrow());
        assertTrue(ThornedPursuerRules.immediateReleaseReason(new ThornedPursuerRules.ReleaseFacts(
            true, true, true, false, true)).isEmpty());
    }

    @Test
    void prioritiesAndQuarryOrderingAreDeterministic() {
        assertEquals(ThornedPursuerRules.Priority.HAZARD,
            ThornedPursuerRules.priority(true, true, true));
        assertEquals(ThornedPursuerRules.Priority.COMBAT,
            ThornedPursuerRules.priority(false, true, true));
        assertEquals(ThornedPursuerRules.Priority.EPISODE,
            ThornedPursuerRules.priority(false, false, true));
        assertEquals(ThornedPursuerRules.Priority.ROUTINE,
            ThornedPursuerRules.priority(false, false, false));
        UUID lower = new UUID(0, 1);
        UUID higher = new UUID(0, 2);
        var selected = ThornedPursuerRules.selectQuarry(List.of(
            new ThornedPursuerRules.QuarryCandidate(higher, 4.0D, false),
            new ThornedPursuerRules.QuarryCandidate(lower, 4.0D, false)));
        assertEquals(lower, selected.orElseThrow());
        assertEquals(higher, ThornedPursuerRules.selectQuarry(List.of(
            new ThornedPursuerRules.QuarryCandidate(lower, 4.0D, false),
            new ThornedPursuerRules.QuarryCandidate(higher, 4.0D, true))).orElseThrow());
        assertEquals(lower, ThornedPursuerRules.selectQuarry(List.of(
            new ThornedPursuerRules.QuarryCandidate(lower, 3.0D, false),
            new ThornedPursuerRules.QuarryCandidate(higher, 4.0D, true))).orElseThrow(),
            "the owner hint only wins among equal-distance candidates");
    }

    @Test
    void quarryScanBoundaryIsInclusiveAtExactlySixteenBlocks() {
        assertTrue(ThornedPursuerRules.withinQuarryScan(16.0D * 16.0D));
        assertFalse(ThornedPursuerRules.withinQuarryScan(Math.nextUp(16.0D * 16.0D)));
    }

    @Test
    void retentionAndLeashBoundariesAreInclusiveAndWorldTimeIndependent() {
        assertTrue(ThornedPursuerRules.withinRetention(32.0D * 32.0D));
        assertFalse(ThornedPursuerRules.withinRetention(Math.nextUp(32.0D * 32.0D)));
        assertTrue(ThornedPursuerRules.withinLeash(48.0D * 48.0D));
        assertFalse(ThornedPursuerRules.withinLeash(Math.nextUp(48.0D * 48.0D)));
        assertEquals(ThornedPursuerRules.cooldownDue(0), ThornedPursuerRules.cooldownDue(0),
            "a due loaded-tick cooldown has no absolute-world-time input");
    }

    @Test
    void quarryEligibilityClosesEveryApprovedSocialAndIdentityExclusion() {
        boolean[] facts = {true, false, false, false, false, false, false, false, false, true};
        assertTrue(ThornedPursuerRules.eligibleQuarry(new ThornedPursuerRules.QuarryFacts(
            facts[0], facts[1], facts[2], facts[3], facts[4], facts[5], facts[6], facts[7], facts[8], facts[9])));
        for (int veto = 1; veto <= 8; veto++) {
            boolean[] changed = facts.clone(); changed[veto] = true;
            assertFalse(ThornedPursuerRules.eligibleQuarry(new ThornedPursuerRules.QuarryFacts(
                changed[0], changed[1], changed[2], changed[3], changed[4], changed[5],
                changed[6], changed[7], changed[8], changed[9])), "veto " + veto);
        }
        facts[0] = false;
        assertFalse(ThornedPursuerRules.eligibleQuarry(new ThornedPursuerRules.QuarryFacts(
            facts[0], facts[1], facts[2], facts[3], facts[4], facts[5], facts[6], facts[7], facts[8], facts[9])));
    }

    @Test
    void zeroSentinelsFreshnessAndStaggeringUseLoadedTicks() {
        assertTrue(ThornedPursuerRules.cooldownDue(0));
        assertTrue(ThornedPursuerRules.attributionFresh(0));
        assertTrue(ThornedPursuerRules.attributionFresh(40));
        assertFalse(ThornedPursuerRules.attributionFresh(41));
        assertTrue(ThornedPursuerRules.cadenceDue(39, 1, 40));
        assertFalse(ThornedPursuerRules.cadenceDue(38, 1, 40));
    }

    @Test
    void retaliationIsContactBoundCooldownBoundAndClamped() {
        assertEquals(0.0F, ThornedPursuerRules.retaliationDamage(8.0F, 0, 10.0D, true, 0));
        assertEquals(0.0F, ThornedPursuerRules.retaliationDamage(8.0F, 0, 9.0D, false, 0));
        assertEquals(0.0F, ThornedPursuerRules.retaliationDamage(8.0F, 0, 9.0D, true, 1));
        assertEquals(4.0F, ThornedPursuerRules.retaliationDamage(8.0F, 0, 9.0D, true, 0));
        assertEquals(5.0F, ThornedPursuerRules.retaliationDamage(8.0F, 1, 9.0D, true, 0));
        assertEquals(6.0F, ThornedPursuerRules.retaliationDamage(80.0F, 2, 9.0D, true, 0));
        assertEquals(0, ThornedPursuerRules.nextLadderStep(false, 2));
        assertEquals(2, ThornedPursuerRules.nextLadderStep(true, 2));
    }

    @Test
    void acceptedLossIncludesHealthAndAbsorptionWithoutMintingZeroLoss() {
        assertEquals(2.0F, ThornedPursuerRules.acceptedEffectiveLoss(100.0F, 4.0F, 100.0F, 2.0F));
        assertEquals(3.0F, ThornedPursuerRules.acceptedEffectiveLoss(100.0F, 4.0F, 98.0F, 3.0F));
        assertEquals(0.0F, ThornedPursuerRules.acceptedEffectiveLoss(100.0F, 4.0F, 100.0F, 4.0F));
        assertEquals(0.0F, ThornedPursuerRules.acceptedEffectiveLoss(98.0F, 0.0F, 100.0F, 0.0F));
    }

    @Test
    void retaliationCooldownAndLadderArePerAttackerAndLedgerIsBounded() {
        var ledger = new ThornedPursuerRules.RetaliationLedger(8);
        UUID first = new UUID(0L, 1L);
        UUID second = new UUID(0L, 2L);
        assertTrue(ledger.mayRetaliate(first));
        assertEquals(0, ledger.recordRetaliation(first));
        assertFalse(ledger.mayRetaliate(first));
        assertTrue(ledger.mayRetaliate(second), "one attacker cannot globally cool down another");
        assertEquals(0, ledger.recordRetaliation(second));
        for (int tick = 0; tick < ThornedPursuerRules.RETALIATION_COOLDOWN; tick++) ledger.tick();
        assertTrue(ledger.mayRetaliate(first));
        assertEquals(1, ledger.recordRetaliation(first), "the same fresh attacker advances only its own ladder");
        for (long id = 3; id <= 10; id++) ledger.recordRetaliation(new UUID(0L, id));
        assertEquals(8, ledger.size());
        assertFalse(ledger.contains(second), "the bounded ledger evicts its oldest authority deterministically");
    }

    @Test
    void escortRouteTrailAndQuotaArithmeticStayBounded() {
        assertEquals(2, ThornedPursuerRules.escortSlots(0));
        assertEquals(1, ThornedPursuerRules.escortSlots(1));
        assertEquals(0, ThornedPursuerRules.escortSlots(2));
        assertEquals(3, ThornedPursuerRules.recordRouteFailure(2));
        assertTrue(ThornedPursuerRules.routeBackoffRequired(3));
        assertTrue(ThornedPursuerRules.trailExpired(200));
        var budget = ThornedPursuerRules.LevelBudget.empty(17);
        for (int i = 0; i < 16; i++) budget = budget.take(ThornedPursuerRules.Work.EXPENSIVE).orElseThrow();
        assertTrue(budget.take(ThornedPursuerRules.Work.EXPENSIVE).isEmpty());
        assertEquals(16, budget.expensive());
        assertEquals(17, budget.serverTick());
        var first = ThornedPursuerRules.recordRouteFailure(0, 0);
        var second = ThornedPursuerRules.recordRouteFailure(first.failures(), first.backoffTicks());
        var third = ThornedPursuerRules.recordRouteFailure(second.failures(), second.backoffTicks());
        assertEquals(0, third.failures());
        assertTrue(third.backoffTicks() >= 100, "the third strict failure installs the frozen backoff");
        assertTrue(ThornedPursuerRules.routeAttemptDeferred(third.backoffTicks()));
        assertEquals(third.backoffTicks() - 1, ThornedPursuerRules.tickRouteBackoff(third.backoffTicks()));
    }

    @Test
    void episodeBudgetArbitrationIncludesBayAndSet() {
        assertFalse(ThornedPursuerRules.episodeBudgetReached(ThornedPursuerRules.Phase.BAY, 1_199));
        assertTrue(ThornedPursuerRules.episodeBudgetReached(ThornedPursuerRules.Phase.BAY, 1_200));
        assertTrue(ThornedPursuerRules.episodeBudgetReached(ThornedPursuerRules.Phase.SET, 1_200));
        assertTrue(ThornedPursuerRules.episodeBudgetReached(ThornedPursuerRules.Phase.COURSE, 1_200));
        assertTrue(ThornedPursuerRules.episodeBudgetReached(ThornedPursuerRules.Phase.PRESS, 1_200));
        assertFalse(ThornedPursuerRules.episodeBudgetReached(ThornedPursuerRules.Phase.RECOVER, 1_200));
    }

    @Test
    void levelQuotasResetOnlyByServerTickAndDenyOffThread() {
        Object level = new Object();
        ThornedPursuerRuntime.clearBudgetsForTest();
        for (int i = 0; i < 16; i++) {
            assertTrue(ThornedPursuerRuntime.claimForTest(level, 9, true,
                ThornedPursuerRules.Work.EXPENSIVE));
        }
        assertFalse(ThornedPursuerRuntime.claimForTest(level, 9, true,
            ThornedPursuerRules.Work.EXPENSIVE));
        assertFalse(ThornedPursuerRuntime.claimForTest(level, 10, false,
            ThornedPursuerRules.Work.EXPENSIVE));
        assertTrue(ThornedPursuerRuntime.claimForTest(level, 10, true,
            ThornedPursuerRules.Work.EXPENSIVE), "a new server tick resets the quota");
    }

    @Test
    void everyLevelQuotaHasTheExactApprovedLimit() {
        for (var work : ThornedPursuerRules.Work.values()) {
            var budget = ThornedPursuerRules.LevelBudget.empty(1);
            int accepted = 0;
            while (true) {
                var next = budget.take(work);
                if (next.isEmpty()) break;
                budget = next.orElseThrow();
                accepted++;
            }
            int expected = switch (work) {
                case EXPENSIVE -> 16; case PATH -> 8; case ENTITY_VISIT -> 128;
                case SIGHT_RAY -> 32; case READ -> 512; case SAFE_ENTITY_VISIT -> 128;
                case HOLD -> 4; case MELEE -> 8; case RETALIATION -> 8;
                case ESCORT -> 4; case FEEDBACK -> 8;
            };
            assertEquals(expected, accepted, work.name());
        }
    }

    @Test
    void hazardSearchIsDeterministicBoundedAndRequiresRealImprovement() {
        var offsets = ThornedPursuerRules.safeOffsets();
        assertEquals(16, offsets.size());
        assertEquals(16, offsets.stream().distinct().count());
        assertTrue(offsets.stream().allMatch(offset -> Math.abs(offset.x()) <= 6
            && Math.abs(offset.z()) <= 6 && Math.abs(offset.y()) <= 2));
        assertFalse(ThornedPursuerRules.safeDestination(new ThornedPursuerRules.SafeFacts(
            true, true, true, true, false, 1, 2)));
        assertTrue(ThornedPursuerRules.safeDestination(new ThornedPursuerRules.SafeFacts(
            true, true, true, true, true, 2, 1)));
        assertFalse(ThornedPursuerRules.safeDestination(new ThornedPursuerRules.SafeFacts(
            true, true, true, true, true, 1, 1)));
    }

    @Test
    void diagnosticsExposeEveryApprovedCounterAsTransientScalars() {
        var names = java.util.Arrays.stream(ThornedPursuerRuntime.Counters.class.getDeclaredFields())
            .map(java.lang.reflect.Field::getName).collect(java.util.stream.Collectors.toSet());
        assertTrue(names.containsAll(java.util.Set.of(
            "aiTicks", "cheapDecisions", "anchoredTicks", "episodeStarts", "episodeCancelsByReason",
            "phaseTransitionsByPair", "quarryScans", "quarryRawVisits", "quarrySightRays",
            "quarryAcquisitions", "quarryReleasesByReason", "hintPreferences", "hintExpiries",
            "bayStarts", "bayNavigationWrites", "baySounds", "courseEntries",
            "courseModifierApplications", "courseModifierRemovals", "trailWrites", "trailExpiries",
            "trailFollowPaths", "sightRays", "sightLossTicks", "holdTelegraphs", "holdCommits",
            "holdAbortsByReason", "slownessApplications", "holdCooldownBlocks", "pressAttempts",
            "pressAccepted", "pressTimeouts", "breakEvaluations", "breaksByReason", "recoverStarts",
            "recoverArrivals", "reanchors", "attackerAttributions", "attackerRejectionsByReason",
            "attackerExpiries", "retaliations", "retaliationLadderSteps", "retaliationRangeRejections",
            "retaliationCooldownBlocks", "escortEvaluations", "escortCreations", "escortPositionRejections",
            "escortReleases", "escortOrphans", "wolfScans", "pathRequests", "pathsAccepted",
            "pathFailures", "pathBackoffs", "navigationOverwrites", "hazardObservationReads",
            "safeCandidates", "safeReads", "safeEntityVisits", "hazardRoutes", "hazardEscapeSuccesses",
            "tokensGranted", "tokensDeferred", "feedbackEmitted", "feedbackSuppressed", "sounds",
            "particles", "genericBehaviorDispatches", "genericTacticalDispatches", "genericAmbientDispatches",
            "genericHazardDispatches", "teleports", "projectileCreations", "blockEdits", "chunkLoadRequests",
            "crossDimensionLookups", "reinforcements", "villagerConversions", "drownedConversions",
            "turtleEggBreaks", "doorBreaks", "babyStates", "equipmentStates", "piglinAlerts",
            "stateKeys", "stateBytes", "stateMismatches", "transientReplays")));
        assertTrue(java.util.Arrays.stream(ThornedPursuerRuntime.Counters.class.getDeclaredFields())
            .allMatch(field -> field.getType() == long.class || field.getType() == long[].class));
    }
}
