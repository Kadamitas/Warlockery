package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.SpectreRules.HauntEnd;
import com.kadamitas.warlockery.entity.SpectreRules.Phase;
import com.kadamitas.warlockery.entity.SpectreRules.WitnessObservation;
import org.junit.jupiter.api.Test;

/** Truth tables for the pure F21 Spectre policy. */
final class SpectreRulesTest {
    private static WitnessObservation healthy() {
        return new WitnessObservation(true, true, true, true, 4.0D, 300, 0);
    }

    // ------------------------------------------------------------ episode retention

    @Test
    void aHealthyWitnessKeepsTheHauntingRunning() {
        assertEquals(HauntEnd.NONE, SpectreRules.hauntEnd(healthy()));
    }

    @Test
    void hauntEndPriorityIsExactAndOrdered() {
        assertEquals(HauntEnd.WITNESS_LOST, SpectreRules.hauntEnd(
            new WitnessObservation(false, true, true, true, 4.0D, 300, 0)));
        assertEquals(HauntEnd.DIMENSION, SpectreRules.hauntEnd(
            new WitnessObservation(true, false, true, true, 4.0D, 300, 0)));
        assertEquals(HauntEnd.WITNESS_LOST, SpectreRules.hauntEnd(
            new WitnessObservation(true, true, false, true, 4.0D, 300, 0)));
        assertEquals(HauntEnd.WITNESS_LOST, SpectreRules.hauntEnd(
            new WitnessObservation(true, true, true, false, 4.0D, 300, 0)),
            "a creative or spectator witness is no longer an eligible subject");
        assertEquals(HauntEnd.OUT_OF_RANGE, SpectreRules.hauntEnd(
            new WitnessObservation(true, true, true, true,
                SpectreRules.WITNESS_RELEASE_RANGE_SQUARED + 1.0D, 300, 0)));
        assertEquals(HauntEnd.ROUTE_FAILURE, SpectreRules.hauntEnd(
            new WitnessObservation(true, true, true, true, 4.0D, 300,
                ApparitionEpisodeRules.MAX_ROUTE_FAILURES)));
        assertEquals(HauntEnd.EXPIRED, SpectreRules.hauntEnd(
            new WitnessObservation(true, true, true, true, 4.0D, 0, 0)));
    }

    @Test
    void aNewHauntingNeedsBothAnElapsedCooldownAndNoSurvivingWitness() {
        assertTrue(SpectreRules.hauntStartAllowed(0, false));
        assertFalse(SpectreRules.hauntStartAllowed(1, false), "the cadence binds");
        assertFalse(SpectreRules.hauntStartAllowed(0, true),
            "a surviving witness forbids a second simultaneous haunting");
    }

    // ------------------------------------------------------------ telegraph

    @Test
    void aTelegraphIsDueOnlyWhileTheIntervalAndTheCapBothAllowIt() {
        assertTrue(SpectreRules.telegraphDue(0, 0));
        assertFalse(SpectreRules.telegraphDue(5, 0), "an unelapsed interval is never due");
        assertFalse(SpectreRules.telegraphDue(0, SpectreRules.MAX_TELEGRAPHS),
            "the per-haunting telegraph cap binds");
    }

    @Test
    void aFreshlyLoadedZeroIntervalNeverReplaysTheTelegraphOnLoad() {
        assertEquals(SpectreRules.TELEGRAPH_INTERVAL_TICKS,
            SpectreRules.resetTelegraphIntervalOnLoad(0),
            "a persisted zero interval is restored to a full interval, never read as due");
        assertEquals(SpectreRules.TELEGRAPH_INTERVAL_TICKS,
            SpectreRules.resetTelegraphIntervalOnLoad(-4));
        assertEquals(7, SpectreRules.resetTelegraphIntervalOnLoad(7),
            "a genuine partial interval is preserved across the reload");
        assertEquals(SpectreRules.TELEGRAPH_INTERVAL_TICKS,
            SpectreRules.resetTelegraphIntervalOnLoad(9_999));
    }

    @Test
    void telegraphsRemainingNeverGoesNegative() {
        assertEquals(SpectreRules.MAX_TELEGRAPHS, SpectreRules.telegraphsRemaining(0));
        assertEquals(0, SpectreRules.telegraphsRemaining(SpectreRules.MAX_TELEGRAPHS));
        assertEquals(0, SpectreRules.telegraphsRemaining(999));
        assertEquals(SpectreRules.MAX_TELEGRAPHS, SpectreRules.telegraphsRemaining(-5));
    }

    @Test
    void theManifestationGraduatesOnlyOnceItsVisibleWindowHasElapsed() {
        assertFalse(SpectreRules.manifestationGraduates(1),
            "a Spectre may not skip its telegraph because the witness walked in early");
        assertTrue(SpectreRules.manifestationGraduates(0));
    }

    // ------------------------------------------------------------ the single dread

    @Test
    void theDreadGateNeedsAnUnspentDeliveryBandVisibilityAndAnOpenWindow() {
        assertTrue(SpectreRules.dreadAllowed(0, 1.0D, true, 10));
        assertFalse(SpectreRules.dreadAllowed(SpectreRules.MAX_DREADS, 1.0D, true, 10),
            "the one delivery per haunting is spent and there is no refresh path");
        assertFalse(SpectreRules.dreadAllowed(0,
            SpectreRules.DREAD_BAND_SQUARED + 1.0D, true, 10), "outside the dread band");
        assertFalse(SpectreRules.dreadAllowed(0, 1.0D, false, 10),
            "an unseen witness receives nothing");
        assertFalse(SpectreRules.dreadAllowed(0, 1.0D, true, 0), "the window has closed");
    }

    @Test
    void aWitnessWhoLingersInTheBandStillReceivesNothingFurther() {
        assertTrue(SpectreRules.dreadAllowed(0, 0.5D, true, 40));
        assertFalse(SpectreRules.dreadAllowed(1, 0.5D, true, 40),
            "the replaced behavior refreshed forever; the redesign delivers exactly once");
    }

    @Test
    void aSpectreNeverAttacksUnderAnyCircumstances() {
        assertFalse(SpectreRules.canAttack());
    }

    // ------------------------------------------------------------ preserved effects

    @Test
    void theTwoPreservedEffectsKeepTheirShippedStrengths() {
        assertEquals(0, SpectreRules.DARKNESS_AMPLIFIER,
            "the dread never becomes stronger than the behavior it replaces");
        assertEquals(0, SpectreRules.WEAKNESS_AMPLIFIER);
        assertTrue(SpectreRules.DARKNESS_TICKS > 0 && SpectreRules.WEAKNESS_TICKS > 0);
    }

    // ------------------------------------------------------------ priority

    @Test
    void anEscapableHazardPreemptsEverySpeciesPhase() {
        for (final Phase phase : Phase.values()) {
            assertTrue(SpectreRules.hazardPreempts(phase, true), "a hazard outranks " + phase);
            assertFalse(SpectreRules.hazardPreempts(phase, false));
            assertEquals(0, SpectreRules.priority(phase, true));
        }
    }

    @Test
    void speciesPriorityIsAStrictOrderWithTheDreadHighest() {
        assertTrue(SpectreRules.priority(Phase.DREAD, false)
            < SpectreRules.priority(Phase.MANIFEST, false));
        assertTrue(SpectreRules.priority(Phase.MANIFEST, false)
            < SpectreRules.priority(Phase.FADE, false));
        assertTrue(SpectreRules.priority(Phase.FADE, false)
            < SpectreRules.priority(Phase.DRIFT, false));
    }

    @Test
    void thePhaseSetContainsNoStrikeRecordOrBindingRung() {
        assertEquals(4, Phase.values().length,
            "a Spectre drifts, manifests, dreads and fades, and does nothing else");
    }
}
