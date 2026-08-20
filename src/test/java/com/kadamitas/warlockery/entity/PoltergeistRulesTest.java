package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.PoltergeistRules.BandAction;
import com.kadamitas.warlockery.entity.PoltergeistRules.EpisodeEnd;
import com.kadamitas.warlockery.entity.PoltergeistRules.Phase;
import com.kadamitas.warlockery.entity.PoltergeistRules.PropCandidate;
import com.kadamitas.warlockery.entity.PoltergeistRules.RouteResult;
import com.kadamitas.warlockery.entity.PoltergeistRules.ScanOffset;
import com.kadamitas.warlockery.entity.PoltergeistRules.TargetCandidate;
import com.kadamitas.warlockery.entity.PoltergeistRules.TargetObservation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure policy contracts for the F20 Poltergeist disturbance. */
final class PoltergeistRulesTest {
    private static final UUID LOW = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID HIGH = UUID.fromString("ffffffff-0000-0000-0000-000000000002");

    // ---------------------------------------------------------------- phases and priority

    @Test
    void theSpeciesOwnsExactlySixPhasesAndNoBindingOrDefenceRung() {
        assertEquals(
            List.of(Phase.LURK, Phase.RATTLE, Phase.MARK, Phase.LIFT, Phase.THROW, Phase.RECOVER),
            List.of(Phase.values()),
            "the disturbance chain is LURK, RATTLE, MARK, LIFT, THROW, RECOVER and nothing else"
        );
    }

    @Test
    void priorityIsHazardThenDamageThenEpisodeThenIdle() {
        assertEquals(0, PoltergeistRules.priority(Phase.THROW, true, false));
        assertEquals(1, PoltergeistRules.priority(Phase.THROW, false, true));
        assertTrue(PoltergeistRules.priority(Phase.THROW, false, false)
            < PoltergeistRules.priority(Phase.LIFT, false, false));
        assertTrue(PoltergeistRules.priority(Phase.LIFT, false, false)
            < PoltergeistRules.priority(Phase.MARK, false, false));
        assertTrue(PoltergeistRules.priority(Phase.MARK, false, false)
            < PoltergeistRules.priority(Phase.RATTLE, false, false));
        assertTrue(PoltergeistRules.priority(Phase.RATTLE, false, false)
            < PoltergeistRules.priority(Phase.RECOVER, false, false));
        assertTrue(PoltergeistRules.priority(Phase.RECOVER, false, false)
            < PoltergeistRules.priority(Phase.LURK, false, false));
    }

    @Test
    void anEscapableHazardPreemptsEveryPhaseAndNothingElseDoes() {
        for (final Phase phase : Phase.values()) {
            assertTrue(PoltergeistRules.hazardPreempts(phase, true), phase.name());
            assertFalse(PoltergeistRules.hazardPreempts(phase, false), phase.name());
        }
    }

    @Test
    void onlyALiveAttackPhaseIsCancelledByDamage() {
        assertTrue(PoltergeistRules.damageReactionPreempts(Phase.RATTLE));
        assertTrue(PoltergeistRules.damageReactionPreempts(Phase.MARK));
        assertTrue(PoltergeistRules.damageReactionPreempts(Phase.LIFT));
        assertTrue(PoltergeistRules.damageReactionPreempts(Phase.THROW));
        assertFalse(PoltergeistRules.damageReactionPreempts(Phase.LURK),
            "cancelling from an idle lurk would open a recovery no episode ever earned");
        assertFalse(PoltergeistRules.damageReactionPreempts(Phase.RECOVER),
            "cancelling from the recovery would restart the window that is already closing");
    }

    @Test
    void thePreservedBlinkKeepsItsAuditedTwoDamageThreshold() {
        assertFalse(PoltergeistRules.blinkOnDamage(1.9F));
        assertTrue(PoltergeistRules.blinkOnDamage(PoltergeistRules.BLINK_MIN_DAMAGE));
        assertTrue(PoltergeistRules.blinkOnDamage(9.0F));
    }

    @Test
    void aPoltergeistNeverAcquiresACombatTarget() {
        assertFalse(PoltergeistRules.canAttack());
    }

    // ---------------------------------------------------------------- episode retention

    @Test
    void everyEpisodeEndReasonIsReachableInItsExactPrecedence() {
        assertEquals(EpisodeEnd.DIMENSION,
            PoltergeistRules.episodeEnd(new TargetObservation(true, false, false, 0,
                PoltergeistRules.MAX_ROUTE_FAILURES, PoltergeistRules.MAX_EPISODE_PATH_REQUESTS)),
            "a target that walked into another dimension outranks every other release");
        assertEquals(EpisodeEnd.TARGET_LOST,
            PoltergeistRules.episodeEnd(new TargetObservation(true, true, false, 0,
                PoltergeistRules.MAX_ROUTE_FAILURES, PoltergeistRules.MAX_EPISODE_PATH_REQUESTS)));
        assertEquals(EpisodeEnd.TARGET_LOST,
            PoltergeistRules.episodeEnd(new TargetObservation(false, true, true, 10, 0, 0)));
        assertEquals(EpisodeEnd.ROUTE_FAILURE,
            PoltergeistRules.episodeEnd(new TargetObservation(true, true, true, 10,
                PoltergeistRules.MAX_ROUTE_FAILURES, PoltergeistRules.MAX_EPISODE_PATH_REQUESTS)));
        assertEquals(EpisodeEnd.PATH_QUOTA,
            PoltergeistRules.episodeEnd(new TargetObservation(true, true, true, 10, 0,
                PoltergeistRules.MAX_EPISODE_PATH_REQUESTS)));
        assertEquals(EpisodeEnd.EXPIRED,
            PoltergeistRules.episodeEnd(new TargetObservation(true, true, true, 0, 0, 0)));
        assertEquals(EpisodeEnd.NONE,
            PoltergeistRules.episodeEnd(new TargetObservation(true, true, true, 1, 2,
                PoltergeistRules.MAX_EPISODE_PATH_REQUESTS - 1)));
    }

    @Test
    void anEpisodeOnlyStartsOffCooldownAndOutsideAnotherEpisode() {
        assertTrue(PoltergeistRules.episodeStartAllowed(0, false));
        assertFalse(PoltergeistRules.episodeStartAllowed(1, false));
        assertFalse(PoltergeistRules.episodeStartAllowed(0, true));
    }

    @Test
    void everySavedAttackPhaseResumesAsTheRecoveryThatClosesIt() {
        for (final Phase phase : Phase.values()) {
            final Phase resumed = PoltergeistRules.phaseAfterLoad(phase);
            if (phase == Phase.LURK) {
                assertEquals(Phase.LURK, resumed);
            } else {
                assertEquals(Phase.RECOVER, resumed,
                    phase + " must never resume mid disturbance");
            }
        }
    }

    // ---------------------------------------------------------------- bands and one-shot gates

    @Test
    void theMarkBandHoldsExactlyBetweenItsInclusiveBounds() {
        assertEquals(BandAction.WITHDRAW, PoltergeistRules.markBand(0.5D));
        assertEquals(BandAction.HOLD, PoltergeistRules.markBand(
            (double) PoltergeistRules.MARK_BAND_MIN * PoltergeistRules.MARK_BAND_MIN));
        assertEquals(BandAction.HOLD, PoltergeistRules.markBand(
            (double) PoltergeistRules.MARK_BAND_MAX * PoltergeistRules.MARK_BAND_MAX));
        assertEquals(BandAction.APPROACH, PoltergeistRules.markBand(
            (double) PoltergeistRules.MARK_BAND_MAX * PoltergeistRules.MARK_BAND_MAX + 0.01D));
    }

    @Test
    void aLiftIsRefusedBeyondItsDeclaredReach() {
        assertTrue(PoltergeistRules.liftAllowed(PoltergeistRules.LIFT_RANGE_SQUARED));
        assertFalse(PoltergeistRules.liftAllowed(PoltergeistRules.LIFT_RANGE_SQUARED + 0.01D));
    }

    @Test
    void exactlyOneHitAndOneBellRingArePermittedPerEpisode() {
        assertTrue(PoltergeistRules.throwHitAllowed(0, PoltergeistRules.THROW_HIT_RADIUS_SQUARED));
        assertFalse(PoltergeistRules.throwHitAllowed(0,
            PoltergeistRules.THROW_HIT_RADIUS_SQUARED + 0.01D));
        assertFalse(PoltergeistRules.throwHitAllowed(PoltergeistRules.MAX_THROW_HITS, 0.0D));

        assertTrue(PoltergeistRules.bellRingAllowed(0, PoltergeistRules.BELL_RING_RANGE_SQUARED));
        assertFalse(PoltergeistRules.bellRingAllowed(0,
            PoltergeistRules.BELL_RING_RANGE_SQUARED + 0.01D));
        assertFalse(PoltergeistRules.bellRingAllowed(PoltergeistRules.MAX_BELL_RINGS, 0.0D));
    }

    @Test
    void theRattleTelegraphIsFiniteAndCapped() {
        assertTrue(PoltergeistRules.pulseDue(0, 0));
        assertFalse(PoltergeistRules.pulseDue(1, 0), "a running interval is never due");
        assertFalse(PoltergeistRules.pulseDue(0, PoltergeistRules.MAX_RATTLE_PULSES));
        assertEquals(PoltergeistRules.MAX_RATTLE_PULSES, PoltergeistRules.rattlePulsesRemaining(0));
        assertEquals(0, PoltergeistRules.rattlePulsesRemaining(PoltergeistRules.MAX_RATTLE_PULSES));
        assertEquals(0, PoltergeistRules.rattlePulsesRemaining(99));
    }

    // ---------------------------------------------------------------- selection ordering

    @Test
    void targetSelectionIsStableByDistanceThenIdentity() {
        final List<TargetCandidate> inspected = List.of(
            new TargetCandidate(HIGH, 4.0D),
            new TargetCandidate(LOW, 4.0D),
            new TargetCandidate(UUID.fromString("00000000-0000-0000-0000-00000000000a"), 9.0D)
        );
        assertEquals(LOW, PoltergeistRules.selectTarget(inspected).orElseThrow().id(),
            "a distance tie is broken by stable identity, never by iteration order");
        assertEquals(PoltergeistRules.selectTarget(inspected),
            PoltergeistRules.selectTarget(new ArrayList<>(inspected).reversed()),
            "reversing the visit order cannot change the marked target");
    }

    @Test
    void retentionReRankingLetsAFarCandidateStillWinAgainstEarlierNearerOnes() {
        // Five visits with the nearest arriving last: a retain-first-four policy would drop it.
        List<TargetCandidate> retained = new ArrayList<>();
        final List<TargetCandidate> visitOrder = List.of(
            new TargetCandidate(UUID.fromString("00000000-0000-0000-0000-0000000000aa"), 60.0D),
            new TargetCandidate(UUID.fromString("00000000-0000-0000-0000-0000000000bb"), 50.0D),
            new TargetCandidate(UUID.fromString("00000000-0000-0000-0000-0000000000cc"), 40.0D),
            new TargetCandidate(UUID.fromString("00000000-0000-0000-0000-0000000000dd"), 30.0D),
            new TargetCandidate(LOW, 1.0D)
        );
        for (final TargetCandidate candidate : visitOrder) {
            retained.add(candidate);
            if (retained.size() > PoltergeistRules.MAX_RETAINED_CANDIDATES) {
                retained = new ArrayList<>(PoltergeistRules.rankTargets(retained));
            }
        }
        assertEquals(PoltergeistRules.MAX_RETAINED_CANDIDATES, retained.size(),
            "retention never exceeds its declared cap");
        assertEquals(LOW, PoltergeistRules.selectTarget(retained).orElseThrow().id(),
            "the genuinely nearest eligible player wins even when it is visited last");
    }

    @Test
    void propSelectionUsesTheSameStableOrderingAndCap() {
        final List<PropCandidate> inspected = List.of(
            new PropCandidate(HIGH, 2.0D),
            new PropCandidate(LOW, 2.0D),
            new PropCandidate(UUID.fromString("00000000-0000-0000-0000-0000000000ee"), 1.0D),
            new PropCandidate(UUID.fromString("00000000-0000-0000-0000-0000000000ff"), 3.0D),
            new PropCandidate(UUID.fromString("00000000-0000-0000-0000-00000000010a"), 4.0D)
        );
        assertEquals(PoltergeistRules.MAX_RETAINED_CANDIDATES,
            PoltergeistRules.rankProps(inspected).size());
        assertEquals(UUID.fromString("00000000-0000-0000-0000-0000000000ee"),
            PoltergeistRules.selectProp(inspected).orElseThrow().id());
        assertTrue(PoltergeistRules.selectProp(List.of()).isEmpty(),
            "an empty scene qualifies no prop at all");
    }

    // ---------------------------------------------------------------- movement bounds

    @Test
    void routeFailuresExhaustAtThreeAndOnlySuccessResetsThem() {
        int failures = 0;
        for (int attempt = 0; attempt < 5; attempt++) {
            failures = PoltergeistRules.routeFailuresAfter(failures,
                new RouteResult(false, false, false));
        }
        assertEquals(PoltergeistRules.MAX_ROUTE_FAILURES, failures);
        assertTrue(PoltergeistRules.routeExhausted(failures));
        assertEquals(PoltergeistRules.ROUTE_BACKOFF_TICKS,
            PoltergeistRules.routeBackoffAfter(failures));
        assertEquals(0, PoltergeistRules.routeFailuresAfter(failures,
            new RouteResult(true, true, true)));
        assertEquals(0, PoltergeistRules.routeBackoffAfter(0));
    }

    @Test
    void thePathQuotaBoundsEveryEpisodeIndependentlyOfTheCadence() {
        assertTrue(PoltergeistRules.pathRequestAllowed(0, 0, 0));
        assertFalse(PoltergeistRules.pathRequestAllowed(1, 0, 0), "a running cadence blocks");
        assertFalse(PoltergeistRules.pathRequestAllowed(0, 1, 0), "a running backoff blocks");
        assertFalse(
            PoltergeistRules.pathRequestAllowed(0, 0, PoltergeistRules.MAX_EPISODE_PATH_REQUESTS),
            "an exhausted episode quota blocks even with both cadences clear");
    }

    @Test
    void durationsClampAndDecrementWithoutConsultingWorldTime() {
        assertEquals(0, PoltergeistRules.clampRemaining(-5, 100));
        assertEquals(100, PoltergeistRules.clampRemaining(9_999, 100));
        assertEquals(0, PoltergeistRules.decrementLoaded(0));
        assertEquals(4, PoltergeistRules.decrementLoaded(5));
        assertEquals(0, PoltergeistRules.phaseWindowTicks(Phase.LURK));
        assertEquals(PoltergeistRules.RATTLE_TICKS, PoltergeistRules.phaseWindowTicks(Phase.RATTLE));
        assertEquals(PoltergeistRules.MARK_TICKS, PoltergeistRules.phaseWindowTicks(Phase.MARK));
        assertEquals(PoltergeistRules.LIFT_TICKS, PoltergeistRules.phaseWindowTicks(Phase.LIFT));
        assertEquals(PoltergeistRules.THROW_TICKS, PoltergeistRules.phaseWindowTicks(Phase.THROW));
        assertEquals(PoltergeistRules.RECOVER_TICKS,
            PoltergeistRules.phaseWindowTicks(Phase.RECOVER));
    }

    @Test
    void theStaggerIsDeterministicBoundedAndSafeOnDegenerateInput() {
        assertEquals(PoltergeistRules.stableOffset(LOW, 40), PoltergeistRules.stableOffset(LOW, 40));
        assertTrue(PoltergeistRules.stableOffset(HIGH, 40) >= 0
            && PoltergeistRules.stableOffset(HIGH, 40) < 40);
        assertEquals(0, PoltergeistRules.stableOffset(null, 40));
        assertEquals(0, PoltergeistRules.stableOffset(LOW, 0));
    }

    // ---------------------------------------------------------------- bell envelope coverage

    @Test
    void theBellEnvelopeIsTheCompleteBoxOrderedCentreOut() {
        final List<ScanOffset> offsets = PoltergeistRules.envelope(
            PoltergeistRules.BELL_SEARCH_HORIZONTAL, PoltergeistRules.BELL_SEARCH_VERTICAL
        );
        assertEquals(9 * 5 * 9, offsets.size(), "the 4/2 envelope is exactly 405 cells");
        assertEquals(offsets.size(), Set.copyOf(offsets).size(), "no cell is enumerated twice");
        assertEquals(new ScanOffset(0, 0, 0), offsets.getFirst(),
            "the entity's own cell is always the first offset evaluated");
        for (int index = 1; index < offsets.size(); index++) {
            assertTrue(offsets.get(index - 1).distanceSquared()
                    <= offsets.get(index).distanceSquared(),
                "the envelope is ordered centre-out by squared distance");
        }
    }

    @Test
    void everyScanEvaluatesTheNearAnchorAndNeverExceedsTheReadCap() {
        final List<ScanOffset> offsets = PoltergeistRules.envelope(
            PoltergeistRules.BELL_SEARCH_HORIZONTAL, PoltergeistRules.BELL_SEARCH_VERTICAL
        );
        final int anchor =
            PoltergeistRules.anchorSize(offsets.size(), PoltergeistRules.MAX_BELL_READS);
        assertEquals(PoltergeistRules.MAX_BELL_READS / 2, anchor);
        assertEquals(PoltergeistRules.MAX_BELL_READS - anchor,
            PoltergeistRules.pageSize(offsets.size(), PoltergeistRules.MAX_BELL_READS));
        for (int cursor = 0; cursor < 5; cursor++) {
            final List<ScanOffset> window =
                PoltergeistRules.scanWindow(offsets, PoltergeistRules.MAX_BELL_READS, cursor * 37);
            assertTrue(window.size() <= PoltergeistRules.MAX_BELL_READS,
                "one scan never charges more than its declared read ceiling");
            assertTrue(window.containsAll(offsets.subList(0, anchor)),
                "the near anchor, including the entity's own cell, is evaluated on every scan");
        }
    }

    /**
     * The defect-class-three proof. A naive raster over 405 cells with a 96-read cap would spend
     * every scan on the innermost ring, so the far corner would never be evaluated at all. The
     * rotating page must reach the whole envelope, including {@code (+4, +2, +4)}, within
     * {@code ceil(tail / page)} successive scans from any starting cursor.
     */
    @Test
    void successiveScansReachTheWholeEnvelopeIncludingTheFarCorner() {
        final List<ScanOffset> offsets = PoltergeistRules.envelope(
            PoltergeistRules.BELL_SEARCH_HORIZONTAL, PoltergeistRules.BELL_SEARCH_VERTICAL
        );
        final int anchor =
            PoltergeistRules.anchorSize(offsets.size(), PoltergeistRules.MAX_BELL_READS);
        final int page = PoltergeistRules.pageSize(offsets.size(), PoltergeistRules.MAX_BELL_READS);
        final int tail = offsets.size() - anchor;
        final int scansNeeded = Math.ceilDiv(tail, page);
        assertEquals(357, tail);
        assertEquals(48, page);
        assertEquals(8, scansNeeded);

        for (final int seed : new int[] {0, 1, 47, 200, 356}) {
            final Set<ScanOffset> seen = new LinkedHashSet<>();
            int cursor = seed;
            for (int scan = 0; scan < scansNeeded; scan++) {
                seen.addAll(
                    PoltergeistRules.scanWindow(offsets, PoltergeistRules.MAX_BELL_READS, cursor)
                );
                cursor = PoltergeistRules.advanceCursor(
                    offsets.size(), PoltergeistRules.MAX_BELL_READS, cursor
                );
            }
            assertTrue(seen.contains(new ScanOffset(4, 2, 4)),
                "the far corner is reachable from cursor seed " + seed);
            assertTrue(seen.contains(new ScanOffset(-4, -2, -4)),
                "the opposite far corner is reachable from cursor seed " + seed);
            assertEquals(offsets.size(), seen.size(),
                "eight successive scans evaluate the complete envelope from seed " + seed);
        }
    }

    @Test
    void theCursorWrapsInsideTheFarTailAndNeverReturnsToTheAnchor() {
        final int size = PoltergeistRules.envelope(
            PoltergeistRules.BELL_SEARCH_HORIZONTAL, PoltergeistRules.BELL_SEARCH_VERTICAL
        ).size();
        int cursor = 350;
        for (int scan = 0; scan < 20; scan++) {
            cursor = PoltergeistRules.advanceCursor(size, PoltergeistRules.MAX_BELL_READS, cursor);
            assertTrue(cursor >= 0 && cursor < 357, "cursor stayed inside the tail: " + cursor);
        }
        assertEquals(0, PoltergeistRules.advanceCursor(4, 96, 3),
            "an envelope smaller than the anchor has no tail to rotate");
    }

    // ---------------------------------------------------------------- safe destinations

    @Test
    void safeSearchOffsetsAreBoundedDeterministicAndNeverTheOrigin() {
        final List<PoltergeistRules.SafeSearchOffset> offsets = PoltergeistRules.safeSearchOffsets(
            LOW, PoltergeistRules.ESCAPE_SEARCH_HORIZONTAL,
            PoltergeistRules.ESCAPE_SEARCH_VERTICAL, PoltergeistRules.MAX_SAFE_CANDIDATES
        );
        assertTrue(offsets.size() <= PoltergeistRules.MAX_SAFE_CANDIDATES);
        assertEquals(offsets.size(), Set.copyOf(offsets).size(), "no offset is spent twice");
        assertTrue(offsets.stream().noneMatch(offset ->
            offset.dx() == 0 && offset.dy() == 0 && offset.dz() == 0));
        assertEquals(offsets, PoltergeistRules.safeSearchOffsets(
            LOW, PoltergeistRules.ESCAPE_SEARCH_HORIZONTAL,
            PoltergeistRules.ESCAPE_SEARCH_VERTICAL, PoltergeistRules.MAX_SAFE_CANDIDATES
        ), "the same entity always produces the same escape envelope");
        assertNotEquals(offsets.getFirst(), PoltergeistRules.safeSearchOffsets(
            HIGH, PoltergeistRules.ESCAPE_SEARCH_HORIZONTAL,
            PoltergeistRules.ESCAPE_SEARCH_VERTICAL, PoltergeistRules.MAX_SAFE_CANDIDATES
        ).getFirst(), "different entities start their rotation at different compass points");
        assertTrue(PoltergeistRules.safeSearchOffsets(LOW, 6, 3, 0).isEmpty());
    }

    @Test
    void safeCandidatePreferenceRanksSeparationThenSafetyThenDisplacement() {
        final var far = new PoltergeistRules.SafeCandidate(100.0D, true, 30.0D, 5L);
        final var near = new PoltergeistRules.SafeCandidate(10.0D, true, 1.0D, 6L);
        final var farHazardous = new PoltergeistRules.SafeCandidate(100.0D, false, 1.0D, 7L);
        final var preference = PoltergeistRules.safeCandidatePreference();
        assertTrue(preference.compare(far, near) < 0, "greater separation wins first");
        assertTrue(preference.compare(far, farHazardous) < 0, "then hazard safety");
        final var tie = new PoltergeistRules.SafeCandidate(100.0D, true, 30.0D, 4L);
        assertTrue(preference.compare(tie, far) < 0, "then the stable packed position");
    }
}
