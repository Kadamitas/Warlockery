package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ApparitionEpisodeRules.PlayerCandidate;
import com.kadamitas.warlockery.entity.ApparitionEpisodeRules.RouteLedger;
import com.kadamitas.warlockery.entity.ApparitionEpisodeRules.RouteResult;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * Truth tables for the one policy Echo Shade and Spectre share. These are the contracts that have
 * historically shipped broken twice inside a single family, so they are proved once, here, against
 * the single implementation both kinds call.
 */
final class ApparitionEpisodeRulesTest {
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");

    // ------------------------------------------------------------ envelope coverage

    @Test
    void theEnvelopeIsCompleteStartsAtTheOwnPositionAndReachesEveryCorner() {
        final List<BlockPos> envelope = ApparitionEpisodeRules.envelope(2, 1);
        assertEquals(5 * 3 * 5, envelope.size(), "the envelope is the whole 5 x 3 x 5 box");
        assertEquals(BlockPos.ZERO, envelope.getFirst(),
            "the centre-out ordering always evaluates the apparition's own position first");
        assertTrue(envelope.contains(new BlockPos(2, 1, 2)), "the far corner is inside the box");
        assertTrue(envelope.contains(new BlockPos(-2, -1, -2)),
            "the opposite far corner is inside the box");
        assertEquals(envelope.size(), Set.copyOf(envelope).size(),
            "no offset is enumerated twice and no budget is wasted on a duplicate");
    }

    /**
     * The exact defect this guards: a budget below the box volume makes a naive raster spend
     * everything in one corner, so the far shell and often the entity's own position are never
     * evaluated at all. The anchor-plus-rotating-page window must cover the complete envelope
     * across successive sweeps.
     */
    @Test
    void successiveSweepsCoverTheCompleteEnvelopeIncludingTheFarShell() {
        final List<BlockPos> envelope = ApparitionEpisodeRules.envelope(2, 1);
        final int readCap = ApparitionEpisodeRules.MAX_DESTINATION_READS
            / ApparitionEpisodeRules.READS_PER_DESTINATION_CANDIDATE;
        assertTrue(readCap < envelope.size(),
            "the coverage contract only means something while the budget is below the volume");

        final Set<BlockPos> seen = new HashSet<>();
        int cursor = ApparitionEpisodeRules.seedCursor(FIRST, envelope.size(), readCap);
        for (int sweep = 0; sweep < 8; sweep++) {
            final List<BlockPos> window =
                ApparitionEpisodeRules.sweepWindow(envelope, readCap, cursor);
            assertTrue(window.size() <= readCap, "no sweep may exceed the declared candidate cap");
            assertTrue(window.contains(BlockPos.ZERO),
                "every single sweep re-evaluates the apparition's own position");
            seen.addAll(window);
            cursor = ApparitionEpisodeRules.advanceCursor(envelope.size(), readCap, cursor);
        }
        assertEquals(Set.copyOf(envelope), seen,
            "eight successive sweeps evaluate every offset in the whole envelope");
        assertTrue(seen.contains(new BlockPos(2, 1, 2)), "the far corner is genuinely reached");
        assertTrue(seen.contains(new BlockPos(-2, -1, -2)),
            "the opposite far corner is genuinely reached");
    }

    /**
     * The hand-traced escape case, machine checked. The 7 x 5 x 7 escape envelope is 245 offsets
     * against a 32-candidate budget: a 16-offset near anchor evaluated every sweep plus a 16-wide
     * page over the 229-offset tail, so the whole envelope including the (3, 2, 3) corner is
     * covered within fifteen successive sweeps.
     */
    @Test
    void theEscapeEnvelopeIsFullyCoveredWithinItsHandTracedSweepCount() {
        final List<BlockPos> envelope = ApparitionEpisodeRules.envelope(3, 2);
        assertEquals(245, envelope.size());
        final int readCap = 32;
        assertEquals(16, ApparitionEpisodeRules.anchorSize(envelope.size(), readCap));
        assertEquals(16, ApparitionEpisodeRules.pageSize(envelope.size(), readCap));

        final Set<BlockPos> seen = new HashSet<>();
        int cursor = ApparitionEpisodeRules.seedCursor(FIRST, envelope.size(), readCap);
        for (int sweep = 0; sweep < 15; sweep++) {
            seen.addAll(ApparitionEpisodeRules.sweepWindow(envelope, readCap, cursor));
            cursor = ApparitionEpisodeRules.advanceCursor(envelope.size(), readCap, cursor);
        }
        assertEquals(Set.copyOf(envelope), seen,
            "fifteen successive escape sweeps evaluate the complete 245-offset envelope");
        assertTrue(seen.contains(new BlockPos(3, 2, 3)), "the farthest escape corner is reached");
    }

    @Test
    void aBudgetThatCoversTheWholeBoxStillEnumeratesItExactlyOnce() {
        final List<BlockPos> envelope = ApparitionEpisodeRules.envelope(1, 0);
        final List<BlockPos> window = ApparitionEpisodeRules.sweepWindow(envelope, 512, 0);
        assertEquals(Set.copyOf(envelope), Set.copyOf(window));
        assertEquals(0, ApparitionEpisodeRules.advanceCursor(envelope.size(), 512, 3),
            "a sweep with no tail has no page to rotate");
    }

    @Test
    void twoApparitionsSeedDifferentPagesSoTheyNeverSweepInLockstep() {
        final List<BlockPos> envelope = ApparitionEpisodeRules.envelope(3, 2);
        final int readCap = 32;
        assertNotEquals(
            ApparitionEpisodeRules.seedCursor(FIRST, envelope.size(), readCap),
            ApparitionEpisodeRules.seedCursor(SECOND, envelope.size(), readCap)
        );
    }

    @Test
    void anchorAndPageSplitTheBudgetWithoutOverspending() {
        final List<BlockPos> envelope = ApparitionEpisodeRules.envelope(2, 1);
        final int readCap = 32;
        final int anchor = ApparitionEpisodeRules.anchorSize(envelope.size(), readCap);
        final int page = ApparitionEpisodeRules.pageSize(envelope.size(), readCap);
        assertEquals(16, anchor, "half the budget is the fixed near anchor");
        assertEquals(16, page, "the remainder is the rotating far page");
        assertTrue(anchor + page <= readCap, "the two halves never exceed the declared budget");
    }

    // ------------------------------------------------------------ route policy

    @Test
    void aSweepThatQualifiedNothingStillArmsTheCadenceAndCountsTheFailure() {
        final RouteLedger idle = new RouteLedger(0, 0, 0);
        final RouteLedger after = ApparitionEpisodeRules.ledgerAfter(idle, RouteResult.unroutable());
        assertEquals(ApparitionEpisodeRules.PATH_INTERVAL_TICKS, after.pathCooldownTicks(),
            "an empty sweep spent real reads, so it arms the same path cadence as a real attempt");
        assertEquals(1, after.routeFailures(),
            "an empty sweep counts a route failure, so the declared cap can actually bind");
    }

    @Test
    void theThirdConsecutiveFailureOpensTheBackoffAndSuccessResetsIt() {
        RouteLedger ledger = new RouteLedger(0, 0, 0);
        for (int attempt = 0; attempt < 2; attempt++) {
            ledger = ApparitionEpisodeRules.ledgerAfter(
                new RouteLedger(0, ledger.routeFailures(), 0), RouteResult.unroutable());
            assertEquals(0, ledger.routeRetryTicks(), "no backoff before the cap is reached");
        }
        ledger = ApparitionEpisodeRules.ledgerAfter(
            new RouteLedger(0, ledger.routeFailures(), 0), RouteResult.unroutable());
        assertEquals(ApparitionEpisodeRules.MAX_ROUTE_FAILURES, ledger.routeFailures());
        assertTrue(ApparitionEpisodeRules.routeExhausted(ledger.routeFailures()));
        assertEquals(ApparitionEpisodeRules.ROUTE_BACKOFF_TICKS, ledger.routeRetryTicks(),
            "the third consecutive failure opens the backoff");

        final RouteLedger recovered = ApparitionEpisodeRules.ledgerAfter(
            new RouteLedger(0, ledger.routeFailures(), 0), new RouteResult(true, true, true));
        assertEquals(0, recovered.routeFailures(), "a real success clears the failure run");
        assertFalse(ApparitionEpisodeRules.routeExhausted(recovered.routeFailures()));
    }

    @Test
    void aPathRequestIsGatedByBothTheCadenceAndTheBackoff() {
        assertTrue(ApparitionEpisodeRules.pathRequestAllowed(0, 0));
        assertFalse(ApparitionEpisodeRules.pathRequestAllowed(1, 0));
        assertFalse(ApparitionEpisodeRules.pathRequestAllowed(0, 1));
        assertFalse(ApparitionEpisodeRules.pathRequestAllowed(1, 1));
    }

    @Test
    void theLedgerClampsEveryStoredDurationIntoItsDeclaredRange() {
        final RouteLedger ledger = new RouteLedger(9_999, 99, 9_999);
        assertEquals(ApparitionEpisodeRules.PATH_INTERVAL_TICKS, ledger.pathCooldownTicks());
        assertEquals(ApparitionEpisodeRules.MAX_ROUTE_FAILURES, ledger.routeFailures());
        assertEquals(ApparitionEpisodeRules.ROUTE_BACKOFF_TICKS, ledger.routeRetryTicks());
        final RouteLedger negative = new RouteLedger(-5, -5, -5);
        assertEquals(0, negative.pathCooldownTicks());
        assertEquals(0, negative.routeFailures());
        assertEquals(0, negative.routeRetryTicks());
    }

    // ------------------------------------------------------------ appointment

    @Test
    void exactlyOneVisibleEligibleNearestCandidateIsAppointed() {
        final List<PlayerCandidate> inspected = List.of(
            new PlayerCandidate(FIRST, true, true, 36.0D),
            new PlayerCandidate(SECOND, true, true, 9.0D)
        );
        assertEquals(SECOND, ApparitionEpisodeRules.appoint(inspected).orElseThrow().id(),
            "the nearest qualifying candidate is appointed");
    }

    @Test
    void anIneligibleOrUnseenCandidateIsNeverAppointedHoweverClose() {
        assertTrue(ApparitionEpisodeRules.appoint(List.of(
            new PlayerCandidate(FIRST, false, true, 1.0D)
        )).isEmpty(), "an ineligible player is never appointed");
        assertTrue(ApparitionEpisodeRules.appoint(List.of(
            new PlayerCandidate(FIRST, true, false, 1.0D)
        )).isEmpty(), "an apparition that cannot see a player has not observed one");
        assertTrue(ApparitionEpisodeRules.appoint(List.of()).isEmpty(),
            "an empty sweep appoints nobody rather than inventing a subject");
    }

    @Test
    void appointmentTiesResolveOnStableIdentityRatherThanIterationOrder() {
        final List<PlayerCandidate> ordered = List.of(
            new PlayerCandidate(SECOND, true, true, 4.0D),
            new PlayerCandidate(FIRST, true, true, 4.0D)
        );
        assertEquals(FIRST, ApparitionEpisodeRules.appoint(ordered).orElseThrow().id());
        assertEquals(FIRST, ApparitionEpisodeRules.appoint(ordered.reversed()).orElseThrow().id());
    }

    // ------------------------------------------------------------ durations

    @Test
    void loadedDurationsClampAndDecrementWithoutConsultingWorldTime() {
        assertEquals(0, ApparitionEpisodeRules.clampRemaining(-7, 100));
        assertEquals(100, ApparitionEpisodeRules.clampRemaining(500, 100));
        assertEquals(42, ApparitionEpisodeRules.clampRemaining(42, 100));
        assertEquals(0, ApparitionEpisodeRules.decrementLoaded(0));
        assertEquals(0, ApparitionEpisodeRules.decrementLoaded(-3));
        assertEquals(4, ApparitionEpisodeRules.decrementLoaded(5));
    }

    @Test
    void stableOffsetIsDeterministicPerIdentityAndInsideItsSpan() {
        assertEquals(ApparitionEpisodeRules.stableOffset(FIRST, 40),
            ApparitionEpisodeRules.stableOffset(FIRST, 40));
        assertTrue(ApparitionEpisodeRules.stableOffset(FIRST, 40) >= 0);
        assertTrue(ApparitionEpisodeRules.stableOffset(FIRST, 40) < 40);
        assertEquals(0, ApparitionEpisodeRules.stableOffset(null, 40));
        assertEquals(0, ApparitionEpisodeRules.stableOffset(FIRST, 0));
    }
}
