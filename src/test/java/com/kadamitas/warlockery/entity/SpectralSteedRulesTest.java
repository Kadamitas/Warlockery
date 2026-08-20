package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.SpectralSteedRules.Concern;
import com.kadamitas.warlockery.entity.SpectralSteedRules.Gait;
import com.kadamitas.warlockery.entity.behavior.PriorityLadder;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The whole decision surface of F27, plus the proof that the hand-rolled per-tick concern chain and
 * the shared {@link PriorityLadder} agree on every input the runtime can produce.
 */
final class SpectralSteedRulesTest {

    // ------------------------------------------------------- concern ordering

    /**
     * The reason the tick does not call {@code PriorityLadder.select} is cost, not disagreement, so
     * the two have to be shown to answer identically across the entire input space rather than on a
     * few sampled cases.
     */
    @Test
    void theHandRolledChainAgreesWithTheSharedLadderOnEveryPossibleInput() {
        final PriorityLadder<Concern> ladder = SpectralSteedRules.ladder();
        for (int bits = 0; bits < 16; bits++) {
            final boolean hazard = (bits & 1) != 0;
            final boolean balking = (bits & 2) != 0;
            final boolean carrying = (bits & 4) != 0;
            final boolean resting = (bits & 8) != 0;
            final List<PriorityLadder.Rung<Concern, Void>> rungs = List.of(
                PriorityLadder.Rung.inspectOnly(Concern.HAZARD, _ -> hazard),
                PriorityLadder.Rung.inspectOnly(Concern.BALK, _ -> balking),
                PriorityLadder.Rung.inspectOnly(Concern.CARRY, _ -> carrying),
                PriorityLadder.Rung.inspectOnly(Concern.REST, _ -> resting),
                PriorityLadder.Rung.inspectOnly(Concern.IDLE, _ -> true)
            );
            final Concern viaLadder = ladder.select(rungs, null).orElseThrow().concern();
            final Concern viaChain =
                SpectralSteedRules.chooseConcern(hazard, balking, carrying, resting);
            assertEquals(viaLadder, viaChain, "input bits " + bits);
        }
    }

    @Test
    void hazardOutranksEveryOtherConcernAndIdleIsOutrankedByEveryOther() {
        final PriorityLadder<Concern> ladder = SpectralSteedRules.ladder();
        for (final Concern other : Concern.values()) {
            if (other != Concern.HAZARD) {
                assertTrue(ladder.outranks(Concern.HAZARD, other), other.name());
            }
            if (other != Concern.IDLE) {
                assertTrue(ladder.outranks(other, Concern.IDLE), other.name());
            }
        }
    }

    @Test
    void aBalkingSteedIsNotAlsoAskedToCarryOrRest() {
        assertEquals(Concern.BALK, SpectralSteedRules.chooseConcern(false, true, true, true));
        assertEquals(Concern.CARRY, SpectralSteedRules.chooseConcern(false, false, true, true));
        assertEquals(Concern.REST, SpectralSteedRules.chooseConcern(false, false, false, true));
        assertEquals(Concern.IDLE, SpectralSteedRules.chooseConcern(false, false, false, false));
    }

    // ------------------------------------------------------------------ gaits

    @Test
    void anUpshiftWaitsOutTheHoldWindowAndThenMovesExactlyOneBand() {
        assertEquals(Gait.HALT, SpectralSteedRules.nextGait(Gait.HALT, Gait.SPRINT, 1, false));
        assertEquals(Gait.WALK, SpectralSteedRules.nextGait(Gait.HALT, Gait.SPRINT, 0, false));
        assertEquals(Gait.TROT, SpectralSteedRules.nextGait(Gait.WALK, Gait.SPRINT, 0, false));
    }

    @Test
    void aDownshiftNeverWaitsAndAnUrgentOneDropsStraightToTheAskedBand() {
        assertEquals(Gait.CANTER, SpectralSteedRules.nextGait(Gait.SPRINT, Gait.HALT, 9, false));
        assertEquals(Gait.HALT, SpectralSteedRules.nextGait(Gait.SPRINT, Gait.HALT, 9, true));
    }

    @Test
    void anUrgentRequestCanNeverBeUsedToJumpUpwards() {
        assertEquals(Gait.WALK, SpectralSteedRules.nextGait(Gait.HALT, Gait.SPRINT, 0, true));
        assertEquals(Gait.HALT, SpectralSteedRules.nextGait(Gait.HALT, Gait.SPRINT, 5, true));
    }

    @Test
    void aBandIsAlwaysCappedByItsCeiling() {
        assertEquals(Gait.CANTER, SpectralSteedRules.capped(Gait.SPRINT, Gait.CANTER));
        assertEquals(Gait.WALK, SpectralSteedRules.capped(Gait.WALK, Gait.CANTER));
    }

    @Test
    void aNightmareReachesABandAnUnmaturedPaleSteedCannot() {
        assertEquals(Gait.SPRINT, SpectralSteedRules.ceilingGait(CreatureKind.NIGHTMARE, 0, 0));
        assertEquals(Gait.CANTER, SpectralSteedRules.ceilingGait(CreatureKind.PALE_STEED, 0, 0));
        assertEquals(Gait.SPRINT, SpectralSteedRules.ceilingGait(
            CreatureKind.PALE_STEED, SpectralSteedRules.PALE_SPRINT_BOND, 0));
    }

    @Test
    void exhaustionCapsBothSteedsAtATrotHoweverMaturedTheyAre() {
        assertEquals(Gait.TROT, SpectralSteedRules.ceilingGait(
            CreatureKind.NIGHTMARE, SpectralSteedRules.MAX_BOND, SpectralSteedRules.EXHAUSTED_FATIGUE));
        assertEquals(Gait.TROT, SpectralSteedRules.ceilingGait(
            CreatureKind.PALE_STEED, SpectralSteedRules.MAX_BOND, SpectralSteedRules.MAX_FATIGUE));
    }

    @Test
    void reverseInputNeverAsksForMoreThanAWalkAndNoInputAsksForAHalt() {
        assertEquals(Gait.HALT, SpectralSteedRules.desiredGait(CreatureKind.PALE_STEED, 0.0F, 0.0F));
        assertEquals(Gait.WALK, SpectralSteedRules.desiredGait(CreatureKind.PALE_STEED, -1.0F, 0.0F));
        assertEquals(Gait.WALK, SpectralSteedRules.desiredGait(CreatureKind.NIGHTMARE, -1.0F, 0.0F));
    }

    @Test
    void fullForwardInputAsksForTheSpeciesTopBand() {
        assertEquals(Gait.SPRINT, SpectralSteedRules.desiredGait(CreatureKind.NIGHTMARE, 1.0F, 0.0F));
        assertEquals(Gait.CANTER, SpectralSteedRules.desiredGait(CreatureKind.PALE_STEED, 1.0F, 0.0F));
        assertEquals(Gait.TROT, SpectralSteedRules.desiredGait(CreatureKind.PALE_STEED, 0.4F, 0.0F));
    }

    @Test
    void gaitSpeedFactorsRiseWithTheBandAndAHaltIsAStandstill() {
        assertEquals(0.0F, SpectralSteedRules.gaitSpeedFactor(Gait.HALT));
        float previous = -1.0F;
        for (final Gait gait : Gait.values()) {
            final float factor = SpectralSteedRules.gaitSpeedFactor(gait);
            assertTrue(factor > previous, gait.name());
            previous = factor;
        }
    }

    // -------------------------------------------------------- fatigue and bond

    @Test
    void haltingAndWalkingRecoverWhileTheTopBandsCost() {
        assertTrue(SpectralSteedRules.fatigueDelta(CreatureKind.PALE_STEED, Gait.HALT) < 0);
        assertTrue(SpectralSteedRules.fatigueDelta(CreatureKind.PALE_STEED, Gait.WALK) < 0);
        assertEquals(0, SpectralSteedRules.fatigueDelta(CreatureKind.PALE_STEED, Gait.TROT));
        assertTrue(SpectralSteedRules.fatigueDelta(CreatureKind.PALE_STEED, Gait.SPRINT) > 0);
        assertTrue(SpectralSteedRules.restFatigueDelta()
            < SpectralSteedRules.fatigueDelta(CreatureKind.PALE_STEED, Gait.HALT));
    }

    @Test
    void aNightmarePaysMoreForItsTopBandThanAPaleSteedPaysForOne() {
        assertTrue(SpectralSteedRules.fatigueDelta(CreatureKind.NIGHTMARE, Gait.SPRINT)
            > SpectralSteedRules.fatigueDelta(CreatureKind.PALE_STEED, Gait.SPRINT));
    }

    @Test
    void bondIsEarnedOnlyByRidingTheOwnerOrCompletingARestAndNeverMoreThanOne() {
        assertEquals(1, SpectralSteedRules.bondGain(true, false, 0));
        assertEquals(1, SpectralSteedRules.bondGain(false, true, 0));
        assertEquals(0, SpectralSteedRules.bondGain(false, false, 0));
        assertEquals(SpectralSteedRules.MAX_BOND_PER_TICK, SpectralSteedRules.bondGain(true, true, 0));
    }

    @Test
    void theEpisodeCapStopsRidingBondButNeverStopsRestBond() {
        final int full = SpectralSteedRules.MAX_BOND_PER_EPISODE;
        assertEquals(0, SpectralSteedRules.bondGain(true, false, full));
        assertEquals(1, SpectralSteedRules.bondGain(false, true, full),
            "a rest is already limited by its own cooldown and must not be gated by a ride counter");
    }

    // ------------------------------------------------------------ balk and fear

    @Test
    void higherBondShortensAStartleButNeverRemovesIt() {
        final int green = SpectralSteedRules.balkTicks(CreatureKind.PALE_STEED, 0);
        final int matured = SpectralSteedRules.balkTicks(
            CreatureKind.PALE_STEED, SpectralSteedRules.MAX_BOND);
        assertTrue(matured < green);
        assertTrue(matured >= SpectralSteedRules.MIN_BALK_TICKS);
        assertTrue(SpectralSteedRules.balkTicks(CreatureKind.NIGHTMARE, 0) < green,
            "a Nightmare recovers from a startle faster than a Pale Steed");
    }

    @Test
    void aBalkingSteedAcceptsNoSteeringAtAll() {
        assertEquals(0.0, SpectralSteedRules.riddenInputScale(true));
        assertEquals(1.0, SpectralSteedRules.riddenInputScale(false));
    }

    @Test
    void onlyABondedNightmareUnderThreatWithNoCooldownMayWarn() {
        assertTrue(SpectralSteedRules.warningWarranted(CreatureKind.NIGHTMARE, true,
            SpectralSteedRules.NIGHTMARE_WARNING_BOND, 0));
        assertFalse(SpectralSteedRules.warningWarranted(CreatureKind.PALE_STEED, true,
            SpectralSteedRules.MAX_BOND, 0), "a Pale Steed never warns");
        assertFalse(SpectralSteedRules.warningWarranted(CreatureKind.NIGHTMARE, false,
            SpectralSteedRules.MAX_BOND, 0), "an unthreatened Nightmare never warns");
        assertFalse(SpectralSteedRules.warningWarranted(CreatureKind.NIGHTMARE, true,
            SpectralSteedRules.NIGHTMARE_WARNING_BOND - 1, 0), "an immature bond never warns");
        assertFalse(SpectralSteedRules.warningWarranted(CreatureKind.NIGHTMARE, true,
            SpectralSteedRules.MAX_BOND, 1), "a running cooldown never warns");
    }

    @Test
    void everyDeclaredExclusionActuallyExcludes() {
        assertTrue(SpectralSteedRules.warningReaches(true, true, false, false, false, false));
        assertFalse(SpectralSteedRules.warningReaches(false, true, false, false, false, false),
            "dead");
        assertFalse(SpectralSteedRules.warningReaches(true, false, false, false, false, false),
            "untagged");
        assertFalse(SpectralSteedRules.warningReaches(true, true, true, false, false, false),
            "owner");
        assertFalse(SpectralSteedRules.warningReaches(true, true, false, true, false, false),
            "ally of the same owner");
        assertFalse(SpectralSteedRules.warningReaches(true, true, false, false, true, false),
            "another Warlockery creature");
        assertFalse(SpectralSteedRules.warningReaches(true, true, false, false, false, true),
            "a player who cannot be fought");
    }

    // ------------------------------------------------------------------ guards

    @Test
    void everyKindGuardedEntryPointRefusesANonSteed() {
        assertFalse(SpectralSteedRules.isSteed(CreatureKind.OWL));
        assertTrue(SpectralSteedRules.isSteed(CreatureKind.PALE_STEED));
        assertTrue(SpectralSteedRules.isSteed(CreatureKind.NIGHTMARE));
        assertThrows(IllegalArgumentException.class,
            () -> SpectralSteedRules.ceilingGait(CreatureKind.OWL, 0, 0));
        assertThrows(IllegalArgumentException.class,
            () -> SpectralSteedRules.desiredGait(CreatureKind.OWL, 1.0F, 0.0F));
        assertThrows(IllegalArgumentException.class,
            () -> SpectralSteedRules.fatigueDelta(CreatureKind.OWL, Gait.WALK));
        assertThrows(IllegalArgumentException.class,
            () -> SpectralSteedRules.balkTicks(CreatureKind.OWL, 0));
        assertThrows(IllegalArgumentException.class,
            () -> SpectralSteedRules.warningWarranted(CreatureKind.OWL, true, 0, 0));
    }

    @Test
    void seekingRestNeedsFatigueNoCooldownAndNobodyAboard() {
        assertTrue(SpectralSteedRules.seeksRest(SpectralSteedRules.REST_SEEK_FATIGUE, 0, false));
        assertFalse(SpectralSteedRules.seeksRest(SpectralSteedRules.REST_SEEK_FATIGUE - 1, 0, false));
        assertFalse(SpectralSteedRules.seeksRest(SpectralSteedRules.MAX_FATIGUE, 1, false));
        assertFalse(SpectralSteedRules.seeksRest(SpectralSteedRules.MAX_FATIGUE, 0, true));
    }

    @Test
    void theTwoSteedsAreNotTheSameSteedOnAnyAxisThatProducesBehaviour() {
        assertNotEquals(
            SpectralSteedRules.ceilingGait(CreatureKind.PALE_STEED, 0, 0),
            SpectralSteedRules.ceilingGait(CreatureKind.NIGHTMARE, 0, 0));
        assertNotEquals(
            SpectralSteedRules.fatigueDelta(CreatureKind.PALE_STEED, Gait.SPRINT),
            SpectralSteedRules.fatigueDelta(CreatureKind.NIGHTMARE, Gait.SPRINT));
        assertNotEquals(
            SpectralSteedRules.balkTicks(CreatureKind.PALE_STEED, 0),
            SpectralSteedRules.balkTicks(CreatureKind.NIGHTMARE, 0));
        assertNotEquals(
            SpectralSteedRules.warningWarranted(CreatureKind.PALE_STEED, true, 999, 0),
            SpectralSteedRules.warningWarranted(CreatureKind.NIGHTMARE, true, 999, 0));
    }
}


