package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.SpiritRules.AttackerObservation;
import com.kadamitas.warlockery.entity.SpiritRules.AttendCandidate;
import com.kadamitas.warlockery.entity.SpiritRules.BandAction;
import com.kadamitas.warlockery.entity.SpiritRules.GuardEnd;
import com.kadamitas.warlockery.entity.SpiritRules.GuardObservation;
import com.kadamitas.warlockery.entity.SpiritRules.Phase;
import com.kadamitas.warlockery.entity.SpiritRules.RouteResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Truth tables for the pure F19 Spirit policy. */
final class SpiritRulesTest {
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private static AttackerObservation legalAttacker() {
        return new AttackerObservation(true, true, true, false, false, false, true, true, false, 0);
    }

    @Test
    void anUnboundSpiritWithdrawsFromLivingPlayersInsideItsExactWaryRadius() {
        assertEquals(144.0D, SpiritRules.WARY_RANGE_SQUARED,
            "the audited twelve block wary radius is preserved exactly");
        assertTrue(SpiritRules.shouldWithdraw(false, true, true, 144.0D, 0));
        assertFalse(SpiritRules.shouldWithdraw(false, true, true, 144.1D, 0));
        assertFalse(SpiritRules.shouldWithdraw(true, true, true, 4.0D, 0),
            "binding stops avoidance outright rather than decaying it");
        assertFalse(SpiritRules.shouldWithdraw(false, false, true, 4.0D, 0));
        assertFalse(SpiritRules.shouldWithdraw(false, true, false, 4.0D, 0));
        assertFalse(SpiritRules.shouldWithdraw(false, true, true, 4.0D, 1),
            "the wary reaction is finite: its own cooldown suppresses an immediate repeat");
    }

    @Test
    void separationThenCooldownGovernSoulLightAttendance() {
        assertTrue(SpiritRules.separated(SpiritRules.SEPARATION_RANGE_SQUARED + 1.0D));
        assertFalse(SpiritRules.separated(SpiritRules.SEPARATION_RANGE_SQUARED));
        assertTrue(SpiritRules.attendAllowed(false, true, 0));
        assertFalse(SpiritRules.attendAllowed(true, true, 0),
            "a bound Spirit attends its owner, never a soul light");
        assertFalse(SpiritRules.attendAllowed(false, false, 0));
        assertFalse(SpiritRules.attendAllowed(false, true, 1));
    }

    @Test
    void attendanceSelectionIsStableByDistanceThenBlockPosition() {
        final List<AttendCandidate> inspected = List.of(
            new AttendCandidate(80L, 16.0D),
            new AttendCandidate(20L, 4.0D),
            new AttendCandidate(5L, 4.0D)
        );
        assertEquals(new AttendCandidate(5L, 4.0D), SpiritRules.select(inspected).orElseThrow());
        assertEquals(SpiritRules.rank(inspected), SpiritRules.rank(inspected.reversed()));
        assertTrue(SpiritRules.select(List.of()).isEmpty());
        assertEquals(SpiritRules.MAX_ATTEND_CANDIDATES_RETAINED, SpiritRules.rank(
            java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> new AttendCandidate(index, index))
                .toList()
        ).size());
    }

    @Test
    void onlyTheOwnersRecentValidDirectAttackerIsEverALegalDefenceSubject() {
        assertTrue(SpiritRules.attackerLegal(legalAttacker()));
        assertFalse(SpiritRules.attackerLegal(new AttackerObservation(
            false, true, true, false, false, false, true, true, false, 0)), "not living");
        assertFalse(SpiritRules.attackerLegal(new AttackerObservation(
            true, false, true, false, false, false, true, true, false, 0)), "not alive");
        assertFalse(SpiritRules.attackerLegal(new AttackerObservation(
            true, true, false, false, false, false, true, true, false, 0)), "other dimension");
        assertFalse(SpiritRules.attackerLegal(new AttackerObservation(
            true, true, true, true, false, false, true, true, false, 0)), "the Spirit itself");
        assertFalse(SpiritRules.attackerLegal(new AttackerObservation(
            true, true, true, false, true, false, true, true, false, 0)), "the owner");
        assertFalse(SpiritRules.attackerLegal(new AttackerObservation(
            true, true, true, false, false, true, false, false, false, 0)), "a sibling of the owner");
        assertFalse(SpiritRules.attackerLegal(new AttackerObservation(
            true, true, true, false, false, false, true, false, false, 0)), "creative or spectator");
        assertFalse(SpiritRules.attackerLegal(new AttackerObservation(
            true, true, true, false, false, false, true, true, true, 0)), "invulnerable");
        assertTrue(SpiritRules.attackerLegal(new AttackerObservation(
            true, true, true, false, false, false, false, false, false, 0)),
            "a nonplayer attacker needs no game mode check");
    }

    @Test
    void staleOwnerDamageNeverJustifiesADefence() {
        assertEquals(40, SpiritRules.ATTACKER_FRESHNESS_TICKS);
        assertTrue(SpiritRules.attackerLegal(new AttackerObservation(
            true, true, true, false, false, false, true, true, false,
            SpiritRules.ATTACKER_FRESHNESS_TICKS)));
        assertFalse(SpiritRules.attackerLegal(new AttackerObservation(
            true, true, true, false, false, false, true, true, false,
            SpiritRules.ATTACKER_FRESHNESS_TICKS + 1)));
        assertFalse(SpiritRules.attackerLegal(new AttackerObservation(
            true, true, true, false, false, false, true, true, false, -1)),
            "a negative freshness reading is treated as no evidence at all");
    }

    @Test
    void guardOpensOnlyWhenBoundLegalAndRecovered() {
        assertTrue(SpiritRules.guardAllowed(true, true, 0));
        assertFalse(SpiritRules.guardAllowed(false, true, 0), "a free Spirit has no one to defend");
        assertFalse(SpiritRules.guardAllowed(true, false, 0));
        assertFalse(SpiritRules.guardAllowed(true, true, 1),
            "the recovery window forbids an immediate second defence");
    }

    @Test
    void guardEndPrefersOwnerThenDimensionThenAttackerThenStrikeThenRangeThenRouteThenTimeout() {
        assertEquals(GuardEnd.NO_OWNER, SpiritRules.guardEnd(
            new GuardObservation(false, true, true, true, false, 1.0D, 0, 10, 0)));
        assertEquals(GuardEnd.DIMENSION, SpiritRules.guardEnd(
            new GuardObservation(true, true, true, false, false, 1.0D, 0, 10, 0)));
        assertEquals(GuardEnd.INVALID_ATTACKER, SpiritRules.guardEnd(
            new GuardObservation(true, false, true, true, false, 1.0D, 0, 10, 0)));
        assertEquals(GuardEnd.INVALID_ATTACKER, SpiritRules.guardEnd(
            new GuardObservation(true, true, false, true, false, 1.0D, 0, 10, 0)));
        assertEquals(GuardEnd.STRUCK, SpiritRules.guardEnd(new GuardObservation(
            true, true, true, true, false, 1.0D, SpiritRules.MAX_DEFENCE_STRIKES, 10, 0)));
        assertEquals(GuardEnd.RANGE, SpiritRules.guardEnd(new GuardObservation(
            true, true, true, true, false, SpiritRules.DEFEND_RANGE_SQUARED + 1.0D, 0, 10, 0)));
        assertEquals(GuardEnd.ROUTE_FAILURE, SpiritRules.guardEnd(new GuardObservation(
            true, true, true, true, false, 1.0D, 0, 10, SpiritRules.MAX_ROUTE_FAILURES)));
        assertEquals(GuardEnd.EXPIRED, SpiritRules.guardEnd(
            new GuardObservation(true, true, true, true, false, 1.0D, 0, 0, 0)));
        assertEquals(GuardEnd.NONE, SpiritRules.guardEnd(
            new GuardObservation(true, true, true, true, false, 1.0D, 0, 1, 2)));
    }

    @Test
    void anElapsedWarningGraduatesIntoTheDefenceWindowInsteadOfExpiringTheGuard() {
        // Regression: the warning countdown and the guard expiry used to be read from the same
        // field, so on the tick the warning elapsed the guard ended and DEFEND was unreachable.
        assertEquals(GuardEnd.NONE, SpiritRules.guardEnd(
            new GuardObservation(true, true, true, true, true, 1.0D, 0, 0, 0)),
            "a warning whose countdown reached zero never ends the guard");
        assertTrue(SpiritRules.warningGraduates(0));
        assertTrue(SpiritRules.warningGraduates(-1));
        assertFalse(SpiritRules.warningGraduates(1));
        assertEquals(GuardEnd.EXPIRED, SpiritRules.guardEnd(
            new GuardObservation(true, true, true, true, false, 1.0D, 0, 0, 0)),
            "only the defence window itself may expire the guard");
        assertEquals(GuardEnd.INVALID_ATTACKER, SpiritRules.guardEnd(
            new GuardObservation(true, false, true, true, true, 1.0D, 0, 0, 0)),
            "a warning still ends the moment its subject stops being legal");
    }

    @Test
    void theSingleAttributedStrikeIsGenuinelyReachableFromAFreshWarning() {
        // The whole defence chain, evaluated as the runtime evaluates it.
        assertTrue(SpiritRules.guardAllowed(true, SpiritRules.attackerLegal(legalAttacker()), 0));
        assertEquals(GuardEnd.NONE, SpiritRules.guardEnd(new GuardObservation(
            true, true, true, true, true, 1.0D, 0, SpiritRules.WARN_TICKS, 0)));
        assertEquals(GuardEnd.NONE, SpiritRules.guardEnd(new GuardObservation(
            true, true, true, true, true, 1.0D, 0, 0, 0)));
        assertTrue(SpiritRules.warningGraduates(0));
        assertEquals(GuardEnd.NONE, SpiritRules.guardEnd(new GuardObservation(
            true, true, true, true, false, 1.0D, 0, SpiritRules.DEFEND_TICKS, 0)));
        assertTrue(SpiritRules.strikeAllowed(0, 1.0D, true, SpiritRules.DEFEND_TICKS));
        assertEquals(GuardEnd.STRUCK, SpiritRules.guardEnd(new GuardObservation(
            true, true, true, true, false, 1.0D, 1, SpiritRules.DEFEND_TICKS, 0)));
    }

    @Test
    void theShippedOwnerRecallIsPreservedAtItsExactAuditedDistance() {
        assertTrue(SpiritRules.ownerRecallRequired(
            CreatureBehaviorRules.OWNER_TELEPORT_DISTANCE_SQUARED));
        assertFalse(SpiritRules.ownerRecallRequired(
            CreatureBehaviorRules.OWNER_TELEPORT_DISTANCE_SQUARED - 1.0D));
        assertTrue(SpiritRules.ownerRecallRequired(
            SpiritRules.OWNER_RELEASE_RANGE_SQUARED + 1.0D),
            "a Spirit past the attendance gate is recalled rather than silently abandoned");
    }

    @Test
    void exactlyOneOrdinaryAttributedStrikeIsPermittedPerDefenceWindow() {
        assertEquals(1, SpiritRules.MAX_DEFENCE_STRIKES);
        assertTrue(SpiritRules.strikeAllowed(0, SpiritRules.STRIKE_REACH_SQUARED, true, 1));
        assertFalse(SpiritRules.strikeAllowed(1, 1.0D, true, 1), "the second strike is refused");
        assertFalse(SpiritRules.strikeAllowed(0, 1.0D, false, 1), "an unseen attacker is not struck");
        assertFalse(SpiritRules.strikeAllowed(0, 1.0D, true, 0), "the closed window is not struck in");
        assertFalse(SpiritRules.strikeAllowed(
            0, SpiritRules.STRIKE_REACH_SQUARED + 0.1D, true, 1), "out of reach");
        assertEquals(4.0F, SpiritRules.strikeDamage(4.0F),
            "the strike carries the Spirit's own attack attribute with no amplification");
        assertEquals(0.0F, SpiritRules.strikeDamage(Float.NaN));
        assertEquals(0.0F, SpiritRules.strikeDamage(-3.0F));
    }

    @Test
    void aSpiritNeverProactivelyTargetsAnything() {
        assertTrue(SpiritRules.canAttack(true, true, true));
        assertFalse(SpiritRules.canAttack(false, true, true), "a free Spirit attacks nothing");
        assertFalse(SpiritRules.canAttack(true, false, true), "outside a defence window, nothing");
        assertFalse(SpiritRules.canAttack(true, true, false),
            "only the one accepted attacker may ever be struck");
    }

    @Test
    void warnAndAttendPulsesAreCappedAndNeverReplayOnLoad() {
        assertTrue(SpiritRules.pulseDue(0, 0, SpiritRules.MAX_WARN_PULSES));
        assertFalse(SpiritRules.pulseDue(1, 0, SpiritRules.MAX_WARN_PULSES));
        assertFalse(SpiritRules.pulseDue(0, SpiritRules.MAX_WARN_PULSES, SpiritRules.MAX_WARN_PULSES));
        assertEquals(SpiritRules.WARN_PULSE_INTERVAL_TICKS,
            SpiritRules.resetPulseIntervalOnLoad(0, SpiritRules.WARN_PULSE_INTERVAL_TICKS));
        assertEquals(SpiritRules.ATTEND_PULSE_INTERVAL_TICKS,
            SpiritRules.resetPulseIntervalOnLoad(0, SpiritRules.ATTEND_PULSE_INTERVAL_TICKS));
        assertEquals(0, SpiritRules.warnPulsesRemaining(SpiritRules.MAX_WARN_PULSES + 5));
        assertEquals(SpiritRules.MAX_WARN_PULSES, SpiritRules.warnPulsesRemaining(-2));
        assertEquals(0, SpiritRules.attendPulsesRemaining(SpiritRules.MAX_ATTEND_PULSES));
    }

    @Test
    void bandsAndOwnerAttendanceMatchTheAuditedAuraContract() {
        assertEquals(BandAction.APPROACH, SpiritRules.followBand(
            (double) SpiritRules.FOLLOW_BAND_MAX * SpiritRules.FOLLOW_BAND_MAX + 1.0D));
        assertEquals(BandAction.HOLD, SpiritRules.followBand(
            (double) SpiritRules.FOLLOW_BAND_MAX * SpiritRules.FOLLOW_BAND_MAX));
        assertEquals(BandAction.WITHDRAW, SpiritRules.followBand(1.0D));
        assertEquals(BandAction.HOLD, SpiritRules.attendBand(4.0D));
        assertTrue(SpiritRules.ownerAttendanceAllowed(true, true, true, 4.0D));
        assertFalse(SpiritRules.ownerAttendanceAllowed(true, true, true,
            SpiritRules.OWNER_RELEASE_RANGE_SQUARED + 1.0D));
        assertEquals(240, SpiritRules.AURA_NIGHT_VISION_TICKS,
            "the audited Night Vision duration is preserved exactly");
        assertEquals(20, SpiritRules.AURA_INTERVAL_TICKS);
        assertTrue(SpiritRules.auraDue(0));
        assertFalse(SpiritRules.auraDue(19));
    }

    @Test
    void hazardOutranksDefenceWhichOutranksBindingAndEveryAttentionPhase() {
        for (final Phase phase : Phase.values()) {
            assertEquals(0, SpiritRules.priority(phase, true, false, false));
            assertEquals(1, SpiritRules.priority(phase, false, true, false));
            assertEquals(2, SpiritRules.priority(phase, false, false, true));
            assertTrue(SpiritRules.hazardPreempts(phase, true));
            assertFalse(SpiritRules.hazardPreempts(phase, false));
            assertTrue(SpiritRules.defencePreempts(phase, true));
            assertTrue(SpiritRules.bindingPreempts(phase, true));
        }
        assertTrue(SpiritRules.priority(Phase.DEFEND, false, false, false)
            < SpiritRules.priority(Phase.WARN, false, false, false));
        assertTrue(SpiritRules.priority(Phase.WARN, false, false, false)
            < SpiritRules.priority(Phase.WARY, false, false, false));
        assertTrue(SpiritRules.priority(Phase.WARY, false, false, false)
            < SpiritRules.priority(Phase.ATTEND, false, false, false));
        assertTrue(SpiritRules.priority(Phase.BOUND, false, false, false)
            < SpiritRules.priority(Phase.WANDER, false, false, false));
    }

    @Test
    void routeFailuresAccumulateToThreeThenBackOffAndResetOnSuccess() {
        assertEquals(1, SpiritRules.routeFailuresAfter(0, new RouteResult(false, false, false)));
        assertEquals(3, SpiritRules.routeFailuresAfter(2, new RouteResult(true, true, false)));
        assertEquals(3, SpiritRules.routeFailuresAfter(9, new RouteResult(true, true, false)));
        assertEquals(0, SpiritRules.routeFailuresAfter(2, new RouteResult(true, true, true)));
        assertTrue(SpiritRules.routeExhausted(3));
        assertEquals(SpiritRules.ROUTE_BACKOFF_TICKS, SpiritRules.routeBackoffAfter(3));
        assertEquals(0, SpiritRules.routeBackoffAfter(0));
        assertTrue(SpiritRules.pathRequestAllowed(0, 0));
        assertFalse(SpiritRules.pathRequestAllowed(1, 0));
        assertFalse(SpiritRules.pathRequestAllowed(0, 1));
    }

    @Test
    void durationsClampWithoutEverReadingAsAnUnboundedSentinel() {
        assertEquals(0, SpiritRules.clampRemaining(-1, SpiritRules.WARY_TICKS));
        assertEquals(SpiritRules.WARY_TICKS,
            SpiritRules.clampRemaining(Integer.MAX_VALUE, SpiritRules.WARY_TICKS));
        assertEquals(0, SpiritRules.decrementLoaded(0));
        assertEquals(4, SpiritRules.decrementLoaded(5));
        assertTrue(SpiritRules.WARY_TICKS < 20_000);
        assertTrue(SpiritRules.ATTEND_COOLDOWN_TICKS < 20_000);
        assertTrue(SpiritRules.RECOVER_TICKS < 20_000);
    }

    @Test
    void safeSearchOffsetsSpanTheEnvelopeWithoutTheOriginOrDuplicates() {
        final List<SpiritRules.SafeSearchOffset> offsets =
            SpiritRules.safeSearchOffsets(FIRST, 5, 2, SpiritRules.MAX_SAFE_CANDIDATES);
        assertTrue(offsets.size() <= SpiritRules.MAX_SAFE_CANDIDATES);
        assertEquals(offsets.size(), java.util.Set.copyOf(offsets).size());
        assertTrue(offsets.stream().noneMatch(
            offset -> offset.dx() == 0 && offset.dy() == 0 && offset.dz() == 0));
        assertTrue(offsets.stream().anyMatch(offset -> offset.dy() > 0));
        assertTrue(offsets.stream().anyMatch(offset -> offset.dy() < 0));
    }

    @Test
    void safeCandidatePreferenceIsSeparationThenSafetyThenDisplacementThenPosition() {
        final var preference = SpiritRules.safeCandidatePreference();
        assertTrue(preference.compare(
            new SpiritRules.SafeCandidate(100.0D, true, 9.0D, 1L),
            new SpiritRules.SafeCandidate(1.0D, true, 1.0D, 1L)) < 0);
        assertTrue(preference.compare(
            new SpiritRules.SafeCandidate(4.0D, true, 9.0D, 1L),
            new SpiritRules.SafeCandidate(4.0D, false, 1.0D, 1L)) < 0);
        assertTrue(preference.compare(
            new SpiritRules.SafeCandidate(4.0D, true, 1.0D, 8L),
            new SpiritRules.SafeCandidate(4.0D, true, 9.0D, 1L)) < 0);
        assertTrue(preference.compare(
            new SpiritRules.SafeCandidate(4.0D, true, 1.0D, 2L),
            new SpiritRules.SafeCandidate(4.0D, true, 1.0D, 8L)) < 0);
    }

    @Test
    void everyDeclaredBudgetIsFiniteAndSmallEnoughToStateAsAContract() {
        assertEquals(
            (2 * SpiritRules.ATTEND_SEARCH_HORIZONTAL + 1)
                * (2 * SpiritRules.ATTEND_SEARCH_VERTICAL + 1)
                * (2 * SpiritRules.ATTEND_SEARCH_HORIZONTAL + 1),
            SpiritRules.MAX_ATTEND_READS);
        assertTrue(SpiritRules.MAX_PROXIMITY_CANDIDATES <= 8);
        assertTrue(SpiritRules.MAX_SAFE_CANDIDATES <= 24);
        assertTrue(SpiritRules.MAX_CHARGED_READS <= 256);
        assertEquals(3, SpiritRules.MAX_ROUTE_FAILURES);
        assertTrue(SpiritRules.MAX_STATE_BYTES <= 512);
        assertTrue(SpiritRules.WARY_WITHDRAW_HORIZONTAL < SpiritRules.WARY_RANGE,
            "a withdrawal destination stays local so a computed goal cannot strand the Spirit");
    }

    @Test
    void theTwoSpeciesShareNoPhaseVocabularyAtAll() {
        final var spiritPhases = java.util.Arrays.stream(Phase.values())
            .map(Enum::name).collect(java.util.stream.Collectors.toSet());
        final var soulPhases = java.util.Arrays.stream(LostSoulRules.Phase.values())
            .map(Enum::name).collect(java.util.stream.Collectors.toSet());
        final var shared = new java.util.HashSet<>(spiritPhases);
        shared.retainAll(soulPhases);
        assertEquals(java.util.Set.of("WANDER", "BOUND"), shared,
            "only the two structural phases are shared; every motive phase is disjoint");
        assertFalse(soulPhases.contains("DEFEND"));
        assertFalse(soulPhases.contains("WARN"));
        assertFalse(soulPhases.contains("WARY"));
        assertFalse(spiritPhases.contains("PETITION"));
        assertFalse(spiritPhases.contains("SETTLE"));
        assertFalse(spiritPhases.contains("COOLDOWN"));
    }
}
