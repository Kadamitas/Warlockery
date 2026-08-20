package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class BrambleColossusRulesTest {
    @Test void geometryCadencesAndNerveUseExactBoundaries() {
        assertTrue(BrambleColossusRules.insideHeldVolume(10, 5, 0));
        assertFalse(BrambleColossusRules.insideHeldVolume(10.01, 0, 0));
        assertFalse(BrambleColossusRules.insideHeldVolume(10, 0, 10));
        assertFalse(BrambleColossusRules.insideHeldVolume(0, 5.01, 0));
        assertTrue(BrambleColossusRules.insideLeash(14, 0, 0));
        assertFalse(BrambleColossusRules.insideLeash(14.01, 0, 0));
        assertEquals(25, BrambleColossusRules.loseNerve(33));
        assertEquals(26, BrambleColossusRules.recoverNerve(25));
        assertTrue(BrambleColossusRules.falterAt(25));
        assertFalse(BrambleColossusRules.mayBindAt(25));
        assertTrue(BrambleColossusRules.recoveredAt(50));
        assertTrue(BrambleColossusRules.staysFaltered(BrambleColossusRules.Phase.FALTER, 26));
        assertFalse(BrambleColossusRules.staysFaltered(BrambleColossusRules.Phase.FALTER, 50));
        assertTrue(BrambleColossusRules.fresh(40));
        assertFalse(BrambleColossusRules.fresh(41));
        assertTrue(BrambleColossusRules.due(0));
        assertEquals(20_000, BrambleColossusRules.boundedSentinel(50_000));
    }

    @Test void circuitOrderingLegalityAndDeterminismAreStable() {
        assertArrayEquals(new double[] {6, 0}, BrambleColossusRules.waypointOffset(0));
        assertArrayEquals(new double[] {0, 6}, BrambleColossusRules.waypointOffset(1));
        assertArrayEquals(new double[] {-6, 0}, BrambleColossusRules.waypointOffset(2));
        assertArrayEquals(new double[] {0, -6}, BrambleColossusRules.waypointOffset(3));
        assertArrayEquals(new double[] {4, 0}, BrambleColossusRules.waypointApproachOffset(0));
        assertArrayEquals(new double[] {0, -4}, BrambleColossusRules.waypointApproachOffset(3));
        assertEquals(0, BrambleColossusRules.nextLeg(3));
        assertTrue(BrambleColossusRules.legal(false, false, false, true, true, 26));
        assertFalse(BrambleColossusRules.legal(true, false, false, true, true, 100));
        assertFalse(BrambleColossusRules.legal(false, true, false, true, true, 100));
        assertFalse(BrambleColossusRules.legal(false, false, true, true, true, 100));
        assertTrue(BrambleColossusRules.shouldSweep(0, 0));
        assertTrue(BrambleColossusRules.shouldAlarm(20, 0));
        assertFalse(BrambleColossusRules.shouldAlarm(0, 0));
        UUID a = new UUID(0, 1), b = new UUID(0, 2);
        assertTrue(BrambleColossusRules.compareCandidate(4, a, 4, b) < 0);
    }

    @Test void phasesPrioritiesAndCancellationAreExplicit() {
        assertEquals(BrambleColossusRules.Phase.DISPLAY,
            BrambleColossusRules.afterMark(true, true, 0));
        assertEquals(BrambleColossusRules.Phase.THRESH,
            BrambleColossusRules.afterMark(true, true, 1));
        assertEquals(BrambleColossusRules.Phase.KEEPING,
            BrambleColossusRules.afterMark(false, true, 0));
        assertEquals(BrambleColossusRules.DisplayGate.WAIT_FOR_QUOTA,
            BrambleColossusRules.displayGate(40, false));
        assertEquals(BrambleColossusRules.DisplayGate.EMIT,
            BrambleColossusRules.displayGate(40, true));
        assertEquals(BrambleColossusRules.DisplayGate.ADVANCE,
            BrambleColossusRules.displayGate(39, false));
        assertEquals(BrambleColossusRules.Band.HAZARD,
            BrambleColossusRules.priority(true, true, true, true));
        assertEquals(BrambleColossusRules.Band.COMBAT,
            BrambleColossusRules.priority(false, true, true, true));
        for (BrambleColossusRules.Cancellation ignored : BrambleColossusRules.Cancellation.values()) {
            assertEquals(BrambleColossusRules.Phase.KEEPING,
                BrambleColossusRules.cancel(BrambleColossusRules.Phase.THRESH));
        }
    }

    @Test void everyBoundedWorkLimitAndHazardCandidateIsExact() {
        assertEquals(16, BrambleColossusRules.LEVEL_EXPENSIVE_LIMIT);
        assertEquals(8, BrambleColossusRules.LEVEL_PATH_LIMIT);
        assertEquals(96, BrambleColossusRules.LEVEL_RAW_VISIT_LIMIT);
        assertEquals(32, BrambleColossusRules.LEVEL_RESOLVE_LIMIT);
        assertEquals(32, BrambleColossusRules.LEVEL_RAY_LIMIT);
        assertEquals(1024, BrambleColossusRules.LEVEL_READ_LIMIT);
        assertEquals(128, BrambleColossusRules.LEVEL_OCCUPANCY_LIMIT);
        assertEquals(4, BrambleColossusRules.LEVEL_DISPLAY_LIMIT);
        assertEquals(8, BrambleColossusRules.LEVEL_MELEE_LIMIT);
        assertEquals(8, BrambleColossusRules.LEVEL_THORN_LIMIT);
        assertEquals(8, BrambleColossusRules.LEVEL_FEEDBACK_LIMIT);
        assertEquals(16, BrambleColossusRules.SAFE_CANDIDATES.length);
        assertArrayEquals(new int[] {1, 0, 0}, BrambleColossusRules.SAFE_CANDIDATES[0]);
        assertTrue(BrambleColossusRules.safeImproves(8, 3));
        assertFalse(BrambleColossusRules.safeImproves(3, 3));
        assertTrue(BrambleColossusRules.safeSearchAffordable(0, 0, 128, 32));
        assertFalse(BrambleColossusRules.safeSearchAffordable(1, 0, 128, 32));
        assertFalse(BrambleColossusRules.safeSearchAffordable(0, 1, 128, 32));
        assertTrue(BrambleColossusRules.safeDestination(true, 5, 4));
        assertFalse(BrambleColossusRules.safeDestination(false, 5, 0));
        assertFalse(BrambleColossusRules.safeDestination(true, 5, 5));
    }

    @Test void acceptedAttributionAndContactRetaliationHaveSeparateContracts() {
        assertTrue(BrambleColossusRules.acceptedAttribution(1, 40, true));
        assertFalse(BrambleColossusRules.acceptedAttribution(0, 0, true));
        assertFalse(BrambleColossusRules.acceptedAttribution(1, 41, true));
        assertFalse(BrambleColossusRules.acceptedAttribution(1, 0, false));
        assertTrue(BrambleColossusRules.thornContact(false, false, false, 9, 1));
        assertFalse(BrambleColossusRules.thornContact(true, false, false, 0, 1));
        assertFalse(BrambleColossusRules.thornContact(false, true, false, 0, 1));
        assertFalse(BrambleColossusRules.thornContact(false, false, true, 0, 1));
        assertFalse(BrambleColossusRules.thornContact(false, false, false, 9.01, 1));
        assertEquals(BrambleColossusRules.Phase.THRESH,
            BrambleColossusRules.afterAcceptedDamage(BrambleColossusRules.Phase.DISPLAY));
        assertEquals(BrambleColossusRules.Phase.MARK,
            BrambleColossusRules.afterAcceptedDamage(BrambleColossusRules.Phase.KEEPING));
    }

    @Test void returnTimeoutAndThirdFailureUseExactBoundaries() {
        assertFalse(BrambleColossusRules.returnTimedOut(299));
        assertTrue(BrambleColossusRules.returnTimedOut(300));
        assertEquals(300, BrambleColossusRules.RETURN_TIMEOUT_TICKS);
        assertTrue(BrambleColossusRules.ROUTE_BACKOFF_TICKS >= 100);
        assertEquals(101, BrambleColossusRules.routeBackoffSentinel());
        assertFalse(BrambleColossusRules.thirdFailure(2));
        assertTrue(BrambleColossusRules.thirdFailure(3));
    }

    @Test void loadedCoordinatesClampToThePlayableInterval() {
        assertEquals(-29, BrambleColossusRules.clampCoordinate(-100, -30.2, 40.8));
        assertEquals(39, BrambleColossusRules.clampCoordinate(100, -30.2, 40.8));
        assertEquals(7, BrambleColossusRules.clampCoordinate(7, -30.2, 40.8));
        assertEquals(-64, BrambleColossusRules.clampBuildY(-100, -64, 320));
        assertEquals(319, BrambleColossusRules.clampBuildY(500, -64, 320));
    }

    @Test void cancellationAndRetentionRejectEveryForbiddenOrdinaryState() {
        assertFalse(BrambleColossusRules.ordinarySubject(true, false, false, false, false, false, false, false, false));
        assertFalse(BrambleColossusRules.ordinarySubject(false, true, false, false, false, false, false, false, false));
        assertFalse(BrambleColossusRules.ordinarySubject(false, false, true, false, false, false, false, false, false));
        assertFalse(BrambleColossusRules.ordinarySubject(false, false, false, true, false, false, false, false, false));
        assertFalse(BrambleColossusRules.ordinarySubject(false, false, false, false, true, false, false, false, false));
        assertFalse(BrambleColossusRules.ordinarySubject(false, false, false, false, false, true, false, false, false));
        assertFalse(BrambleColossusRules.ordinarySubject(false, false, false, false, false, false, true, false, false));
        assertFalse(BrambleColossusRules.ordinarySubject(false, false, false, false, false, false, false, true, false));
        assertFalse(BrambleColossusRules.ordinarySubject(false, false, false, false, false, false, false, false, true));
        assertTrue(BrambleColossusRules.ordinarySubject(false, false, false, false, false, false, false, false, false));
        assertTrue(BrambleColossusRules.retainSubject(true, true, true, 14, 16, 39, 26));
        assertFalse(BrambleColossusRules.retainSubject(true, true, true, 14, 16.01, 0, 100));
        assertFalse(BrambleColossusRules.retainSubject(true, true, true, 14, 1, 40, 100));
    }
}
