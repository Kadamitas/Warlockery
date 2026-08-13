package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.NamiLifeRules.Activity;
import com.kadamitas.warlockery.entity.NamiLifeRules.Defense;
import org.junit.jupiter.api.Test;

final class NamiLifeRulesTest {
    @Test
    void everyDayWindowSelectsItsDistinctRoutine() {
        assertEquals(Activity.APOTHECARY, NamiLifeRules.scheduledActivity(0L));
        assertEquals(Activity.APOTHECARY, NamiLifeRules.scheduledActivity(2_999L));
        assertEquals(Activity.HERB_WALK, NamiLifeRules.scheduledActivity(3_000L));
        assertEquals(Activity.HERB_WALK, NamiLifeRules.scheduledActivity(8_999L));
        assertEquals(Activity.SOCIAL_VISIT, NamiLifeRules.scheduledActivity(9_000L));
        assertEquals(Activity.SOCIAL_VISIT, NamiLifeRules.scheduledActivity(12_999L));
        assertEquals(Activity.SHELTER, NamiLifeRules.scheduledActivity(13_000L));
        assertEquals(Activity.SHELTER, NamiLifeRules.scheduledActivity(23_999L));
        assertEquals(Activity.APOTHECARY, NamiLifeRules.scheduledActivity(24_000L));
        assertEquals(Activity.SHELTER, NamiLifeRules.scheduledActivity(-1L));
    }

    @Test
    void urgentSafetyAndExistingHouseholdWorkOverrideTheClock() {
        assertEquals(Activity.WITHDRAW, NamiLifeRules.chooseActivity(context(6_000L, true, false, false, false, false)));
        assertEquals(Activity.WITHDRAW, NamiLifeRules.chooseActivity(context(6_000L, false, true, false, false, false)));
        assertEquals(Activity.WARD, NamiLifeRules.chooseActivity(context(6_000L, false, false, true, false, false)));
        assertEquals(Activity.SPOUSE_ROUTINE, NamiLifeRules.chooseActivity(context(6_000L, false, false, false, true, false)));
        assertEquals(Activity.SHELTER, NamiLifeRules.chooseActivity(context(6_000L, false, false, false, false, true)));
    }

    @Test
    void validCommittedWorkDoesNotOscillateButSafetyStillPreemptsIt() {
        final NamiLifeRules.ActivityContext committed = new NamiLifeRules.ActivityContext(
            9_500L, 100L, Activity.HERB_WALK, 140L, true, false, false, false, false, false
        );
        assertEquals(Activity.HERB_WALK, NamiLifeRules.chooseActivity(committed));
        assertEquals(Activity.WARD, NamiLifeRules.chooseActivity(new NamiLifeRules.ActivityContext(
            9_500L, 100L, Activity.HERB_WALK, 140L, true, false, false, true, false, false
        )));
        assertEquals(Activity.SOCIAL_VISIT, NamiLifeRules.chooseActivity(new NamiLifeRules.ActivityContext(
            9_500L, 141L, Activity.HERB_WALK, 140L, true, false, false, false, false, false
        )));
    }

    @Test
    void combatDoctrineWarnsWardsWithdrawsAndReleases() {
        assertEquals(Defense.WITHDRAW, NamiLifeRules.chooseDefense(new NamiLifeRules.DefenseContext(
            true, true, false, false, false, false
        )));
        assertEquals(Defense.WARN, NamiLifeRules.chooseDefense(new NamiLifeRules.DefenseContext(
            false, false, true, false, false, false
        )));
        assertEquals(Defense.WARD, NamiLifeRules.chooseDefense(new NamiLifeRules.DefenseContext(
            false, true, false, true, false, false
        )));
        assertEquals(Defense.RELEASE, NamiLifeRules.chooseDefense(new NamiLifeRules.DefenseContext(
            false, true, false, true, true, false
        )));
        assertEquals(Defense.RELEASE, NamiLifeRules.chooseDefense(new NamiLifeRules.DefenseContext(
            false, true, false, true, false, true
        )));
        assertEquals(Defense.NONE, NamiLifeRules.chooseDefense(new NamiLifeRules.DefenseContext(
            false, false, false, false, false, false
        )));
    }

    @Test
    void decisionsAndDiscoveryAreDeterministicallyStaggeredAndBounded() {
        int maximumDecisionsOnOneTick = 0;
        for (int tick = 0; tick < NamiLifeRules.DECISION_INTERVAL_TICKS; tick++) {
            int decisions = 0;
            for (int entityId = 0; entityId < 64; entityId++) {
                decisions += NamiLifeRules.shouldDecide(tick, entityId) ? 1 : 0;
            }
            maximumDecisionsOnOneTick = Math.max(maximumDecisionsOnOneTick, decisions);
        }
        assertEquals(2, maximumDecisionsOnOneTick);
        assertTrue(NamiLifeRules.shouldDiscover(200L, 200L));
        assertFalse(NamiLifeRules.shouldDiscover(199L, 200L));
        assertEquals(256, NamiLifeRules.MAX_BLOCK_STATES_EXAMINED);
        assertEquals(16, NamiLifeRules.MAX_SOCIAL_CANDIDATES);
        assertEquals(8, NamiLifeRules.SOCIAL_RADIUS);
    }

    @Test
    void spouseAmbientCadenceIsIndependentOfStaggeredLifeDecisions() {
        for (int entityId = 1; entityId < SpouseAmbientRules.DECISION_INTERVAL_TICKS; entityId++) {
            assertFalse(NamiLifeRules.shouldDecide(20L, entityId));
            assertTrue(NamiLifeRules.shouldPollSpouseRoutine(20L, false));
        }
        assertFalse(NamiLifeRules.shouldPollSpouseRoutine(19L, false));
        assertTrue(NamiLifeRules.shouldPollSpouseRoutine(19L, true));
    }

    @Test
    void navigationRetriesStopAtThreeAndWaitAtLeastOneHundredTicks() {
        assertTrue(NamiLifeRules.mayRequestNavigation(200L, 160L, 2, 0L));
        assertFalse(NamiLifeRules.mayRequestNavigation(199L, 160L, 2, 0L));
        assertFalse(NamiLifeRules.mayRequestNavigation(200L, 160L, 3, 299L));
        assertTrue(NamiLifeRules.mayRequestNavigation(300L, 160L, 3, 300L));
        assertEquals(350L, NamiLifeRules.retryAfterFailure(250L, 3));
        assertThrows(IllegalArgumentException.class, () -> NamiLifeRules.retryAfterFailure(0L, -1));
    }

    private static NamiLifeRules.ActivityContext context(
        final long dayTime,
        final boolean hazard,
        final boolean lowHealth,
        final boolean threat,
        final boolean spouseRoutine,
        final boolean severeWeather
    ) {
        return new NamiLifeRules.ActivityContext(
            dayTime, 100L, Activity.IDLE, 0L, false, hazard, lowHealth, threat, spouseRoutine, severeWeather
        );
    }
}
