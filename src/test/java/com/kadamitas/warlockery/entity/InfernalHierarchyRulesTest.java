package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.MemberCandidate;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.OrderKind;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.Rank;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class InfernalHierarchyRulesTest {
    @Test
    void targetEngagementFollowsTheDesignedIntentBoundary() {
        for (final InfernalHierarchyRules.Intent engaged : List.of(
            InfernalHierarchyRules.Intent.PRESS, InfernalHierarchyRules.Intent.INTERCEPT,
            InfernalHierarchyRules.Intent.PACT_GUARD, InfernalHierarchyRules.Intent.ORDERED_GUARD,
            InfernalHierarchyRules.Intent.FOCUS, InfernalHierarchyRules.Intent.EMBER_FRONT,
            InfernalHierarchyRules.Intent.COMMAND, InfernalHierarchyRules.Intent.SCREEN,
            InfernalHierarchyRules.Intent.DISPLACE, InfernalHierarchyRules.Intent.FEAR_PULSE,
            InfernalHierarchyRules.Intent.APPRAISE
        )) {
            assertTrue(InfernalHierarchyRules.engagesTarget(engaged),
                "an engaged combat intent may hold a live target claim: " + engaged);
        }
        for (final InfernalHierarchyRules.Intent restrained : List.of(
            InfernalHierarchyRules.Intent.WARN, InfernalHierarchyRules.Intent.TRUCE,
            InfernalHierarchyRules.Intent.RETREAT, InfernalHierarchyRules.Intent.RETURN,
            InfernalHierarchyRules.Intent.WITHDRAW, InfernalHierarchyRules.Intent.DISSOLVE,
            InfernalHierarchyRules.Intent.IDLE, InfernalHierarchyRules.Intent.POST_WATCH,
            InfernalHierarchyRules.Intent.PACT_FOLLOW, InfernalHierarchyRules.Intent.PHASE_TELEGRAPH
        )) {
            assertFalse(InfernalHierarchyRules.engagesTarget(restrained),
                "a warning, truce, retreat, or routine intent never acquires a target: " + restrained);
        }
    }

    @Test
    void exactRanksMapOnlyTheThreeHierarchyKinds() {
        assertEquals(Rank.DEMON, InfernalHierarchyRules.rankOf(CreatureKind.DEMON).orElseThrow());
        assertEquals(Rank.EMBERHORN_ARCHFIEND,
            InfernalHierarchyRules.rankOf(CreatureKind.EMBERHORN_ARCHFIEND).orElseThrow());
        assertEquals(Rank.ABYSSAL_REGENT,
            InfernalHierarchyRules.rankOf(CreatureKind.ABYSSAL_REGENT).orElseThrow());
        assertTrue(InfernalHierarchyRules.rankOf(CreatureKind.IMP).isEmpty());
        assertTrue(InfernalHierarchyRules.rankOf(CreatureKind.HELLHOUND).isEmpty());
        assertTrue(InfernalHierarchyRules.rankOf(CreatureKind.NAAMAH).isEmpty());
    }

    @Test
    void approvedCadenceAndBudgetConstantsHold() {
        assertEquals(20, InfernalHierarchyRules.DECISION_INTERVAL_TICKS);
        assertEquals(400L, InfernalHierarchyRules.MEMBERSHIP_LEASE_TICKS);
        assertEquals(200L, InfernalHierarchyRules.ARCHFIEND_ORDER_TICKS);
        assertEquals(300L, InfernalHierarchyRules.REGENT_ORDER_TICKS);
        assertEquals(4, InfernalHierarchyRules.SQUAD_MEMBER_CAP);
        assertEquals(7, InfernalHierarchyRules.COURT_MEMBER_CAP);
        assertEquals(1, InfernalHierarchyRules.COURT_ARCHFIEND_CAP);
        assertEquals(80, InfernalHierarchyRules.ARCHFIEND_GROUP_REFRESH_TICKS);
        assertEquals(100, InfernalHierarchyRules.REGENT_GROUP_REFRESH_TICKS);
        assertEquals(24, InfernalHierarchyRules.REGENT_RETAINED_CANDIDATES);
        assertEquals(2_048, InfernalHierarchyRules.MAX_ANCHOR_BLOCK_READS_TOTAL);
        assertEquals(20_000L, InfernalHierarchyRules.MAX_FUTURE_HORIZON_TICKS);
        assertEquals(2, InfernalHierarchyRules.PHASE_SUMMON_CAP);
        assertEquals(1_200L, InfernalHierarchyRules.SUMMON_LIFE_TICKS);
        assertTrue(InfernalHierarchyRules.DEMON_OBSERVATION_INTERVAL_TICKS >= 40);
        assertTrue(InfernalHierarchyRules.REGENT_OBSERVATION_INTERVAL_TICKS >= 40);
        assertTrue(InfernalHierarchyRules.NAVIGATION_INTERVAL_TICKS >= 10);
    }

    @Test
    void authorityPrecedenceFollowsTheApprovedOrder() {
        assertEquals(InfernalHierarchyRules.AuthorityClass.HAZARD,
            InfernalHierarchyRules.resolveAuthority(true, true, true, true, true, true, true));
        assertEquals(InfernalHierarchyRules.AuthorityClass.DIRECT_PACT,
            InfernalHierarchyRules.resolveAuthority(false, true, true, true, true, true, true));
        assertEquals(InfernalHierarchyRules.AuthorityClass.ANIMUS,
            InfernalHierarchyRules.resolveAuthority(false, false, true, true, true, true, true));
        assertEquals(InfernalHierarchyRules.AuthorityClass.AUTHORED_OBJECTIVE,
            InfernalHierarchyRules.resolveAuthority(false, false, false, true, true, true, true));
        assertEquals(InfernalHierarchyRules.AuthorityClass.REGENT_ORDER,
            InfernalHierarchyRules.resolveAuthority(false, false, false, false, true, true, true));
        assertEquals(InfernalHierarchyRules.AuthorityClass.ARCHFIEND_ORDER,
            InfernalHierarchyRules.resolveAuthority(false, false, false, false, false, true, true));
        assertEquals(InfernalHierarchyRules.AuthorityClass.SELF_DEFENSE,
            InfernalHierarchyRules.resolveAuthority(false, false, false, false, false, false, true));
        assertEquals(InfernalHierarchyRules.AuthorityClass.AUTONOMY,
            InfernalHierarchyRules.resolveAuthority(false, false, false, false, false, false, false));
    }

    @Test
    void onlyDemonAcceptsPlayerAuthorityAndAnimusNeverSteals() {
        assertTrue(InfernalHierarchyRules.acceptsPlayerAuthority(Rank.DEMON));
        assertFalse(InfernalHierarchyRules.acceptsPlayerAuthority(Rank.EMBERHORN_ARCHFIEND));
        assertFalse(InfernalHierarchyRules.acceptsPlayerAuthority(Rank.ABYSSAL_REGENT));
        assertFalse(InfernalHierarchyRules.animusMaySteal());
    }

    @Test
    void directPactOwnerWinsOwnerConflicts() {
        final UUID direct = UUID.randomUUID();
        final UUID animus = UUID.randomUUID();
        assertEquals(direct, InfernalHierarchyRules.effectiveOwner(
            java.util.Optional.of(direct), java.util.Optional.of(animus)).orElseThrow());
        assertEquals(animus, InfernalHierarchyRules.effectiveOwner(
            java.util.Optional.empty(), java.util.Optional.of(animus)).orElseThrow());
        assertTrue(InfernalHierarchyRules.commandAccepted(
            direct, java.util.Optional.of(direct), java.util.Optional.of(animus)));
        assertFalse(InfernalHierarchyRules.commandAccepted(
            animus, java.util.Optional.of(direct), java.util.Optional.of(animus)));
        assertTrue(InfernalHierarchyRules.commandAccepted(
            animus, java.util.Optional.empty(), java.util.Optional.of(animus)));
    }

    @Test
    void orderIssuerRecipientMatrixIsExact() {
        for (final OrderKind kind : OrderKind.values()) {
            assertFalse(InfernalHierarchyRules.mayIssueOrder(Rank.DEMON, Rank.DEMON, kind));
        }
        assertTrue(InfernalHierarchyRules.mayIssueOrder(
            Rank.EMBERHORN_ARCHFIEND, Rank.DEMON, OrderKind.FOCUS_CHALLENGER));
        assertFalse(InfernalHierarchyRules.mayIssueOrder(
            Rank.EMBERHORN_ARCHFIEND, Rank.EMBERHORN_ARCHFIEND, OrderKind.FOCUS_CHALLENGER));
        assertFalse(InfernalHierarchyRules.mayIssueOrder(
            Rank.EMBERHORN_ARCHFIEND, Rank.DEMON, OrderKind.HOLD_COURT));
        assertTrue(InfernalHierarchyRules.mayIssueOrder(
            Rank.ABYSSAL_REGENT, Rank.DEMON, OrderKind.HOLD_COURT));
        assertTrue(InfernalHierarchyRules.mayIssueOrder(
            Rank.ABYSSAL_REGENT, Rank.EMBERHORN_ARCHFIEND, OrderKind.WITHDRAW_TO_ANCHOR));
        assertFalse(InfernalHierarchyRules.mayIssueOrder(
            Rank.ABYSSAL_REGENT, Rank.ABYSSAL_REGENT, OrderKind.HOLD_COURT));
        assertFalse(InfernalHierarchyRules.mayIssueOrder(
            Rank.ABYSSAL_REGENT, Rank.DEMON, OrderKind.HOLD_POST));
        assertFalse(InfernalHierarchyRules.recursiveOrderCopyingAllowed());
        assertEquals(200L, InfernalHierarchyRules.orderLifetimeTicks(Rank.EMBERHORN_ARCHFIEND));
        assertEquals(300L, InfernalHierarchyRules.orderLifetimeTicks(Rank.ABYSSAL_REGENT));
    }

    @Test
    void rosterRetentionKeepsCurrentMembersFirstAndRespectsCaps() {
        final UUID current = new UUID(0L, 1L);
        final List<MemberCandidate> candidates = new java.util.ArrayList<>();
        candidates.add(new MemberCandidate(current, Rank.DEMON, true, 900.0D, false, false, true, true));
        for (int index = 0; index < 10; index++) {
            candidates.add(new MemberCandidate(
                new UUID(1L, index), Rank.DEMON, false, index, false, false, true, true));
        }
        final List<MemberCandidate> squad = InfernalHierarchyRules.retainRoster(
            Rank.EMBERHORN_ARCHFIEND, candidates);
        assertEquals(InfernalHierarchyRules.SQUAD_MEMBER_CAP, squad.size());
        assertEquals(current, squad.get(0).id(), "the distant current member is preseeded before generics");
        final List<MemberCandidate> court = InfernalHierarchyRules.retainRoster(
            Rank.ABYSSAL_REGENT, candidates);
        assertEquals(InfernalHierarchyRules.COURT_MEMBER_CAP, court.size());
    }

    @Test
    void courtRetainsAtMostOneArchfiendAndRejectsBoundOrLeasedMembers() {
        final List<MemberCandidate> candidates = List.of(
            new MemberCandidate(new UUID(2L, 1L), Rank.EMBERHORN_ARCHFIEND, false, 1.0D, false, false, true, true),
            new MemberCandidate(new UUID(2L, 2L), Rank.EMBERHORN_ARCHFIEND, false, 2.0D, false, false, true, true),
            new MemberCandidate(new UUID(2L, 3L), Rank.DEMON, false, 3.0D, true, false, true, true),
            new MemberCandidate(new UUID(2L, 4L), Rank.DEMON, false, 4.0D, false, true, true, true),
            new MemberCandidate(new UUID(2L, 5L), Rank.DEMON, false, 5.0D, false, false, false, true),
            new MemberCandidate(new UUID(2L, 6L), Rank.DEMON, false, 6.0D, false, false, true, true),
            new MemberCandidate(new UUID(2L, 7L), Rank.ABYSSAL_REGENT, false, 0.5D, false, false, true, true)
        );
        final List<MemberCandidate> court = InfernalHierarchyRules.retainRoster(Rank.ABYSSAL_REGENT, candidates);
        assertEquals(2, court.size(), "one archfiend and one eligible demon remain");
        assertEquals(1L, court.stream().filter(member -> member.rank() == Rank.EMBERHORN_ARCHFIEND).count());
        assertTrue(court.stream().noneMatch(member -> member.rank() == Rank.ABYSSAL_REGENT));
        assertTrue(InfernalHierarchyRules.retainRoster(Rank.EMBERHORN_ARCHFIEND, candidates).stream()
            .allMatch(member -> member.rank() == Rank.DEMON));
    }

    @Test
    void moraleEventsClampRecoverAndGateRetreat() {
        assertEquals(200, InfernalHierarchyRules.damageMoralePenalty(1_000.0F, 60.0F));
        assertEquals(50, InfernalHierarchyRules.damageMoralePenalty(10.0F, 60.0F));
        assertEquals(0, InfernalHierarchyRules.clampMorale(-50));
        assertEquals(1_000, InfernalHierarchyRules.clampMorale(9_999));
        assertEquals(650, InfernalHierarchyRules.recoveredMorale(650, 0L, 100_000L));
        assertEquals(660, InfernalHierarchyRules.recoveredMorale(660, 0L, 100_000L),
            "recovery never pulls morale above its own value toward the baseline");
        assertEquals(610, InfernalHierarchyRules.recoveredMorale(600, 0L, 400L));
        assertTrue(InfernalHierarchyRules.moraleRetreatRequired(299, 1.0F));
        assertFalse(InfernalHierarchyRules.moraleRetreatRequired(300, 1.0F));
        assertTrue(InfernalHierarchyRules.moraleRetreatRequired(1_000, 0.2F));
        assertFalse(InfernalHierarchyRules.mayReenterPressure(499, false, false));
        assertTrue(InfernalHierarchyRules.mayReenterPressure(500, false, false));
        assertTrue(InfernalHierarchyRules.mayReenterPressure(0, true, false));
        assertTrue(InfernalHierarchyRules.mayReenterPressure(0, false, true));
        assertTrue(InfernalHierarchyRules.allyLossPenaltyDue(0L, 10L));
        assertFalse(InfernalHierarchyRules.allyLossPenaltyDue(100L, 120L));
        assertFalse(InfernalHierarchyRules.rallyRewardDue(100L, 250L));
        assertTrue(InfernalHierarchyRules.rallyRewardDue(100L, 300L));
    }

    @Test
    void truceLifecycleIsBoundedAndBreachable() {
        assertTrue(InfernalHierarchyRules.mayFormTruce(true, false, 0L, 100L));
        assertFalse(InfernalHierarchyRules.mayFormTruce(false, false, 0L, 100L));
        assertFalse(InfernalHierarchyRules.mayFormTruce(true, true, 0L, 100L));
        assertFalse(InfernalHierarchyRules.mayFormTruce(true, false, 200L, 100L));
        assertTrue(InfernalHierarchyRules.truceRefreshDue(0L, 5L), "zero sentinels read as due");
        assertFalse(InfernalHierarchyRules.truceRefreshDue(100L, 110L));
        assertTrue(InfernalHierarchyRules.truceRefreshDue(100L, 120L));
        assertEquals(700L, InfernalHierarchyRules.truceBreachUntil(100L));
        assertTrue(InfernalHierarchyRules.truceValid(200L, true, true, 100L));
        assertFalse(InfernalHierarchyRules.truceValid(200L, true, true, 200L));
        assertFalse(InfernalHierarchyRules.truceValid(200L, false, true, 100L));
        assertFalse(InfernalHierarchyRules.truceValid(200L, true, false, 100L));
    }

    @Test
    void summonTransactionRequiresCapacitySafetyAndOneGroupPerLifetime() {
        assertTrue(InfernalHierarchyRules.summonTransactionAllowed(5, false, 2, false));
        assertFalse(InfernalHierarchyRules.summonTransactionAllowed(6, false, 2, false));
        assertFalse(InfernalHierarchyRules.summonTransactionAllowed(5, true, 2, false));
        assertFalse(InfernalHierarchyRules.summonTransactionAllowed(5, false, 1, false));
        assertFalse(InfernalHierarchyRules.summonTransactionAllowed(0, false, 2, true));
    }

    @Test
    void deadlinesClampRouteFailuresBackOffAndZeroSentinelsAreDue() {
        assertTrue(InfernalHierarchyRules.due(0L, 5L));
        assertTrue(InfernalHierarchyRules.due(5L, 5L));
        assertFalse(InfernalHierarchyRules.due(6L, 5L));
        assertEquals(0L, InfernalHierarchyRules.clampDeadline(0L, 100L, 400L));
        assertEquals(500L, InfernalHierarchyRules.clampDeadline(Long.MAX_VALUE, 100L, 400L));
        assertEquals(20_100L, InfernalHierarchyRules.clampDeadline(Long.MAX_VALUE, 100L, Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, InfernalHierarchyRules.saturatingAdd(Long.MAX_VALUE - 1L, 100L));
        assertEquals(1, InfernalHierarchyRules.routeFailures(0));
        assertEquals(3, InfernalHierarchyRules.routeFailures(9));
        assertEquals(0L, InfernalHierarchyRules.routeBackoffUntil(2, 100L));
        assertEquals(200L, InfernalHierarchyRules.routeBackoffUntil(3, 100L));
        final UUID id = UUID.randomUUID();
        assertTrue(InfernalHierarchyRules.stableOffset(id, 20) >= 0);
        assertTrue(InfernalHierarchyRules.stableOffset(id, 20) < 20);
    }

    @Test
    void emberFrontAndAnchorClaimsUseTheApprovedBounds() {
        assertEquals(20, InfernalHierarchyRules.EMBER_FRONT_TELEGRAPH_TICKS);
        assertEquals(120, InfernalHierarchyRules.EMBER_FRONT_SPACING_TICKS);
        assertEquals(40, InfernalHierarchyRules.EMBER_FRONT_RECOVERY_TICKS);
        assertEquals(8, InfernalHierarchyRules.EMBER_FRONT_TARGET_CAP);
        assertEquals(8, InfernalHierarchyRules.EMBER_FRONT_RANGE);
        assertEquals(6.0F, InfernalHierarchyRules.EMBER_FRONT_DAMAGE);
        assertEquals(4.0F, InfernalHierarchyRules.EMBER_FRONT_FIRE_SECONDS);
        assertEquals(400L, InfernalHierarchyRules.ANCHOR_CLAIM_TICKS);
        assertTrue(InfernalHierarchyRules.EMBER_FRONT_SPACING_TICKS
            >= InfernalHierarchyRules.EMBER_FRONT_RECOVERY_TICKS,
            "the spacing window subsumes the recovery window");
    }

    @Test
    void everyDeclaredIntentIsReachableThroughRankSelection() {
        final java.util.EnumSet<InfernalHierarchyRules.Intent> reachable =
            java.util.EnumSet.noneOf(InfernalHierarchyRules.Intent.class);
        DEMON_SELECTION_MATRIX.forEach((facts, expected) -> {
            assertEquals(expected, InfernalHierarchyRules.selectDemonIntent(facts));
            reachable.add(expected);
        });
        ARCHFIEND_SELECTION_MATRIX.forEach((facts, expected) -> {
            assertEquals(expected, InfernalHierarchyRules.selectArchfiendIntent(facts));
            reachable.add(expected);
        });
        REGENT_SELECTION_MATRIX.forEach((facts, expected) -> {
            assertEquals(expected, InfernalHierarchyRules.selectRegentIntent(facts));
            reachable.add(expected);
        });
        assertEquals(java.util.EnumSet.allOf(InfernalHierarchyRules.Intent.class), reachable,
            "every declared intent must be reachable from live rank selection, with no dead enum arm");
    }

    @Test
    void hazardOutranksCombatAndCombatOutranksRoutineForEveryRank() {
        assertEquals(InfernalHierarchyRules.Intent.RETREAT, InfernalHierarchyRules.selectDemonIntent(
            demon(true, true, true, true, true, true, true, true, true, true, true, true, true, true, true)),
            "a Demon hazard outranks truce, pact, order, and territory");
        assertEquals(InfernalHierarchyRules.Intent.WITHDRAW, InfernalHierarchyRules.selectArchfiendIntent(
            archfiend(true, true, true, true, true, true, true, true, true, true, true)),
            "an Archfiend hazard outranks rally, focus, and the ember front");
        assertEquals(InfernalHierarchyRules.Intent.RETURN, InfernalHierarchyRules.selectRegentIntent(
            regent(true, true, true, true, true, true, true, true, true, true, true, true)),
            "a Regent hazard outranks even the half-health phase window");
        assertEquals(InfernalHierarchyRules.Intent.PRESS, InfernalHierarchyRules.selectDemonIntent(
            demon(false, false, false, true, false, false, false, false, false, false,
                true, false, false, true, true)),
            "combat outranks the routine post schedule");
        assertEquals(InfernalHierarchyRules.Intent.FEAR_PULSE, InfernalHierarchyRules.selectRegentIntent(
            regent(false, false, false, false, false, false, true, true, true, true, true, true)),
            "a Regent combat action outranks command and the routine court schedule");
    }

    @Test
    void moraleHysteresisGatesTheRetreatIntentRatherThanBeingWriteOnly() {
        assertEquals(InfernalHierarchyRules.Intent.RETREAT, InfernalHierarchyRules.selectDemonIntent(
            demon(false, false, true, false, false, false, false, false, false, false,
                true, false, false, false, true)),
            "broken morale retreats instead of pressing");
        assertEquals(InfernalHierarchyRules.Intent.PRESS, InfernalHierarchyRules.selectDemonIntent(
            demon(false, false, true, true, false, false, false, false, false, false,
                true, false, false, false, true)),
            "hysteresis releases only once re-entry is allowed");
        assertEquals(InfernalHierarchyRules.Intent.RETREAT, InfernalHierarchyRules.selectDemonIntent(
            demon(false, true, false, true, false, false, false, false, false, false,
                true, false, false, false, true)),
            "at or below twenty percent health retreat outranks morale");
    }

    @Test
    void cancellationCoversEveryDesignedReleaseIntent() {
        for (final InfernalHierarchyRules.Intent intent : List.of(
            InfernalHierarchyRules.Intent.RETREAT, InfernalHierarchyRules.Intent.RETURN,
            InfernalHierarchyRules.Intent.WITHDRAW, InfernalHierarchyRules.Intent.DISSOLVE,
            InfernalHierarchyRules.Intent.TRUCE
        )) {
            assertTrue(InfernalHierarchyRules.cancelsExecution(intent), intent.name());
        }
        for (final InfernalHierarchyRules.Intent intent : List.of(
            InfernalHierarchyRules.Intent.PRESS, InfernalHierarchyRules.Intent.EMBER_FRONT,
            InfernalHierarchyRules.Intent.HOLD_COURT, InfernalHierarchyRules.Intent.PACT_GUARD
        )) {
            assertFalse(InfernalHierarchyRules.cancelsExecution(intent), intent.name());
        }
    }

    @Test
    void requiredIdentitiesAlwaysPrecedeGenericObservationCandidates() {
        final UUID truce = new UUID(9L, 1L);
        final UUID challenger = new UUID(9L, 2L);
        final List<UUID> generic = new java.util.ArrayList<>();
        for (int index = 0; index < 40; index++) {
            generic.add(new UUID(10L, index));
        }
        final List<UUID> retained = InfernalHierarchyRules.retainObservation(
            List.of(truce, challenger), generic, 12
        );
        assertEquals(12, retained.size());
        assertEquals(truce, retained.get(0), "the current truce player is preseeded first");
        assertEquals(challenger, retained.get(1), "the current challenger is preseeded next");
        assertTrue(retained.containsAll(List.of(truce, challenger)));
        final List<UUID> duplicated = InfernalHierarchyRules.retainObservation(
            List.of(truce, truce, challenger), List.of(challenger, new UUID(11L, 1L)), 12
        );
        assertEquals(3, duplicated.size(), "preseeded identities are never counted twice");
        assertTrue(InfernalHierarchyRules.retainObservation(List.of(truce), generic, 0).isEmpty());
    }

    @Test
    void boundedNearestSelectionIsDeterministicUnderEqualDistance() {
        final UUID low = new UUID(Long.MAX_VALUE, 0L);
        final UUID high = new UUID(Long.MIN_VALUE, 0L);
        final List<InfernalHierarchyRules.RankedActor> tied = List.of(
            new InfernalHierarchyRules.RankedActor(high, 4.0D),
            new InfernalHierarchyRules.RankedActor(low, 4.0D)
        );
        assertEquals(List.of(low, high), InfernalHierarchyRules.retainNearest(tied, 2),
            "equal distances resolve by the same stable unsigned UUID order the roster uses");
        assertEquals(List.of(low, high), InfernalHierarchyRules.retainNearest(
            List.of(tied.get(1), tied.get(0)), 2
        ), "the ordering is independent of the order the engine handed the candidates over");
        assertEquals(List.of(low), InfernalHierarchyRules.retainNearest(tied, 1),
            "the cap keeps the deterministic nearest prefix");
        final List<InfernalHierarchyRules.RankedActor> crowd = new java.util.ArrayList<>();
        for (int index = 0; index < 64; index++) {
            crowd.add(new InfernalHierarchyRules.RankedActor(new UUID(12L, index), 64 - index));
        }
        assertEquals(InfernalHierarchyRules.PHASE_PLAYER_CAP,
            InfernalHierarchyRules.retainNearest(crowd, InfernalHierarchyRules.PHASE_PLAYER_CAP).size(),
            "the phase player cap bounds the recipient set");
        assertEquals(new UUID(12L, 63), InfernalHierarchyRules.retainNearest(crowd, 1).get(0),
            "the nearest candidate wins regardless of engine iteration order");
    }

    @Test
    void boundedScanConstantsReplaceTheRelocatedVolumeScan() {
        assertEquals(100, InfernalHierarchyRules.CAULDRON_EVALUATION_INTERVAL_TICKS);
        assertEquals(4, InfernalHierarchyRules.CAULDRON_CONTRIBUTOR_CAP);
        assertEquals(8, InfernalHierarchyRules.CAULDRON_CONTRIBUTOR_RADIUS);
        assertEquals(128, InfernalHierarchyRules.CAULDRON_SCAN_BLOCK_READS);
        assertEquals(140, InfernalHierarchyRules.CAULDRON_LUCK_TICKS);
        assertEquals(200, InfernalHierarchyRules.CAULDRON_WEAKNESS_TICKS);
        assertEquals(8, InfernalHierarchyRules.cauldronReach(0));
        assertEquals(12, InfernalHierarchyRules.cauldronReach(1));
        assertEquals(24, InfernalHierarchyRules.cauldronReach(4));
        assertEquals(24, InfernalHierarchyRules.cauldronReach(99),
            "the existing maximum reach of twenty four blocks holds");
        assertTrue(InfernalHierarchyRules.CAULDRON_SCAN_BLOCK_READS < 2_023,
            "the charged read budget is far below the relocated seventeen by seven by seventeen volume");
        assertEquals(InfernalHierarchyRules.CAULDRON_EVALUATION_INTERVAL_TICKS,
            CreatureBehaviorProfile.find(CreatureKind.EMBERHORN_ARCHFIEND).orElseThrow()
                .pulseIntervalTicks(),
            "the archfiend aura evaluation cadence stays at one hundred ticks");
        assertEquals(InfernalHierarchyRules.FEAR_PULSE_INTERVAL_TICKS,
            CreatureBehaviorProfile.find(CreatureKind.ABYSSAL_REGENT).orElseThrow()
                .pulseIntervalTicks(),
            "the regent fear pulse cadence stays at eighty ticks");
    }

    @Test
    void perRankLineOfSightBudgetsMatchTheApprovedDesign() {
        assertEquals(2, InfernalHierarchyRules.lineOfSightBudget(Rank.DEMON));
        assertEquals(6, InfernalHierarchyRules.lineOfSightBudget(Rank.EMBERHORN_ARCHFIEND));
        assertEquals(8, InfernalHierarchyRules.lineOfSightBudget(Rank.ABYSSAL_REGENT));
        assertEquals(32, InfernalHierarchyRules.PHASE_PLAYER_CAP);
        assertEquals(24, InfernalHierarchyRules.PHASE_PLAYER_RADIUS);
        assertEquals(16, InfernalHierarchyRules.FEAR_PULSE_CANDIDATE_CAP);
        assertEquals(10, InfernalHierarchyRules.FEAR_PULSE_RADIUS);
        assertEquals(120, InfernalHierarchyRules.FEAR_PULSE_EFFECT_TICKS);
    }

    private static InfernalHierarchyRules.DemonIntentFacts demon(
        final boolean hazard, final boolean healthCritical, final boolean retreatRequired,
        final boolean reenterAllowed, final boolean unmoored, final boolean truceActive,
        final boolean playerAuthority, final boolean ownerUnderAttack,
        final boolean ownerBeyondFollowRange, final boolean guardOrdered,
        final boolean pressAuthorized, final boolean intruderInTerritory,
        final boolean warningWindowOpen, final boolean postHeld, final boolean daylight
    ) {
        return new InfernalHierarchyRules.DemonIntentFacts(hazard, healthCritical, retreatRequired,
            reenterAllowed, unmoored, truceActive, playerAuthority, ownerUnderAttack,
            ownerBeyondFollowRange, guardOrdered, pressAuthorized, intruderInTerritory,
            warningWindowOpen, postHeld, daylight);
    }

    private static final java.util.LinkedHashMap<
        InfernalHierarchyRules.DemonIntentFacts, InfernalHierarchyRules.Intent
    > DEMON_SELECTION_MATRIX = demonSelectionMatrix();

    private static java.util.LinkedHashMap<
        InfernalHierarchyRules.DemonIntentFacts, InfernalHierarchyRules.Intent
    > demonSelectionMatrix() {
        final java.util.LinkedHashMap<
            InfernalHierarchyRules.DemonIntentFacts, InfernalHierarchyRules.Intent
        > matrix = new java.util.LinkedHashMap<>();
        matrix.put(demon(true, false, false, false, false, false, false, false, false, false,
            false, false, false, false, false), InfernalHierarchyRules.Intent.RETREAT);
        matrix.put(demon(false, true, false, true, false, false, false, false, false, false,
            false, false, false, false, false), InfernalHierarchyRules.Intent.RETREAT);
        matrix.put(demon(false, false, true, false, false, false, false, false, false, false,
            false, false, false, false, false), InfernalHierarchyRules.Intent.RETREAT);
        matrix.put(demon(false, false, false, true, true, false, false, false, false, false,
            false, false, false, false, false), InfernalHierarchyRules.Intent.RETURN);
        matrix.put(demon(false, false, false, true, false, true, false, false, false, false,
            false, false, false, false, false), InfernalHierarchyRules.Intent.TRUCE);
        matrix.put(demon(false, false, false, true, false, false, true, true, false, false,
            false, false, false, false, false), InfernalHierarchyRules.Intent.INTERCEPT);
        matrix.put(demon(false, false, false, true, false, false, false, false, false, true,
            false, false, false, false, false), InfernalHierarchyRules.Intent.ORDERED_GUARD);
        matrix.put(demon(false, false, false, true, false, false, true, false, true, false,
            false, false, false, false, false), InfernalHierarchyRules.Intent.PACT_FOLLOW);
        matrix.put(demon(false, false, false, true, false, false, true, false, false, false,
            false, false, false, false, false), InfernalHierarchyRules.Intent.PACT_GUARD);
        matrix.put(demon(false, false, false, true, false, false, false, false, false, false,
            true, false, false, false, false), InfernalHierarchyRules.Intent.PRESS);
        matrix.put(demon(false, false, false, true, false, false, false, false, false, false,
            false, true, true, false, false), InfernalHierarchyRules.Intent.WARN);
        matrix.put(demon(false, false, false, true, false, false, false, false, false, false,
            false, true, false, false, false), InfernalHierarchyRules.Intent.PRESS);
        matrix.put(demon(false, false, false, true, false, false, false, false, false, false,
            false, false, false, true, true), InfernalHierarchyRules.Intent.POST_WATCH);
        matrix.put(demon(false, false, false, true, false, false, false, false, false, false,
            false, false, false, true, false), InfernalHierarchyRules.Intent.APPRAISE);
        matrix.put(demon(false, false, false, true, false, false, false, false, false, false,
            false, false, false, false, true), InfernalHierarchyRules.Intent.IDLE);
        return matrix;
    }

    private static InfernalHierarchyRules.ArchfiendIntentFacts archfiend(
        final boolean hazard, final boolean healthCritical, final boolean rallyReady,
        final boolean withdrawOrdered, final boolean dismissDue, final boolean challengerValid,
        final boolean emberFrontReady, final boolean intruderWarned, final boolean rosterBelowCap,
        final boolean anchorHeld, final boolean anchorSearchDue
    ) {
        return new InfernalHierarchyRules.ArchfiendIntentFacts(hazard, healthCritical, rallyReady,
            withdrawOrdered, dismissDue, challengerValid, emberFrontReady, intruderWarned,
            rosterBelowCap, anchorHeld, anchorSearchDue);
    }

    private static final java.util.LinkedHashMap<
        InfernalHierarchyRules.ArchfiendIntentFacts, InfernalHierarchyRules.Intent
    > ARCHFIEND_SELECTION_MATRIX = archfiendSelectionMatrix();

    private static java.util.LinkedHashMap<
        InfernalHierarchyRules.ArchfiendIntentFacts, InfernalHierarchyRules.Intent
    > archfiendSelectionMatrix() {
        final java.util.LinkedHashMap<
            InfernalHierarchyRules.ArchfiendIntentFacts, InfernalHierarchyRules.Intent
        > matrix = new java.util.LinkedHashMap<>();
        matrix.put(archfiend(true, false, false, false, false, false, false, false, false, false, false),
            InfernalHierarchyRules.Intent.WITHDRAW);
        matrix.put(archfiend(false, true, true, false, false, false, false, false, false, false, false),
            InfernalHierarchyRules.Intent.RALLY);
        matrix.put(archfiend(false, true, false, false, false, false, false, false, false, false, false),
            InfernalHierarchyRules.Intent.WITHDRAW);
        matrix.put(archfiend(false, false, false, true, false, false, false, false, false, false, false),
            InfernalHierarchyRules.Intent.WITHDRAW);
        matrix.put(archfiend(false, false, false, false, true, false, false, false, false, false, false),
            InfernalHierarchyRules.Intent.DISMISS);
        matrix.put(archfiend(false, false, false, false, false, true, true, false, false, false, false),
            InfernalHierarchyRules.Intent.EMBER_FRONT);
        matrix.put(archfiend(false, false, false, false, false, true, false, false, false, false, false),
            InfernalHierarchyRules.Intent.FOCUS);
        matrix.put(archfiend(false, false, false, false, false, false, false, true, false, false, false),
            InfernalHierarchyRules.Intent.WARN);
        matrix.put(archfiend(false, false, false, false, false, false, false, false, true, false, false),
            InfernalHierarchyRules.Intent.MUSTER);
        matrix.put(archfiend(false, false, false, false, false, false, false, false, false, true, false),
            InfernalHierarchyRules.Intent.HOLD_OFFICE);
        matrix.put(archfiend(false, false, false, false, false, false, false, false, false, false, true),
            InfernalHierarchyRules.Intent.SEEK_OFFICE);
        matrix.put(archfiend(false, false, false, false, false, false, false, false, false, false, false),
            InfernalHierarchyRules.Intent.IDLE);
        return matrix;
    }

    private static InfernalHierarchyRules.RegentIntentFacts regent(
        final boolean hazard, final boolean phaseTelegraph, final boolean phaseCommit,
        final boolean phaseRecovery, final boolean dissolving, final boolean displaceReady,
        final boolean fearPulseDue, final boolean commandDue, final boolean screening,
        final boolean appraising, final boolean anchorHeld, final boolean anchorSearchDue
    ) {
        return new InfernalHierarchyRules.RegentIntentFacts(hazard, phaseTelegraph, phaseCommit,
            phaseRecovery, dissolving, displaceReady, fearPulseDue, commandDue, screening,
            appraising, anchorHeld, anchorSearchDue);
    }

    private static final java.util.LinkedHashMap<
        InfernalHierarchyRules.RegentIntentFacts, InfernalHierarchyRules.Intent
    > REGENT_SELECTION_MATRIX = regentSelectionMatrix();

    private static java.util.LinkedHashMap<
        InfernalHierarchyRules.RegentIntentFacts, InfernalHierarchyRules.Intent
    > regentSelectionMatrix() {
        final java.util.LinkedHashMap<
            InfernalHierarchyRules.RegentIntentFacts, InfernalHierarchyRules.Intent
        > matrix = new java.util.LinkedHashMap<>();
        matrix.put(regent(true, false, false, false, false, false, false, false, false, false, false, false),
            InfernalHierarchyRules.Intent.RETURN);
        matrix.put(regent(false, true, false, false, false, false, false, false, false, false, false, false),
            InfernalHierarchyRules.Intent.PHASE_TELEGRAPH);
        matrix.put(regent(false, false, true, false, false, false, false, false, false, false, false, false),
            InfernalHierarchyRules.Intent.PHASE_COMMIT);
        matrix.put(regent(false, false, false, true, false, false, false, false, false, false, false, false),
            InfernalHierarchyRules.Intent.PHASE_RECOVERY);
        matrix.put(regent(false, false, false, false, true, false, false, false, false, false, false, false),
            InfernalHierarchyRules.Intent.DISSOLVE);
        matrix.put(regent(false, false, false, false, false, true, false, false, false, false, false, false),
            InfernalHierarchyRules.Intent.DISPLACE);
        matrix.put(regent(false, false, false, false, false, false, true, false, false, false, false, false),
            InfernalHierarchyRules.Intent.FEAR_PULSE);
        matrix.put(regent(false, false, false, false, false, false, false, true, false, false, false, false),
            InfernalHierarchyRules.Intent.COMMAND);
        matrix.put(regent(false, false, false, false, false, false, false, false, true, false, false, false),
            InfernalHierarchyRules.Intent.SCREEN);
        matrix.put(regent(false, false, false, false, false, false, false, false, false, true, false, false),
            InfernalHierarchyRules.Intent.APPRAISE);
        matrix.put(regent(false, false, false, false, false, false, false, false, false, false, true, false),
            InfernalHierarchyRules.Intent.HOLD_COURT);
        matrix.put(regent(false, false, false, false, false, false, false, false, false, false, false, true),
            InfernalHierarchyRules.Intent.SEEK_DEEP_ANCHOR);
        matrix.put(regent(false, false, false, false, false, false, false, false, false, false, false, false),
            InfernalHierarchyRules.Intent.IDLE_COURT);
        return matrix;
    }

    @Test
    void eligibilityRefusesPlayerBoundLeasedUnloadedAndCrossDimensionMembers() {
        final MemberCandidate bound = new MemberCandidate(
            UUID.randomUUID(), Rank.DEMON, false, 1.0D, true, false, true, true);
        final MemberCandidate leased = new MemberCandidate(
            UUID.randomUUID(), Rank.DEMON, false, 1.0D, false, true, true, true);
        final MemberCandidate unloaded = new MemberCandidate(
            UUID.randomUUID(), Rank.DEMON, false, 1.0D, false, false, true, false);
        final MemberCandidate valid = new MemberCandidate(
            UUID.randomUUID(), Rank.DEMON, false, 1.0D, false, false, true, true);
        assertFalse(InfernalHierarchyRules.eligibleMember(Rank.EMBERHORN_ARCHFIEND, bound));
        assertFalse(InfernalHierarchyRules.eligibleMember(Rank.EMBERHORN_ARCHFIEND, leased));
        assertFalse(InfernalHierarchyRules.eligibleMember(Rank.EMBERHORN_ARCHFIEND, unloaded));
        assertTrue(InfernalHierarchyRules.eligibleMember(Rank.EMBERHORN_ARCHFIEND, valid));
        assertFalse(InfernalHierarchyRules.eligibleMember(Rank.DEMON, valid));
    }
}
