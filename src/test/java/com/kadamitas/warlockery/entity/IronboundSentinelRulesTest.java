package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.IronboundSentinelRules.Band;
import com.kadamitas.warlockery.entity.IronboundSentinelRules.Candidate;
import com.kadamitas.warlockery.entity.IronboundSentinelRules.Charge;
import com.kadamitas.warlockery.entity.IronboundSentinelRules.Interposition;
import com.kadamitas.warlockery.entity.IronboundSentinelRules.Legality;
import com.kadamitas.warlockery.entity.IronboundSentinelRules.Phase;
import com.kadamitas.warlockery.entity.IronboundSentinelRules.SocketAct;
import com.kadamitas.warlockery.entity.behavior.Ticks;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The pure F36 policy: geometry, both state machines, the ladder, legality, strain and the socket. */
final class IronboundSentinelRulesTest {

    /** Everything permitted. Each test below flips exactly the rung it is about. */
    private static Candidate eligible() {
        return new Candidate(false, false, false, false, true, false, true, true, true);
    }

    // ---------------------------------------------------------------- geometry

    @Test
    void wardMembershipUsesTwoRadiiTestedSeparatelyAndIsInclusiveAtBoth() {
        assertTrue(IronboundSentinelRules.insideWard(12.0D, 0.0D, 0.0D),
            "the horizontal boundary is inclusive at exactly 12.0");
        assertTrue(IronboundSentinelRules.insideWard(0.0D, 5.0D, 0.0D),
            "the vertical boundary is inclusive at exactly 5.0");
        assertTrue(IronboundSentinelRules.insideWard(0.0D, -5.0D, 0.0D),
            "the vertical radius is symmetric");
        assertFalse(IronboundSentinelRules.insideWard(12.001D, 0.0D, 0.0D));
        assertFalse(IronboundSentinelRules.insideWard(0.0D, 5.001D, 0.0D));
        assertFalse(IronboundSentinelRules.insideWard(9.0D, 0.0D, 9.0D),
            "the horizontal radius is a circle, not a square: 9,9 is 12.72 away");
    }

    @Test
    void reachAndTetherAreSeparateConstantsMeasuredFromDifferentAnchors() {
        assertTrue(IronboundSentinelRules.withinReach(64.0D), "8.0 squared is inclusive");
        assertFalse(IronboundSentinelRules.withinReach(64.01D));
        assertTrue(IronboundSentinelRules.insideTether(64.0D));
        assertFalse(IronboundSentinelRules.insideTether(64.01D));
        assertTrue(IronboundSentinelRules.withinRetention(256.0D), "16.0 squared is inclusive");
        assertFalse(IronboundSentinelRules.withinRetention(256.01D));
    }

    @Test
    void aCandidateInsideTheWardButOutOfReachAndOneInReachButOutsideTheWardBothFail() {
        final Candidate insideWardOutOfReach = new Candidate(
            false, false, false, false, true, false, true, false, true);
        assertEquals(Legality.OUT_OF_REACH,
            IronboundSentinelRules.legality(Charge.CHARGED, insideWardOutOfReach));

        final Candidate inReachOutsideWard = new Candidate(
            false, false, false, false, true, false, false, true, true);
        assertEquals(Legality.OUTSIDE_WARD,
            IronboundSentinelRules.legality(Charge.CHARGED, inReachOutsideWard));
    }

    @Test
    void aStationFurtherThanFortyEightBlocksIsCorruptAndTheBoundaryIsInclusive() {
        assertFalse(IronboundSentinelRules.stationCorrupt(48.0D * 48.0D));
        assertTrue(IronboundSentinelRules.stationCorrupt(48.0D * 48.0D + 0.01D));
    }

    @Test
    void theFourBearingsAscendWrapAndTheirQuadrantsCoverTheWholeWardIncludingTheStationColumn() {
        assertEquals(1, IronboundSentinelRules.nextBearing(0));
        assertEquals(3, IronboundSentinelRules.nextBearing(2));
        assertEquals(0, IronboundSentinelRules.nextBearing(3),
            "the bearing wraps rather than running off the end");

        final EnumSet<Corner> covered = EnumSet.noneOf(Corner.class);
        boolean stationColumnInEveryQuadrant = true;
        for (int bearing = 0; bearing < IronboundSentinelRules.BEARINGS; bearing++) {
            final int signX = IronboundSentinelRules.quadrantSignX(bearing);
            final int signZ = IronboundSentinelRules.quadrantSignZ(bearing);
            covered.add(Corner.of(signX, signZ));
            final double lowX = IronboundSentinelRules.quadrantLow(signX);
            final double highX = IronboundSentinelRules.quadrantHigh(signX);
            final double lowZ = IronboundSentinelRules.quadrantLow(signZ);
            final double highZ = IronboundSentinelRules.quadrantHigh(signZ);
            stationColumnInEveryQuadrant &= lowX <= 0.0D && highX >= 0.0D
                && lowZ <= 0.0D && highZ >= 0.0D;
            assertEquals(IronboundSentinelRules.WARD_HORIZONTAL, highX - lowX,
                "each quadrant spans the full horizontal ward radius on X");
            assertEquals(IronboundSentinelRules.WARD_HORIZONTAL, highZ - lowZ,
                "each quadrant spans the full horizontal ward radius on Z");
        }
        assertEquals(4, covered.size(),
            "the four bearings occupy four distinct quadrants, so their union is the whole ward");
        assertTrue(stationColumnInEveryQuadrant,
            "the station's own column belongs to every quadrant, so the position the Sentinel "
                + "stands on is evaluated on every bearing rather than on none");
    }

    private enum Corner {
        PLUS_PLUS,
        MINUS_PLUS,
        MINUS_MINUS,
        PLUS_MINUS;

        static Corner of(final int signX, final int signZ) {
            if (signX > 0) {
                return signZ > 0 ? PLUS_PLUS : PLUS_MINUS;
            }
            return signZ > 0 ? MINUS_PLUS : MINUS_MINUS;
        }
    }

    @Test
    void theQuadrantCentreFollowsItsBearingAndIsAlwaysInsideTheWard() {
        for (int bearing = 0; bearing < IronboundSentinelRules.BEARINGS; bearing++) {
            final Interposition centre = IronboundSentinelRules.quadrantCentre(100.0D, -50.0D, bearing);
            final double deltaX = centre.x() - 100.0D;
            final double deltaZ = centre.z() + 50.0D;
            assertEquals(IronboundSentinelRules.quadrantSignX(bearing), (int) Math.signum(deltaX));
            assertEquals(IronboundSentinelRules.quadrantSignZ(bearing), (int) Math.signum(deltaZ));
            assertTrue(IronboundSentinelRules.insideWard(deltaX, 0.0D, deltaZ));
        }
    }

    @Test
    void interpositionStandsBetweenTheSubjectAndTheStationAndNeverLeavesTheTether() {
        final Interposition point =
            IronboundSentinelRules.interposition(10.0D, 0.0D, 0.0D, 0.0D, 2.0D);
        assertEquals(8.0D, point.x(), 1.0E-9D, "two blocks in from the subject, toward the station");
        assertEquals(0.0D, point.z(), 1.0E-9D);

        final Interposition far =
            IronboundSentinelRules.interposition(40.0D, 0.0D, 0.0D, 0.0D, 2.0D);
        assertTrue(IronboundSentinelRules.insideTether(far.x() * far.x() + far.z() * far.z()),
            "a distant subject cannot pull the interposition point outside the tether");

        final Interposition degenerate =
            IronboundSentinelRules.interposition(5.0D, 5.0D, 5.0D, 5.0D, 2.0D);
        assertEquals(5.0D, degenerate.x(), 1.0E-9D,
            "a subject standing on the station produces the station, never a division by zero");
    }

    @Test
    void tetherClampingIsIdempotentAndOnlyMovesPointsThatAreActuallyOutside() {
        final Interposition inside = IronboundSentinelRules.clampToTether(3.0D, 4.0D, 0.0D, 0.0D);
        assertEquals(3.0D, inside.x(), 1.0E-9D);
        assertEquals(4.0D, inside.z(), 1.0E-9D);
        final Interposition clamped = IronboundSentinelRules.clampToTether(30.0D, 40.0D, 0.0D, 0.0D);
        final double distance = Math.hypot(clamped.x(), clamped.z());
        assertEquals(IronboundSentinelRules.TETHER, distance, 1.0E-9D);
        final Interposition again =
            IronboundSentinelRules.clampToTether(clamped.x(), clamped.z(), 0.0D, 0.0D);
        assertEquals(clamped.x(), again.x(), 1.0E-9D);
    }

    // ---------------------------------------------------------------- the legality function

    @Test
    void theLegalityFunctionShortCircuitsAtTheFirstRungThatAnswersInTheDeclaredOrder() {
        assertEquals(Legality.ELIGIBLE, IronboundSentinelRules.legality(Charge.CHARGED, eligible()));
        assertEquals(Legality.SELF, IronboundSentinelRules.legality(Charge.CHARGED,
            new Candidate(true, true, true, true, false, true, false, false, false)),
            "self answers before every other rung, even when every other rung would also refuse");
        assertEquals(Legality.SIBLING_SENTINEL, IronboundSentinelRules.legality(Charge.CHARGED,
            new Candidate(false, true, true, true, false, true, false, false, false)));
        assertEquals(Legality.OWNER, IronboundSentinelRules.legality(Charge.CHARGED,
            new Candidate(false, false, true, true, false, true, false, false, false)));
        assertEquals(Legality.CREATIVE_OR_SPECTATOR, IronboundSentinelRules.legality(Charge.CHARGED,
            new Candidate(false, false, false, true, false, true, false, false, false)));
        assertEquals(Legality.INVALID, IronboundSentinelRules.legality(Charge.CHARGED,
            new Candidate(false, false, false, false, false, true, false, false, false)));
        assertEquals(Legality.OCCUPIED, IronboundSentinelRules.legality(Charge.CHARGED,
            new Candidate(false, false, false, false, true, true, false, false, false)));
    }

    @Test
    void onlyAChargedSentinelMayBarRepelOrBindAnythingAtAll() {
        for (final Charge charge : Charge.values()) {
            final Legality verdict = IronboundSentinelRules.legality(charge, eligible());
            if (charge == Charge.CHARGED) {
                assertEquals(Legality.ELIGIBLE, verdict);
            } else {
                assertEquals(Legality.NOT_CHARGED, verdict,
                    "a " + charge + " Sentinel permits nobody");
            }
        }
    }

    @Test
    void anOwnedCandidateIsProtectedAndSightIsTheLastRungRatherThanTheFirst() {
        final Candidate owned = new Candidate(false, false, true, false, true, false, true, true, true);
        assertEquals(Legality.OWNER, IronboundSentinelRules.legality(Charge.CHARGED, owned));
        final Candidate unseen = new Candidate(false, false, false, false, true, false, true, true, false);
        assertEquals(Legality.UNSEEN, IronboundSentinelRules.legality(Charge.CHARGED, unseen),
            "membership of the ward never mints a response on its own");
    }

    @Test
    void speciesIsNotARungBecauseTheFunctionCannotSeeSpeciesAtAll() {
        assertEquals(9, Candidate.class.getRecordComponents().length);
        final var componentNames = java.util.Arrays.stream(Candidate.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .toList();
        assertFalse(componentNames.stream().anyMatch(name ->
                name.contains("kind") || name.contains("species") || name.contains("type")
                    || name.contains("player") || name.contains("villager") || name.contains("golem")),
            "no component names a species, so a villager, a golem, a familiar and a player are "
                + "judged by exactly the same nine facts; got " + componentNames);
    }

    // ---------------------------------------------------------------- the state machines

    @Test
    void thePhaseImpliedByEachChargeIsTotalAndTransitionalArmsCarryTheirDeclaredDuration() {
        assertEquals(Phase.STILLED, IronboundSentinelRules.phaseFor(Charge.INERT));
        assertEquals(Phase.STILLED, IronboundSentinelRules.phaseFor(Charge.WAKING));
        assertEquals(Phase.VIGIL, IronboundSentinelRules.phaseFor(Charge.CHARGED));
        assertEquals(Phase.UNDOING, IronboundSentinelRules.phaseFor(Charge.STANDING_DOWN));

        assertEquals(60, IronboundSentinelRules.transitionTicksFor(Charge.WAKING));
        assertEquals(60, IronboundSentinelRules.transitionTicksFor(Charge.STANDING_DOWN));
        assertEquals(0, IronboundSentinelRules.transitionTicksFor(Charge.INERT));
        assertEquals(0, IronboundSentinelRules.transitionTicksFor(Charge.CHARGED));
    }

    @Test
    void everyTransitionalArmHasExactlyOneExitAndTheSettledArmsAreFixedPoints() {
        assertEquals(Charge.CHARGED, IronboundSentinelRules.chargeAfterTransition(Charge.WAKING));
        assertEquals(Charge.INERT,
            IronboundSentinelRules.chargeAfterTransition(Charge.STANDING_DOWN));
        assertEquals(Charge.INERT, IronboundSentinelRules.chargeAfterTransition(Charge.INERT));
        assertEquals(Charge.CHARGED, IronboundSentinelRules.chargeAfterTransition(Charge.CHARGED));
        assertTrue(Charge.WAKING.transitional() && Charge.STANDING_DOWN.transitional());
        assertFalse(Charge.INERT.transitional() || Charge.CHARGED.transitional());
        assertTrue(Charge.CHARGED.mayAct());
        assertFalse(Charge.INERT.mayAct() || Charge.WAKING.mayAct()
            || Charge.STANDING_DOWN.mayAct());
    }

    // ---------------------------------------------------------------- the priority ladder

    @Test
    void hazardPreemptsEveryBandIncludingTheShutdownBand() {
        assertTrue(IronboundSentinelRules.hazardPreemptsShutdown());
        assertEquals(Band.HAZARD, IronboundSentinelRules.band(
            Charge.INERT, Phase.STILLED, true, false, false, false));
        assertEquals(Band.HAZARD, IronboundSentinelRules.band(
            Charge.CHARGED, Phase.REPEL, true, true, true, true),
            "a hazard takes the tick from a seizing Sentinel mid-episode");
    }

    @Test
    void exactlyOneBandWinsAndTheOrderIsShutdownThenSeizeThenEpisodeThenReturnThenRoutine() {
        assertEquals(Band.SHUTDOWN, IronboundSentinelRules.band(
            Charge.STANDING_DOWN, Phase.UNDOING, false, true, true, true),
            "a drawn charge outranks a seize and an episode: nothing queued may land");
        assertEquals(Band.SEIZE, IronboundSentinelRules.band(
            Charge.CHARGED, Phase.VIGIL, false, true, true, true));
        assertEquals(Band.EPISODE, IronboundSentinelRules.band(
            Charge.CHARGED, Phase.BAR, false, false, true, true));
        assertEquals(Band.RETURN, IronboundSentinelRules.band(
            Charge.CHARGED, Phase.VIGIL, false, false, false, true));
        assertEquals(Band.ROUTINE, IronboundSentinelRules.band(
            Charge.CHARGED, Phase.VIGIL, false, false, false, false));
    }

    @Test
    void aSeizePhaseKeepsTheSeizeBandEvenAfterStrainWouldNoLongerRaiseIt() {
        assertEquals(Band.SEIZE, IronboundSentinelRules.band(
            Charge.CHARGED, Phase.SEIZE, false, false, false, false),
            "the seize window runs to completion rather than being abandoned mid-way");
    }

    // ---------------------------------------------------------------- strain

    @Test
    void strainRisesOnlyWhileHeldOrRouteBlockedFallsOnlyWhenClearAndClampsAtBothEnds() {
        assertEquals(1, IronboundSentinelRules.strainAfterHeldSubject(0));
        assertEquals(200, IronboundSentinelRules.strainAfterHeldSubject(199));
        assertEquals(200, IronboundSentinelRules.strainAfterHeldSubject(200),
            "the cap is a clamp, not an overflow");
        assertEquals(2, IronboundSentinelRules.strainAfterRouteFailure(0));
        assertEquals(200, IronboundSentinelRules.strainAfterRouteFailure(199));
        assertEquals(0, IronboundSentinelRules.strainAfterDecay(1));
        assertEquals(0, IronboundSentinelRules.strainAfterDecay(0),
            "the floor is a clamp, so a clear ward cannot drive strain negative");
        assertEquals(200, IronboundSentinelRules.clampStrain(201));
        assertEquals(0, IronboundSentinelRules.clampStrain(-1));
    }

    @Test
    void theSeizeThresholdIsExactlyTheCapAndNothingBelowItSeizes() {
        assertFalse(IronboundSentinelRules.seizeDue(0));
        assertFalse(IronboundSentinelRules.seizeDue(199));
        assertTrue(IronboundSentinelRules.seizeDue(200));
        assertTrue(IronboundSentinelRules.seizeDue(201));
        assertEquals(20, IronboundSentinelRules.STRAIN_ACCRUAL_TICKS);
        assertEquals(40, IronboundSentinelRules.STRAIN_DECAY_TICKS);
        assertEquals(2, IronboundSentinelRules.STRAIN_ROUTE_FAILURE_PENALTY);
        assertEquals(40, IronboundSentinelRules.SEIZE_TICKS);
    }

    // ---------------------------------------------------------------- the socket act

    @Test
    void theSocketActSeatsAnInertChargeAndDrawsAWakingOrChargedOne() {
        assertEquals(SocketAct.SEAT, socket(Charge.INERT));
        assertEquals(SocketAct.DRAW, socket(Charge.WAKING));
        assertEquals(SocketAct.DRAW, socket(Charge.CHARGED));
        assertEquals(SocketAct.PASS, socket(Charge.STANDING_DOWN),
            "a Sentinel already going down cannot be interrupted by another act");
    }

    @Test
    void everyMissingPreconditionFallsThroughToTheMandatoryDefaultArm() {
        assertEquals(SocketAct.PASS, IronboundSentinelRules.socketAct(
            Charge.INERT, false, true, true, 1.0D, 1.0D, false), "not crouching");
        assertEquals(SocketAct.PASS, IronboundSentinelRules.socketAct(
            Charge.INERT, true, false, true, 1.0D, 1.0D, false), "main hand holding something");
        assertEquals(SocketAct.PASS, IronboundSentinelRules.socketAct(
            Charge.INERT, true, true, false, 1.0D, 1.0D, false), "off hand holding something");
        assertEquals(SocketAct.PASS, IronboundSentinelRules.socketAct(
            Charge.INERT, true, true, true, 4.01D, 1.0D, false), "beyond interaction reach");
        assertEquals(SocketAct.PASS, IronboundSentinelRules.socketAct(
            Charge.INERT, true, true, true, 1.0D, 0.0D, false), "exactly on the front arc edge");
        assertEquals(SocketAct.PASS, IronboundSentinelRules.socketAct(
            Charge.INERT, true, true, true, 1.0D, -0.5D, false), "behind the Sentinel");
        assertEquals(SocketAct.PASS, IronboundSentinelRules.socketAct(
            Charge.CHARGED, true, true, true, 1.0D, 1.0D, true), "the bound subject may not socket");
        assertEquals(SocketAct.SEAT, IronboundSentinelRules.socketAct(
            Charge.INERT, true, true, true, 4.0D, 0.001D, false),
            "both boundaries are inclusive on the permitting side");
    }

    @Test
    void theSocketActHasNoOwnerGateAnywhereInItsSignature() {
        final var parameters = java.util.Arrays.stream(
                IronboundSentinelRules.class.getDeclaredMethods())
            .filter(method -> "socketAct".equals(method.getName()))
            .findFirst()
            .orElseThrow()
            .getParameters();
        assertEquals(7, parameters.length);
        assertFalse(java.util.Arrays.stream(parameters)
                .anyMatch(parameter -> parameter.getType() == UUID.class),
            "the unmaking is available to somebody who is not the maker");
    }

    private static SocketAct socket(final Charge charge) {
        return IronboundSentinelRules.socketAct(charge, true, true, true, 1.0D, 1.0D, false);
    }

    // ---------------------------------------------------------------- DC helpers

    @Test
    void attributionFreshnessIsInclusiveAtFortyAndRejectsAStaleOrNegativeAge() {
        assertTrue(IronboundSentinelRules.attributionFresh(0));
        assertTrue(IronboundSentinelRules.attributionFresh(40));
        assertFalse(IronboundSentinelRules.attributionFresh(41));
        assertFalse(IronboundSentinelRules.attributionFresh(-1),
            "a reset clock is never fresh");
    }

    @Test
    void noCadenceSentinelEverReachesTheLongMaxValueThatReconciliationWouldResetToNow() {
        assertEquals(20_000L, Ticks.MAX_FUTURE_HORIZON_TICKS);
        assertEquals(20_000L, IronboundSentinelRules.boundedCadenceTicks(Long.MAX_VALUE));
        assertEquals(20_000L, IronboundSentinelRules.boundedCadenceTicks(20_001L));
        assertEquals(0L, IronboundSentinelRules.boundedCadenceTicks(-1L));
        assertEquals(500L, IronboundSentinelRules.boundedCadenceTicks(500L));
    }

    @Test
    void theSweepAndBearingStaggersAreDeterministicBoundedAndOffsetFromEachOther() {
        int sameTick = 0;
        for (int seed = 0; seed < 512; seed++) {
            final UUID identity = new UUID(seed * 31L + 7L, seed * 131L + 11L);
            final int sweep = IronboundSentinelRules.sweepOffset(identity);
            final int bearing = IronboundSentinelRules.bearingOffset(identity);
            assertTrue(sweep >= 0 && sweep < IronboundSentinelRules.SWEEP_TICKS);
            assertTrue(bearing >= 0 && bearing < IronboundSentinelRules.BEARING_ADVANCE_TICKS);
            assertEquals(sweep, IronboundSentinelRules.sweepOffset(identity),
                "the stagger is a pure function of identity and never of world time");
            if (sweep == bearing % IronboundSentinelRules.SWEEP_TICKS) {
                sameTick++;
            }
        }
        assertNotEquals(512, sameTick,
            "the bearing offset is deliberately displaced from the sweep offset, so one Sentinel "
                + "does not pay for both on the same tick");
    }

    @Test
    void everyDeclaredValueMatchesTheApprovedDesignExactly() {
        assertEquals(12.0D, IronboundSentinelRules.WARD_HORIZONTAL);
        assertEquals(5.0D, IronboundSentinelRules.WARD_VERTICAL);
        assertEquals(8.0D, IronboundSentinelRules.REACH);
        assertEquals(8.0D, IronboundSentinelRules.TETHER);
        assertEquals(48.0D, IronboundSentinelRules.CORRUPT_STATION_DISTANCE);
        assertEquals(16.0D, IronboundSentinelRules.RETENTION_RADIUS);
        assertEquals(4, IronboundSentinelRules.BEARINGS);
        assertEquals(60, IronboundSentinelRules.BEARING_ADVANCE_TICKS);
        assertEquals(20, IronboundSentinelRules.SWEEP_TICKS);
        assertEquals(10, IronboundSentinelRules.REVALIDATION_TICKS);
        assertEquals(6, IronboundSentinelRules.SWEEP_ENTITY_VISITS);
        assertEquals(2, IronboundSentinelRules.SWEEP_SIGHT_RAYS);
        assertEquals(6, IronboundSentinelRules.RETAINED_IDENTITIES);
        assertEquals(40, IronboundSentinelRules.SIGHT_LOSS_RELEASE_TICKS);
        assertEquals(400, IronboundSentinelRules.EPISODE_CAP_TICKS);
        assertEquals(20, IronboundSentinelRules.REPEL_CADENCE_TICKS);
        assertEquals(40, IronboundSentinelRules.ATTRIBUTION_FRESHNESS_TICKS);
        assertEquals(200, IronboundSentinelRules.STRAIN_MAX);
        assertEquals(60, IronboundSentinelRules.WAKING_TICKS);
        assertEquals(60, IronboundSentinelRules.STAND_DOWN_TICKS);
        assertEquals(4.0D, IronboundSentinelRules.SOCKET_REACH_SQR);
        assertEquals(4.0D, IronboundSentinelRules.RETURN_ARRIVAL_DISTANCE_SQR);
        assertEquals(300, IronboundSentinelRules.RETURN_TIMEOUT_TICKS);
        assertEquals(20, IronboundSentinelRules.PATH_CADENCE_TICKS);
        assertEquals(3, IronboundSentinelRules.ROUTE_FAILURES_BEFORE_BACKOFF);
        assertEquals(100, IronboundSentinelRules.ROUTE_BACKOFF_TICKS);
        assertEquals(160, IronboundSentinelRules.MAX_STATE_BYTES,
            "the approved 128-byte target proved unreachable with the design's own key names; "
                + "the honest measured ceiling is declared instead");
        assertEquals(147, IronboundSentinelRules.REPRESENTATIVE_STATE_BYTES);
    }

    @Test
    void theRouteBackoffEngagesOnTheThirdFailureAndNeverGrowsPastItsFlatWindow() {
        assertEquals(0, IronboundSentinelRules.ROUTE_BACKOFF.windowAfter(0));
        assertEquals(0, IronboundSentinelRules.ROUTE_BACKOFF.windowAfter(2));
        assertEquals(100, IronboundSentinelRules.ROUTE_BACKOFF.windowAfter(3));
        assertEquals(100, IronboundSentinelRules.ROUTE_BACKOFF.windowAfter(9),
            "the window is flat: a Sentinel in bad terrain never backs off for longer and longer");
        assertTrue(IronboundSentinelRules.ROUTE_BACKOFF.engagedAt(3));
        assertFalse(IronboundSentinelRules.ROUTE_BACKOFF.engagedAt(2));
    }

    @Test
    void everyPhaseNameIsOwnedByThisFamilyAloneAmongTheCommittedNeighbours() {
        final var neighbours = java.util.List.of(
            java.util.Arrays.stream(PoltergeistRules.Phase.values()).map(Enum::name).toList(),
            java.util.Arrays.stream(EchoShadeRules.Phase.values()).map(Enum::name).toList(),
            java.util.Arrays.stream(SpectreRules.Phase.values()).map(Enum::name).toList(),
            java.util.Arrays.stream(LostSoulRules.Phase.values()).map(Enum::name).toList(),
            java.util.Arrays.stream(SpiritRules.Phase.values()).map(Enum::name).toList()
        );
        final var ours = java.util.Arrays.stream(Phase.values()).map(Enum::name).toList();
        neighbours.forEach(theirs -> assertTrue(
            theirs.stream().noneMatch(ours::contains),
            "F36 shares no phase name with a committed neighbour; theirs=" + theirs));
        assertEquals(8, ours.size());
    }
}
