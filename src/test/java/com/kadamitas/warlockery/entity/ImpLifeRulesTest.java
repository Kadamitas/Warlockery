package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ImpLifeRules.Action;
import com.kadamitas.warlockery.entity.ImpLifeRules.Authority;
import com.kadamitas.warlockery.entity.ImpLifeRules.Duty;
import com.kadamitas.warlockery.entity.ImpLifeRules.InfernalOrder;
import com.kadamitas.warlockery.entity.ImpLifeRules.Observation;
import com.kadamitas.warlockery.entity.ImpLifeRules.ObservationType;
import com.kadamitas.warlockery.entity.ImpLifeRules.OrderAction;
import com.kadamitas.warlockery.entity.ImpLifeRules.OrderFacts;
import com.kadamitas.warlockery.entity.ImpLifeRules.OrderRank;
import com.kadamitas.warlockery.entity.ImpLifeRules.TargetFacts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class ImpLifeRulesTest {
    private static final UUID IMP_ID = new UUID(21L, 84L);
    private static final UUID PLAYER_A = new UUID(1L, 1L);
    private static final UUID PLAYER_B = new UUID(2L, 2L);
    private static final long NOW = 36_000L;

    @Test
    void approvedOperationBudgetsStayExact() {
        assertEquals(10, ImpLifeRules.COMBAT_DECISION_TICKS);
        assertEquals(20, ImpLifeRules.IDLE_DECISION_TICKS);
        assertEquals(20, ImpLifeRules.OWNER_RESOLUTION_TICKS);
        assertEquals(40, ImpLifeRules.TARGET_DISCOVERY_TICKS);
        assertEquals(24, ImpLifeRules.TARGET_DISCOVERY_RADIUS);
        assertEquals(12, ImpLifeRules.TARGET_RETENTION);
        assertEquals(200, ImpLifeRules.THREAT_EXPIRY_TICKS);
        assertEquals(40, ImpLifeRules.ATTRIBUTION_FRESHNESS_TICKS);
        assertEquals(200, ImpLifeRules.CURIOSITY_INTERVAL_TICKS);
        assertEquals(8, ImpLifeRules.CURIOSITY_HORIZONTAL_RADIUS);
        assertEquals(4, ImpLifeRules.CURIOSITY_VERTICAL_RADIUS);
        assertEquals(96, ImpLifeRules.CURIOSITY_READ_BUDGET);
        assertEquals(4, ImpLifeRules.MAX_OBSERVATIONS);
        assertEquals(1_200, ImpLifeRules.OBSERVATION_EXPIRY_TICKS);
        assertEquals(3, ImpLifeRules.SCOUT_LEGS);
        assertEquals(600, ImpLifeRules.SCOUT_TOTAL_TICKS);
        assertEquals(192, ImpLifeRules.SCOUT_TOTAL_READ_BUDGET);
        assertEquals(12, ImpLifeRules.SCOUT_OBSERVATION_RADIUS);
        assertEquals(4, ImpLifeRules.SCOUT_LINE_OF_SIGHT_CHECKS);
        assertEquals(8, ImpLifeRules.WAYPOINT_CANDIDATES);
        assertEquals(64, ImpLifeRules.WAYPOINT_READ_BUDGET);
        assertEquals(6, ImpLifeRules.LANE_CANDIDATES);
        assertEquals(64, ImpLifeRules.LANE_READ_BUDGET);
        assertEquals(3, ImpLifeRules.LANE_LINE_OF_SIGHT_CHECKS);
        assertEquals(20, ImpLifeRules.NAVIGATION_INTERVAL_TICKS);
        assertEquals(3, ImpLifeRules.MAX_ROUTE_FAILURES);
        assertEquals(100, ImpLifeRules.ROUTE_BACKOFF_TICKS);
        assertEquals(10, ImpLifeRules.WINDUP_TICKS);
        assertEquals(30, ImpLifeRules.SHOT_INTERVAL_TICKS);
        assertEquals(40, ImpLifeRules.MELEE_RECOVERY_TICKS);
        assertEquals(600, ImpLifeRules.ORDER_MAX_TICKS);
        assertEquals(20_000L, ImpLifeRules.MAX_FUTURE_HORIZON_TICKS);
    }

    @Test
    void effectiveAuthorityFollowsTheApprovedPrecedence() {
        assertEquals(Authority.CREATURE_OWNER, ImpLifeRules.effectiveAuthority(
            Optional.of(PLAYER_A), Optional.empty(), false, false));
        assertEquals(Authority.SAME_PLAYER_DUAL, ImpLifeRules.effectiveAuthority(
            Optional.of(PLAYER_A), Optional.of(PLAYER_A), false, false));
        assertEquals(Authority.CONFLICTED, ImpLifeRules.effectiveAuthority(
            Optional.of(PLAYER_A), Optional.of(PLAYER_B), false, false));
        assertEquals(Authority.INFERNAL_ONLY, ImpLifeRules.effectiveAuthority(
            Optional.empty(), Optional.of(PLAYER_B), false, false));
        assertEquals(Authority.REGENT_ORDER, ImpLifeRules.effectiveAuthority(
            Optional.empty(), Optional.empty(), true, true),
            "a valid Regent order outranks an Archfiend order");
        assertEquals(Authority.ARCHFIEND_ORDER, ImpLifeRules.effectiveAuthority(
            Optional.empty(), Optional.empty(), false, true));
        assertEquals(Authority.AUTONOMOUS, ImpLifeRules.effectiveAuthority(
            Optional.empty(), Optional.empty(), false, false));
    }

    @Test
    void conflictingAuthorityRefusesInfernalExecutionWithoutTransfer() {
        assertTrue(ImpLifeRules.commandAuthorityHolds(Authority.CREATURE_OWNER));
        assertTrue(ImpLifeRules.commandAuthorityHolds(Authority.SAME_PLAYER_DUAL));
        assertFalse(ImpLifeRules.commandAuthorityHolds(Authority.INFERNAL_ONLY));
        assertFalse(ImpLifeRules.commandAuthorityHolds(Authority.CONFLICTED));

        assertTrue(ImpLifeRules.infernalCommandRefused(Authority.CONFLICTED));
        assertFalse(ImpLifeRules.infernalCommandRefused(Authority.SAME_PLAYER_DUAL));
        assertFalse(ImpLifeRules.infernalCommandRefused(Authority.INFERNAL_ONLY));

        assertTrue(ImpLifeRules.infernalSacrificeAuthorized(Authority.INFERNAL_ONLY));
        assertTrue(ImpLifeRules.infernalSacrificeAuthorized(Authority.SAME_PLAYER_DUAL));
        assertFalse(ImpLifeRules.infernalSacrificeAuthorized(Authority.CONFLICTED),
            "a conflicting infernal key can never consume the creature owner's imp");
    }

    @Test
    void dutyTogglesBetweenFollowAndWatchWithFollowDefault() {
        assertEquals(Duty.FOLLOW, ImpLifeRules.defaultDuty());
        assertEquals(Duty.WATCH, ImpLifeRules.toggledDuty(Optional.of(Duty.FOLLOW)));
        assertEquals(Duty.FOLLOW, ImpLifeRules.toggledDuty(Optional.of(Duty.WATCH)));
        assertEquals(Duty.WATCH, ImpLifeRules.toggledDuty(Optional.empty()),
            "an implicit FOLLOW default toggles to WATCH");
    }

    @Test
    void scoutBeginsOnlyWithAValidLoadedOwnerAnchor() {
        assertTrue(ImpLifeRules.mayBeginScout(true, true, true));
        assertFalse(ImpLifeRules.mayBeginScout(false, true, true));
        assertFalse(ImpLifeRules.mayBeginScout(true, false, true));
        assertFalse(ImpLifeRules.mayBeginScout(true, true, false));
    }

    @Test
    void scoutLegsAreThreeBoundedRadiiWithDeterministicWaypoints() {
        assertEquals(8, ImpLifeRules.legRadius(0));
        assertEquals(12, ImpLifeRules.legRadius(1));
        assertEquals(16, ImpLifeRules.legRadius(2));
        final BlockPos anchor = new BlockPos(100, 70, -40);
        final List<BlockPos> waypoints = ImpLifeRules.legWaypoints(anchor, 1, IMP_ID);
        assertEquals(ImpLifeRules.WAYPOINT_CANDIDATES, waypoints.size());
        assertEquals(waypoints, ImpLifeRules.legWaypoints(anchor, 1, IMP_ID),
            "waypoints are deterministic per identity and leg");
        for (final BlockPos waypoint : waypoints) {
            final double horizontal = Math.sqrt(
                Math.pow(waypoint.getX() - anchor.getX(), 2.0)
                    + Math.pow(waypoint.getZ() - anchor.getZ(), 2.0));
            assertTrue(horizontal <= ImpLifeRules.legRadius(1) + 0.5,
                "every waypoint stays on or inside the leg radius");
            assertTrue(Math.abs(waypoint.getY() - anchor.getY()) <= ImpLifeRules.SCOUT_MAX_VERTICAL_OFFSET,
                "vertical offsets never exceed six blocks");
        }
    }

    @Test
    void scoutTimesOutAndReportsAtMostOnce() {
        assertFalse(ImpLifeRules.scoutTimedOut(NOW, NOW + ImpLifeRules.SCOUT_TOTAL_TICKS - 1L));
        assertTrue(ImpLifeRules.scoutTimedOut(NOW, NOW + ImpLifeRules.SCOUT_TOTAL_TICKS));
        assertTrue(ImpLifeRules.scoutBudgetExhausted(ImpLifeRules.SCOUT_TOTAL_READ_BUDGET));
        assertFalse(ImpLifeRules.scoutBudgetExhausted(ImpLifeRules.SCOUT_TOTAL_READ_BUDGET - 1));
        assertTrue(ImpLifeRules.mayDeliverReport(false, true, true));
        assertFalse(ImpLifeRules.mayDeliverReport(true, true, true),
            "a delivered report never repeats");
        assertFalse(ImpLifeRules.mayDeliverReport(false, false, true));
        assertFalse(ImpLifeRules.mayDeliverReport(false, true, false));
    }

    @Test
    void reportedHostileCountIsBoundedAndIgnoresExpiredRows() {
        final List<Observation> rows = List.of(
            observation(ObservationType.HOSTILE, new BlockPos(0, 64, 0), NOW + 100L),
            observation(ObservationType.HOSTILE, new BlockPos(5, 64, 0), NOW - 1L),
            observation(ObservationType.SHINY, new BlockPos(9, 64, 0), NOW + 100L)
        );
        assertEquals(1, ImpLifeRules.reportedHostileCount(rows, NOW));
    }

    @Test
    void observationLedgerDeduplicatesTruncatesAndPrioritizesDeterministically() {
        List<Observation> ledger = List.of();
        ledger = ImpLifeRules.recordObservation(ledger,
            observation(ObservationType.SHINY, new BlockPos(0, 64, 0), NOW + 1_200L), NOW);
        ledger = ImpLifeRules.recordObservation(ledger,
            observation(ObservationType.SHINY, new BlockPos(1, 64, 1), NOW + 1_200L), NOW);
        assertEquals(1, ledger.size(), "same-type stimuli within two blocks deduplicate");

        ledger = ImpLifeRules.recordObservation(ledger,
            observation(ObservationType.HEAT, new BlockPos(8, 64, 8), NOW + 1_200L), NOW);
        ledger = ImpLifeRules.recordObservation(ledger,
            observation(ObservationType.SHINY, new BlockPos(-8, 64, 0), NOW + 1_200L), NOW);
        ledger = ImpLifeRules.recordObservation(ledger,
            observation(ObservationType.SHINY, new BlockPos(0, 64, -8), NOW + 1_200L), NOW);
        ledger = ImpLifeRules.recordObservation(ledger,
            observation(ObservationType.HOSTILE, new BlockPos(12, 64, 12), NOW + 200L), NOW);
        assertEquals(ImpLifeRules.MAX_OBSERVATIONS, ledger.size(), "the ledger caps at four rows");
        assertTrue(ledger.stream().anyMatch(row -> row.type() == ObservationType.HOSTILE),
            "hostile evidence survives truncation by type priority");

        final List<Observation> expired = ImpLifeRules.recordObservation(
            ledger, observation(ObservationType.HEAT, new BlockPos(40, 64, 40), NOW + 1_200L),
            NOW + ImpLifeRules.OBSERVATION_EXPIRY_TICKS + 1_300L
        );
        assertEquals(1, expired.size(), "expired rows drop before new evidence is recorded");
    }

    @Test
    void archfiendAndRegentOrderCapsHold() {
        assertEquals(4, ImpLifeRules.subordinateCap(OrderRank.ARCHFIEND));
        assertEquals(2, ImpLifeRules.impSlotCap(OrderRank.ARCHFIEND));
        assertEquals(7, ImpLifeRules.subordinateCap(OrderRank.REGENT));
        assertEquals(2, ImpLifeRules.impSlotCap(OrderRank.REGENT));

        final OrderFacts eligible = new OrderFacts(true, true, false, true, true, false, false, 1, 0);
        assertTrue(ImpLifeRules.acceptsOrder(OrderRank.ARCHFIEND, eligible));
        assertTrue(ImpLifeRules.acceptsOrder(OrderRank.REGENT, eligible));

        assertFalse(ImpLifeRules.acceptsOrder(OrderRank.ARCHFIEND,
            new OrderFacts(true, true, true, true, true, false, false, 1, 0)),
            "a player-bound imp refuses every infernal order");
        assertFalse(ImpLifeRules.acceptsOrder(OrderRank.ARCHFIEND,
            new OrderFacts(true, true, false, false, true, false, false, 1, 0)));
        assertFalse(ImpLifeRules.acceptsOrder(OrderRank.ARCHFIEND,
            new OrderFacts(true, true, false, true, false, false, false, 1, 0)));
        assertFalse(ImpLifeRules.acceptsOrder(OrderRank.ARCHFIEND,
            new OrderFacts(true, true, false, true, true, true, false, 1, 0)),
            "orders are one hop and never delegated by an imp");
        assertFalse(ImpLifeRules.acceptsOrder(OrderRank.ARCHFIEND,
            new OrderFacts(true, true, false, true, true, false, true, 1, 0)),
            "a higher valid order epoch refuses replacement");
        assertFalse(ImpLifeRules.acceptsOrder(OrderRank.ARCHFIEND,
            new OrderFacts(true, true, false, true, true, false, false, 4, 0)),
            "the Archfiend squad cap of four total subordinates holds");
        assertFalse(ImpLifeRules.acceptsOrder(OrderRank.ARCHFIEND,
            new OrderFacts(true, true, false, true, true, false, false, 2, 2)),
            "at most two Archfiend slots may hold imps");
        assertFalse(ImpLifeRules.acceptsOrder(OrderRank.REGENT,
            new OrderFacts(true, true, false, true, true, false, false, 7, 0)),
            "the Regent court cap of seven total subordinates holds");
        assertFalse(ImpLifeRules.acceptsOrder(OrderRank.REGENT,
            new OrderFacts(true, true, false, true, true, false, false, 3, 2)));
    }

    @Test
    void ordersExpireWithinSixHundredTicksAndClearOnLeaderLoss() {
        final InfernalOrder order = new InfernalOrder(
            PLAYER_B, OrderRank.REGENT, new UUID(7L, 7L), 3L, OrderAction.WATCH,
            Optional.empty(), NOW, ImpLifeRules.orderExpiry(NOW, NOW + 50_000L)
        );
        assertEquals(NOW + ImpLifeRules.ORDER_MAX_TICKS, order.expiresAt(),
            "a hostile expiry clamps to six hundred ticks");
        assertTrue(order.valid(NOW + ImpLifeRules.ORDER_MAX_TICKS - 1L));
        assertFalse(order.valid(NOW + ImpLifeRules.ORDER_MAX_TICKS));

        assertTrue(ImpLifeRules.orderCleared(true, false, false, false, 0));
        assertTrue(ImpLifeRules.orderCleared(false, true, false, false, 0));
        assertTrue(ImpLifeRules.orderCleared(false, false, true, false, 0));
        assertTrue(ImpLifeRules.orderCleared(false, false, false, true, 0));
        assertTrue(ImpLifeRules.orderCleared(false, false, false, false, ImpLifeRules.MAX_ROUTE_FAILURES));
        assertFalse(ImpLifeRules.orderCleared(false, false, false, false, 2));
    }

    @Test
    void boundImpsNeverAcquireNeutralPlayers() {
        final TargetFacts neutral = new TargetFacts(
            true, true, true, true, false, false, false, false, false, false, false, false);
        assertFalse(ImpLifeRules.boundMayEngage(neutral));
        assertTrue(ImpLifeRules.boundMayEngage(withDirectAttacker(neutral)));
        assertTrue(ImpLifeRules.boundMayEngage(withOwnerAttacker(neutral)));
        assertTrue(ImpLifeRules.boundMayEngage(withCommand(neutral)));
        assertFalse(ImpLifeRules.boundMayEngage(new TargetFacts(
            true, true, true, true, false, true, false, false, false, true, false, false)),
            "the owner is never attackable even with attribution");
        assertFalse(ImpLifeRules.boundMayEngage(new TargetFacts(
            true, true, true, true, false, false, true, false, false, true, false, false)),
            "same-authority allies are protected");
        assertFalse(ImpLifeRules.boundMayEngage(new TargetFacts(
            true, true, true, true, true, false, false, false, false, true, false, false)),
            "creative and spectator players are excluded");
        assertFalse(ImpLifeRules.boundMayEngage(new TargetFacts(
            false, true, true, true, false, false, false, false, false, true, false, false)));
    }

    @Test
    void unboundAcquisitionIsBoundedAndExcludesProtectedIdentities() {
        final TargetFacts eligible = new TargetFacts(
            true, true, true, true, false, false, false, false, false, false, false, false);
        assertTrue(ImpLifeRules.unboundMayAcquire(eligible, 24.0));
        assertFalse(ImpLifeRules.unboundMayAcquire(eligible, 24.5),
            "acquisition stops at twenty-four blocks");
        assertFalse(ImpLifeRules.unboundMayAcquire(new TargetFacts(
            true, true, true, true, true, false, false, false, false, false, false, false), 8.0));
        assertFalse(ImpLifeRules.unboundMayAcquire(new TargetFacts(
            true, true, true, true, false, false, false, true, false, false, false, false), 8.0),
            "accepted local-group allies are not prey");
        assertFalse(ImpLifeRules.unboundMayAcquire(new TargetFacts(
            true, true, true, true, false, false, false, false, true, false, false, false), 8.0),
            "owls and steeds are not identity prey");
        assertFalse(ImpLifeRules.unboundMayAcquire(new TargetFacts(
            true, false, true, true, false, false, false, false, false, false, false, false), 8.0));
    }

    @Test
    void attributionFreshnessBoundsHold() {
        assertTrue(ImpLifeRules.attributionFresh(NOW - ImpLifeRules.ATTRIBUTION_FRESHNESS_TICKS, NOW));
        assertFalse(ImpLifeRules.attributionFresh(NOW - ImpLifeRules.ATTRIBUTION_FRESHNESS_TICKS - 1L, NOW));
        assertTrue(ImpLifeRules.ownerThreatFresh(NOW - ImpLifeRules.OWNER_THREAT_FRESHNESS_TICKS, NOW));
        assertFalse(ImpLifeRules.ownerThreatFresh(NOW - ImpLifeRules.OWNER_THREAT_FRESHNESS_TICKS - 1L, NOW));
    }

    @Test
    void rangedBandsWindupAndCadenceAreExact() {
        assertTrue(ImpLifeRules.tooClose(4.9));
        assertFalse(ImpLifeRules.tooClose(5.0));
        assertTrue(ImpLifeRules.withinPreferredBand(8.0));
        assertTrue(ImpLifeRules.withinPreferredBand(12.0));
        assertFalse(ImpLifeRules.withinPreferredBand(12.1));
        assertTrue(ImpLifeRules.tooFar(16.1));
        assertFalse(ImpLifeRules.tooFar(16.0));

        assertFalse(ImpLifeRules.windupComplete(NOW, NOW + ImpLifeRules.WINDUP_TICKS - 1L));
        assertTrue(ImpLifeRules.windupComplete(NOW, NOW + ImpLifeRules.WINDUP_TICKS));
        assertTrue(ImpLifeRules.shotReady(0L, NOW), "a zero sentinel reads as due");
        assertFalse(ImpLifeRules.shotReady(NOW, NOW + ImpLifeRules.SHOT_INTERVAL_TICKS - 1L));
        assertTrue(ImpLifeRules.shotReady(NOW, NOW + ImpLifeRules.SHOT_INTERVAL_TICKS));
    }

    @Test
    void corneredMeleeIsReachableOnlyAfterAFailedDisengage() {
        assertTrue(ImpLifeRules.mayCloseEscape(2.9, true, false, 0L, NOW));
        assertFalse(ImpLifeRules.mayCloseEscape(3.1, true, false, 0L, NOW));
        assertFalse(ImpLifeRules.mayCloseEscape(2.9, false, false, 0L, NOW),
            "close melee needs at least one failed safe disengage first");
        assertFalse(ImpLifeRules.mayCloseEscape(2.9, true, true, 0L, NOW));
        assertFalse(ImpLifeRules.mayCloseEscape(2.9, true, false, NOW + 1L, NOW),
            "melee recovery forbids repeated close attacks");
    }

    @Test
    void retreatLatchesAtQuarterHealthAndReleasesAtNearlyHalf() {
        assertTrue(ImpLifeRules.retreatLatches(0.25F, 0));
        assertFalse(ImpLifeRules.retreatLatches(0.26F, 0));
        assertTrue(ImpLifeRules.retreatLatches(1.0F, ImpLifeRules.MAX_ROUTE_FAILURES));
        assertTrue(ImpLifeRules.retreatReleases(0.45F, true, true));
        assertFalse(ImpLifeRules.retreatReleases(0.44F, true, true));
        assertFalse(ImpLifeRules.retreatReleases(0.45F, false, true));
        assertFalse(ImpLifeRules.retreatReleases(0.45F, true, false),
            "release requires a safe lane");
    }

    @Test
    void routeFailuresClearAfterThreeAndBackOffForAtLeastOneHundredTicks() {
        assertEquals(1, ImpLifeRules.nextRouteFailures(0));
        assertEquals(3, ImpLifeRules.nextRouteFailures(2));
        assertEquals(3, ImpLifeRules.nextRouteFailures(9));
        assertEquals(0L, ImpLifeRules.routeBackoffUntil(2, NOW));
        assertEquals(NOW + ImpLifeRules.ROUTE_BACKOFF_TICKS,
            ImpLifeRules.routeBackoffUntil(3, NOW));
    }

    @Test
    void curiosityYieldsToSafetyDutyAndOrders() {
        assertTrue(ImpLifeRules.curiosityAllowed(false, false, false, false, 0L, 0L, NOW));
        assertFalse(ImpLifeRules.curiosityAllowed(true, false, false, false, 0L, 0L, NOW));
        assertFalse(ImpLifeRules.curiosityAllowed(false, true, false, false, 0L, 0L, NOW));
        assertFalse(ImpLifeRules.curiosityAllowed(false, false, true, false, 0L, 0L, NOW));
        assertFalse(ImpLifeRules.curiosityAllowed(false, false, false, true, 0L, 0L, NOW));
        assertFalse(ImpLifeRules.curiosityAllowed(false, false, false, false, NOW + 1L, 0L, NOW));
        assertFalse(ImpLifeRules.curiosityAllowed(false, false, false, false, 0L, NOW + 1L, NOW),
            "discovery runs no faster than its two-hundred-tick cadence");
    }

    @Test
    void cadenceSentinelsAreZeroDueStaggeredAndClamped() {
        assertTrue(ImpLifeRules.due(0L, NOW));
        assertTrue(ImpLifeRules.due(-5L, NOW));
        assertTrue(ImpLifeRules.due(NOW, NOW));
        assertFalse(ImpLifeRules.due(NOW + 1L, NOW));

        final int offset = ImpLifeRules.stableOffset(IMP_ID, 20);
        assertTrue(offset >= 0 && offset < 20);
        assertEquals(offset, ImpLifeRules.stableOffset(IMP_ID, 20), "staggering is identity-stable");

        assertEquals(Long.MAX_VALUE, ImpLifeRules.saturatingAdd(Long.MAX_VALUE - 1L, 100L));
        assertEquals(NOW + ImpLifeRules.MAX_FUTURE_HORIZON_TICKS,
            ImpLifeRules.clampDeadline(Long.MAX_VALUE - 8L, NOW, Long.MAX_VALUE / 2L),
            "no persisted deadline may exceed the twenty-thousand-tick horizon");
        assertEquals(0L, ImpLifeRules.clampDeadline(0L, NOW, 100L));
        assertEquals(ImpLifeRules.IDLE_DECISION_TICKS, ImpLifeRules.decisionInterval(false));
        assertEquals(ImpLifeRules.COMBAT_DECISION_TICKS, ImpLifeRules.decisionInterval(true));
    }

    @Test
    void boundImmunityCoversOnlyTheFourSafeCategoriesAndNeverTransfers() {
        assertTrue(ImpLifeRules.ignoresWhileBound(true, true, false, false, false));
        assertTrue(ImpLifeRules.ignoresWhileBound(true, false, true, false, false));
        assertTrue(ImpLifeRules.ignoresWhileBound(true, false, false, true, false));
        assertTrue(ImpLifeRules.ignoresWhileBound(true, false, false, false, true));
        assertFalse(ImpLifeRules.ignoresWhileBound(true, false, false, false, false),
            "attacker, projectile, magic, holy, freezing, ward, and contact damage stay effective");
        assertFalse(ImpLifeRules.ignoresWhileBound(false, true, true, true, true),
            "unbound imps gain no new immunity beyond inherent fire immunity");
        assertFalse(ImpLifeRules.familiarDamageTransfers(),
            "the imp stays excluded from classic familiar damage transfer");
    }

    @Test
    void followBandsAndWatchEnvelopeAreExact() {
        assertEquals(8.0, ImpLifeRules.FOLLOW_ORBIT_DISTANCE);
        assertEquals(24.0, ImpLifeRules.FOLLOW_PATH_DISTANCE);
        assertEquals(4, ImpLifeRules.FOLLOW_RECOVERY_RADIUS);
        assertEquals(3.0, ImpLifeRules.WATCH_ENVELOPE_MIN);
        assertEquals(6.0, ImpLifeRules.WATCH_ENVELOPE_MAX);
        assertEquals(20, ImpLifeRules.OWNER_AURA_INTERVAL_TICKS);
        assertEquals(60, ImpLifeRules.OWNER_AURA_DURATION_TICKS);
    }

    @Test
    void nextActionEpochOnlyMovesForward() {
        assertEquals(1L, ImpLifeRules.nextEpoch(0L));
        assertEquals(Long.MAX_VALUE, ImpLifeRules.nextEpoch(Long.MAX_VALUE));
    }

    @Test
    void transientActionsNeverResumeFromDisk() {
        assertTrue(ImpLifeRules.actionResumableAfterLoad(Action.NONE));
        assertTrue(ImpLifeRules.actionResumableAfterLoad(Action.FOLLOW));
        assertTrue(ImpLifeRules.actionResumableAfterLoad(Action.WATCH));
        assertTrue(ImpLifeRules.actionResumableAfterLoad(Action.SCOUT_OUT));
        assertTrue(ImpLifeRules.actionResumableAfterLoad(Action.SCOUT_RETURN));
        assertFalse(ImpLifeRules.actionResumableAfterLoad(Action.RANGED_WINDUP));
        assertFalse(ImpLifeRules.actionResumableAfterLoad(Action.CLOSE_ESCAPE));
        assertFalse(ImpLifeRules.actionResumableAfterLoad(Action.DISENGAGE));
        assertFalse(ImpLifeRules.actionResumableAfterLoad(Action.HAZARD_ESCAPE));
        assertFalse(ImpLifeRules.actionResumableAfterLoad(Action.INSPECT));
        assertFalse(ImpLifeRules.actionResumableAfterLoad(Action.PERCH));
        assertFalse(ImpLifeRules.actionResumableAfterLoad(Action.NPC_ORDER));
    }

    private static Observation observation(final ObservationType type, final BlockPos pos, final long expiresAt) {
        return new Observation(type, pos.asLong(), Optional.empty(), NOW, NOW, 1_000, expiresAt);
    }

    private static TargetFacts withDirectAttacker(final TargetFacts facts) {
        return new TargetFacts(facts.alive(), facts.sameDimension(), facts.loaded(), facts.withinRange(),
            facts.creativeOrSpectator(), facts.owner(), facts.sameAuthorityAlly(), facts.acceptedGroupAlly(),
            facts.protectedIdentity(), true, facts.recentOwnerAttacker(), facts.commandedTarget());
    }

    private static TargetFacts withOwnerAttacker(final TargetFacts facts) {
        return new TargetFacts(facts.alive(), facts.sameDimension(), facts.loaded(), facts.withinRange(),
            facts.creativeOrSpectator(), facts.owner(), facts.sameAuthorityAlly(), facts.acceptedGroupAlly(),
            facts.protectedIdentity(), facts.recentDirectAttacker(), true, facts.commandedTarget());
    }

    private static TargetFacts withCommand(final TargetFacts facts) {
        return new TargetFacts(facts.alive(), facts.sameDimension(), facts.loaded(), facts.withinRange(),
            facts.creativeOrSpectator(), facts.owner(), facts.sameAuthorityAlly(), facts.acceptedGroupAlly(),
            facts.protectedIdentity(), facts.recentDirectAttacker(), facts.recentOwnerAttacker(), true);
    }
}
