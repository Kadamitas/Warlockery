package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.VampireCourtRules.AssaultRole;
import com.kadamitas.warlockery.entity.VampireCourtRules.Intent;
import com.kadamitas.warlockery.entity.VampireCourtRules.ReportOutcome;
import com.kadamitas.warlockery.entity.VampireCourtRules.VictimReport;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class VampireCourtRulesTest {
    @Test
    void feedingPressureUsesClosedFormBoundedArithmetic() {
        assertEquals(VampireCourtRules.DEFAULT_PRESSURE,
            VampireCourtRules.reconcilePressure(CreatureKind.VAMPIRE, 350, 100L, 119L));
        assertEquals(351,
            VampireCourtRules.reconcilePressure(CreatureKind.VAMPIRE, 350, 100L, 120L));
        assertEquals(VampireCourtRules.MAX_PRESSURE,
            VampireCourtRules.reconcilePressure(CreatureKind.VAMPIRE, 999, 0L, Long.MAX_VALUE));
        assertEquals(0,
            VampireCourtRules.reconcilePressure(CreatureKind.BLOOD_THRALL, 999, 0L, Long.MAX_VALUE));
        assertEquals(460, VampireCourtRules.afterOrdinaryFeed(700));
        assertEquals(250, VampireCourtRules.afterAssaultFeed(900));
        assertEquals(0, VampireCourtRules.afterOrdinaryFeed(100));
        assertThrows(IllegalArgumentException.class,
            () -> VampireCourtRules.reconcilePressure(CreatureKind.VAMPIRE, -1, 0L, 20L));
    }

    @Test
    void schedulePrioritizesDangerAndKeepsCourtRolesDistinct() {
        assertEquals(Intent.SEEK_SHELTER,
            VampireCourtRules.chooseIntent(CreatureKind.VAMPIRE, 18_000L, true, false, false, 800, true));
        assertEquals(Intent.ASSAULT_LEAD,
            VampireCourtRules.chooseIntent(CreatureKind.VAMPIRE, 18_000L, false, false, true, 800, true));
        assertEquals(Intent.STALK,
            VampireCourtRules.chooseIntent(CreatureKind.VAMPIRE, 18_000L, false, false, false, 700, true));
        assertEquals(Intent.WATCH,
            VampireCourtRules.chooseIntent(CreatureKind.VAMPIRE, 13_000L, false, false, false, 500, true));
        assertEquals(Intent.ROOST,
            VampireCourtRules.chooseIntent(CreatureKind.VAMPIRE, 18_000L, false, false, false, 499, true));
        assertEquals(Intent.INTERCEPT,
            VampireCourtRules.chooseIntent(CreatureKind.VAMPIRE, 18_000L, false, true, false, 499, false));
        assertEquals(Intent.INTERCEPT,
            VampireCourtRules.chooseIntent(CreatureKind.BLOOD_THRALL, 18_000L, false, true, false, 0, true));
        assertEquals(Intent.INTERCEPT,
            VampireCourtRules.chooseIntent(CreatureKind.BLOOD_THRALL, 18_000L, false, true, false, 0, false));
        assertEquals(Intent.THRESHOLD_GUARD,
            VampireCourtRules.chooseIntent(CreatureKind.BLOOD_THRALL, 18_000L, false, false, false, 0, true));
        assertEquals(Intent.UNBOUND,
            VampireCourtRules.chooseIntent(CreatureKind.BLOOD_THRALL, 18_000L, false, false, false, 0, false));
        assertEquals(39, VampireCourtRules.scheduleOffset(new UUID(0L, 39L)));
    }

    @Test
    void preyAndAuthorityPredicatesPreserveFamilyAndOwnerBoundaries() {
        assertTrue(VampireCourtRules.eligibleOrdinaryPrey(true, false, false, false, false, false));
        assertFalse(VampireCourtRules.eligibleOrdinaryPrey(true, true, false, false, false, false));
        assertFalse(VampireCourtRules.eligibleOrdinaryPrey(true, false, true, false, false, false));
        assertFalse(VampireCourtRules.eligibleOrdinaryPrey(false, false, false, false, false, false));
        assertTrue(VampireCourtRules.eligibleOrdinaryPrey(true, true, false, false, false, true));
        assertFalse(VampireCourtRules.mayAttack(false, false, false, false));
        assertTrue(VampireCourtRules.mayAttack(false, false, false, true));
        assertFalse(VampireCourtRules.validMaster(true, true, true, true, false, false));
        assertFalse(VampireCourtRules.validMaster(true, true, true, false, true, false));
        assertTrue(VampireCourtRules.validMaster(true, true, true, false, false, false));
    }

    @Test
    void victimReportsExpireUpdateAndEvictDeterministically() {
        final long now = 30_000L;
        final ArrayList<VictimReport> reports = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            reports.add(new VictimReport(new UUID(0L, index), index, 64, index, now - index, ReportOutcome.LOST, 1));
        }
        final UUID newest = new UUID(0L, 99L);
        final List<VictimReport> bounded = VampireCourtRules.rememberVictim(
            reports, new VictimReport(newest, 9, 64, 9, now, ReportOutcome.FED, 5), now
        );
        assertEquals(VampireCourtRules.MAX_REPORTS, bounded.size());
        assertTrue(bounded.stream().anyMatch(report -> report.victimId().equals(newest)));
        assertFalse(bounded.stream().anyMatch(report -> report.victimId().equals(new UUID(0L, 3L))));

        final VictimReport replacement = new VictimReport(newest, 10, 65, 10, now + 1, ReportOutcome.RESISTED, 7);
        final List<VictimReport> updated = VampireCourtRules.rememberVictim(bounded, replacement, now + 1);
        assertEquals(4, updated.size());
        assertEquals(replacement, updated.stream().filter(report -> report.victimId().equals(newest)).findFirst().orElseThrow());
        assertTrue(VampireCourtRules.pruneReports(updated, now + VampireCourtRules.REPORT_EXPIRY_TICKS + 2).isEmpty());
        assertTrue(VampireCourtRules.rememberVictim(List.of(), replacement, now).size() == 1);
    }

    @Test
    void assaultCompositionPreservesEveryExistingTotal() {
        assertEquals(new VampireCourtRules.AssaultComposition(1, 1), VampireCourtRules.assaultComposition(1));
        assertEquals(new VampireCourtRules.AssaultComposition(1, 3), VampireCourtRules.assaultComposition(2));
        assertEquals(new VampireCourtRules.AssaultComposition(1, 4), VampireCourtRules.assaultComposition(3));
        assertEquals(2, VampireCourtRules.assaultComposition(1).total());
        assertEquals(4, VampireCourtRules.assaultComposition(2).total());
        assertEquals(5, VampireCourtRules.assaultComposition(3).total());
        assertEquals(AssaultRole.PREDATOR_LEADER, VampireCourtRules.assaultRole(true));
        assertEquals(AssaultRole.BOUND_GUARD, VampireCourtRules.assaultRole(false));
        assertThrows(IllegalArgumentException.class, () -> VampireCourtRules.assaultComposition(0));
        assertTrue(VampireCourtRules.mayAdvanceObjective(CreatureKind.VAMPIRE, AssaultRole.PREDATOR_LEADER));
        assertFalse(VampireCourtRules.mayAdvanceObjective(CreatureKind.BLOOD_THRALL, AssaultRole.BOUND_GUARD));
        assertTrue(VampireCourtRules.mayAttackAssaultObjective(
            CreatureKind.VAMPIRE, AssaultRole.PREDATOR_LEADER, true, true
        ));
        assertFalse(VampireCourtRules.mayAttackAssaultObjective(
            CreatureKind.VAMPIRE, AssaultRole.PREDATOR_LEADER, false, true
        ));
        assertFalse(VampireCourtRules.mayAttackAssaultObjective(
            CreatureKind.BLOOD_THRALL, AssaultRole.BOUND_GUARD, true, true
        ));
    }

    @Test
    void cadenceClaimsRoutesAndRelationshipsRemainHardBounded() {
        assertEquals(20, VampireCourtRules.DECISION_INTERVAL_TICKS);
        assertEquals(80, VampireCourtRules.ENTITY_SCAN_INTERVAL_TICKS);
        assertEquals(24.0D, VampireCourtRules.ENTITY_SCAN_RADIUS);
        assertEquals(16, VampireCourtRules.MAX_CANDIDATES);
        assertEquals(100, VampireCourtRules.SHELTER_SCAN_INTERVAL_TICKS);
        assertEquals(256, VampireCourtRules.MAX_SHELTER_BLOCKS);
        assertEquals(20, VampireCourtRules.NAVIGATION_INTERVAL_TICKS);
        assertEquals(3, VampireCourtRules.MAX_ROUTE_FAILURES);
        assertEquals(100, VampireCourtRules.ROUTE_RETRY_TICKS);
        assertEquals(200, VampireCourtRules.MAX_CLAIM_LEASE_TICKS);
        assertEquals(8, VampireCourtRules.MAX_COURT_MEMBERS);
        assertEquals(1, VampireCourtRules.MAX_ALERT_DEPTH);
        assertEquals(40, VampireCourtRules.FEEDBACK_INTERVAL_TICKS);
        assertEquals(200, VampireCourtRules.WAVERING_TICKS);
        assertFalse(VampireCourtRules.navigationDue(100L, 119L));
        assertTrue(VampireCourtRules.navigationDue(100L, 120L));
        assertTrue(VampireCourtRules.navigationDue(0L, 0L));
        assertFalse(VampireCourtRules.decisionDue(120L, 119L, false, Intent.WATCH));
        assertTrue(VampireCourtRules.decisionDue(120L, 120L, false, Intent.WATCH));
        assertTrue(VampireCourtRules.decisionDue(120L, 119L, true, Intent.ASSAULT_LEAD));
        assertFalse(VampireCourtRules.decisionDue(120L, 119L, true, Intent.SEEK_SHELTER));
        assertEquals(Intent.WAVERING, VampireCourtRules.afterWavering(119L, 120L, true));
        assertEquals(Intent.RETREAT, VampireCourtRules.afterWavering(120L, 120L, true));
        assertEquals(Intent.UNBOUND, VampireCourtRules.afterWavering(120L, 120L, false));
        assertEquals(new VampireCourtRules.RouteRetry(3, 240L), VampireCourtRules.routeFailure(2, 140L));
        assertEquals(new VampireCourtRules.RouteRetry(0, 0L), VampireCourtRules.routeSuccess());
        assertEquals(200L, VampireCourtRules.claimExpiry(0L, 999));
        assertEquals(8, VampireCourtRules.boundedMemberCount(99));
        assertEquals(1, VampireCourtRules.boundedAlertDepth(99));
        assertTrue(VampireCourtRules.requiresUrgentShelter(true, false, false, false, false, false, false));
        assertFalse(VampireCourtRules.requiresUrgentShelter(true, true, false, false, false, false, false));
        assertTrue(VampireCourtRules.requiresUrgentShelter(false, false, true, false, false, false, false));
        assertTrue(VampireCourtRules.requiresUrgentShelter(false, false, false, true, false, false, false));
        assertTrue(VampireCourtRules.requiresUrgentShelter(false, false, false, false, true, false, false));
        assertTrue(VampireCourtRules.requiresUrgentShelter(false, false, false, false, false, true, false));
        assertTrue(VampireCourtRules.requiresUrgentShelter(false, false, false, false, false, false, true));
        assertFalse(VampireCourtRules.requiresUrgentShelter(false, false, false, false, false, false, false));
        assertTrue(VampireCourtRules.feedbackDue(100L, 100L, false));
        assertFalse(VampireCourtRules.feedbackDue(101L, 100L, false));
        assertTrue(VampireCourtRules.feedbackDue(101L, 100L, true));
    }
}
