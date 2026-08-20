package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class EntRulesTest {
    @Test void claimAndLeashUseInclusiveApprovedBoundaries() {
        assertTrue(EntRules.insideClaim(0, 0, 0, 12, 8, 0));
        assertFalse(EntRules.insideClaim(0, 0, 0, 12.0001, 0, 0));
        assertFalse(EntRules.insideClaim(0, 0, 0, 0, 8.0001, 0));
        assertTrue(EntRules.insideLeash(0, 0, 0, 24, 0, 0));
        assertFalse(EntRules.insideLeash(0, 0, 0, 24.0001, 0, 0));
        assertFalse(EntRules.anchorCorrupt(0, 0, 0, 64, 0, 0));
        assertTrue(EntRules.anchorCorrupt(0, 0, 0, 64.0001, 0, 0));
    }

    @Test void grievanceIsBoundedAndUsesExactAccrualAndDecay() {
        assertEquals(20, EntRules.addFellingGrievance(0));
        assertEquals(100, EntRules.addFellingGrievance(95));
        assertEquals(10, EntRules.addDamageGrievance(0));
        assertEquals(99, EntRules.decayGrievance(100, 100));
        assertEquals(99, EntRules.decayGrievance(100, 199));
        assertEquals(98, EntRules.decayGrievance(100, 200));
        assertEquals(0, EntRules.decayGrievance(0, 10_000));
    }

    @Test void zeroSentinelsAreDueAndEvidenceExpiresAfterFortyTicks() {
        assertTrue(EntRules.remainingDue(0));
        assertFalse(EntRules.remainingDue(1));
        assertTrue(EntRules.evidenceFresh(0));
        assertTrue(EntRules.evidenceFresh(40));
        assertFalse(EntRules.evidenceFresh(41));
        assertFalse(EntRules.evidenceFresh(-1));
    }

    @Test void phasePriorityAndTransitionsAreExact() {
        assertEquals(EntRules.Band.HAZARD, EntRules.priority(true, true, true));
        assertEquals(EntRules.Band.COMBAT, EntRules.priority(false, true, true));
        assertEquals(EntRules.Band.EPISODE, EntRules.priority(false, false, true));
        assertEquals(EntRules.Band.ROUTINE, EntRules.priority(false, false, false));
        assertEquals(EntRules.Phase.WARN, EntRules.afterOrientation(20, 0, true));
        assertEquals(EntRules.Phase.STRIKE, EntRules.afterOrientation(60, 0, true));
        assertEquals(EntRules.Phase.STRIKE, EntRules.afterOrientation(20, 1, true));
        assertEquals(EntRules.Phase.SETTLE, EntRules.afterOrientation(20, 0, false));
        assertTrue(EntRules.strikeExpired(200));
        assertTrue(EntRules.warningExpired(40));
        assertTrue(EntRules.settleExpired(300));
    }

    @Test void cadenceAndPathFailureStayBounded() {
        assertTrue(EntRules.staggeredDue(19, 1, 20));
        assertFalse(EntRules.staggeredDue(18, 1, 20));
        assertTrue(EntRules.pathDue(19, 1));
        assertEquals(3, EntRules.routeFailuresAfter(2));
        assertTrue(EntRules.routeExhausted(3));
        assertEquals(100, EntRules.ROUTE_BACKOFF_TICKS);
        assertEquals(20_000, EntRules.MAX_LOADED_CADENCE_TICKS);
        assertEquals(new EntRules.RouteFailure(0, 100, true), EntRules.routeFailure(2));
        assertEquals(new EntRules.RouteFailure(1, 0, false), EntRules.routeFailure(0));
    }

    @Test void deniedWarningTokenDefersWithoutChangingPhaseOrCooldown() {
        assertEquals(new EntRules.WarningTransition(EntRules.Phase.ROUSED, 0, false),
            EntRules.warningTransition(false, 0));
        assertEquals(new EntRules.WarningTransition(EntRules.Phase.WARN, 600, true),
            EntRules.warningTransition(true, 0));
    }

    @Test void noticeCandidatesSortByDistanceThenUuidAndCapAtTwo() {
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID high = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var ordered = EntRules.selectNotices(List.of(
            new EntRules.NoticeCandidate(high, 1), new EntRules.NoticeCandidate(low, 1),
            new EntRules.NoticeCandidate(UUID.randomUUID(), 0)));
        assertEquals(2, ordered.size());
        assertEquals(0, ordered.getFirst().distanceSquared());
        assertEquals(low, ordered.get(1).uuid());
    }

    @Test void quotaContractUsesExactPerTickCapsAndDeniesOffThread() {
        EntRules.Quota quota = EntRules.Quota.fresh(7);
        for (int i = 0; i < EntRules.MAX_PATHS_PER_LEVEL_TICK; i++) assertTrue(quota.tryPath(true));
        assertFalse(quota.tryPath(true));
        assertFalse(EntRules.Quota.fresh(7).tryPath(false));
        assertEquals(16, EntRules.MAX_EXPENSIVE_TOKENS_PER_LEVEL_TICK);
        assertEquals(128, EntRules.MAX_RAW_ENTITY_VISITS_PER_LEVEL_TICK);
        assertEquals(1024, EntRules.MAX_CHARGED_READS_PER_LEVEL_TICK);
    }

    @Test void everyApprovedQuotaCategoryDeniesBeyondItsExactCap() {
        EntRules.Quota q=EntRules.Quota.fresh(9);
        assertAcceptedThenDenied(16, ()->q.tryExpensive(true));
        assertAcceptedThenDenied(128, ()->q.tryRawEntityVisit(true));
        assertAcceptedThenDenied(32, ()->q.trySightRay(true));
        assertTrue(q.tryChargedReads(1024,true)); assertFalse(q.tryChargedReads(1,true));
        assertAcceptedThenDenied(128, ()->q.trySafeDestinationVisit(true));
        assertAcceptedThenDenied(2, ()->q.tryNoticeScan(true));
        assertAcceptedThenDenied(4, ()->q.tryNotice(true));
        assertAcceptedThenDenied(4, ()->q.tryWarning(true));
        assertAcceptedThenDenied(8, ()->q.tryMelee(true));
        assertAcceptedThenDenied(1, ()->q.tryTendJob(true));
        assertAcceptedThenDenied(1, ()->q.tryBlockEdit(true));
        assertAcceptedThenDenied(8, ()->q.tryFeedback(true));
    }

    @Test void cancellationVocabularyIsExhaustiveAndSubjectLegalityRejectsSpecialStates() {
        assertEquals(Set.of("REMOVAL","TELEPORT","DIMENSION_CHANGE","DEATH","DISCARD","TRADE","SLEEP","RAID","PANIC","BREEDING","HAZARD","UNLOAD","TOKEN_DENIED"),
            java.util.Arrays.stream(EntRules.Cancellation.values()).map(Enum::name).collect(java.util.stream.Collectors.toSet()));
        assertFalse(EntRules.subjectLegal(false,true,true,true,true,true,true,true,true));
        assertFalse(EntRules.subjectLegal(true,false,true,true,true,true,true,true,true));
        assertFalse(EntRules.subjectLegal(true,true,false,true,true,true,true,true,true));
        assertFalse(EntRules.subjectLegal(true,true,true,true,true,true,false,true,true));
        assertFalse(EntRules.subjectLegal(true,true,true,true,true,true,true,false,true));
        assertFalse(EntRules.subjectLegal(true,true,true,true,true,true,true,true,false));
        assertTrue(EntRules.subjectLegal(true,true,true,true,true,true,true,true,true));
    }

    @Test void attributedReactionRequiresEffectiveFreshLegalVisibleDamage() {
        assertFalse(EntRules.reactionAllowed(false,0,true,true));
        assertFalse(EntRules.reactionAllowed(true,41,true,true));
        assertFalse(EntRules.reactionAllowed(true,0,false,true));
        assertFalse(EntRules.reactionAllowed(true,0,true,false));
        assertTrue(EntRules.reactionAllowed(true,40,true,true));
    }

    @Test void thirdStrikeRouteFailureReleasesWithoutReanchoring() {
        assertEquals(new EntRules.RouteResolution(0,100,EntRules.Phase.SETTLE,false),EntRules.strikeRouteFailure(2));
        assertEquals(new EntRules.RouteResolution(0,100,EntRules.Phase.WARDING,true),EntRules.settleRouteFailure(2));
    }

    private static void assertAcceptedThenDenied(int cap, java.util.function.BooleanSupplier action){for(int i=0;i<cap;i++)assertTrue(action.getAsBoolean(),"token "+i);assertFalse(action.getAsBoolean());}
}
