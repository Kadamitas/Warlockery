package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.NaamahCourtRules.Action;
import com.kadamitas.warlockery.entity.NaamahCourtRules.Candidate;
import com.kadamitas.warlockery.entity.NaamahCourtRules.CandidateType;
import com.kadamitas.warlockery.entity.NaamahCourtRules.AmbientMode;
import com.kadamitas.warlockery.entity.NaamahCourtRules.Phase;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class NaamahCourtRulesTest {
    @Test
    void healthThresholdsLatchForwardAndNeverRegressAfterHealing() {
        assertEquals(Phase.ENTHRONED, NaamahCourtRules.latchPhase(Phase.ENTHRONED, 68.0F, 100.0F));
        assertEquals(Phase.CHORUS_OF_WAVES, NaamahCourtRules.latchPhase(Phase.ENTHRONED, 67.0F, 100.0F));
        assertEquals(Phase.SOVEREIGN_REFUSAL,
            NaamahCourtRules.latchPhase(Phase.CHORUS_OF_WAVES, 34.0F, 100.0F));
        assertEquals(Phase.SOVEREIGN_REFUSAL,
            NaamahCourtRules.latchPhase(Phase.SOVEREIGN_REFUSAL, 100.0F, 100.0F));
        assertEquals(Phase.AUDIENCE_CONCLUDED,
            NaamahCourtRules.latchPhase(Phase.AUDIENCE_CONCLUDED, 1.0F, 100.0F));
    }

    @Test
    void trialOwnersThenDirectAttackersThenDistanceAndUuidDetermineOneChallenger() {
        final UUID nearby = new UUID(0L, 5L);
        final UUID trialOwner = new UUID(0L, 4L);
        final UUID directAttacker = new UUID(0L, 3L);
        final List<Candidate> candidates = List.of(
            new Candidate(nearby, CandidateType.PLAYER, false, false, 1.0D),
            new Candidate(trialOwner, CandidateType.PLAYER, false, true, 16.0D),
            new Candidate(directAttacker, CandidateType.PLAYER, true, false, 25.0D)
        );

        assertEquals(Optional.of(trialOwner), NaamahCourtRules.chooseChallenger(candidates));
        assertEquals(Optional.of(trialOwner), NaamahCourtRules.chooseChallenger(candidates.subList(0, 2)));
        assertEquals(Optional.of(new UUID(0L, 1L)), NaamahCourtRules.chooseChallenger(List.of(
            new Candidate(new UUID(0L, 2L), CandidateType.PLAYER, false, false, 9.0D),
            new Candidate(new UUID(0L, 1L), CandidateType.PLAYER, false, false, 9.0D)
        )));
    }

    @Test
    void courtCandidatePolicyExcludesProtectedFamiliesAndInvalidPlayers() {
        for (final CandidateType type : List.of(
            CandidateType.NAMI,
            CandidateType.VILLAGER,
            CandidateType.GOLEM,
            CandidateType.TURTLE,
            CandidateType.NAAMAH,
            CandidateType.VAMPIRE,
            CandidateType.BLOOD_THRALL,
            CandidateType.OTHER
        )) {
            assertFalse(NaamahCourtRules.canChallenge(type));
        }
        assertTrue(NaamahCourtRules.canChallenge(CandidateType.PLAYER));
        assertTrue(NaamahCourtRules.canChallenge(CandidateType.OTHER, true));
        assertFalse(NaamahCourtRules.canChallenge(CandidateType.OTHER, false));
        assertTrue(NaamahCourtRules.chooseChallenger(List.of(
            new Candidate(UUID.randomUUID(), CandidateType.NAMI, true, true, 1.0D)
        )).isEmpty());
    }

    @Test
    void protectedCandidatesCannotConsumeTheBoundedEligibleCandidateWindow() {
        final UUID eligible = UUID.randomUUID();
        final java.util.ArrayList<Candidate> candidates = new java.util.ArrayList<>();
        for (int index = 0; index < NaamahCourtRules.MAX_CANDIDATES; index++) {
            candidates.add(new Candidate(UUID.randomUUID(), CandidateType.NAMI, false, false, index));
        }
        candidates.add(new Candidate(eligible, CandidateType.OTHER, true, false, 100.0D));
        assertEquals(Optional.of(eligible), NaamahCourtRules.chooseChallenger(candidates));
    }

    @Test
    void boundedAccumulatorRetainsLatePriorityCandidatesAndStableCurrentChallenger() {
        final UUID trialOwner = new UUID(0L, 101L);
        final UUID directAttacker = new UUID(0L, 102L);
        final UUID currentChallenger = new UUID(0L, 103L);
        final NaamahCourtRules.CandidateAccumulator accumulator =
            new NaamahCourtRules.CandidateAccumulator();
        for (int index = 0; index < 40; index++) {
            accumulator.accept(new Candidate(
                new UUID(1L, index), CandidateType.PLAYER, false, false, false, index + 1.0D
            ));
        }
        accumulator.accept(new Candidate(currentChallenger, CandidateType.PLAYER, false, false, true, 900.0D));
        accumulator.accept(new Candidate(directAttacker, CandidateType.OTHER, true, false, false, 1_000.0D));
        accumulator.accept(new Candidate(trialOwner, CandidateType.PLAYER, false, true, false, 2_000.0D));

        assertEquals(NaamahCourtRules.MAX_CANDIDATES, accumulator.size());
        assertEquals(Optional.of(trialOwner), NaamahCourtRules.chooseChallenger(accumulator.snapshot()));
        assertTrue(accumulator.snapshot().stream().anyMatch(candidate -> candidate.id().equals(directAttacker)));
        assertTrue(accumulator.snapshot().stream().anyMatch(candidate -> candidate.id().equals(currentChallenger)));
    }

    @Test
    void challengerSelectionExaminesAllBoundedInputInsteadOfFirstSixteenRows() {
        final java.util.ArrayList<Candidate> candidates = new java.util.ArrayList<>();
        for (int index = 0; index < 24; index++) {
            candidates.add(new Candidate(
                new UUID(2L, index), CandidateType.PLAYER, false, false, false, index + 1.0D
            ));
        }
        final UUID lateTrialOwner = new UUID(0L, 77L);
        candidates.add(new Candidate(lateTrialOwner, CandidateType.PLAYER, false, true, false, 900.0D));

        assertEquals(Optional.of(lateTrialOwner), NaamahCourtRules.chooseChallenger(candidates));
    }

    @Test
    void strongActionsEnforceWindupRecoveryAndExecutionRevalidation() {
        for (final Action action : List.of(Action.DREAM_APPROACH, Action.COURT_WAVE, Action.VEIL_STEP)) {
            final NaamahCourtRules.ActionWindow window = NaamahCourtRules.begin(action, 100L, 1, 1);
            assertEquals(120L, window.executeAt());
            assertEquals(150L, window.recoverUntil());
            assertFalse(NaamahCourtRules.canExecute(window, 119L, true));
            assertFalse(NaamahCourtRules.canExecute(window, 120L, false));
            assertTrue(NaamahCourtRules.canExecute(window, 120L, true));
            assertTrue(NaamahCourtRules.isRecovering(window, 149L));
            assertFalse(NaamahCourtRules.isRecovering(window, 150L));
        }
    }

    @Test
    void automaticCourtScheduleLeavesMeleeWindowsAndKeepsLatePhaseActionsOccasional() {
        final java.util.ArrayList<Action> enthroned = new java.util.ArrayList<>();
        final java.util.ArrayList<Action> chorus = new java.util.ArrayList<>();
        final java.util.ArrayList<Action> refusal = new java.util.ArrayList<>();
        for (long slot = 0L; slot < 32L; slot++) {
            enthroned.add(NaamahCourtRules.automaticAction(Phase.ENTHRONED, slot));
            chorus.add(NaamahCourtRules.automaticAction(Phase.CHORUS_OF_WAVES, slot));
            refusal.add(NaamahCourtRules.automaticAction(Phase.SOVEREIGN_REFUSAL, slot));
        }

        assertEquals(4L, enthroned.stream().filter(Action.DREAM_APPROACH::equals).count());
        assertEquals(8L, chorus.stream().filter(Action.COURT_WAVE::equals).count());
        assertEquals(2L, refusal.stream().filter(Action.COURT_WAVE::equals).count());
        assertEquals(2L, refusal.stream().filter(Action.VEIL_STEP::equals).count());
        assertTrue(enthroned.stream().filter(Action.NONE::equals).count() > 0L);
        assertTrue(chorus.stream().filter(Action.NONE::equals).count() > 0L);
        assertTrue(refusal.stream().filter(Action.NONE::equals).count() > 0L);
        assertTrue(refusal.stream().filter(Action.COURT_WAVE::equals).count()
            < chorus.stream().filter(Action.COURT_WAVE::equals).count());
    }

    @Test
    void safeAmbientScheduleHoldsCourtAndMakesShelteredDayRestSparse() {
        assertEquals(AmbientMode.VEILED_REST,
            NaamahCourtRules.ambientMode(true, true, false));
        assertEquals(AmbientMode.HOLD_COURT,
            NaamahCourtRules.ambientMode(false, false, false));
        assertEquals(AmbientMode.SEA_BORNE_COMPOSURE,
            NaamahCourtRules.ambientMode(true, true, true));
        assertEquals(400, NaamahCourtRules.ambientFeedbackInterval(AmbientMode.VEILED_REST));
        assertEquals(200, NaamahCourtRules.ambientFeedbackInterval(AmbientMode.HOLD_COURT));
        assertEquals(200, NaamahCourtRules.ambientFeedbackInterval(AmbientMode.SEA_BORNE_COMPOSURE));
        assertTrue(NaamahCourtRules.holdsPosition(AmbientMode.VEILED_REST));
        assertTrue(NaamahCourtRules.holdsPosition(AmbientMode.HOLD_COURT));
        assertTrue(NaamahCourtRules.holdsPosition(AmbientMode.SEA_BORNE_COMPOSURE));
    }

    @Test
    void scanNavigationRetryStepAndWaveBoundsStayFixed() {
        assertEquals(10, NaamahCourtRules.DECISION_INTERVAL_TICKS);
        assertEquals(40, NaamahCourtRules.CANDIDATE_SCAN_INTERVAL_TICKS);
        assertEquals(40, NaamahCourtRules.EXPENSIVE_SCAN_INTERVAL_TICKS);
        assertEquals(20, NaamahCourtRules.NAVIGATION_INTERVAL_TICKS);
        assertEquals(16, NaamahCourtRules.MAX_CANDIDATES);
        assertEquals(24.0D, NaamahCourtRules.CANDIDATE_RADIUS);
        assertEquals(256, NaamahCourtRules.MAX_DESTINATION_BLOCKS);
        assertEquals(6.0D, NaamahCourtRules.WAVE_RADIUS);
        assertEquals(4.0F, NaamahCourtRules.WAVE_DAMAGE);
        assertTrue(NaamahCourtRules.withinLocalStep(new BlockPos(0, 64, 0), new BlockPos(8, 64, 0)));
        assertFalse(NaamahCourtRules.withinLocalStep(new BlockPos(0, 64, 0), new BlockPos(9, 64, 0)));
        assertEquals(new NaamahCourtRules.RouteRetry(3, 500L), NaamahCourtRules.routeFailure(2, 400L));
        assertEquals(new NaamahCourtRules.RouteRetry(0, 0L), NaamahCourtRules.routeSuccess());
        assertFalse(NaamahCourtRules.navigationDue(100L, 119L));
        assertTrue(NaamahCourtRules.navigationDue(100L, 120L));
        assertFalse(NaamahCourtRules.mayBeginMovementAction(500L, 501L, false, true));
        assertFalse(NaamahCourtRules.mayBeginMovementAction(500L, 0L, true, true));
        assertFalse(NaamahCourtRules.mayBeginMovementAction(500L, 0L, false, false));
        assertTrue(NaamahCourtRules.mayBeginMovementAction(500L, 0L, false, true));
    }

    @Test
    void concludedAudienceExcludesOnlyItsOwnerAndDoesNotPacifyEveryone() {
        final UUID owner = UUID.randomUUID();
        final UUID other = UUID.randomUUID();
        assertFalse(NaamahCourtRules.mayAttack(owner, true, Optional.of(owner)));
        assertTrue(NaamahCourtRules.mayAttack(other, true, Optional.of(owner)));
        assertTrue(NaamahCourtRules.mayAttack(owner, false, Optional.of(owner)));
        assertEquals(507L, NaamahCourtRules.staggeredDeadline(500L, 7, 10));
        assertEquals(503L, NaamahCourtRules.staggeredDeadline(500L, 43, 40));
    }
}
