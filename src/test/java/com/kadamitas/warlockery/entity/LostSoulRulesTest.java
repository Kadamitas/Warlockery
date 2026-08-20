package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.LostSoulRules.AnchorCandidate;
import com.kadamitas.warlockery.entity.LostSoulRules.AnchorObservation;
import com.kadamitas.warlockery.entity.LostSoulRules.BandAction;
import com.kadamitas.warlockery.entity.LostSoulRules.EpisodeEnd;
import com.kadamitas.warlockery.entity.LostSoulRules.Phase;
import com.kadamitas.warlockery.entity.LostSoulRules.RouteResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Truth tables for the pure F19 Lost Soul policy. */
final class LostSoulRulesTest {
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void anchorSelectionIsStableByDistanceThenBlockPosition() {
        final List<AnchorCandidate> inspected = List.of(
            new AnchorCandidate(90L, 25.0D),
            new AnchorCandidate(30L, 9.0D),
            new AnchorCandidate(10L, 9.0D),
            new AnchorCandidate(50L, 4.0D)
        );
        assertEquals(new AnchorCandidate(50L, 4.0D), LostSoulRules.select(inspected).orElseThrow());
        assertEquals(
            List.of(
                new AnchorCandidate(50L, 4.0D),
                new AnchorCandidate(10L, 9.0D),
                new AnchorCandidate(30L, 9.0D),
                new AnchorCandidate(90L, 25.0D)
            ),
            LostSoulRules.rank(inspected),
            "equal distances break by the lower packed block position, never by iteration order"
        );
        assertEquals(LostSoulRules.rank(inspected), LostSoulRules.rank(inspected.reversed()),
            "the ordering does not depend on the order candidates were read in");
    }

    @Test
    void rankNeverRetainsMoreThanTheDeclaredCandidateCap() {
        final List<AnchorCandidate> many = java.util.stream.IntStream.range(0, 16)
            .mapToObj(index -> new AnchorCandidate(index, index))
            .map(AnchorCandidate.class::cast)
            .toList();
        assertEquals(LostSoulRules.MAX_ANCHOR_CANDIDATES_RETAINED, LostSoulRules.rank(many).size());
        assertTrue(LostSoulRules.select(List.of()).isEmpty());
    }

    @Test
    void episodeEndPrefersBindingThenDimensionThenAnchorThenRouteThenTimeout() {
        assertEquals(EpisodeEnd.BOUND, LostSoulRules.episodeEnd(
            new AnchorObservation(true, true, true, true, 100, 0, true)));
        assertEquals(EpisodeEnd.DIMENSION, LostSoulRules.episodeEnd(
            new AnchorObservation(true, false, true, true, 100, 0, false)));
        assertEquals(EpisodeEnd.ANCHOR_LOST, LostSoulRules.episodeEnd(
            new AnchorObservation(true, true, false, true, 100, 0, false)));
        assertEquals(EpisodeEnd.ANCHOR_LOST, LostSoulRules.episodeEnd(
            new AnchorObservation(true, true, true, false, 100, 0, false)));
        assertEquals(EpisodeEnd.ROUTE_FAILURE, LostSoulRules.episodeEnd(
            new AnchorObservation(true, true, true, true, 100, LostSoulRules.MAX_ROUTE_FAILURES, false)));
        assertEquals(EpisodeEnd.EXPIRED, LostSoulRules.episodeEnd(
            new AnchorObservation(true, true, true, true, 0, 0, false)));
        assertEquals(EpisodeEnd.NONE, LostSoulRules.episodeEnd(
            new AnchorObservation(true, true, true, true, 1, 2, false)));
    }

    @Test
    void anEpisodeOnlyStartsUnboundOffCooldownAndWithoutAnAnchor() {
        assertTrue(LostSoulRules.episodeStartAllowed(false, 0, false));
        assertFalse(LostSoulRules.episodeStartAllowed(true, 0, false),
            "a bound Lost Soul never begins a memorial episode");
        assertFalse(LostSoulRules.episodeStartAllowed(false, 1, false));
        assertFalse(LostSoulRules.episodeStartAllowed(false, 0, true));
    }

    @Test
    void bandsApproachWithdrawAndHoldAtTheirExactBoundaries() {
        assertEquals(BandAction.APPROACH, LostSoulRules.petitionBand(
            (double) LostSoulRules.PETITION_BAND_MAX * LostSoulRules.PETITION_BAND_MAX + 0.1D));
        assertEquals(BandAction.HOLD, LostSoulRules.petitionBand(
            (double) LostSoulRules.PETITION_BAND_MAX * LostSoulRules.PETITION_BAND_MAX));
        assertEquals(BandAction.HOLD, LostSoulRules.petitionBand(
            (double) LostSoulRules.PETITION_BAND_MIN * LostSoulRules.PETITION_BAND_MIN));
        assertEquals(BandAction.WITHDRAW, LostSoulRules.petitionBand(0.0D));
        assertEquals(BandAction.APPROACH, LostSoulRules.followBand(
            (double) LostSoulRules.FOLLOW_BAND_MAX * LostSoulRules.FOLLOW_BAND_MAX + 1.0D));
        assertEquals(BandAction.WITHDRAW, LostSoulRules.followBand(1.0D));
        assertTrue(LostSoulRules.petitionReached(4.0D));
        assertFalse(LostSoulRules.petitionReached(100.0D));
        assertTrue(LostSoulRules.settleReached(25.0D));
        assertFalse(LostSoulRules.settleReached(26.0D));
    }

    @Test
    void petitionPulsesAreCappedAndNeverReplayOnLoad() {
        assertTrue(LostSoulRules.pulseDue(0, 0, LostSoulRules.MAX_PETITION_PULSES));
        assertFalse(LostSoulRules.pulseDue(1, 0, LostSoulRules.MAX_PETITION_PULSES));
        assertFalse(LostSoulRules.pulseDue(
            0, LostSoulRules.MAX_PETITION_PULSES, LostSoulRules.MAX_PETITION_PULSES));
        assertEquals(LostSoulRules.PETITION_PULSE_INTERVAL_TICKS,
            LostSoulRules.resetPulseIntervalOnLoad(0, LostSoulRules.PETITION_PULSE_INTERVAL_TICKS),
            "a persisted zero interval restores the full interval instead of reading as due");
        assertEquals(5, LostSoulRules.resetPulseIntervalOnLoad(
            5, LostSoulRules.PETITION_PULSE_INTERVAL_TICKS));
        assertEquals(LostSoulRules.PETITION_PULSE_INTERVAL_TICKS,
            LostSoulRules.resetPulseIntervalOnLoad(
                LostSoulRules.PETITION_PULSE_INTERVAL_TICKS + 40,
                LostSoulRules.PETITION_PULSE_INTERVAL_TICKS));
        assertEquals(0, LostSoulRules.petitionPulsesRemaining(LostSoulRules.MAX_PETITION_PULSES + 3));
        assertEquals(LostSoulRules.MAX_PETITION_PULSES, LostSoulRules.petitionPulsesRemaining(-1));
    }

    @Test
    void aLostSoulCanNeverAttackAndBindingAlwaysEntersQuietAttendance() {
        assertFalse(LostSoulRules.canAttack(false, false));
        assertFalse(LostSoulRules.canAttack(true, false));
        assertFalse(LostSoulRules.canAttack(true, true),
            "an owner under attack never grants a Lost Soul a target: it is not a defender");
        assertEquals(Phase.BOUND, LostSoulRules.phaseAfterBinding());
    }

    @Test
    void ownerAttendanceRequiresALiveSameDimensionOwnerInsideTheReleaseRange() {
        assertTrue(LostSoulRules.ownerAttendanceAllowed(true, true, true, 100.0D));
        assertFalse(LostSoulRules.ownerAttendanceAllowed(false, true, true, 100.0D));
        assertFalse(LostSoulRules.ownerAttendanceAllowed(true, false, true, 100.0D));
        assertFalse(LostSoulRules.ownerAttendanceAllowed(true, true, false, 100.0D));
        assertFalse(LostSoulRules.ownerAttendanceAllowed(true, true, true,
            LostSoulRules.OWNER_RELEASE_RANGE_SQUARED + 1.0D));
        assertTrue(LostSoulRules.auraDue(0));
        assertTrue(LostSoulRules.auraDue(LostSoulRules.AURA_INTERVAL_TICKS));
        assertFalse(LostSoulRules.auraDue(LostSoulRules.AURA_INTERVAL_TICKS - 1));
        assertEquals(240, LostSoulRules.AURA_NIGHT_VISION_TICKS,
            "the audited Night Vision duration is preserved exactly");
        assertEquals(20, LostSoulRules.AURA_INTERVAL_TICKS,
            "the audited aura cadence is preserved exactly");
    }

    @Test
    void routeFailuresAccumulateToThreeThenBackOffAndResetOnSuccess() {
        assertEquals(1, LostSoulRules.routeFailuresAfter(0, new RouteResult(false, false, false)));
        assertEquals(2, LostSoulRules.routeFailuresAfter(1, new RouteResult(true, false, false)));
        assertEquals(3, LostSoulRules.routeFailuresAfter(2, new RouteResult(true, true, false)));
        assertEquals(3, LostSoulRules.routeFailuresAfter(3, new RouteResult(true, true, false)));
        assertEquals(0, LostSoulRules.routeFailuresAfter(2, new RouteResult(true, true, true)));
        assertTrue(LostSoulRules.routeExhausted(LostSoulRules.MAX_ROUTE_FAILURES));
        assertFalse(LostSoulRules.routeExhausted(LostSoulRules.MAX_ROUTE_FAILURES - 1));
        assertEquals(LostSoulRules.ROUTE_BACKOFF_TICKS, LostSoulRules.routeBackoffAfter(3));
        assertEquals(0, LostSoulRules.routeBackoffAfter(2));
        assertTrue(LostSoulRules.pathRequestAllowed(0, 0));
        assertFalse(LostSoulRules.pathRequestAllowed(1, 0));
        assertFalse(LostSoulRules.pathRequestAllowed(0, 1));
    }

    @Test
    void hazardOutranksBindingWhichOutranksEveryAttentionPhase() {
        for (final Phase phase : Phase.values()) {
            assertEquals(0, LostSoulRules.priority(phase, true, false));
            assertTrue(LostSoulRules.priority(phase, false, true)
                < LostSoulRules.priority(phase, false, false),
                "the binding transition preempts every ordinary phase");
            assertTrue(LostSoulRules.hazardPreempts(phase, true));
            assertFalse(LostSoulRules.hazardPreempts(phase, false));
            assertTrue(LostSoulRules.bindingPreempts(phase, true));
            assertFalse(LostSoulRules.bindingPreempts(phase, false));
        }
        assertTrue(LostSoulRules.priority(Phase.PETITION, false, false)
            < LostSoulRules.priority(Phase.WANDER, false, false));
        assertTrue(LostSoulRules.priority(Phase.APPROACH, false, false)
            < LostSoulRules.priority(Phase.COOLDOWN, false, false));
    }

    @Test
    void durationsClampWithoutEverReadingAsAnUnboundedSentinel() {
        assertEquals(0, LostSoulRules.clampRemaining(-50, LostSoulRules.EPISODE_TICKS));
        assertEquals(LostSoulRules.EPISODE_TICKS,
            LostSoulRules.clampRemaining(Integer.MAX_VALUE, LostSoulRules.EPISODE_TICKS));
        assertEquals(0, LostSoulRules.clampRemaining(10, -5));
        assertEquals(0, LostSoulRules.decrementLoaded(0));
        assertEquals(0, LostSoulRules.decrementLoaded(-9));
        assertEquals(9, LostSoulRules.decrementLoaded(10));
        assertTrue(LostSoulRules.EPISODE_TICKS < 20_000);
        assertTrue(LostSoulRules.COOLDOWN_TICKS < 20_000);
        assertNotEquals(Long.MAX_VALUE, (long) LostSoulRules.EPISODE_TICKS);
    }

    @Test
    void stableOffsetIsDeterministicBoundedAndNeverNegative() {
        assertEquals(0, LostSoulRules.stableOffset(null, 40));
        assertEquals(0, LostSoulRules.stableOffset(FIRST, 0));
        assertEquals(LostSoulRules.stableOffset(FIRST, 40), LostSoulRules.stableOffset(FIRST, 40));
        for (final UUID id : List.of(FIRST, SECOND, UUID.randomUUID())) {
            final int offset = LostSoulRules.stableOffset(id, 40);
            assertTrue(offset >= 0 && offset < 40);
        }
    }

    @Test
    void safeSearchOffsetsSpanTheEnvelopeWithoutTheOriginOrDuplicates() {
        final List<LostSoulRules.SafeSearchOffset> offsets =
            LostSoulRules.safeSearchOffsets(FIRST, 6, 3, LostSoulRules.MAX_SAFE_CANDIDATES);
        assertTrue(offsets.size() <= LostSoulRules.MAX_SAFE_CANDIDATES);
        assertEquals(offsets.size(), java.util.Set.copyOf(offsets).size(), "no duplicates");
        assertTrue(offsets.stream().noneMatch(
            offset -> offset.dx() == 0 && offset.dy() == 0 && offset.dz() == 0));
        assertTrue(offsets.stream().anyMatch(offset -> offset.dy() > 0));
        assertTrue(offsets.stream().anyMatch(offset -> offset.dy() < 0));
        assertTrue(offsets.stream().anyMatch(offset -> offset.dx() > 0));
        assertTrue(offsets.stream().anyMatch(offset -> offset.dx() < 0));
        assertTrue(offsets.stream().anyMatch(offset -> offset.dz() > 0));
        assertTrue(offsets.stream().anyMatch(offset -> offset.dz() < 0));
        assertEquals(0, LostSoulRules.safeSearchOffsets(FIRST, 6, 3, 0).size());
    }

    @Test
    void safeCandidatePreferenceIsSeparationThenSafetyThenDisplacementThenPosition() {
        final var preference = LostSoulRules.safeCandidatePreference();
        final var far = new LostSoulRules.SafeCandidate(100.0D, true, 4.0D, 1L);
        final var near = new LostSoulRules.SafeCandidate(9.0D, true, 1.0D, 1L);
        assertTrue(preference.compare(far, near) < 0);
        final var safe = new LostSoulRules.SafeCandidate(9.0D, true, 4.0D, 5L);
        final var hazardous = new LostSoulRules.SafeCandidate(9.0D, false, 1.0D, 5L);
        assertTrue(preference.compare(safe, hazardous) < 0);
        final var closer = new LostSoulRules.SafeCandidate(9.0D, true, 1.0D, 9L);
        assertTrue(preference.compare(closer, safe) < 0);
        final var lowerPosition = new LostSoulRules.SafeCandidate(9.0D, true, 1.0D, 2L);
        assertTrue(preference.compare(lowerPosition, closer) < 0);
    }

    @Test
    void everyDeclaredBudgetIsFiniteAndSmallEnoughToStateAsAContract() {
        assertEquals(845, LostSoulRules.MAX_ANCHOR_READS,
            "the anchor envelope read cap is exactly the 13 x 5 x 13 box");
        assertEquals(
            (2 * LostSoulRules.ANCHOR_SEARCH_HORIZONTAL + 1)
                * (2 * LostSoulRules.ANCHOR_SEARCH_VERTICAL + 1)
                * (2 * LostSoulRules.ANCHOR_SEARCH_HORIZONTAL + 1),
            LostSoulRules.MAX_ANCHOR_READS);
        assertTrue(LostSoulRules.MAX_HAZARD_READS >= 27);
        assertTrue(LostSoulRules.MAX_SAFE_CANDIDATES <= 24);
        assertTrue(LostSoulRules.MAX_CHARGED_READS <= 256);
        assertTrue(LostSoulRules.MAX_ROUTE_FAILURES == 3);
        assertTrue(LostSoulRules.DISCOVERY_INTERVAL_TICKS >= 20);
        assertTrue(LostSoulRules.MAX_STATE_BYTES <= 512);
    }

    @Test
    void theShippedOwnerRecallIsPreservedAtItsExactAuditedDistance() {
        assertTrue(LostSoulRules.ownerRecallRequired(
            CreatureBehaviorRules.OWNER_TELEPORT_DISTANCE_SQUARED));
        assertFalse(LostSoulRules.ownerRecallRequired(
            CreatureBehaviorRules.OWNER_TELEPORT_DISTANCE_SQUARED - 1.0D));
        assertTrue(LostSoulRules.ownerRecallRequired(
            LostSoulRules.OWNER_RELEASE_RANGE_SQUARED + 1.0D),
            "a shade past the attendance gate is recalled rather than silently abandoned");
    }

    @Test
    void theChargedReadCeilingGenuinelyBoundsTheSafeSearch() {
        // Regression: only accepted candidates used to be charged, at two reads each, so the
        // ceiling could never bind and the counter understated the dominant cost.
        assertTrue(SpectralEntity.READS_PER_SAFE_CANDIDATE > 2,
            "a qualification costs a border test, four chunk tests, two states and a sweep");
        assertTrue(SpectralEntity.READS_PER_SAFE_CANDIDATE * LostSoulRules.MAX_SAFE_CANDIDATES
            <= LostSoulRules.MAX_CHARGED_READS,
            "the honest per-candidate charge still fits inside the declared ceiling");
        assertTrue(SpectralEntity.READS_PER_SAFE_CANDIDATE * LostSoulRules.MAX_SAFE_CANDIDATES
            > LostSoulRules.MAX_CHARGED_READS / 2,
            "and it is large enough that the ceiling is a real bound rather than decoration");
    }

    @Test
    void unusedOptionalHelpersStayTotalOnEmptyInput() {
        assertEquals(Optional.empty(), LostSoulRules.select(List.of()));
        assertEquals(List.of(), LostSoulRules.rank(List.of()));
    }
}
