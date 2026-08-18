package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.EchoShadeRules.EchoEnd;
import com.kadamitas.warlockery.entity.EchoShadeRules.MarkObservation;
import com.kadamitas.warlockery.entity.EchoShadeRules.MotionSample;
import com.kadamitas.warlockery.entity.EchoShadeRules.Phase;
import org.junit.jupiter.api.Test;

/** Truth tables for the pure F21 Echo Shade policy. */
final class EchoShadeRulesTest {
    private static MarkObservation healthy() {
        return new MarkObservation(true, true, true, true, 16.0D, 200, 0);
    }

    // ------------------------------------------------------------ motion sampling

    @Test
    void aSampleIsClampedIntoMillisAndSurvivesNonFiniteInput() {
        assertEquals(1_500, EchoShadeRules.sampleMillis(1.5D));
        assertEquals(-1_500, EchoShadeRules.sampleMillis(-1.5D));
        assertEquals(EchoShadeRules.MAX_SAMPLE_MILLIS, EchoShadeRules.sampleMillis(99.0D),
            "one sample can never contribute more than its declared clamp");
        assertEquals(-EchoShadeRules.MAX_SAMPLE_MILLIS, EchoShadeRules.sampleMillis(-99.0D));
        assertEquals(0, EchoShadeRules.sampleMillis(Double.NaN));
        assertEquals(0, EchoShadeRules.sampleMillis(Double.POSITIVE_INFINITY));
    }

    @Test
    void aSampleRecordClampsBothAxesIndependently() {
        final MotionSample sample = new MotionSample(99_999, -99_999);
        assertEquals(EchoShadeRules.MAX_SAMPLE_MILLIS, sample.millisX());
        assertEquals(-EchoShadeRules.MAX_SAMPLE_MILLIS, sample.millisZ());
    }

    @Test
    void accumulationSaturatesAtTheWindowClampInsteadOfOverflowing() {
        assertEquals(3_000, EchoShadeRules.accumulate(1_000, 2_000));
        assertEquals(EchoShadeRules.MAX_RECORDED_MILLIS,
            EchoShadeRules.accumulate(EchoShadeRules.MAX_RECORDED_MILLIS, 4_000));
        assertEquals(-EchoShadeRules.MAX_RECORDED_MILLIS,
            EchoShadeRules.accumulate(-EchoShadeRules.MAX_RECORDED_MILLIS, -4_000));
        assertEquals(Integer.MAX_VALUE > EchoShadeRules.MAX_RECORDED_MILLIS
            ? EchoShadeRules.MAX_RECORDED_MILLIS : 0,
            EchoShadeRules.accumulate(Integer.MAX_VALUE, Integer.MAX_VALUE),
            "accumulation is done in long arithmetic so it cannot wrap");
    }

    @Test
    void aSampleIsDueOnlyWhileTheWindowAndTheSampleCapBothAllowIt() {
        assertTrue(EchoShadeRules.sampleDue(0, 0));
        assertFalse(EchoShadeRules.sampleDue(3, 0), "an unelapsed interval is never due");
        assertFalse(EchoShadeRules.sampleDue(0, EchoShadeRules.MAX_SAMPLES),
            "the per-window sample cap binds");
    }

    // ------------------------------------------------------------ the mirror answer

    @Test
    void theAnswerNegatesTheRecordedGestureSoTheShadeStepsToItsReflection() {
        assertTrue(EchoShadeRules.answerOffset(EchoShadeRules.MAX_RECORDED_MILLIS) < 0,
            "a player who walked east is answered from the west");
        assertTrue(EchoShadeRules.answerOffset(-EchoShadeRules.MAX_RECORDED_MILLIS) > 0,
            "a player who walked west is answered from the east");
        assertEquals(0, EchoShadeRules.answerOffset(0), "no gesture produces no offset");
    }

    @Test
    void theAnswerIsAlwaysInsideTheDeclaredOffsetBoxHoweverFarThePlayerWent() {
        for (final int recorded : new int[]{
            1, 250, 5_000, EchoShadeRules.MAX_RECORDED_MILLIS, Integer.MAX_VALUE,
            -1, -250, -5_000, -EchoShadeRules.MAX_RECORDED_MILLIS, Integer.MIN_VALUE
        }) {
            final int offset = EchoShadeRules.answerOffset(recorded);
            assertTrue(Math.abs(offset) <= EchoShadeRules.MAX_ANSWER_OFFSET,
                "no recorded motion may produce an unbounded destination: " + recorded);
        }
    }

    @Test
    void anyNonZeroGestureProducesAtLeastOneBlockOfAnswer() {
        assertEquals(-1, EchoShadeRules.answerOffset(1),
            "a tiny gesture still moves the shade rather than resolving to a no-op offset");
        assertEquals(1, EchoShadeRules.answerOffset(-1));
    }

    @Test
    void aMotionlessMarkIsNotAnswerableSoNoGestureIsInvented() {
        assertFalse(EchoShadeRules.answerable(0, 0));
        assertFalse(EchoShadeRules.answerable(
            EchoShadeRules.MIN_ANSWERABLE_MILLIS - 1, EchoShadeRules.MIN_ANSWERABLE_MILLIS - 1));
        assertTrue(EchoShadeRules.answerable(EchoShadeRules.MIN_ANSWERABLE_MILLIS, 0));
        assertTrue(EchoShadeRules.answerable(0, -EchoShadeRules.MIN_ANSWERABLE_MILLIS),
            "either axis alone is enough of a gesture to answer");
    }

    // ------------------------------------------------------------ episode retention

    @Test
    void aHealthyMarkKeepsTheEchoRunning() {
        assertEquals(EchoEnd.NONE, EchoShadeRules.echoEnd(healthy()));
    }

    @Test
    void echoEndPriorityIsExactAndOrdered() {
        assertEquals(EchoEnd.MARK_LOST, EchoShadeRules.echoEnd(
            new MarkObservation(false, true, true, true, 16.0D, 200, 0)));
        assertEquals(EchoEnd.DIMENSION, EchoShadeRules.echoEnd(
            new MarkObservation(true, false, true, true, 16.0D, 200, 0)));
        assertEquals(EchoEnd.MARK_LOST, EchoShadeRules.echoEnd(
            new MarkObservation(true, true, false, true, 16.0D, 200, 0)));
        assertEquals(EchoEnd.MARK_LOST, EchoShadeRules.echoEnd(
            new MarkObservation(true, true, true, false, 16.0D, 200, 0)),
            "a creative or spectator mark is no longer an eligible subject");
        assertEquals(EchoEnd.OUT_OF_RANGE, EchoShadeRules.echoEnd(
            new MarkObservation(true, true, true, true,
                EchoShadeRules.MARK_RELEASE_RANGE_SQUARED + 1.0D, 200, 0)));
        assertEquals(EchoEnd.ROUTE_FAILURE, EchoShadeRules.echoEnd(
            new MarkObservation(true, true, true, true, 16.0D, 200,
                ApparitionEpisodeRules.MAX_ROUTE_FAILURES)));
        assertEquals(EchoEnd.EXPIRED, EchoShadeRules.echoEnd(
            new MarkObservation(true, true, true, true, 16.0D, 0, 0)));
    }

    @Test
    void aNewEchoNeedsBothAnElapsedCooldownAndNoSurvivingMark() {
        assertTrue(EchoShadeRules.echoStartAllowed(0, false));
        assertFalse(EchoShadeRules.echoStartAllowed(1, false), "the cadence binds");
        assertFalse(EchoShadeRules.echoStartAllowed(0, true),
            "a surviving mark forbids a second simultaneous echo");
    }

    // ------------------------------------------------------------ the single strike

    @Test
    void theStrikeGateNeedsAnUnspentAttemptReachVisibilityAndAnOpenWindow() {
        assertTrue(EchoShadeRules.strikeAllowed(0, 1.0D, true, 10));
        assertFalse(EchoShadeRules.strikeAllowed(EchoShadeRules.MAX_STRIKES, 1.0D, true, 10),
            "the one attempt per echo is spent");
        assertFalse(EchoShadeRules.strikeAllowed(0,
            EchoShadeRules.STRIKE_BAND_SQUARED + 1.0D, true, 10), "out of reach");
        assertFalse(EchoShadeRules.strikeAllowed(0, 1.0D, false, 10),
            "a shade that cannot see its mark never swings");
        assertFalse(EchoShadeRules.strikeAllowed(0, 1.0D, true, 0), "the window has closed");
    }

    @Test
    void aShadeStrippedOfItsAttackAttributeContributesNothing() {
        assertEquals(0.0F, EchoShadeRules.strikeDamage(0.0F));
        assertEquals(0.0F, EchoShadeRules.strikeDamage(-4.0F),
            "a negative attribute never becomes healing");
        assertEquals(3.0F, EchoShadeRules.strikeDamage(3.0F));
    }

    @Test
    void onlyTheMarkOfAnOpenStrikeWindowMayEverBeAttacked() {
        assertTrue(EchoShadeRules.canAttack(true, true));
        assertFalse(EchoShadeRules.canAttack(false, true),
            "outside the runtime-owned window nothing may be attacked");
        assertFalse(EchoShadeRules.canAttack(true, false),
            "a bystander is never a legal subject even mid strike");
        assertFalse(EchoShadeRules.canAttack(false, false));
    }

    @Test
    void theAnswerBandIsReachedOnlyInsideItsDeclaredRadius() {
        assertTrue(EchoShadeRules.answerReached(0.0D));
        assertTrue(EchoShadeRules.answerReached(EchoShadeRules.ANSWER_BAND_SQUARED));
        assertFalse(EchoShadeRules.answerReached(EchoShadeRules.ANSWER_BAND_SQUARED + 0.1D));
    }

    // ------------------------------------------------------------ priority

    @Test
    void anEscapableHazardPreemptsEverySpeciesPhase() {
        for (final Phase phase : Phase.values()) {
            assertTrue(EchoShadeRules.hazardPreempts(phase, true),
                "a hazard outranks " + phase);
            assertFalse(EchoShadeRules.hazardPreempts(phase, false));
            assertEquals(0, EchoShadeRules.priority(phase, true));
        }
    }

    @Test
    void speciesPriorityIsAStrictOrderWithTheStrikeHighest() {
        assertTrue(EchoShadeRules.priority(Phase.STRIKE, false)
            < EchoShadeRules.priority(Phase.ANSWER, false));
        assertTrue(EchoShadeRules.priority(Phase.ANSWER, false)
            < EchoShadeRules.priority(Phase.RECORD, false));
        assertTrue(EchoShadeRules.priority(Phase.RECORD, false)
            < EchoShadeRules.priority(Phase.RECOVER, false));
        assertTrue(EchoShadeRules.priority(Phase.RECOVER, false)
            < EchoShadeRules.priority(Phase.WATCH, false));
    }

    @Test
    void thePhaseSetContainsNoBindingDefenceOrAuraRung() {
        assertEquals(5, Phase.values().length,
            "an Echo Shade watches, records, answers, strikes and recovers, and does nothing else");
    }
}
