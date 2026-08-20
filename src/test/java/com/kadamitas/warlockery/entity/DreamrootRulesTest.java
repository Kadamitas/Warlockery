package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DreamrootRulesTest {
    @Test void approvedThresholdDreamAndSustainNumbersAreExact() {
        assertArrayEquals(new DreamrootRules.Phase[] {
            DreamrootRules.Phase.ROOTED, DreamrootRules.Phase.STIR, DreamrootRules.Phase.THRESHOLD,
            DreamrootRules.Phase.DREAM, DreamrootRules.Phase.SUBSIDE, DreamrootRules.Phase.ESCAPE
        }, DreamrootRules.Phase.values());
        assertEquals(6.0, DreamrootRules.OUTER_RADIUS);
        assertEquals(3.0, DreamrootRules.INNER_RADIUS);
        assertEquals(8, DreamrootRules.RAW_CANDIDATE_CAP);
        assertEquals(100, DreamrootRules.dreamDuration(0));
        assertEquals(120, DreamrootRules.dreamDuration(1));
        assertEquals(200, DreamrootRules.dreamDuration(5));
        assertEquals(1.0F, DreamrootRules.sustainAmount(0));
        assertEquals(3.5F, DreamrootRules.sustainAmount(5));
    }

    @Test void bulbBatchesPreserveDeferredTotal() {
        assertEquals(4, DreamrootRules.BULBS_PER_WAKE);
        assertEquals(4, MinedrakeCombatRules.BULB_PER_WAKE_BATCH);
        assertEquals(4, DreamrootRules.bulbsThisWake(64, 8));
        assertEquals(3, DreamrootRules.bulbsThisWake(3, 8));
        assertEquals(0, DreamrootRules.bulbsThisWake(3, 0));
        assertTrue(DreamrootRules.freshAttribution(40));
        assertFalse(DreamrootRules.freshAttribution(41));
    }


    @Test void deniedDreamTokenDefersAtTheThreshold() {
        assertEquals(DreamrootRules.Phase.THRESHOLD, DreamrootRules.afterDreamToken(false));
        assertEquals(DreamrootRules.Phase.DREAM, DreamrootRules.afterDreamToken(true));
    }

    @Test void ownerHintOnlyBreaksAnExactDistanceTie() {
        final java.util.UUID low = new java.util.UUID(0L, 1L);
        final java.util.UUID hint = new java.util.UUID(0L, 2L);
        assertTrue(DreamrootRules.compareCandidate(4.0D, hint, hint, 4.0D, low) < 0);
        assertTrue(DreamrootRules.compareCandidate(4.01D, hint, hint, 4.0D, low) > 0);
        assertTrue(DreamrootRules.compareCandidate(4.0D, null, hint, 4.0D, low) > 0);
    }

    @Test void escapeDestinationClearsOnlyOnExactThirdFailure() {
        assertFalse(DreamrootRules.clearEscapeDestination(1));
        assertFalse(DreamrootRules.clearEscapeDestination(2));
        assertTrue(DreamrootRules.clearEscapeDestination(3));
    }


    @Test void hazardAndReachOnlyCombatBudgetsAreExact() {
        assertEquals(20, DreamrootRules.HAZARD_CADENCE_TICKS);
        assertEquals(18, DreamrootRules.HAZARD_FOOTPRINT_READ_CAP);
        assertEquals(16, DreamrootRules.SAFE_CANDIDATE_CAP);
        assertEquals(128, DreamrootRules.SAFE_READ_CAP);
        assertEquals(8, DreamrootRules.OCCUPANCY_VISITS_PER_CANDIDATE);
        assertEquals(32, DreamrootRules.OCCUPANCY_VISITS_PER_SEARCH);
        assertEquals(100, DreamrootRules.COMBAT_WINDOW_TICKS);
        assertEquals(20, DreamrootRules.MELEE_CADENCE_TICKS);
    }
}
