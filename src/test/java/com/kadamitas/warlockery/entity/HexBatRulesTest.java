package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.HexBatRules.AbsoluteFacts;
import com.kadamitas.warlockery.entity.HexBatRules.Mode;
import com.kadamitas.warlockery.entity.HexBatRules.ProactiveFacts;
import com.kadamitas.warlockery.entity.HexBatRules.ReleaseFacts;
import com.kadamitas.warlockery.entity.HexBatRules.RoostFacts;
import com.kadamitas.warlockery.entity.HexBatRules.SwoopCancelFacts;
import com.kadamitas.warlockery.entity.HexBatRules.SwoopStartFacts;
import com.kadamitas.warlockery.entity.HexBatRules.TargetCandidate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class HexBatRulesTest {
    private static final UUID BAT_ID = new UUID(3L, 71L);

    @Test
    void everyConstantIsExactAndInternallyValid() {
        assertEquals(20, HexBatRules.TARGET_SCAN_INTERVAL_TICKS);
        assertEquals(16, HexBatRules.TARGET_QUERY_RADIUS);
        assertEquals(16, HexBatRules.MAX_TARGET_VISITS);
        assertEquals(8, HexBatRules.MAX_RETAINED_TARGETS);
        assertEquals(8, HexBatRules.MAX_LINE_OF_SIGHT_CLIPS);
        assertEquals(12, HexBatRules.PROACTIVE_ACQUIRE_RANGE);
        assertEquals(16, HexBatRules.CHASE_RANGE);
        assertEquals(80, HexBatRules.UNSEEN_RELEASE_TICKS);
        assertEquals(40, HexBatRules.ATTRIBUTION_FRESHNESS_TICKS);
        assertEquals(40, HexBatRules.CALL_SCAN_INTERVAL_TICKS);
        assertEquals(12, HexBatRules.CALL_RADIUS);
        assertEquals(8, HexBatRules.MAX_PEER_VISITS);
        assertEquals(3, HexBatRules.MAX_CALL_RECIPIENTS);
        assertEquals(40, HexBatRules.CALL_DEDUPE_TICKS);
        assertEquals(80, HexBatRules.CALL_EXPIRY_TICKS);
        assertEquals(1, HexBatRules.MAX_CALL_HOPS);
        assertEquals(80, HexBatRules.ROOST_SEARCH_INTERVAL_TICKS);
        assertEquals(48, HexBatRules.MAX_ROOST_CANDIDATES);
        assertEquals(128, HexBatRules.MAX_ROOST_BLOCK_READS);
        assertEquals(10, HexBatRules.ROOST_HORIZONTAL_RANGE);
        assertEquals(6, HexBatRules.ROOST_VERTICAL_RANGE);
        assertEquals(10, HexBatRules.SORTIE_RANGE);
        assertEquals(600, HexBatRules.SORTIE_MAX_TICKS);
        assertEquals(20, HexBatRules.HAZARD_SCAN_INTERVAL_TICKS);
        assertEquals(27, HexBatRules.MAX_HAZARD_BLOCK_READS);
        assertEquals(0.20F, HexBatRules.WITHDRAW_HEALTH_FRACTION);
        assertEquals(100, HexBatRules.WITHDRAW_TICKS);
        assertEquals(20, HexBatRules.NAVIGATION_INTERVAL_TICKS);
        assertEquals(3, HexBatRules.MAX_ROUTE_FAILURES);
        assertEquals(100, HexBatRules.ROUTE_BACKOFF_TICKS);
        assertEquals(24, HexBatRules.MAX_DESTINATION_CANDIDATES);
        assertEquals(256, HexBatRules.MAX_DESTINATION_BLOCK_READS);
        assertEquals(10, HexBatRules.SWOOP_WINDUP_TICKS);
        assertEquals(40, HexBatRules.SWOOP_EXECUTE_TICKS);
        assertEquals(60, HexBatRules.SWOOP_RECOVERY_TICKS);
        assertEquals(40, HexBatRules.POST_CONTACT_WITHDRAW_TICKS);
        assertEquals(200, HexBatRules.JINX_DURATION_TICKS);
        assertEquals(0, HexBatRules.JINX_AMPLIFIER);
        assertEquals(6, HexBatRules.MAX_TELEGRAPH_PARTICLES);
        assertEquals(4, HexBatRules.MAX_CALL_PARTICLES);
        assertEquals(8, HexBatRules.MAX_CONTACT_PARTICLES);
        assertEquals(20_000L, HexBatRules.MAX_FUTURE_HORIZON_TICKS);
        assertEquals(0.34D, HexBatRules.FLYING_SPEED,
            "the deferred ModEntities attribute edit reads this exact winged-convention value");
        assertTrue(HexBatRules.MAX_RETAINED_TARGETS <= HexBatRules.MAX_TARGET_VISITS);
        assertTrue(HexBatRules.CALL_DEDUPE_TICKS <= HexBatRules.CALL_EXPIRY_TICKS);
    }

    @Test
    void scheduleBoundariesAreExactAtTheNightWindowEdges() {
        assertFalse(HexBatRules.isNight(12_999L));
        assertTrue(HexBatRules.isNight(13_000L));
        assertTrue(HexBatRules.isNight(23_000L));
        assertFalse(HexBatRules.isNight(23_001L));
        assertTrue(HexBatRules.isNight(24_000L + 13_000L), "the clock wraps modulo one day");
    }

    @Test
    void absoluteExclusionsRejectEveryProtectedIdentity() {
        assertFalse(HexBatRules.absolutelyExcluded(new AbsoluteFacts(
            false, false, false, false, false, false, false, false)));
        assertTrue(HexBatRules.absolutelyExcluded(new AbsoluteFacts(
            true, false, false, false, false, false, false, false)), "self");
        assertTrue(HexBatRules.absolutelyExcluded(new AbsoluteFacts(
            false, true, false, false, false, false, false, false)), "dead");
        assertTrue(HexBatRules.absolutelyExcluded(new AbsoluteFacts(
            false, false, true, false, false, false, false, false)), "invulnerable");
        assertTrue(HexBatRules.absolutelyExcluded(new AbsoluteFacts(
            false, false, false, true, false, false, false, false)), "owner");
        assertTrue(HexBatRules.absolutelyExcluded(new AbsoluteFacts(
            false, false, false, false, true, false, false, false)), "same owner");
        assertTrue(HexBatRules.absolutelyExcluded(new AbsoluteFacts(
            false, false, false, false, false, true, false, false)), "exact hex bat");
        assertTrue(HexBatRules.absolutelyExcluded(new AbsoluteFacts(
            false, false, false, false, false, false, true, false)), "creative or spectator");
        assertTrue(HexBatRules.absolutelyExcluded(new AbsoluteFacts(
            false, false, false, false, false, false, false, true)), "other dimension");
    }

    @Test
    void proactiveExclusionsProtectNoncombatantsWitchesOwlsArcanesWallsAndRange() {
        assertFalse(HexBatRules.proactivelyExcluded(new ProactiveFacts(false, false, false, false, false)));
        assertTrue(HexBatRules.proactivelyExcluded(new ProactiveFacts(true, false, false, false, false)));
        assertTrue(HexBatRules.proactivelyExcluded(new ProactiveFacts(false, true, false, false, false)));
        assertTrue(HexBatRules.proactivelyExcluded(new ProactiveFacts(false, false, true, false, false)));
        assertTrue(HexBatRules.proactivelyExcluded(new ProactiveFacts(false, false, false, true, false)));
        assertTrue(HexBatRules.proactivelyExcluded(new ProactiveFacts(false, false, false, false, true)));
    }

    @Test
    void candidatePriorityOrdersDirectExplicitOwnerStableMarkPlayerHostile() {
        final UUID direct = new UUID(0L, 1L);
        final UUID explicit = new UUID(0L, 2L);
        final UUID ownerAttacker = new UUID(0L, 3L);
        final UUID stable = new UUID(0L, 4L);
        final UUID marked = new UUID(0L, 5L);
        final UUID player = new UUID(0L, 6L);
        final UUID hostile = new UUID(0L, 7L);
        final List<TargetCandidate> retained = List.of(
            new TargetCandidate(hostile, TargetCandidate.RANK_ORDINARY_HOSTILE, 1.0D),
            new TargetCandidate(player, TargetCandidate.RANK_SURVIVAL_PLAYER, 1.0D),
            new TargetCandidate(marked, TargetCandidate.RANK_MARKED, 1.0D),
            new TargetCandidate(stable, TargetCandidate.RANK_STABLE_CURRENT, 1.0D),
            new TargetCandidate(ownerAttacker, TargetCandidate.RANK_OWNER_ATTACKER, 1.0D),
            new TargetCandidate(explicit, TargetCandidate.RANK_EXPLICIT_FLOCK, 1.0D),
            new TargetCandidate(direct, TargetCandidate.RANK_DIRECT_ATTACKER, 99.0D)
        );
        assertEquals(direct, HexBatRules.selectTarget(retained).orElseThrow().id(),
            "the direct attacker wins even at greater distance");
        final List<TargetCandidate> withoutDirect = retained.stream()
            .filter(candidate -> !candidate.id().equals(direct)).toList();
        assertEquals(explicit, HexBatRules.selectTarget(withoutDirect).orElseThrow().id());
    }

    @Test
    void deterministicTiesBreakByDistanceThenUnsignedUuid() {
        final UUID low = new UUID(0L, 1L);
        final UUID high = new UUID(-1L, -1L);
        assertEquals(low, HexBatRules.selectTarget(List.of(
            new TargetCandidate(high, TargetCandidate.RANK_ORDINARY_HOSTILE, 4.0D),
            new TargetCandidate(low, TargetCandidate.RANK_ORDINARY_HOSTILE, 4.0D)
        )).orElseThrow().id(), "unsigned UUID order breaks exact ties");
        assertEquals(high, HexBatRules.selectTarget(List.of(
            new TargetCandidate(high, TargetCandidate.RANK_ORDINARY_HOSTILE, 1.0D),
            new TargetCandidate(low, TargetCandidate.RANK_ORDINARY_HOSTILE, 4.0D)
        )).orElseThrow().id(), "distance beats UUID order");
    }

    @Test
    void preseededCandidatesSurviveSixteenGenericEntriesAndTheCapHolds() {
        final List<TargetCandidate> preseeded = List.of(
            new TargetCandidate(new UUID(9L, 1L), TargetCandidate.RANK_DIRECT_ATTACKER, 50.0D),
            new TargetCandidate(new UUID(9L, 2L), TargetCandidate.RANK_EXPLICIT_FLOCK, 60.0D)
        );
        final List<TargetCandidate> generic = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            generic.add(new TargetCandidate(new UUID(1L, index),
                TargetCandidate.RANK_ORDINARY_HOSTILE, index));
        }
        final List<TargetCandidate> retained = HexBatRules.retainCandidates(preseeded, generic);
        assertEquals(HexBatRules.MAX_RETAINED_TARGETS, retained.size());
        assertTrue(retained.containsAll(preseeded),
            "priority candidates cannot be lost beyond the retention cap");
        assertEquals(preseeded.get(0).id(), HexBatRules.selectTarget(retained).orElseThrow().id());
    }

    @Test
    void duplicatePreseedsAreNotDoubleCounted() {
        final UUID id = new UUID(5L, 5L);
        final List<TargetCandidate> retained = HexBatRules.retainCandidates(
            List.of(
                new TargetCandidate(id, TargetCandidate.RANK_DIRECT_ATTACKER, 1.0D),
                new TargetCandidate(id, TargetCandidate.RANK_STABLE_CURRENT, 1.0D)
            ),
            List.of(new TargetCandidate(id, TargetCandidate.RANK_ORDINARY_HOSTILE, 1.0D))
        );
        assertEquals(1, retained.size());
        assertEquals(TargetCandidate.RANK_DIRECT_ATTACKER, retained.get(0).priorityRank());
    }

    @Test
    void everyReleaseReasonReleasesAndNoReasonRetains() {
        assertFalse(HexBatRules.shouldRelease(new ReleaseFacts(
            false, false, false, false, false, false, false)));
        assertTrue(HexBatRules.shouldRelease(new ReleaseFacts(true, false, false, false, false, false, false)));
        assertTrue(HexBatRules.shouldRelease(new ReleaseFacts(false, true, false, false, false, false, false)));
        assertTrue(HexBatRules.shouldRelease(new ReleaseFacts(false, false, true, false, false, false, false)));
        assertTrue(HexBatRules.shouldRelease(new ReleaseFacts(false, false, false, true, false, false, false)));
        assertTrue(HexBatRules.shouldRelease(new ReleaseFacts(false, false, false, false, true, false, false)));
        assertTrue(HexBatRules.shouldRelease(new ReleaseFacts(false, false, false, false, false, true, false)));
        assertTrue(HexBatRules.shouldRelease(new ReleaseFacts(false, false, false, false, false, false, true)));
    }

    @Test
    void callCompatibilityRequiresEqualOwnersOrBothUnbound() {
        final UUID owner = new UUID(2L, 2L);
        final UUID other = new UUID(3L, 3L);
        assertTrue(HexBatRules.callCompatible(Optional.empty(), Optional.empty()));
        assertTrue(HexBatRules.callCompatible(Optional.of(owner), Optional.of(owner)));
        assertFalse(HexBatRules.callCompatible(Optional.of(owner), Optional.of(other)));
        assertFalse(HexBatRules.callCompatible(Optional.of(owner), Optional.empty()));
        assertFalse(HexBatRules.callCompatible(Optional.empty(), Optional.of(owner)));
    }

    @Test
    void oneHopCallCapDedupeAndExpiryAreEnforced() {
        assertTrue(HexBatRules.mayEmitCall(0, 0L, 100L));
        assertFalse(HexBatRules.mayEmitCall(HexBatRules.MAX_CALL_HOPS, 0L, 100L),
            "a received report can never itself emit another call");
        assertFalse(HexBatRules.mayEmitCall(0, 140L, 100L), "the dedupe window blocks repeats");
        assertTrue(HexBatRules.reportExpired(100L, 100L));
        assertFalse(HexBatRules.reportExpired(101L, 100L));
    }

    @Test
    void roostValidityRequiresEveryFactAndTheEnvelopeIsExact() {
        assertTrue(HexBatRules.validRoost(new RoostFacts(true, true, true, true, true, true, true, true)));
        for (int missing = 0; missing < 8; missing++) {
            final boolean[] facts = {true, true, true, true, true, true, true, true};
            facts[missing] = false;
            assertFalse(HexBatRules.validRoost(new RoostFacts(
                facts[0], facts[1], facts[2], facts[3], facts[4], facts[5], facts[6], facts[7]
            )), "missing roost fact index " + missing);
        }
        assertTrue(HexBatRules.withinAnchorEnvelope(10, 6, 10));
        assertFalse(HexBatRules.withinAnchorEnvelope(11, 0, 0));
        assertFalse(HexBatRules.withinAnchorEnvelope(0, 7, 0));
        assertFalse(HexBatRules.withinAnchorEnvelope(0, 0, -11));
    }

    @Test
    void movementCadenceRouteFailureAndBackoffContractsAreExact() {
        assertTrue(HexBatRules.navigationDue(0L, 5L));
        assertTrue(HexBatRules.navigationDue(5L, 5L));
        assertFalse(HexBatRules.navigationDue(6L, 5L));
        assertEquals(1, HexBatRules.routeFailures(0));
        assertEquals(3, HexBatRules.routeFailures(2));
        assertEquals(3, HexBatRules.routeFailures(9), "failures saturate at three");
        assertFalse(HexBatRules.routeBackoffRequired(2));
        assertTrue(HexBatRules.routeBackoffRequired(3));
        assertEquals(0L, HexBatRules.routeBackoffUntil(2, 100L));
        assertEquals(200L, HexBatRules.routeBackoffUntil(3, 100L));
    }

    @Test
    void swoopStartRequiresEveryGate() {
        assertTrue(HexBatRules.mayBeginSwoop(new SwoopStartFacts(true, true, true, true, true, true)));
        for (int missing = 0; missing < 6; missing++) {
            final boolean[] facts = {true, true, true, true, true, true};
            facts[missing] = false;
            assertFalse(HexBatRules.mayBeginSwoop(new SwoopStartFacts(
                facts[0], facts[1], facts[2], facts[3], facts[4], facts[5]
            )), "missing swoop gate index " + missing);
        }
    }

    @Test
    void swoopCancellationTriggersOnEveryListedReasonOnly() {
        assertFalse(HexBatRules.swoopCancelled(new SwoopCancelFacts(false, false, false, false, false)));
        assertTrue(HexBatRules.swoopCancelled(new SwoopCancelFacts(true, false, false, false, false)));
        assertTrue(HexBatRules.swoopCancelled(new SwoopCancelFacts(false, true, false, false, false)));
        assertTrue(HexBatRules.swoopCancelled(new SwoopCancelFacts(false, false, true, false, false)));
        assertTrue(HexBatRules.swoopCancelled(new SwoopCancelFacts(false, false, false, true, false)));
        assertTrue(HexBatRules.swoopCancelled(new SwoopCancelFacts(false, false, false, false, true)));
    }

    @Test
    void modePriorityIsStrictHazardWithdrawInterceptSchedule() {
        assertEquals(Mode.HAZARD, HexBatRules.modePriority(true, true, true, true));
        assertEquals(Mode.WITHDRAW, HexBatRules.modePriority(false, true, true, true));
        assertEquals(Mode.INTERCEPT, HexBatRules.modePriority(false, false, true, true));
        assertEquals(Mode.SORTIE, HexBatRules.modePriority(false, false, false, true));
        assertEquals(Mode.SHELTER, HexBatRules.modePriority(false, false, false, false));
    }

    @Test
    void lowHealthUsesTheExactTwentyPercentBoundary() {
        assertTrue(HexBatRules.lowHealth(2.8F, 14.0F));
        assertFalse(HexBatRules.lowHealth(2.81F, 14.0F));
        assertFalse(HexBatRules.lowHealth(1.0F, 0.0F), "zero max health cannot divide");
    }

    @Test
    void deadlineClampAndSaturatingArithmeticAreOverflowSafe() {
        assertEquals(0L, HexBatRules.clampDeadline(0L, 100L, 200L));
        assertEquals(0L, HexBatRules.clampDeadline(-5L, 100L, 200L), "negative deadlines reset");
        assertEquals(250L, HexBatRules.clampDeadline(250L, 100L, 200L), "near-future deadlines stay");
        assertEquals(300L, HexBatRules.clampDeadline(Long.MAX_VALUE, 100L, 200L),
            "extreme deadlines clamp to the approved horizon");
        assertEquals(100L + HexBatRules.MAX_FUTURE_HORIZON_TICKS,
            HexBatRules.clampDeadline(Long.MAX_VALUE, 100L, Long.MAX_VALUE),
            "no horizon may exceed the bounded twenty-thousand-tick sentinel");
        assertEquals(Long.MAX_VALUE, HexBatRules.saturatingAdd(Long.MAX_VALUE - 1L, 100L));
        assertEquals(7L, HexBatRules.saturatingAdd(3L, 4L));
    }

    @Test
    void stableOffsetStaggersPopulationsDeterministically() {
        final int offset = HexBatRules.stableOffset(BAT_ID, HexBatRules.TARGET_SCAN_INTERVAL_TICKS);
        assertEquals(offset, HexBatRules.stableOffset(BAT_ID, HexBatRules.TARGET_SCAN_INTERVAL_TICKS));
        assertTrue(offset >= 0 && offset < HexBatRules.TARGET_SCAN_INTERVAL_TICKS);
    }

    @Test
    void populationWorkBoundArithmeticStaysWithinDeclaredCaps() {
        // Worst-case per-entity per-cadence work products stay small and finite.
        assertTrue(HexBatRules.MAX_TARGET_VISITS + HexBatRules.MAX_LINE_OF_SIGHT_CLIPS <= 24);
        assertTrue(HexBatRules.MAX_ROOST_CANDIDATES * 2 <= HexBatRules.MAX_ROOST_BLOCK_READS + 32);
        assertTrue(HexBatRules.MAX_DESTINATION_CANDIDATES * 2 <= HexBatRules.MAX_DESTINATION_BLOCK_READS);
        assertTrue(HexBatRules.MAX_PEER_VISITS >= HexBatRules.MAX_CALL_RECIPIENTS);
    }

    @Test
    void flockRankingNeverLetsAnExplicitTargetLoseToANearerHostile() {
        final HexBatRules.FlockCandidate explicitFar = new HexBatRules.FlockCandidate(
            new UUID(9L, 1L), HexBatRules.FlockCandidate.RANK_EXPLICIT_TARGET, 144.0D);
        final HexBatRules.FlockCandidate ownerAttacker = new HexBatRules.FlockCandidate(
            new UUID(9L, 2L), HexBatRules.FlockCandidate.RANK_OWNER_ATTACKER, 100.0D);
        final HexBatRules.FlockCandidate jinxed = new HexBatRules.FlockCandidate(
            new UUID(9L, 3L), HexBatRules.FlockCandidate.RANK_JINX_MARKED, 25.0D);
        final HexBatRules.FlockCandidate player = new HexBatRules.FlockCandidate(
            new UUID(9L, 4L), HexBatRules.FlockCandidate.RANK_SURVIVAL_PLAYER, 4.0D);
        final HexBatRules.FlockCandidate hostileNear = new HexBatRules.FlockCandidate(
            new UUID(9L, 5L), HexBatRules.FlockCandidate.RANK_ORDINARY_HOSTILE, 1.0D);
        assertTrue(HexBatRules.flockOrder().compare(explicitFar, hostileNear) < 0,
            "the explicit cast target must never lose to a nearer hostile");
        assertTrue(HexBatRules.flockOrder().compare(explicitFar, ownerAttacker) < 0);
        assertTrue(HexBatRules.flockOrder().compare(ownerAttacker, jinxed) < 0);
        assertTrue(HexBatRules.flockOrder().compare(jinxed, player) < 0);
        assertTrue(HexBatRules.flockOrder().compare(player, hostileNear) < 0);
        // Within one rank: distance, then unsigned UUID.
        final HexBatRules.FlockCandidate hostileFar = new HexBatRules.FlockCandidate(
            new UUID(9L, 6L), HexBatRules.FlockCandidate.RANK_ORDINARY_HOSTILE, 64.0D);
        assertTrue(HexBatRules.flockOrder().compare(hostileNear, hostileFar) < 0);
        final HexBatRules.FlockCandidate hostileTieLow = new HexBatRules.FlockCandidate(
            new UUID(0L, 1L), HexBatRules.FlockCandidate.RANK_ORDINARY_HOSTILE, 1.0D);
        final HexBatRules.FlockCandidate hostileTieHigh = new HexBatRules.FlockCandidate(
            new UUID(0L, 2L), HexBatRules.FlockCandidate.RANK_ORDINARY_HOSTILE, 1.0D);
        assertTrue(HexBatRules.flockOrder().compare(hostileTieLow, hostileTieHigh) < 0);
        assertEquals(0, HexBatRules.FlockCandidate.RANK_EXPLICIT_TARGET);
        assertTrue(HexBatRules.FlockCandidate.RANK_EXPLICIT_TARGET
            < HexBatRules.FlockCandidate.RANK_OWNER_ATTACKER);
        assertTrue(HexBatRules.FlockCandidate.RANK_OWNER_ATTACKER
            < HexBatRules.FlockCandidate.RANK_JINX_MARKED);
        assertTrue(HexBatRules.FlockCandidate.RANK_JINX_MARKED
            < HexBatRules.FlockCandidate.RANK_SURVIVAL_PLAYER);
        assertTrue(HexBatRules.FlockCandidate.RANK_SURVIVAL_PLAYER
            < HexBatRules.FlockCandidate.RANK_ORDINARY_HOSTILE);
    }

    @Test
    void roostInvitesNeverOverrideAPeersHigherPriorityWork() {
        // Strict priority: an invitation is quiet-mode work. A peer in hazard,
        // withdrawal, or a bound action keeps its own navigation untouched.
        assertTrue(HexBatRules.mayAcceptRoostInvite(Mode.SHELTER, HexBatRules.Action.NONE));
        assertTrue(HexBatRules.mayAcceptRoostInvite(Mode.SORTIE, HexBatRules.Action.NONE));
        assertFalse(HexBatRules.mayAcceptRoostInvite(Mode.HAZARD, HexBatRules.Action.NONE),
            "a burning peer must keep its escape navigation untouched");
        assertFalse(HexBatRules.mayAcceptRoostInvite(Mode.WITHDRAW, HexBatRules.Action.NONE));
        assertFalse(HexBatRules.mayAcceptRoostInvite(Mode.INTERCEPT, HexBatRules.Action.NONE));
        assertFalse(HexBatRules.mayAcceptRoostInvite(Mode.SHELTER, HexBatRules.Action.SWOOP),
            "a bound action always refuses an invitation");
        assertFalse(HexBatRules.mayAcceptRoostInvite(Mode.SORTIE, HexBatRules.Action.SWOOP));
    }

    @Test
    void unseenReleaseFiresOnlyAfterEightyTicksWithoutASighting() {
        assertFalse(HexBatRules.unseenTooLong(0L, 1_000L),
            "no sighting baseline recorded yet means no unseen release");
        assertFalse(HexBatRules.unseenTooLong(1_000L, 1_000L + HexBatRules.UNSEEN_RELEASE_TICKS));
        assertTrue(HexBatRules.unseenTooLong(1_000L, 1_001L + HexBatRules.UNSEEN_RELEASE_TICKS));
    }

    @Test
    void roostSearchNearSweepAndRotatingPagesCoverTheWholeEnvelope() {
        // Budget split is exact.
        assertEquals(36, HexBatRules.ROOST_NEAR_SWEEP_CANDIDATES);
        assertEquals(12, HexBatRules.ROOST_PAGE_CANDIDATES);
        assertEquals(HexBatRules.MAX_ROOST_CANDIDATES,
            HexBatRules.ROOST_NEAR_SWEEP_CANDIDATES + HexBatRules.ROOST_PAGE_CANDIDATES);
        final int width = 2 * HexBatRules.ROOST_HORIZONTAL_RANGE + 1;
        final int height = 2 * HexBatRules.ROOST_VERTICAL_RANGE + 1;
        assertEquals(width * width * height, HexBatRules.roostEnvelopeSize());
        // The near sweep enumerates 36 distinct ceiling-first offsets around the
        // anchor with no ring-zero duplication, including the fixture's own
        // roost offset (+1, +1, 0).
        final java.util.Set<List<Integer>> sweep = new java.util.LinkedHashSet<>();
        for (int index = 0; index < HexBatRules.ROOST_NEAR_SWEEP_CANDIDATES; index++) {
            final HexBatRules.RoostOffset offset = HexBatRules.roostNearSweepOffset(index);
            assertTrue(HexBatRules.withinAnchorEnvelope(offset.dx(), offset.dy(), offset.dz()));
            assertTrue(HexBatRules.inRoostNearSweep(offset.dx(), offset.dy(), offset.dz()));
            assertTrue(sweep.add(List.of(offset.dx(), offset.dy(), offset.dz())),
                "no near-sweep offset may repeat inside one search");
        }
        assertTrue(sweep.contains(List.of(1, 1, 0)),
            "the fixture roost offset is found by the first due search");
        // Ceiling-first: the first sweep offsets sit at the top of the near column.
        assertEquals(4, HexBatRules.roostNearSweepOffset(0).dy());
        assertEquals(1, HexBatRules.roostNearSweepOffset(
            HexBatRules.ROOST_NEAR_SWEEP_CANDIDATES - 1).dy());
        // Rotating pages partition the complete envelope: full eventual coverage.
        final java.util.Set<List<Integer>> covered = new java.util.LinkedHashSet<>();
        for (int page = 0; page < HexBatRules.roostPageCount(); page++) {
            final int start = page * HexBatRules.ROOST_PAGE_CANDIDATES;
            final int end = Math.min(start + HexBatRules.ROOST_PAGE_CANDIDATES,
                HexBatRules.roostEnvelopeSize());
            for (int index = start; index < end; index++) {
                final HexBatRules.RoostOffset offset = HexBatRules.roostEnvelopeOffset(index);
                assertTrue(HexBatRules.withinAnchorEnvelope(offset.dx(), offset.dy(), offset.dz()));
                assertTrue(covered.add(List.of(offset.dx(), offset.dy(), offset.dz())),
                    "pages must partition the envelope without duplication");
            }
        }
        assertEquals(HexBatRules.roostEnvelopeSize(), covered.size(),
            "the union of every rotating page covers the whole envelope");
        // The page index is deterministic, staggered, and wraps every page count.
        final int firstPage = HexBatRules.roostPageIndex(0L, BAT_ID);
        assertEquals(firstPage, HexBatRules.roostPageIndex(0L, BAT_ID));
        assertEquals(firstPage, HexBatRules.roostPageIndex(
            (long) HexBatRules.roostPageCount() * HexBatRules.ROOST_SEARCH_INTERVAL_TICKS, BAT_ID));
        assertEquals(Math.floorMod(firstPage + 1, HexBatRules.roostPageCount()),
            HexBatRules.roostPageIndex(HexBatRules.ROOST_SEARCH_INTERVAL_TICKS, BAT_ID));
    }
}
