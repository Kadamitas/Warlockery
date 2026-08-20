package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.AnimalFamiliarRules.HomeCandidate;
import com.kadamitas.warlockery.entity.AnimalFamiliarRules.SelectionReason;
import com.kadamitas.warlockery.entity.SpectralFamiliarRules.Action;
import com.kadamitas.warlockery.entity.SpectralFamiliarRules.Decision;
import com.kadamitas.warlockery.entity.SpectralFamiliarRules.Facts;
import com.kadamitas.warlockery.entity.SpectralFamiliarRules.Phase;
import com.kadamitas.warlockery.entity.SpectralFamiliarRules.Reason;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class SpectralFamiliarRulesTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OTHER_OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID CANDIDATE = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    /** A bound, quiet, dormant familiar standing beside its owner with nothing due. */
    private static Facts quiet() {
        return new Facts(true, 4.0, false, Phase.DORMANT, true, false, true, false);
    }

    // =====================================================================================
    // The ladder
    // =====================================================================================

    @Test
    void theLadderIsExactlyTheApprovedOrderAndTheChainAgreesWithItOverTheWholeCombinationSpace() {
        // PriorityLadder.select was declined because it copies a list, sorts it and opens a stream
        // per call, and this runs every tick on every loaded familiar. LADDER states the ordering
        // as data; decide hand-rolls it. The two have to be proved equal rather than assumed, over
        // the whole combination space and not over a handful of scenes.
        assertEquals(
            List.of(Action.DEFEND_OWNER, Action.APPROACH_GUIDE, Action.SIGNAL_FIND,
                Action.RETURN_TO_OWNER, Action.TETHER_RETURN, Action.SURVEY, Action.HOVER,
                Action.IDLE),
            SpectralFamiliarRules.LADDER);

        int cases = 0;
        for (final Facts facts : everyCombination()) {
            cases++;
            final Decision decision = SpectralFamiliarRules.decide(facts);
            assertEquals(highestApplicableRung(facts), decision.action(),
                "the chain must elect the same rung the ladder ranks first for " + facts);
        }
        assertEquals(2 * 2 * 2 * 4 * 2 * 2 * 2 * 2, cases,
            "the combination space must be complete");
    }

    @Test
    void exactlyOneMovementWriterIsEverElectedAndNeverTwo() {
        for (final Facts facts : everyCombination()) {
            final Action elected = SpectralFamiliarRules.decide(facts).action();
            final long writers = SpectralFamiliarRules.MOVEMENT_WRITERS.stream()
                .filter(elected::equals)
                .count();
            assertTrue(writers <= 1L, "at most one movement writer per decision, for " + facts);
        }
    }

    @Test
    void decideAllocatesNothingBecauseEveryDecisionItCanReturnIsInterned() {
        // Identity, not equality. A per-tick ladder that allocates a record per call is the cost the
        // house style forbids, and equals() would pass even if it did.
        final Set<Decision> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (final Facts facts : everyCombination()) {
            seen.add(SpectralFamiliarRules.decide(facts));
        }
        for (final Decision decision : seen) {
            assertTrue(
                decision == SpectralFamiliarRules.DEFEND
                    || decision == SpectralFamiliarRules.APPROACHING
                    || decision == SpectralFamiliarRules.SIGNALLING
                    || decision == SpectralFamiliarRules.RETURNING
                    || decision == SpectralFamiliarRules.TETHERED
                    || decision == SpectralFamiliarRules.SURVEYING
                    || decision == SpectralFamiliarRules.HOVER_UNSAMPLED
                    || decision == SpectralFamiliarRules.HOVER_COOLING
                    || decision == SpectralFamiliarRules.HOVER_BACKED_OFF
                    || decision == SpectralFamiliarRules.HOVER_IDLE
                    || decision == SpectralFamiliarRules.UNBOUND,
                "decide returned a freshly allocated decision: " + decision);
        }
    }

    @Test
    void aLegalAttackOnTheOwnerPreemptsEvenARunningEpisode() {
        final Facts surveying = new Facts(true, 4.0, false, Phase.DORMANT, true, true, true, false);
        assertEquals(Action.SURVEY, SpectralFamiliarRules.decide(surveying).action());

        final Facts attacked = new Facts(true, 4.0, true, Phase.APPROACH, true, true, true, false);
        assertSame(SpectralFamiliarRules.DEFEND, SpectralFamiliarRules.decide(attacked));
    }

    @Test
    void aRunningEpisodeOutranksTheTetherAndTheTetherOutranksTheSurvey() {
        final double outside = SpectralFamiliarRules.TETHER_RADIUS_SQUARED + 1.0;

        final Facts episodeOutsideBand =
            new Facts(true, outside, false, Phase.APPROACH, true, true, true, false);
        assertSame(SpectralFamiliarRules.APPROACHING,
            SpectralFamiliarRules.decide(episodeOutsideBand));

        final Facts dormantOutsideBand =
            new Facts(true, outside, false, Phase.DORMANT, true, true, true, false);
        assertSame(SpectralFamiliarRules.TETHERED,
            SpectralFamiliarRules.decide(dormantOutsideBand));
    }

    @Test
    void anUnsampledOrCoolingOrBackedOffFamiliarHoversRatherThanSurveying() {
        assertSame(SpectralFamiliarRules.HOVER_UNSAMPLED, SpectralFamiliarRules.decide(
            new Facts(true, 4.0, false, Phase.DORMANT, false, true, true, false)));
        assertSame(SpectralFamiliarRules.HOVER_COOLING, SpectralFamiliarRules.decide(
            new Facts(true, 4.0, false, Phase.DORMANT, true, true, false, false)));
        assertSame(SpectralFamiliarRules.HOVER_BACKED_OFF, SpectralFamiliarRules.decide(
            new Facts(true, 4.0, false, Phase.DORMANT, true, true, true, true)));
        assertSame(SpectralFamiliarRules.HOVER_IDLE, SpectralFamiliarRules.decide(quiet()));
    }

    @Test
    void anUnboundFamiliarIsIdleAndNeverSurveysNoMatterWhatElseIsTrue() {
        // No owner means no station to hold and nothing to guide. Surveying without an owner would
        // open an episode that is invalidated on the next tick, burning a six-hundred-tick cooldown
        // per survey forever, so the rung is gated on the owner and not merely on the sample.
        for (final boolean sample : List.of(true, false)) {
            for (final boolean due : List.of(true, false)) {
                for (final boolean ready : List.of(true, false)) {
                    final Facts unbound = new Facts(
                        false, Double.MAX_VALUE, false, Phase.DORMANT, sample, due, ready, false);
                    assertSame(SpectralFamiliarRules.UNBOUND, SpectralFamiliarRules.decide(unbound),
                        "sample=" + sample + " due=" + due + " ready=" + ready);
                }
            }
        }
    }

    @Test
    void everyReasonIsReachableSoNoneIsDeadVocabulary() {
        final EnumSet<Reason> reached = EnumSet.noneOf(Reason.class);
        for (final Facts facts : everyCombination()) {
            reached.add(SpectralFamiliarRules.decide(facts).reason());
        }
        assertEquals(EnumSet.allOf(Reason.class), reached,
            "a reason no combination can produce is dead vocabulary");
    }

    // =====================================================================================
    // The tether band, with hysteresis
    // =====================================================================================

    @Test
    void theTetherBandStartsAndStopsAtDifferentDistancesSoTheDecisionCannotFlipEveryTick() {
        assertTrue(SpectralFamiliarRules.beyondTether(true,
            SpectralFamiliarRules.TETHER_RADIUS_SQUARED + 0.1));
        assertFalse(SpectralFamiliarRules.beyondTether(true,
            SpectralFamiliarRules.TETHER_RADIUS_SQUARED));
        assertFalse(SpectralFamiliarRules.beyondTether(false, Double.MAX_VALUE),
            "an unloaded owner is not a tether violation");

        assertFalse(SpectralFamiliarRules.insideTetherBand(true,
            SpectralFamiliarRules.TETHER_RELEASE_DISTANCE_SQUARED + 0.1));
        assertTrue(SpectralFamiliarRules.insideTetherBand(true,
            SpectralFamiliarRules.TETHER_RELEASE_DISTANCE_SQUARED));
        assertTrue(SpectralFamiliarRules.TETHER_RELEASE_DISTANCE_SQUARED
            < SpectralFamiliarRules.TETHER_RADIUS_SQUARED,
            "release must be strictly inside start or the band is a single flipping threshold");
    }

    @Test
    void theTetherRadiusIsThisFamiliarsOwnAndNotAnAnimalFamiliars() {
        assertNotEquals(SpectralFamiliarRules.TETHER_RADIUS_SQUARED,
            AnimalFamiliarRules.profile(AnimalFamiliarSpecies.CAT).tetherRadiusSquared());
        assertNotEquals(SpectralFamiliarRules.TETHER_RADIUS_SQUARED,
            AnimalFamiliarRules.profile(AnimalFamiliarSpecies.OWL).tetherRadiusSquared());
        assertNotEquals(SpectralFamiliarRules.TETHER_RADIUS_SQUARED,
            AnimalFamiliarRules.profile(AnimalFamiliarSpecies.TOAD).tetherRadiusSquared());
    }

    // =====================================================================================
    // Defence legality, taken whole from the shared rule
    // =====================================================================================

    @Test
    void theFrozenDefenceLegalityListIsNeitherWidenedNorNarrowedByThisFamily() {
        final Optional<UUID> owner = Optional.of(OWNER);
        assertTrue(SpectralFamiliarRules.mayDefendAgainst(
            owner, CANDIDATE, Optional.empty(), true, false, false, true));

        assertFalse(SpectralFamiliarRules.mayDefendAgainst(
            owner, OWNER, Optional.empty(), true, false, false, true), "never the owner");
        assertFalse(SpectralFamiliarRules.mayDefendAgainst(
            owner, CANDIDATE, owner, true, false, false, true), "never a sibling familiar");
        assertFalse(SpectralFamiliarRules.mayDefendAgainst(
            owner, CANDIDATE, Optional.empty(), false, false, false, true), "never a dead one");
        assertFalse(SpectralFamiliarRules.mayDefendAgainst(
            owner, CANDIDATE, Optional.empty(), true, true, false, true), "never itself");
        assertFalse(SpectralFamiliarRules.mayDefendAgainst(
            owner, CANDIDATE, Optional.empty(), true, false, true, true),
            "never a creative or spectating player");
        assertFalse(SpectralFamiliarRules.mayDefendAgainst(
            owner, CANDIDATE, Optional.empty(), true, false, false, false),
            "never unattributed irritation");
        assertTrue(SpectralFamiliarRules.mayDefendAgainst(
            owner, CANDIDATE, Optional.of(OTHER_OWNER), true, false, false, true),
            "another player's familiar is still a legal attacker");
    }

    @Test
    void theEmergencyRecallDistanceIsTheFrozenOneAndOutrangesTheTether() {
        assertTrue(SpectralFamiliarRules.recallRequired(true,
            AnimalFamiliarRules.OWNER_RECALL_DISTANCE_SQUARED));
        assertFalse(SpectralFamiliarRules.recallRequired(true,
            AnimalFamiliarRules.OWNER_RECALL_DISTANCE_SQUARED - 0.1));
        assertFalse(SpectralFamiliarRules.recallRequired(false, Double.MAX_VALUE));
        assertTrue(AnimalFamiliarRules.OWNER_RECALL_DISTANCE_SQUARED
            > SpectralFamiliarRules.TETHER_RADIUS_SQUARED,
            "recall must be the outer emergency, not a second tether");
    }

    // =====================================================================================
    // Episode invalidation is identity, never timing
    // =====================================================================================

    @Test
    void anEpisodeIsInvalidatedByEveryWayItsIdentityCanStopBeingItsIdentity() {
        final Optional<String> iron = Optional.of("minecraft:iron_ore");
        final Optional<String> gold = Optional.of("minecraft:gold_ore");

        assertFalse(SpectralFamiliarRules.episodeInvalidated(true, 16.0, iron, iron),
            "a live episode with an unchanged sample survives");
        assertTrue(SpectralFamiliarRules.episodeInvalidated(false, 16.0, iron, iron),
            "an unresolvable owner ends the episode");
        assertTrue(SpectralFamiliarRules.episodeInvalidated(true,
            AnimalFamiliarRules.OWNER_RECALL_DISTANCE_SQUARED, iron, iron),
            "an owner past the emergency recall distance ends the episode");
        assertTrue(SpectralFamiliarRules.episodeInvalidated(true, 16.0, iron, Optional.empty()),
            "a removed sample ends the episode");
        assertTrue(SpectralFamiliarRules.episodeInvalidated(true, 16.0, iron, gold),
            "re-sampling mid-flight ends the episode rather than silently re-aiming it");
    }

    // =====================================================================================
    // The bounded survey traversal
    // =====================================================================================

    @Test
    void everyInspectedCandidateIsChargedBeforeAnyFilterCanRejectIt() {
        // Forty positions that cannot possibly qualify. The cap bounds INSPECTED candidates, not
        // qualifying ones, so the charge must be exactly the cap and not zero.
        final List<HomeCandidate> hopeless = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            hopeless.add(SpectralFamiliarRules.guideCandidate(index, index, false, false, false));
        }
        final var selection = SpectralFamiliarRules.selectGuideBlock(hopeless);
        assertEquals(SpectralFamiliarRules.SURVEY_CANDIDATE_CAP, selection.inspected());
        assertEquals(SelectionReason.BUDGET_EXHAUSTED, selection.reason());
        assertTrue(selection.home().isEmpty());
    }

    @Test
    void theCapBoundsCandidatesRatherThanQualifyingOnesWhichIsTheDefectWearingDifferentClothes() {
        // Eleven rejects then one match: within the cap, so the match is found.
        final List<HomeCandidate> lateMatch = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            lateMatch.add(SpectralFamiliarRules.guideCandidate(index, index, false, true, true));
        }
        lateMatch.add(SpectralFamiliarRules.guideCandidate(99L, 99.0, true, true, true));
        assertEquals(Optional.of(99L), SpectralFamiliarRules.selectGuideBlock(lateMatch).home());

        // Twelve rejects then the same match: past the cap, so it is NOT found, and the reason says
        // budget rather than pretending nothing was there.
        final List<HomeCandidate> tooLate = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            tooLate.add(SpectralFamiliarRules.guideCandidate(index, index, false, true, true));
        }
        tooLate.add(SpectralFamiliarRules.guideCandidate(99L, 99.0, true, true, true));
        final var selection = SpectralFamiliarRules.selectGuideBlock(tooLate);
        assertTrue(selection.home().isEmpty());
        assertEquals(SelectionReason.BUDGET_EXHAUSTED, selection.reason());
        assertEquals(SpectralFamiliarRules.SURVEY_CANDIDATE_CAP, selection.inspected());
    }

    @Test
    void selectionIsTheStableNearestMatchAndTiesBreakOnPackedPosition() {
        final List<HomeCandidate> candidates = List.of(
            SpectralFamiliarRules.guideCandidate(7L, 25.0, true, true, true),
            SpectralFamiliarRules.guideCandidate(3L, 9.0, true, true, true),
            SpectralFamiliarRules.guideCandidate(2L, 9.0, true, true, true),
            SpectralFamiliarRules.guideCandidate(1L, 4.0, true, false, true),
            SpectralFamiliarRules.guideCandidate(0L, 1.0, false, true, true)
        );
        final var selection = SpectralFamiliarRules.selectGuideBlock(candidates);
        assertEquals(SelectionReason.SELECTED, selection.reason());
        assertEquals(Optional.of(2L), selection.home(),
            "nearest fully qualifying wins, and an equal-distance tie breaks on the lower packed "
                + "position so the choice cannot depend on candidate order");
        assertEquals(5, selection.inspected());
    }

    @Test
    void allThreeSurveyPredicatesAreLoadBearingSoNoneOfThemIsDecoration() {
        assertTrue(SpectralFamiliarRules.selectGuideBlock(List.of(
            SpectralFamiliarRules.guideCandidate(1L, 1.0, false, true, true))).home().isEmpty(),
            "a block that is not the sampled block is not a match");
        assertTrue(SpectralFamiliarRules.selectGuideBlock(List.of(
            SpectralFamiliarRules.guideCandidate(1L, 1.0, true, false, true))).home().isEmpty(),
            "a block outside the frozen spectral-ore habitat tag is not a match");
        assertTrue(SpectralFamiliarRules.selectGuideBlock(List.of(
            SpectralFamiliarRules.guideCandidate(1L, 1.0, true, true, false))).home().isEmpty(),
            "a match with nowhere safe to hover beside it is not usable");
        assertEquals(Optional.of(1L), SpectralFamiliarRules.selectGuideBlock(List.of(
            SpectralFamiliarRules.guideCandidate(1L, 1.0, true, true, true))).home());
    }

    // =====================================================================================
    // A survey that qualifies nothing still arms its cadence and records the failure
    // =====================================================================================

    @Test
    void aFruitlessSurveyStillArmsItsFullCadenceAndRecordsTheFailureIntoABackoff() {
        var outcome = SpectralFamiliarRules.recordSurvey(1_000L, false, 0);
        assertEquals(1_000L + SpectralFamiliarRules.SURVEY_INTERVAL_TICKS, outcome.nextDueAt(),
            "the cadence is armed on the failing path too, or the survey spins every tick");
        assertEquals(1, outcome.consecutiveFailures());
        assertEquals(0, SpectralFamiliarRules.backoffTicks(outcome.consecutiveFailures()));

        outcome = SpectralFamiliarRules.recordSurvey(1_200L, false, outcome.consecutiveFailures());
        outcome = SpectralFamiliarRules.recordSurvey(1_400L, false, outcome.consecutiveFailures());
        assertEquals(SpectralFamiliarRules.MAX_SURVEY_FAILURES, outcome.consecutiveFailures());
        assertEquals(AnimalFamiliarRules.ROUTE_BACKOFF_TICKS,
            SpectralFamiliarRules.backoffTicks(outcome.consecutiveFailures()),
            "three fruitless attempts in a row earn the fixed backoff window");

        final var recovered =
            SpectralFamiliarRules.recordSurvey(1_600L, true, outcome.consecutiveFailures());
        assertEquals(0, recovered.consecutiveFailures(), "one success clears the run");
        assertEquals(1_600L + SpectralFamiliarRules.SURVEY_INTERVAL_TICKS, recovered.nextDueAt());
    }

    @Test
    void theBackoffWindowIsOpenUntilItsDeadlineAndClosedOnIt() {
        assertTrue(SpectralFamiliarRules.surveyBackedOff(999L, 1_000L));
        assertFalse(SpectralFamiliarRules.surveyBackedOff(1_000L, 1_000L));
        assertFalse(SpectralFamiliarRules.surveyBackedOff(1_000L, 0L),
            "an unset window is not a backoff");
    }

    // =====================================================================================
    // The envelope really leaves its innermost ring
    // =====================================================================================

    @Test
    void theSurveyEnvelopeCoversItsWholeVolumeRatherThanCirclingTheOrigin() {
        final var envelope = SpectralFamiliarRules.SURVEY_ENVELOPE;
        final int cap = SpectralFamiliarRules.SURVEY_READ_CAP;
        assertEquals(11 * 11 * 7, envelope.size());
        assertTrue(envelope.anchorSize(cap) > 0,
            "the near anchor must be present in every survey or the familiar stops seeing itself");
        assertTrue(envelope.pageSize(cap) > 0,
            "the rotating page must be non-empty or the far tail is never reached");
        assertEquals(envelope.size() - envelope.anchorSize(cap), envelope.tailSize(cap));

        final int scans = envelope.scansToCover(cap);
        assertTrue(scans > 1 && scans < 64,
            "the whole envelope must be covered, and in a bounded number of surveys, got " + scans);

        // The union of that many successive windows really is the whole envelope.
        final Set<net.minecraft.core.BlockPos> union = new java.util.HashSet<>();
        int cursor = 0;
        for (int scan = 0; scan <= scans; scan++) {
            union.addAll(envelope.window(cap, cursor));
            cursor = envelope.advanceCursor(cap, cursor);
        }
        assertEquals(envelope.size(), union.size(),
            "successive windows must eventually cover every offset, including the far corner");
        assertTrue(union.contains(new net.minecraft.core.BlockPos(
                SpectralFamiliarRules.SURVEY_RADIUS_HORIZONTAL,
                SpectralFamiliarRules.SURVEY_RADIUS_VERTICAL,
                SpectralFamiliarRules.SURVEY_RADIUS_HORIZONTAL)),
            "the far corner is the offset a naive raster would never reach");
    }

    @Test
    void thePhaseDurationSwitchIsTheSingleSourceOfEveryDeadline() {
        assertEquals(0, SpectralFamiliarRules.phaseDuration(Phase.DORMANT));
        assertEquals(SpectralFamiliarRules.APPROACH_DEADLINE_TICKS,
            SpectralFamiliarRules.phaseDuration(Phase.APPROACH));
        assertEquals(SpectralFamiliarRules.SIGNAL_TICKS,
            SpectralFamiliarRules.phaseDuration(Phase.SIGNAL));
        assertEquals(SpectralFamiliarRules.RETURN_DEADLINE_TICKS,
            SpectralFamiliarRules.phaseDuration(Phase.RETURN));
        for (final Phase phase : Phase.values()) {
            assertTrue(SpectralFamiliarRules.phaseDuration(phase) >= 0);
        }
    }

    @Test
    void aDriftIsPacedByItsCadenceAndHasNoSecondBackoffWindowToHideBehind() {
        assertFalse(SpectralFamiliarRules.mayDrift(999L, 1_000L));
        assertTrue(SpectralFamiliarRules.mayDrift(1_000L, 1_000L));
        assertEquals(AnimalFamiliarRules.NAVIGATION_INTERVAL_TICKS,
            SpectralFamiliarRules.DRIFT_INTERVAL_TICKS);
    }

    // =====================================================================================
    // helpers
    // =====================================================================================

    private static List<Facts> everyCombination() {
        final List<Facts> all = new ArrayList<>();
        for (final boolean ownerLoaded : List.of(true, false)) {
            // Both sides of the tether band, because a space that only ever sat outside it would
            // let TETHER_RETURN mask every rung below it and prove nothing about any of them.
            for (final boolean outsideBand : List.of(true, false)) {
                for (final boolean ownerUnderAttack : List.of(true, false)) {
                    for (final Phase phase : Phase.values()) {
                        for (final boolean sampleHeld : List.of(true, false)) {
                            for (final boolean surveyDue : List.of(true, false)) {
                                for (final boolean guideReady : List.of(true, false)) {
                                    for (final boolean backedOff : List.of(true, false)) {
                                        all.add(new Facts(
                                            ownerLoaded,
                                            !ownerLoaded ? Double.MAX_VALUE
                                                : outsideBand
                                                    ? SpectralFamiliarRules.TETHER_RADIUS_SQUARED + 1.0
                                                    : 4.0,
                                            ownerUnderAttack, phase, sampleHeld, surveyDue,
                                            guideReady, backedOff));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return List.copyOf(all);
    }

    /**
     * The ladder read as a ranking rather than as a chain: the first action in {@link
     * SpectralFamiliarRules#LADDER} whose own precondition holds. Written independently of
     * {@code decide} on purpose, because a helper that merely called it would prove nothing.
     */
    private static Action highestApplicableRung(final Facts facts) {
        for (final Action rung : SpectralFamiliarRules.LADDER) {
            final boolean applies = switch (rung) {
                case DEFEND_OWNER -> facts.ownerUnderAttack();
                case APPROACH_GUIDE -> facts.phase() == Phase.APPROACH;
                case SIGNAL_FIND -> facts.phase() == Phase.SIGNAL;
                case RETURN_TO_OWNER -> facts.phase() == Phase.RETURN;
                case TETHER_RETURN -> facts.phase() == Phase.DORMANT && facts.ownerLoaded()
                    && SpectralFamiliarRules.beyondTether(
                        facts.ownerLoaded(), facts.ownerDistanceSquared());
                case SURVEY -> facts.phase() == Phase.DORMANT && facts.ownerLoaded()
                    && !SpectralFamiliarRules.beyondTether(
                        facts.ownerLoaded(), facts.ownerDistanceSquared())
                    && facts.sampleHeld() && facts.guideReady() && !facts.surveyBackedOff()
                    && facts.surveyDue();
                case HOVER -> facts.phase() == Phase.DORMANT && facts.ownerLoaded();
                case IDLE -> true;
            };
            if (applies) {
                return rung;
            }
        }
        throw new AssertionError("the ladder must be total");
    }
}
