package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MandrakeRulesTest {
    @Test void approvedBoundariesAndPhasesAreExact() {
        assertArrayEquals(new MandrakeRules.Phase[] {
            MandrakeRules.Phase.SEEDED, MandrakeRules.Phase.DISTURBED, MandrakeRules.Phase.WAIL,
            MandrakeRules.Phase.FLAIL, MandrakeRules.Phase.RESETTLE, MandrakeRules.Phase.ESCAPE
        }, MandrakeRules.Phase.values());
        assertEquals(8.0, MandrakeRules.WAIL_RADIUS);
        assertEquals(8, MandrakeRules.RAW_CANDIDATE_CAP);
        assertEquals(4, MandrakeRules.WAIL_RECIPIENT_CAP);
        assertEquals(120, MandrakeRules.WAIL_DURATION_TICKS);
        assertEquals(600, MandrakeRules.WAIL_COOLDOWN_TICKS);
        assertTrue(MandrakeRules.freshAttribution(0));
        assertTrue(MandrakeRules.freshAttribution(40));
        assertFalse(MandrakeRules.freshAttribution(41));
    }

    @Test void cooldownAndRouteArithmeticClampAndStayBounded() {
        assertEquals(0, MandrakeRules.clampRemaining(-1, 600));
        assertEquals(600, MandrakeRules.clampRemaining(Integer.MAX_VALUE, 600));
        assertTrue(MandrakeRules.routeDue(0));
        assertFalse(MandrakeRules.routeDue(1));
        assertTrue(MandrakeRules.thirdRouteFailure(3));
        assertFalse(MandrakeRules.thirdRouteFailure(2));
        assertTrue(MandrakeRules.boundedCadenceSentinel() <= 20_000);
        assertNotEquals(Long.MAX_VALUE, MandrakeRules.boundedCadenceSentinel());
    }

    @Test void deniedWailTokenDefersWithoutAdvancingTheEpisode() {
        assertEquals(MandrakeRules.Phase.WAIL, MandrakeRules.afterWailToken(false));
        assertEquals(MandrakeRules.Phase.FLAIL, MandrakeRules.afterWailToken(true));
    }

    @Test void acceptedDamageStartsOnlyFromRootedPhasesAndNeverRestartsAnEpisode() {
        assertEquals(MandrakeRules.Phase.DISTURBED,MandrakeRules.afterAcceptedDamage(MandrakeRules.Phase.SEEDED));
        assertEquals(MandrakeRules.Phase.DISTURBED,MandrakeRules.afterAcceptedDamage(MandrakeRules.Phase.RESETTLE));
        assertEquals(MandrakeRules.Phase.DISTURBED,MandrakeRules.afterAcceptedDamage(MandrakeRules.Phase.DISTURBED));
        assertEquals(MandrakeRules.Phase.FLAIL,MandrakeRules.afterAcceptedDamage(MandrakeRules.Phase.FLAIL));
        assertEquals(MandrakeRules.Phase.WAIL,MandrakeRules.afterAcceptedDamage(MandrakeRules.Phase.WAIL));
        assertEquals(MandrakeRules.Phase.ESCAPE,MandrakeRules.afterAcceptedDamage(MandrakeRules.Phase.ESCAPE));
        assertTrue(MandrakeRules.startsDamageEpisode(MandrakeRules.Phase.SEEDED));
        assertTrue(MandrakeRules.startsDamageEpisode(MandrakeRules.Phase.RESETTLE));
        assertFalse(MandrakeRules.startsDamageEpisode(MandrakeRules.Phase.DISTURBED));
        assertFalse(MandrakeRules.startsDamageEpisode(MandrakeRules.Phase.FLAIL));
        assertTrue(MandrakeRules.mayBindDamageSubject(MandrakeRules.Phase.DISTURBED));
        assertTrue(MandrakeRules.mayBindDamageSubject(MandrakeRules.Phase.FLAIL));
        assertFalse(MandrakeRules.mayBindDamageSubject(MandrakeRules.Phase.WAIL));
        assertFalse(MandrakeRules.mayBindDamageSubject(MandrakeRules.Phase.ESCAPE));
    }

    @Test void extractionWithoutASubjectKeepsTheFullFlailWindow() {
        assertFalse(MandrakeRules.flailComplete(99, false, false));
        assertTrue(MandrakeRules.flailComplete(100, false, false));
        assertTrue(MandrakeRules.flailComplete(1, true, false));
        assertFalse(MandrakeRules.flailComplete(1, true, true));
    }

    @Test void escapeDestinationClearsOnlyOnExactThirdFailure() {
        assertFalse(MandrakeRules.clearEscapeDestination(1));
        assertFalse(MandrakeRules.clearEscapeDestination(2));
        assertTrue(MandrakeRules.clearEscapeDestination(3));
    }


    @Test void hazardSearchAndPathRetryBudgetsAreExact() {
        assertEquals(20, MandrakeRules.HAZARD_CADENCE_TICKS);
        assertEquals(18, MandrakeRules.HAZARD_FOOTPRINT_READ_CAP);
        assertEquals(16, MandrakeRules.SAFE_CANDIDATE_CAP);
        assertEquals(128, MandrakeRules.SAFE_READ_CAP);
        assertEquals(8, MandrakeRules.OCCUPANCY_VISITS_PER_CANDIDATE);
        assertEquals(32, MandrakeRules.OCCUPANCY_VISITS_PER_SEARCH);
        assertEquals(100, MandrakeRules.backoffAfterFailure(2));
        assertEquals(0, MandrakeRules.backoffAfterFailure(1));
        assertEquals(100, MandrakeRules.backoffAfterFailure(3));
    }
}
