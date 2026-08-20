package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.AnimalFamiliarRules.SearchOutcome;
import com.kadamitas.warlockery.entity.SpectralFamiliarRules.Phase;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class SpectralFamiliarStateTest {

    private static final UUID IDENTITY = UUID.fromString("00000000-0000-0000-0000-00000000dead");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-00000000f00d");
    private static final UUID ATTACKER = UUID.fromString("00000000-0000-0000-0000-00000000beef");
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String IRON = "minecraft:iron_ore";
    private static final BlockPos ORE = new BlockPos(4, 61, -7);

    private static SpectralFamiliarState fresh() {
        return SpectralFamiliarState.empty(IDENTITY, 1_000L);
    }

    private static SpectralFamiliarState running(final long now) {
        return fresh().withEpisode(Phase.APPROACH, now + SpectralFamiliarRules.phaseDuration(
            Phase.APPROACH), Optional.of(IRON), Optional.of(ORE), Optional.of(OVERWORLD));
    }

    // =====================================================================================
    // Constructor reconciliation, classified
    // =====================================================================================

    @Test
    void identityCouplingIsEnforcedBecauseDependentFieldsCannotDisagree() {
        final SpectralFamiliarState base = fresh();

        final SpectralFamiliarState positionWithoutDimension = base.withEpisode(
            Phase.APPROACH, 2_000L, Optional.of(IRON), Optional.of(ORE), Optional.empty());
        assertTrue(positionWithoutDimension.guideBlock().isEmpty(),
            "a position without its dimension is not a place");
        assertEquals(Phase.DORMANT, positionWithoutDimension.phase(),
            "and without a place there is no episode");

        final SpectralFamiliarState dimensionWithoutPosition = base.withEpisode(
            Phase.APPROACH, 2_000L, Optional.of(IRON), Optional.empty(), Optional.of(OVERWORLD));
        assertTrue(dimensionWithoutPosition.guideDimension().isEmpty());

        final SpectralFamiliarState blockWithoutSample = base.withEpisode(
            Phase.APPROACH, 2_000L, Optional.empty(), Optional.of(ORE), Optional.of(OVERWORLD));
        assertEquals(Phase.DORMANT, blockWithoutSample.phase(),
            "an episode is identified by its guide block AND the sample it opened with");
        assertTrue(blockWithoutSample.guideBlock().isEmpty());

        final SpectralFamiliarState dormantHoldsNothing = base.withEpisode(
            Phase.DORMANT, 2_000L, Optional.of(IRON), Optional.of(ORE), Optional.of(OVERWORLD));
        assertTrue(dormantHoldsNothing.episodeSample().isEmpty());
        assertTrue(dormantHoldsNothing.guideBlock().isEmpty());
        assertEquals(0L, dormantHoldsNothing.phaseEndsAt());

        final SpectralFamiliarState leaseWithoutDefender =
            base.withDefence(Optional.empty(), 5_000L, 0L);
        assertEquals(0L, leaseWithoutDefender.defenceLeaseUntil(),
            "a lease with no defender is not a lease");
    }

    @Test
    void noConstructorEndsATimedPhaseThatATickBranchOwns() {
        // The defect shape, pinned against. Ending a phase also arms the guide cooldown, releases the
        // frozen guide identity and stamps the epoch. A constructor that quietly zeroed an expired
        // phase would steal all three from advanceEpisode, which is the only place that pays them.
        final SpectralFamiliarState expired = fresh().withEpisode(
            Phase.APPROACH, 0L, Optional.of(IRON), Optional.of(ORE), Optional.of(OVERWORLD));

        assertEquals(Phase.APPROACH, expired.phase(),
            "an expired deadline is reported, never acted on, by the constructor");
        assertEquals(Optional.of(ORE), expired.guideBlock(),
            "and the phase still names the guide block the tick branch has to release");
        assertTrue(expired.phaseElapsed(1L), "expiry is only ever reported");
        assertTrue(expired.episodeRunning());
        assertEquals(0L, expired.guideCooldownUntil(),
            "the cooldown the exit arms must NOT have been armed by construction");
    }

    @Test
    void expiryIsReportedRatherThanReconciledForEveryTimerTheStateHolds() {
        final SpectralFamiliarState state = fresh()
            .withDefence(Optional.of(ATTACKER), 1_500L, 1_600L)
            .withGuideCooldown(1_700L)
            .withSurvey(new SearchOutcome(1_800L, 0), 1_900L);

        assertFalse(state.defenceElapsed(1_499L));
        assertTrue(state.defenceElapsed(1_500L));
        assertEquals(Optional.of(ATTACKER), state.defenceTargetId(),
            "an elapsed lease still names its defender until the tick branch releases it");

        assertFalse(state.defenceReady(1_599L));
        assertTrue(state.defenceReady(1_600L));
        assertFalse(state.guideReady(1_699L));
        assertTrue(state.guideReady(1_700L));
        assertFalse(state.surveyDue(1_799L));
        assertTrue(state.surveyDue(1_800L));
        assertTrue(state.surveyBackedOff(1_899L));
        assertFalse(state.surveyBackedOff(1_900L));
    }

    @Test
    void aFreshFamiliarIsDormantAndItsCadenceIsStaggeredOffItsOwnIdentity() {
        final SpectralFamiliarState mine = SpectralFamiliarState.empty(IDENTITY, 1_000L);
        final SpectralFamiliarState theirs = SpectralFamiliarState.empty(OTHER, 1_000L);

        assertEquals(Phase.DORMANT, mine.phase());
        assertFalse(mine.episodeRunning());
        assertFalse(mine.signalSpent());
        assertEquals(SpectralFamiliarRules.STATE_SCHEMA_VERSION, mine.schemaVersion());
        assertNotEquals(mine.survey().nextDueAt(), theirs.survey().nextDueAt(),
            "two familiars created on the same tick must not survey in lockstep");
        assertTrue(mine.survey().nextDueAt() >= 1_000L);
        assertTrue(mine.survey().nextDueAt()
            < 1_000L + SpectralFamiliarRules.SURVEY_INTERVAL_TICKS);
    }

    // =====================================================================================
    // The reload seam: accumulators reset, open backoff windows preserved
    // =====================================================================================

    @Test
    void theEpisodeNeverSurvivesTheReloadSeamSoALoadCanNeverReplayASignal() {
        final SpectralFamiliarState signalled = running(1_000L)
            .withPhase(Phase.SIGNAL, 1_020L)
            .withSignalSpent();
        assertTrue(signalled.signalSpent());

        final SpectralFamiliarState reloaded =
            SpectralFamiliarState.read(signalled.write(), IDENTITY, 2_000L);

        assertEquals(Phase.DORMANT, reloaded.phase());
        assertTrue(reloaded.episodeSample().isEmpty());
        assertTrue(reloaded.guideBlock().isEmpty());
        assertTrue(reloaded.guideDimension().isEmpty());
        assertEquals(0L, reloaded.phaseEndsAt());
        assertFalse(reloaded.signalSpent(),
            "a reload resumes no episode, so it can emit no signal, attack, pulse or discovery");
        assertTrue(reloaded.defenceTargetId().isEmpty());
        assertEquals(0L, reloaded.defenceLeaseUntil());
        assertEquals(0L, reloaded.nextDriftAt());
    }

    @Test
    void anOpenBackoffWindowSurvivesTheSeamThatResetsTheAccumulators() {
        final SpectralFamiliarState backedOff = fresh()
            .withSurvey(new SearchOutcome(1_100L, SpectralFamiliarRules.MAX_SURVEY_FAILURES), 1_090L)
            .withDefence(Optional.of(ATTACKER), 1_050L, 1_080L)
            .withGuideCooldown(1_400L);

        final SpectralFamiliarState reloaded =
            SpectralFamiliarState.read(backedOff.write(), IDENTITY, 1_000L);

        assertEquals(SpectralFamiliarRules.MAX_SURVEY_FAILURES,
            reloaded.survey().consecutiveFailures(),
            "the failure count the backoff is computed from must survive the seam");
        assertEquals(1_090L, reloaded.surveyBackoffUntil(), "the open survey backoff survives");
        assertEquals(1_080L, reloaded.defenceCooldownUntil(), "the open defence window survives");
        assertEquals(1_400L, reloaded.guideCooldownUntil(), "the open guide cooldown survives");
        assertTrue(reloaded.defenceTargetId().isEmpty(), "but the live lease does not");
    }

    @Test
    void everyLoadedWindowIsClampedToAtMostOneFullWindowFromTheCurrentClock() {
        final CompoundTag corrupt = fresh().write();
        corrupt.putLong("GuideCooldownUntil", Long.MAX_VALUE);
        corrupt.putLong("DefenceCooldownUntil", Long.MAX_VALUE);
        corrupt.putLong("SurveyBackoffUntil", Long.MAX_VALUE);
        corrupt.putInt("SurveyFailures", 9_999);
        corrupt.putLong("LastSurveyEpoch", -50L);

        final SpectralFamiliarState reloaded =
            SpectralFamiliarState.read(corrupt, IDENTITY, 5_000L);

        assertEquals(5_000L + SpectralFamiliarRules.GUIDE_COOLDOWN_TICKS,
            reloaded.guideCooldownUntil());
        assertEquals(5_000L + AnimalFamiliarRules.DEFENSE_LEASE_TICKS,
            reloaded.defenceCooldownUntil());
        assertEquals(5_000L + AnimalFamiliarRules.ROUTE_BACKOFF_TICKS,
            reloaded.surveyBackoffUntil());
        assertEquals(SpectralFamiliarRules.MAX_SURVEY_FAILURES,
            reloaded.survey().consecutiveFailures());
        assertEquals(0L, reloaded.lastSurveyEpoch(), "a negative epoch is not an epoch");
    }

    @Test
    void aPayloadFromAnotherSchemaIsDiscardedRatherThanMigrated() {
        final CompoundTag stale = fresh().write();
        stale.putInt("Version", SpectralFamiliarRules.STATE_SCHEMA_VERSION + 1);
        stale.putLong("GuideCooldownUntil", 9_999L);

        final SpectralFamiliarState reloaded = SpectralFamiliarState.read(stale, IDENTITY, 100L);
        assertEquals(0L, reloaded.guideCooldownUntil());
        assertEquals(Phase.DORMANT, reloaded.phase());
    }

    @Test
    void anAbsentPayloadIsNotAnErrorAndTheEmptyStateIsTheOnlyFallback() {
        final SpectralFamiliarState reloaded =
            SpectralFamiliarState.read(new CompoundTag(), IDENTITY, 100L);
        assertEquals(SpectralFamiliarState.empty(IDENTITY, 100L), reloaded);
    }

    // =====================================================================================
    // Episode lifecycle
    // =====================================================================================

    @Test
    void endingAnEpisodePaysEverythingEndingItImpliesInOneCall() {
        final SpectralFamiliarState ended = running(1_000L)
            .withSignalSpent()
            .withEpisodeEnded(1_300L, 1_300L + SpectralFamiliarRules.GUIDE_COOLDOWN_TICKS);

        assertEquals(Phase.DORMANT, ended.phase());
        assertTrue(ended.episodeSample().isEmpty(), "the frozen guide identity is released");
        assertTrue(ended.guideBlock().isEmpty());
        assertFalse(ended.signalSpent(), "the next episode gets its own single signal");
        assertEquals(1_300L + SpectralFamiliarRules.GUIDE_COOLDOWN_TICKS,
            ended.guideCooldownUntil(), "the cooldown is armed");
        assertEquals(1_300L, ended.lastSurveyEpoch(), "the epoch is stamped");
        assertFalse(ended.guideReady(1_300L));
        assertTrue(ended.guideReady(1_300L + SpectralFamiliarRules.GUIDE_COOLDOWN_TICKS));
    }

    @Test
    void theSignalIsSpentOncePerEpisodeAndTheFlagIsDurableWithinIt() {
        final SpectralFamiliarState running = running(1_000L).withPhase(Phase.SIGNAL, 1_020L);
        assertFalse(running.signalSpent());

        final SpectralFamiliarState spent = running.withSignalSpent();
        assertTrue(spent.signalSpent());
        assertTrue(spent.withSignalSpent().signalSpent(), "spending twice is still spent once");
        assertEquals(Phase.SIGNAL, spent.phase());
        assertEquals(Optional.of(ORE), spent.guideBlock(),
            "spending the signal does not release the identity the return leg still hangs off");
    }

    @Test
    void theDurableRoundTripPreservesTheFactsThatAreMeantToCrossAndNothingElse() {
        final SpectralFamiliarState before = fresh()
            .withSurvey(new SearchOutcome(1_100L, 2), 1_150L)
            .withGuideCooldown(1_250L)
            .withDefence(Optional.of(ATTACKER), 1_060L, 1_070L)
            .withEpisodeEnded(1_200L, 1_250L);

        final SpectralFamiliarState after =
            SpectralFamiliarState.read(before.write(), IDENTITY, 1_200L);

        assertEquals(before.survey().consecutiveFailures(), after.survey().consecutiveFailures());
        assertEquals(before.surveyBackoffUntil(), after.surveyBackoffUntil());
        assertEquals(before.guideCooldownUntil(), after.guideCooldownUntil());
        assertEquals(before.defenceCooldownUntil(), after.defenceCooldownUntil());
        assertEquals(before.lastSurveyEpoch(), after.lastSurveyEpoch());
        assertNotEquals(before.survey().nextDueAt(), after.survey().nextDueAt(),
            "the cadence restaggers rather than resuming, so an unloading familiar cannot starve it");
    }

    @Test
    void negativeAndAbsurdValuesAreClampedRatherThanCarried() {
        final SpectralFamiliarState clamped = new SpectralFamiliarState(
            SpectralFamiliarRules.STATE_SCHEMA_VERSION, Phase.DORMANT, Optional.empty(),
            Optional.empty(), Optional.empty(), -5L, -6L, false, Optional.empty(), -7L, -8L,
            new SearchOutcome(0L, 0), -9L, -10L, -11L);

        assertEquals(0L, clamped.phaseEndsAt());
        assertEquals(0L, clamped.guideCooldownUntil());
        assertEquals(0L, clamped.defenceLeaseUntil());
        assertEquals(0L, clamped.defenceCooldownUntil());
        assertEquals(0L, clamped.surveyBackoffUntil());
        assertEquals(0L, clamped.nextDriftAt());
        assertEquals(0L, clamped.lastSurveyEpoch());
    }
}

