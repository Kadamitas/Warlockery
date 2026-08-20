package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.DeathRules.Candidate;
import com.kadamitas.warlockery.entity.DeathRules.CandidateObservation;
import com.kadamitas.warlockery.entity.DeathRules.Phase;
import com.kadamitas.warlockery.entity.DeathRules.ReleaseReason;
import com.kadamitas.warlockery.entity.DeathRules.RouteResult;
import com.kadamitas.warlockery.entity.DeathRules.SubjectObservation;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class DeathRulesTest {
    private static final UUID FIRST = new UUID(0L, 1L);
    private static final UUID SECOND = new UUID(0L, 2L);

    private static CandidateObservation candidate(final double distanceSquared) {
        return new CandidateObservation(true, true, false, false, true, true, distanceSquared);
    }

    private static SubjectObservation holding() {
        return new SubjectObservation(true, true, true, true, false, false, false, 4.0D, 200, 0);
    }

    @Test
    void onlyLoadedLivingSurvivalUndisguisedPlayersInRangeAreAppointable() {
        assertTrue(DeathRules.appointable(candidate(DeathRules.APPOINT_RANGE_SQUARED)));
        assertFalse(DeathRules.appointable(candidate(DeathRules.APPOINT_RANGE_SQUARED + 1.0D)),
            "range is a hard bound, not a preference");
        assertFalse(DeathRules.appointable(
            new CandidateObservation(false, true, false, false, true, true, 4.0D)), "dead");
        assertFalse(DeathRules.appointable(
            new CandidateObservation(true, false, false, false, true, true, 4.0D)), "creative or spectator");
        assertFalse(DeathRules.appointable(
            new CandidateObservation(true, true, true, false, true, true, 4.0D)), "invulnerable");
        assertFalse(DeathRules.appointable(
            new CandidateObservation(true, true, false, true, true, true, 4.0D)), "completely disguised");
        assertFalse(DeathRules.appointable(
            new CandidateObservation(true, true, false, false, false, true, 4.0D)), "other dimension");
        assertFalse(DeathRules.appointable(
            new CandidateObservation(true, true, false, false, true, false, 4.0D)), "unloaded chunk");
    }

    @Test
    void acquisitionIsSuppressedByEveryHigherPriorityFact() {
        assertTrue(DeathRules.discoveryAllowed(true, false, false, false, false, 0));
        assertFalse(DeathRules.discoveryAllowed(false, false, false, false, false, 0), "not quiescent");
        assertFalse(DeathRules.discoveryAllowed(true, true, false, false, false, 0), "hazard");
        assertFalse(DeathRules.discoveryAllowed(true, false, true, false, false, 0), "recently hurt");
        assertFalse(DeathRules.discoveryAllowed(true, false, false, true, false, 0), "peaceful");
        assertFalse(DeathRules.discoveryAllowed(true, false, false, false, true, 0), "disguised player near");
        assertFalse(DeathRules.discoveryAllowed(true, false, false, false, false, 1), "backoff");
    }

    @Test
    void candidateOrderingIsStableDistanceThenUuidAndCapped() {
        final List<Candidate> inspected = List.of(
            new Candidate(SECOND, 16.0D, true),
            new Candidate(FIRST, 16.0D, true),
            new Candidate(new UUID(0L, 3L), 4.0D, false)
        );
        final List<Candidate> ranked = DeathRules.rank(inspected);
        assertEquals(4.0D, ranked.get(0).distanceSquared(), "closest first");
        assertEquals(FIRST, ranked.get(1).id(), "UUID breaks an exact distance tie");
        assertEquals(SECOND, ranked.get(2).id());
        assertEquals(ranked, DeathRules.rank(inspected), "ordering is deterministic");
        assertEquals(FIRST, DeathRules.select(inspected).orElseThrow().id(),
            "only a visible candidate may be appointed");
        assertTrue(DeathRules.select(List.of(new Candidate(FIRST, 1.0D, false))).isEmpty());
        assertEquals(DeathRules.MAX_RETAINED_CANDIDATES, DeathRules.rank(
            IntStream.range(0, 40)
                .mapToObj(index -> new Candidate(new UUID(1L, index), index, true))
                .toList()
        ).size(), "retention is capped");
    }

    @Test
    void oneSubjectMayBeLeasedByOnlyOneInspectedDeath() {
        assertTrue(DeathRules.leaseAvailable(FIRST, List.of()));
        assertTrue(DeathRules.leaseAvailable(FIRST, List.of(SECOND)));
        assertFalse(DeathRules.leaseAvailable(FIRST, List.of(SECOND, FIRST)));
        assertTrue(DeathRules.leaseAvailable(FIRST, IntStream.range(0, 20)
                .mapToObj(index -> new UUID(9L, index))
                .toList()),
            "the lease read is capped and never enumerates a level");
    }

    @Test
    void everyReleaseReasonIsReachableAndOrdered() {
        assertEquals(ReleaseReason.NONE, DeathRules.releaseReason(holding()));
        assertEquals(ReleaseReason.PEACEFUL, DeathRules.releaseReason(new SubjectObservation(
            true, true, true, true, false, false, true, 4.0D, 200, 0)));
        assertEquals(ReleaseReason.DIMENSION, DeathRules.releaseReason(new SubjectObservation(
            true, false, true, true, false, false, false, 4.0D, 200, 0)));
        assertEquals(ReleaseReason.INVALID_SUBJECT, DeathRules.releaseReason(new SubjectObservation(
            false, true, false, false, false, false, false, 4.0D, 200, 0)));
        assertEquals(ReleaseReason.INVALID_SUBJECT, DeathRules.releaseReason(new SubjectObservation(
            true, true, false, true, false, false, false, 4.0D, 200, 0)));
        assertEquals(ReleaseReason.INVALID_SUBJECT, DeathRules.releaseReason(new SubjectObservation(
            true, true, true, false, false, false, false, 4.0D, 200, 0)));
        assertEquals(ReleaseReason.INVALID_SUBJECT, DeathRules.releaseReason(new SubjectObservation(
            true, true, true, true, true, false, false, 4.0D, 200, 0)));
        assertEquals(ReleaseReason.DISGUISED, DeathRules.releaseReason(new SubjectObservation(
            true, true, true, true, false, true, false, 4.0D, 200, 0)));
        assertEquals(ReleaseReason.RANGE, DeathRules.releaseReason(new SubjectObservation(
            true, true, true, true, false, false, false,
            DeathRules.RELEASE_RANGE_SQUARED + 1.0D, 200, 0)));
        assertEquals(ReleaseReason.ROUTE_FAILURE, DeathRules.releaseReason(new SubjectObservation(
            true, true, true, true, false, false, false, 4.0D, 200, DeathRules.MAX_ROUTE_FAILURES)));
        assertEquals(ReleaseReason.TIMEOUT, DeathRules.releaseReason(new SubjectObservation(
            true, true, true, true, false, false, false, 4.0D, 0, 0)));
    }

    @Test
    void theSingleAttemptRequiresReachACompleteTelegraphAndNoEarlierAttempt() {
        assertTrue(DeathRules.withinReach(DeathRules.REACH_SQUARED));
        assertFalse(DeathRules.withinReach(DeathRules.REACH_SQUARED + 0.01D));
        assertTrue(DeathRules.telegraphComplete(0));
        assertFalse(DeathRules.telegraphComplete(1));
        assertTrue(DeathRules.reapAllowed(true, true, 0, false));
        assertFalse(DeathRules.reapAllowed(false, true, 0, false), "a lost predicate cancels the attempt");
        assertFalse(DeathRules.reapAllowed(true, false, 0, false), "no attempt outside reach");
        assertFalse(DeathRules.reapAllowed(true, true, 1, false), "no attempt before the telegraph ends");
        assertFalse(DeathRules.reapAllowed(true, true, 0, true), "at most one attempt per episode");
    }

    @Test
    void recoveryBarsEveryFurtherAttack() {
        assertFalse(DeathRules.recoveryComplete(1));
        assertTrue(DeathRules.recoveryComplete(0));
        assertFalse(DeathRules.mayAttack(Phase.RECOVER, DeathRules.RECOVER_TICKS));
        assertFalse(DeathRules.mayAttack(Phase.RECOVER, 0));
        assertFalse(DeathRules.mayAttack(Phase.APPROACH, 5), "leftover recovery still bars an attack");
        assertTrue(DeathRules.mayAttack(Phase.TELEGRAPH, 0));
        assertTrue(DeathRules.mayAttack(Phase.QUIESCENT, 0));
    }

    @Test
    void hazardBeatsAttackerBeatsAppointmentBeatsIdle() {
        assertEquals(0, DeathRules.priority(Phase.TELEGRAPH, true, true));
        assertEquals(1, DeathRules.priority(Phase.TELEGRAPH, false, true));
        assertEquals(2, DeathRules.priority(Phase.TELEGRAPH, false, false));
        assertEquals(3, DeathRules.priority(Phase.RECOVER, false, false));
        assertEquals(4, DeathRules.priority(Phase.QUIESCENT, false, false));
        assertTrue(DeathRules.hazardPreempts(Phase.APPROACH, true));
        assertFalse(DeathRules.hazardPreempts(Phase.APPROACH, false));
        assertTrue(DeathRules.attackerPreempts(Phase.APPROACH, true));
        assertFalse(DeathRules.attackerPreempts(Phase.APPROACH, false));
        assertTrue(DeathRules.attackerFresh(0));
        assertTrue(DeathRules.attackerFresh(DeathRules.DIRECT_ATTACKER_FRESHNESS_TICKS));
        assertFalse(DeathRules.attackerFresh(DeathRules.DIRECT_ATTACKER_FRESHNESS_TICKS + 1));
        assertFalse(DeathRules.attackerFresh(-1), "a negative age is never fresh");
    }

    @Test
    void routeFailuresCountBackOffAndStopAtThree() {
        assertTrue(DeathRules.pathRequestAllowed(0, 0));
        assertFalse(DeathRules.pathRequestAllowed(1, 0));
        assertFalse(DeathRules.pathRequestAllowed(0, 1));
        final RouteResult failed = new RouteResult(true, false, false);
        final RouteResult succeeded = new RouteResult(true, true, true);
        assertTrue(succeeded.success());
        assertFalse(failed.success());
        assertEquals(1, DeathRules.routeFailuresAfter(0, failed));
        assertEquals(3, DeathRules.routeFailuresAfter(2, failed));
        assertEquals(3, DeathRules.routeFailuresAfter(3, failed), "the counter is clamped");
        assertEquals(0, DeathRules.routeFailuresAfter(2, succeeded), "a success resets the counter");
        assertFalse(DeathRules.routeExhausted(2));
        assertTrue(DeathRules.routeExhausted(DeathRules.MAX_ROUTE_FAILURES));
        assertEquals(0, DeathRules.routeBackoffAfter(2));
        assertEquals(DeathRules.ROUTE_BACKOFF_TICKS, DeathRules.routeBackoffAfter(3));
    }

    @Test
    void quotasDurationsAndStaggerAreBounded() {
        assertTrue(DeathRules.budgetAllows(0, 1));
        assertFalse(DeathRules.budgetAllows(1, 1));
        assertFalse(DeathRules.budgetAllows(0, 0));
        assertEquals(0, DeathRules.clampRemaining(-5, 100));
        assertEquals(100, DeathRules.clampRemaining(400, 100));
        assertEquals(50, DeathRules.clampRemaining(50, 100));
        assertEquals(0, DeathRules.decrementLoaded(0));
        assertEquals(4, DeathRules.decrementLoaded(5));
        final int offset = DeathRules.stableOffset(FIRST, DeathRules.DISCOVERY_INTERVAL_TICKS);
        assertTrue(offset >= 0 && offset < DeathRules.DISCOVERY_INTERVAL_TICKS);
        assertEquals(offset, DeathRules.stableOffset(FIRST, DeathRules.DISCOVERY_INTERVAL_TICKS));
        assertEquals(0, DeathRules.stableOffset(null, 20));
        assertEquals(0, DeathRules.stableOffset(FIRST, 0));
    }

    @Test
    void primaryMeleeBonusPreservesTheExactFifteenPercentShape() {
        assertEquals(1.0F, DeathRules.primaryMeleeBonus(100.0F, 14.0D), 0.0001F,
            "fifteen percent of one hundred maximum health is fifteen total damage");
        assertEquals(0.0F, DeathRules.primaryMeleeBonus(20.0F, 14.0D), 0.0001F,
            "the attribute already exceeds fifteen percent of twenty maximum health");
        assertEquals(0.0F, DeathRules.primaryMeleeBonus(1.0F, 14.0D), 0.0001F,
            "the bonus is never negative");
        assertEquals(136.0F, DeathRules.primaryMeleeBonus(1_000.0F, 14.0D), 0.0001F);
    }

    @Test
    void preservedCombatAndVigilConstantsAreUnchanged() {
        assertEquals(120, DeathRules.WITHER_DURATION_TICKS);
        assertEquals(1, DeathRules.WITHER_AMPLIFIER);
        assertEquals(20, DeathRules.VIGIL_HEAL_INTERVAL_TICKS);
        assertEquals(1.0F, DeathRules.VIGIL_HEAL_AMOUNT);
        assertEquals(1_000.0, DeathCombatRules.MAX_HEALTH);
        assertEquals(15.0F, DeathCombatRules.MAX_INCOMING_DAMAGE);
    }
}
