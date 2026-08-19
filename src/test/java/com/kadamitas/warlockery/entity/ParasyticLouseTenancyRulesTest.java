package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ParasyticLouseTenancyRules.EvictReason;
import com.kadamitas.warlockery.entity.ParasyticLouseTenancyRules.HostFacts;
import com.kadamitas.warlockery.entity.ParasyticLouseTenancyRules.Phase;
import com.kadamitas.warlockery.entity.ParasyticLouseTenancyRules.RedirectFacts;
import com.kadamitas.warlockery.entity.ParasyticLouseTenancyRules.RedirectRejection;
import com.kadamitas.warlockery.entity.ParasyticLouseTenancyRules.TenancyFacts;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Boundary coverage for the pure F31 tenancy policy. Every constant is pinned at its own edges. */
final class ParasyticLouseTenancyRulesTest {

    private static HostFacts legal() {
        return new HostFacts(
            true, true, true, false, false, false, false, false, false,
            false, false, false, false, false, 1.0D
        );
    }

    private static TenancyFacts running() {
        return new TenancyFacts(true, ParasyticLouseTenancyRules.RESIDENCE_TERM_TICKS, 0, 0);
    }

    private static RedirectFacts armed() {
        return new RedirectFacts(true, true, 4.0D, true, true, 9.0D, 0, true);
    }

    // ---------------------------------------------------------------- acquisition

    @Test
    void aFullyLegalCandidateIsTheOnlyOneThatQualifies() {
        assertTrue(ParasyticLouseTenancyRules.eligibleHost(legal()));
    }

    @Test
    void everyDenyConditionAloneIsEnoughToRejectACandidate() {
        final HostFacts base = legal();
        assertFalse(ParasyticLouseTenancyRules.eligibleHost(new HostFacts(false, true, true, false,
            false, false, false, false, false, false, false, false, false, false, 1.0D)), "dead");
        assertFalse(ParasyticLouseTenancyRules.eligibleHost(new HostFacts(true, false, true, false,
            false, false, false, false, false, false, false, false, false, false, 1.0D)), "level");
        assertFalse(ParasyticLouseTenancyRules.eligibleHost(new HostFacts(true, true, false, false,
            false, false, false, false, false, false, false, false, false, false, 1.0D)), "dimension");
        assertFalse(ParasyticLouseTenancyRules.eligibleHost(new HostFacts(true, true, true, true,
            false, false, false, false, false, false, false, false, false, false, 1.0D)), "self");
        assertFalse(ParasyticLouseTenancyRules.eligibleHost(new HostFacts(true, true, true, false,
            true, false, false, false, false, false, false, false, false, false, 1.0D)), "other louse");
        assertFalse(ParasyticLouseTenancyRules.eligibleHost(new HostFacts(true, true, true, false,
            false, true, false, false, false, false, false, false, false, false, 1.0D)), "owner");
        assertFalse(ParasyticLouseTenancyRules.eligibleHost(new HostFacts(true, true, true, false,
            false, false, true, false, false, false, false, false, false, false, 1.0D)), "grace");
        assertFalse(ParasyticLouseTenancyRules.eligibleHost(new HostFacts(true, true, true, false,
            false, false, false, true, false, false, false, false, false, false, 1.0D)), "deny tag");
        assertFalse(ParasyticLouseTenancyRules.eligibleHost(new HostFacts(true, true, true, false,
            false, false, false, false, true, false, false, false, false, false, 1.0D)), "creative");
        assertFalse(ParasyticLouseTenancyRules.eligibleHost(new HostFacts(true, true, true, false,
            false, false, false, false, false, true, false, false, false, false, 1.0D)), "sleeping");
        assertFalse(ParasyticLouseTenancyRules.eligibleHost(new HostFacts(true, true, true, false,
            false, false, false, false, false, false, true, false, false, false, 1.0D)), "trading");
        assertFalse(ParasyticLouseTenancyRules.eligibleHost(new HostFacts(true, true, true, false,
            false, false, false, false, false, false, false, true, false, false, 1.0D)), "breeding");
        assertFalse(ParasyticLouseTenancyRules.eligibleHost(new HostFacts(true, true, true, false,
            false, false, false, false, false, false, false, false, true, false, 1.0D)), "raid");
        assertFalse(ParasyticLouseTenancyRules.eligibleHost(new HostFacts(true, true, true, false,
            false, false, false, false, false, false, false, false, false, true, 1.0D)), "panic");
        assertTrue(ParasyticLouseTenancyRules.eligibleHost(base), "the base case must stay legal");
    }

    // ---------------------------------------------------------------- mark and attach

    @ParameterizedTest
    @CsvSource({"8.99,true,true", "9.0,true,true", "9.01,true,false", "1.0,false,false"})
    void theMarkOpensOnlyInsideItsInclusiveBandAndOnlyWithSight(
        final double distanceSquared, final boolean sighted, final boolean expected
    ) {
        assertEquals(expected, ParasyticLouseTenancyRules.markOpens(distanceSquared, sighted));
    }

    @ParameterizedTest
    @CsvSource({"3.99,true,false,true", "4.0,true,false,true", "4.01,true,false,false",
        "1.0,false,false,false", "1.0,true,true,false"})
    void theAttachCommitsOnlyInsideItsInclusiveBandWithSightAndWithoutHazard(
        final double distanceSquared,
        final boolean sighted,
        final boolean hazard,
        final boolean expected
    ) {
        assertEquals(expected,
            ParasyticLouseTenancyRules.attachCommits(distanceSquared, sighted, hazard));
    }

    @Test
    void aTelegraphOnlyLapsesWhenTheCandidateLeftTheCommitBandOrSightWasLost() {
        assertFalse(ParasyticLouseTenancyRules.markLapses(4.0D, true));
        assertTrue(ParasyticLouseTenancyRules.markLapses(4.01D, true));
        assertTrue(ParasyticLouseTenancyRules.markLapses(1.0D, false));
    }

    // ---------------------------------------------------------------- retention

    @Test
    void aLegalHostInsideItsTermAndRangeIsNeverReleased() {
        assertEquals(Optional.empty(),
            ParasyticLouseTenancyRules.evictReason(legal(), running()));
    }

    @Test
    void everyReleaseConditionProducesItsOwnNamedReason() {
        assertEquals(Optional.of(EvictReason.HOST_UNLOADED), ParasyticLouseTenancyRules.evictReason(
            legal(), new TenancyFacts(false, 100, 0, 0)));
        assertEquals(Optional.of(EvictReason.HOST_DEAD), ParasyticLouseTenancyRules.evictReason(
            new HostFacts(false, true, true, false, false, false, false, false, false, false,
                false, false, false, false, 1.0D), running()));
        assertEquals(Optional.of(EvictReason.HOST_REMOVED), ParasyticLouseTenancyRules.evictReason(
            new HostFacts(true, false, true, false, false, false, false, false, false, false,
                false, false, false, false, 1.0D), running()));
        assertEquals(Optional.of(EvictReason.HOST_DIMENSION), ParasyticLouseTenancyRules.evictReason(
            new HostFacts(true, true, false, false, false, false, false, false, false, false,
                false, false, false, false, 1.0D), running()));
        assertEquals(Optional.of(EvictReason.TERM_EXPIRED), ParasyticLouseTenancyRules.evictReason(
            legal(), new TenancyFacts(true, 0, 0, 0)));
        assertEquals(Optional.of(EvictReason.HOST_OUT_OF_RETENTION),
            ParasyticLouseTenancyRules.evictReason(
                new HostFacts(true, true, true, false, false, false, false, false, false, false,
                    false, false, false, false, 256.01D), running()));
        assertEquals(Optional.of(EvictReason.HOST_SIGHT_LOST),
            ParasyticLouseTenancyRules.evictReason(legal(), new TenancyFacts(
                true, ParasyticLouseTenancyRules.RESIDENCE_TERM_TICKS,
                ParasyticLouseTenancyRules.SIGHT_LOSS_RELEASE_TICKS, 0)));
        assertEquals(Optional.of(EvictReason.HOST_SLEEPING), ParasyticLouseTenancyRules.evictReason(
            new HostFacts(true, true, true, false, false, false, false, false, false, true,
                false, false, false, false, 1.0D), running()));
        assertEquals(Optional.of(EvictReason.HOST_TRADING), ParasyticLouseTenancyRules.evictReason(
            new HostFacts(true, true, true, false, false, false, false, false, false, false,
                true, false, false, false, 1.0D), running()));
        assertEquals(Optional.of(EvictReason.HOST_BREEDING), ParasyticLouseTenancyRules.evictReason(
            new HostFacts(true, true, true, false, false, false, false, false, false, false,
                false, true, false, false, 1.0D), running()));
        assertEquals(Optional.of(EvictReason.HOST_RAID), ParasyticLouseTenancyRules.evictReason(
            new HostFacts(true, true, true, false, false, false, false, false, false, false,
                false, false, true, false, 1.0D), running()));
        assertEquals(Optional.of(EvictReason.HOST_PANIC), ParasyticLouseTenancyRules.evictReason(
            new HostFacts(true, true, true, false, false, false, false, false, false, false,
                false, false, false, true, 1.0D), running()));
        assertEquals(Optional.of(EvictReason.HOST_ILLEGAL), ParasyticLouseTenancyRules.evictReason(
            new HostFacts(true, true, true, false, false, false, false, true, false, false,
                false, false, false, false, 1.0D), running()));
        assertEquals(Optional.of(EvictReason.ROUTE_FAILED), ParasyticLouseTenancyRules.evictReason(
            legal(), new TenancyFacts(true, ParasyticLouseTenancyRules.RESIDENCE_TERM_TICKS, 0,
                ParasyticLouseTenancyRules.ROUTE_FAILURES_BEFORE_BACKOFF)));
    }

    @Test
    void sightLossReleasesOnlyOnceTheContinuousWindowIsFullyElapsed() {
        assertEquals(Optional.empty(), ParasyticLouseTenancyRules.evictReason(legal(),
            new TenancyFacts(true, ParasyticLouseTenancyRules.RESIDENCE_TERM_TICKS,
                ParasyticLouseTenancyRules.SIGHT_LOSS_RELEASE_TICKS - 1, 0)));
    }

    @Test
    void aSeekCandidateIsNeverReleasedForATermItHasNotStarted() {
        final TenancyFacts unstarted = new TenancyFacts(true, 0, 0, 0);
        assertEquals(Optional.of(EvictReason.TERM_EXPIRED),
            ParasyticLouseTenancyRules.evictReason(legal(), unstarted));
        assertEquals(Optional.empty(),
            ParasyticLouseTenancyRules.candidateReleaseReason(legal(), unstarted));
    }

    // ---------------------------------------------------------------- the feed ladder

    @ParameterizedTest
    @CsvSource({"2.24,false,true", "2.25,false,true", "2.26,false,false", "1.0,true,false"})
    void feedingIsAllowedOnlyInContactAndOnlyWithoutAHazard(
        final double distanceSquared, final boolean hazard, final boolean expected
    ) {
        assertEquals(expected, ParasyticLouseTenancyRules.feedAllowed(distanceSquared, hazard));
    }

    @Test
    void aTruthyHitWithNoEffectivePositiveLossIsNotAFeed() {
        assertTrue(ParasyticLouseTenancyRules.effectiveFeed(true, 20.0F, 19.0F));
        assertFalse(ParasyticLouseTenancyRules.effectiveFeed(true, 20.0F, 20.0F),
            "fully absorbed or Forge-zeroed damage raises nothing");
        assertFalse(ParasyticLouseTenancyRules.effectiveFeed(false, 20.0F, 19.0F),
            "a rejected call raises nothing even if health moved for another reason");
        assertFalse(ParasyticLouseTenancyRules.effectiveFeed(true, 20.0F, 21.0F),
            "a healed subject is not a fed parasite");
    }

    @Test
    void theLadderClampsAtFourAndNeverAtFive() {
        int nourishment = 0;
        for (int step = 0; step < 8; step++) {
            nourishment = ParasyticLouseTenancyRules.nourishmentAfter(nourishment, true);
        }
        assertEquals(ParasyticLouseTenancyRules.MAX_NOURISHMENT, nourishment);
        assertTrue(ParasyticLouseTenancyRules.satiated(nourishment));
        assertFalse(ParasyticLouseTenancyRules.satiated(
            ParasyticLouseTenancyRules.MAX_NOURISHMENT - 1));
        assertEquals(2, ParasyticLouseTenancyRules.nourishmentAfter(2, false));
        assertEquals(0, ParasyticLouseTenancyRules.nourishmentAfter(-7, false),
            "a corrupt negative ladder position clamps up rather than staying negative");
    }

    // ---------------------------------------------------------------- the payload

    @ParameterizedTest
    @CsvSource({"599,599,false", "600,600,false", "601,600,true", "20000,600,true"})
    void oneCeilingAppliesIdenticallyOnEveryDeliveryRoute(
        final int stored, final int delivered, final boolean clamped
    ) {
        assertEquals(delivered, ParasyticLouseTenancyRules.payloadDuration(stored));
        assertEquals(clamped, ParasyticLouseTenancyRules.payloadClamped(stored));
    }

    @ParameterizedTest
    @CsvSource({"-1,false", "0,true", "1,true", "39,true", "40,true", "41,false"})
    void attributionFreshnessIsInclusiveAtBothEnds(final int age, final boolean fresh) {
        assertEquals(fresh, ParasyticLouseTenancyRules.attributionFresh(age));
    }

    @Test
    void aFullyArmedRedirectGatePasses() {
        assertEquals(Optional.empty(), ParasyticLouseTenancyRules.redirectRejection(armed()));
    }

    @Test
    void everyRedirectGateFailureIsReportedAsTheFirstThingThatActuallyFailed() {
        assertEquals(Optional.of(RedirectRejection.NO_PAYLOAD),
            ParasyticLouseTenancyRules.redirectRejection(
                new RedirectFacts(false, true, 4.0D, true, true, 9.0D, 0, true)));
        assertEquals(Optional.of(RedirectRejection.NO_OWNER),
            ParasyticLouseTenancyRules.redirectRejection(
                new RedirectFacts(true, false, 4.0D, true, true, 9.0D, 0, true)));
        assertEquals(Optional.of(RedirectRejection.OWNER_OUT_OF_RANGE),
            ParasyticLouseTenancyRules.redirectRejection(
                new RedirectFacts(true, true, 256.01D, true, true, 9.0D, 0, true)));
        assertEquals(Optional.of(RedirectRejection.NO_ARMOR),
            ParasyticLouseTenancyRules.redirectRejection(
                new RedirectFacts(true, true, 4.0D, false, true, 9.0D, 0, true)));
        assertEquals(Optional.of(RedirectRejection.NO_ATTACKER),
            ParasyticLouseTenancyRules.redirectRejection(
                new RedirectFacts(true, true, 4.0D, true, false, 9.0D, 0, true)));
        assertEquals(Optional.of(RedirectRejection.ATTACKER_OUT_OF_RANGE),
            ParasyticLouseTenancyRules.redirectRejection(
                new RedirectFacts(true, true, 4.0D, true, true, 256.01D, 0, true)));
        assertEquals(Optional.of(RedirectRejection.STALE_ATTRIBUTION),
            ParasyticLouseTenancyRules.redirectRejection(
                new RedirectFacts(true, true, 4.0D, true, true, 9.0D, 41, true)));
        assertEquals(Optional.of(RedirectRejection.NO_SIGHT),
            ParasyticLouseTenancyRules.redirectRejection(
                new RedirectFacts(true, true, 4.0D, true, true, 9.0D, 40, false)));
    }

    @Test
    void bothRedirectRangeGatesAreInclusiveAtSixteenBlocks() {
        assertEquals(Optional.empty(), ParasyticLouseTenancyRules.redirectRejection(
            new RedirectFacts(true, true, 256.0D, true, true, 256.0D, 40, true)));
    }

    // ---------------------------------------------------------------- lifecycle

    @Test
    void hazardPreemptsExactlyWhenItIsActive() {
        assertTrue(ParasyticLouseTenancyRules.hazardPreempts(true));
        assertFalse(ParasyticLouseTenancyRules.hazardPreempts(false));
    }

    @ParameterizedTest
    @ValueSource(ints = {-30000, -1, 0, 1, 199})
    void aReloadedCooldownBelowTheFloorIsRaisedToIt(final int stored) {
        assertEquals(ParasyticLouseTenancyRules.LOAD_SEEK_COOLDOWN_FLOOR_TICKS,
            ParasyticLouseTenancyRules.seekCooldownOnLoad(stored));
    }

    @Test
    void aReloadedCooldownAboveTheFloorSurvivesAndAnOverflowIsPulledBack() {
        assertEquals(500, ParasyticLouseTenancyRules.seekCooldownOnLoad(500));
        assertEquals(ParasyticLouseTenancyRules.SEEK_COOLDOWN_TICKS,
            ParasyticLouseTenancyRules.seekCooldownOnLoad(Integer.MAX_VALUE),
            "a far-future sentinel is treated as corrupt and pulled back inside the declared range");
    }

    @Test
    void aZeroCooldownReadsAsDueRatherThanAsRecentlyFired() {
        assertTrue(ParasyticLouseTenancyRules.tenancyMayStart(Phase.FREE, 0, 0));
        assertFalse(ParasyticLouseTenancyRules.tenancyMayStart(Phase.FREE, 1, 0));
        assertFalse(ParasyticLouseTenancyRules.tenancyMayStart(Phase.FREE, 0, 1),
            "a withdrawing louse never begins a tenancy");
        assertFalse(ParasyticLouseTenancyRules.tenancyMayStart(Phase.FEED, 0, 0));
        assertFalse(ParasyticLouseTenancyRules.tenancyMayStart(Phase.ESCAPE, 0, 0));
    }

    @Test
    void theStateSetIsExactlyTheFiveObservablePhases() {
        assertEquals(5, Phase.values().length,
            "ATTACH, SATED, EVICT and DETACH are single-pass transitions, never observable phases");
    }

    @Test
    void theBackoffPolicyEngagesOnTheThirdFailureAndIsClampedThere() {
        assertEquals(0, ParasyticLouseTenancyRules.ROUTE_BACKOFF.windowAfter(2));
        assertEquals(ParasyticLouseTenancyRules.ROUTE_BACKOFF_TICKS,
            ParasyticLouseTenancyRules.ROUTE_BACKOFF.windowAfter(3));
        assertEquals(ParasyticLouseTenancyRules.ROUTE_BACKOFF_TICKS,
            ParasyticLouseTenancyRules.ROUTE_BACKOFF.windowAfter(12));
    }
}
