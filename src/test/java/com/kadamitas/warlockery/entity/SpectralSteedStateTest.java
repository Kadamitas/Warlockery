package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.SpectralSteedRules.Gait;
import com.kadamitas.warlockery.entity.SpectralSteedState.Phase;
import com.kadamitas.warlockery.entity.behavior.PhaseTimer;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Clamps, the single identity reconciliation, the phase that is deliberately never reconciled, the
 * episode boundary, and what a reload is and is not allowed to bring back.
 */
final class SpectralSteedStateTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final BlockPos SITE = new BlockPos(4, 64, -7);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // --------------------------------------------------------------- defaults

    @Test
    void anEmptySteedIsHaltedUnmaturedAndHoldsNoSite() {
        final SpectralSteedState state = SpectralSteedState.empty();
        assertEquals(SpectralSteedState.SCHEMA_VERSION, state.schemaVersion());
        assertEquals(Gait.HALT, state.gait());
        assertEquals(0, state.bond());
        assertEquals(0, state.fatigue());
        assertTrue(state.phase().idle());
        assertFalse(state.rest().isPresent());
        assertTrue(state.restRequest().mayRequest(), "the first rest search is offered at once");
        assertTrue(state.hazardScan().due(), "the first hazard look is offered at once");
        assertEquals(SpectralSteedState.Counters.none(), state.counters());
    }

    @Test
    void everyStoredScalarIsClampedIntoItsDeclaredRange() {
        final SpectralSteedState state = SpectralSteedState.empty()
            .withBond(SpectralSteedRules.MAX_BOND + 5_000)
            .withFatigue(-40);
        assertEquals(SpectralSteedRules.MAX_BOND, state.bond());
        assertEquals(0, state.fatigue());
    }

    // ------------------------------------------------------- the reconciliation

    @Test
    void aRestPositionWithoutItsDimensionIsNotARestSiteAtAll() {
        final SpectralSteedState halfSite =
            SpectralSteedState.empty().withRest(Optional.of(SITE), Optional.empty());
        assertFalse(halfSite.rest().isPresent(), "the identity shape drops both halves");
        assertFalse(halfSite.restDimension().isPresent());

        final SpectralSteedState whole =
            SpectralSteedState.empty().withRest(Optional.of(SITE), Optional.of(OVERWORLD));
        assertEquals(SITE, whole.rest().orElseThrow());
        assertEquals(OVERWORLD, whole.restDimension().orElseThrow());
    }

    /**
     * The defect this record is shaped to make unwritable. If the canonical constructor cleaned up
     * an expired phase, the branch that ends a balk would never see the pair it dispatches on and
     * would never release steering or re-arm anything.
     */
    @Test
    void aPhaseThatRunsOutIsExpiredRatherThanTidiedAway() {
        SpectralSteedState state = SpectralSteedState.empty().startingBalk(2);
        assertTrue(state.balking());
        state = state.step();
        assertTrue(state.phase().running(), "one tick left is still running");
        state = state.step();
        assertTrue(state.phase().expired(), "a spent balk is expired, not idle");
        assertEquals(Phase.BALK, state.phase().expiredPhase().orElseThrow());
        assertTrue(state.balking(), "an expired balk is still a balk until its branch ends it");
        state = state.step();
        assertTrue(state.phase().expired(), "an unhandled expiry stalls visibly rather than lapsing");
        assertEquals(PhaseTimer.<Phase>none(), state.withPhase(state.phase().endExpired()).phase());
    }

    @Test
    void onlyAnExpiredPhaseMayBeEnded() {
        final SpectralSteedState running = SpectralSteedState.empty().startingBalk(10);
        assertThrows(IllegalStateException.class, () -> running.phase().endExpired());
    }

    // ------------------------------------------------------------- transitions

    @Test
    void applyingABandArmsTheHoldWindowAndCountsExactlyTheChangesThatHappened() {
        SpectralSteedState state = SpectralSteedState.empty();
        state = state.withGait(Gait.HALT);
        assertEquals(0L, state.counters().gaitChanges(), "a band that did not move is not a change");
        state = state.withGait(Gait.WALK);
        assertEquals(1L, state.counters().gaitChanges());
        assertEquals(SpectralSteedRules.GAIT_HOLD_TICKS, state.gaitHold());
        state = state.step();
        assertEquals(SpectralSteedRules.GAIT_HOLD_TICKS - 1, state.gaitHold());
    }

    @Test
    void rideBondChargesThePerEpisodeAccumulatorAndRestBondDoesNot() {
        final SpectralSteedState ridden = SpectralSteedState.empty().withBondGain(1, true);
        assertEquals(1, ridden.bond());
        assertEquals(1, ridden.bondThisEpisode());
        assertEquals(1L, ridden.counters().bondGains());

        final SpectralSteedState rested = SpectralSteedState.empty().withBondGain(1, false);
        assertEquals(1, rested.bond());
        assertEquals(0, rested.bondThisEpisode());
        assertEquals(1L, rested.counters().bondGains());
    }

    @Test
    void completingARestArmsItsCooldownAndCountsOnce() {
        final SpectralSteedState done = SpectralSteedState.empty()
            .startingRest(SpectralSteedRules.REST_SETTLE_TICKS)
            .withRestCompleted();
        assertEquals(1L, done.counters().restsCompleted());
        assertEquals(SpectralSteedRules.REST_COOLDOWN_TICKS, done.restCooldown());
        assertTrue(done.phase().idle());
    }

    @Test
    void aRestSearchChargesEveryReadItPerformedAndMovesItsCursor() {
        final SpectralSteedState searched = SpectralSteedState.empty().withRestSearchCharged(48, 8);
        assertEquals(1L, searched.counters().restSearches());
        assertEquals(48L, searched.counters().restBlockReads());
        assertEquals(8, searched.restCursor());
    }

    @Test
    void aWarningThatReachedNobodyCountsItsVisitsButArmsNoCooldown() {
        final SpectralSteedState missed = SpectralSteedState.empty().withWarningIssued(5, 0);
        assertEquals(5L, missed.counters().warningVisits());
        assertEquals(0L, missed.counters().warningsIssued());
        assertEquals(0, missed.fearCooldown());

        final SpectralSteedState landed = SpectralSteedState.empty().withWarningIssued(5, 2);
        assertEquals(1L, landed.counters().warningsIssued());
        assertEquals(SpectralSteedRules.FEAR_COOLDOWN_TICKS, landed.fearCooldown());
    }

    // -------------------------------------------------------- episode boundary

    /**
     * The accumulator reset that must not take the backoff window with it. A steed that has just
     * failed three rest searches in unusable terrain gets no fresh allowance from somebody climbing
     * on and off again.
     */
    @Test
    void anEpisodeBoundaryResetsAccumulatorsAndPreservesAnOpenBackoffWindow() {
        SpectralSteedState state = SpectralSteedState.empty()
            .withBondGain(1, true)
            .withGait(Gait.CANTER)
            .startingBalk(20);
        state = state.withRestRequest(state.restRequest().failed(SpectralSteedRules.REST_BACKOFF)
            .failed(SpectralSteedRules.REST_BACKOFF)
            .failed(SpectralSteedRules.REST_BACKOFF));
        final int backoff = state.restRequest().backoffRemaining();
        final int failures = state.restRequest().consecutiveFailures();
        assertTrue(backoff > 0, "the fixture needs a genuinely open window");

        final SpectralSteedState started = state.episodeStart();
        assertEquals(0, started.bondThisEpisode(), "the per-ride accumulator resets");
        assertEquals(Gait.HALT, started.gait(), "the transient band resets");
        assertTrue(started.phase().idle(), "the transient phase resets");
        assertEquals(1, started.episode());
        assertEquals(backoff, started.restRequest().backoffRemaining(),
            "the open backoff window survives the boundary");
        assertEquals(failures, started.restRequest().consecutiveFailures(),
            "so does the failure run that opened it");
        assertEquals(state.bond(), started.bond(), "durable maturity is untouched");

        final SpectralSteedState ended = started.episodeEnd();
        assertEquals(0, ended.bondThisEpisode());
        assertEquals(Gait.HALT, ended.gait());
        assertEquals(backoff, ended.restRequest().backoffRemaining());
        assertEquals(1, ended.episode(), "ending a ride does not open another");
    }

    // ---------------------------------------------------------------- reload

    @Test
    void durableFactsSurviveAReloadAndTransientOnesDoNot() {
        final SpectralSteedState before = SpectralSteedState.empty()
            .withBond(321)
            .withFatigue(654)
            .withGait(Gait.SPRINT)
            .withRest(Optional.of(SITE), Optional.of(OVERWORLD))
            .withRestSearchCharged(48, 11)
            .startingBalk(25)
            .withBondGain(1, true)
            .withRestCompleted();
        final CompoundTag tag = before.write(CreatureKind.PALE_STEED);
        final SpectralSteedState after = SpectralSteedState.read(tag, CreatureKind.PALE_STEED);

        assertEquals(before.bond(), after.bond());
        assertEquals(before.fatigue(), after.fatigue());
        assertEquals(before.rest(), after.rest());
        assertEquals(before.restDimension(), after.restDimension());
        assertEquals(before.restCooldown(), after.restCooldown());
        assertEquals(before.restCursor(), after.restCursor());
        assertEquals(before.episode(), after.episode());

        assertEquals(Gait.HALT, after.gait(), "no steed comes back mid band");
        assertTrue(after.phase().idle(), "no steed comes back mid phase");
        assertEquals(0, after.gaitHold());
        assertEquals(0, after.bondThisEpisode(), "no bond is credited for elapsed time");
        assertEquals(0, after.fearCooldown(), "a warning cooldown is not a durable world fact");
        assertEquals(SpectralSteedState.Counters.none(), after.counters(),
            "reload replays no work at all");
        assertTrue(after.hazardScan().due(), "a reloaded steed looks for hazards immediately");
    }

    @Test
    void aRouteBackoffWindowSurvivesAReloadRatherThanBeingForgiven() {
        SpectralSteedState before = SpectralSteedState.empty();
        before = before.withRestRequest(before.restRequest()
            .failed(SpectralSteedRules.REST_BACKOFF)
            .failed(SpectralSteedRules.REST_BACKOFF)
            .failed(SpectralSteedRules.REST_BACKOFF));
        final SpectralSteedState after =
            SpectralSteedState.read(before.write(CreatureKind.NIGHTMARE), CreatureKind.NIGHTMARE);
        assertEquals(before.restRequest().backoffRemaining(), after.restRequest().backoffRemaining());
        assertEquals(before.restRequest().consecutiveFailures(),
            after.restRequest().consecutiveFailures());
        assertFalse(after.restRequest().mayRequest(), "a reloaded steed is still backed off");
    }

    @Test
    void anUnknownSchemaIsStartedOverRatherThanGuessedAt() {
        final CompoundTag tag = SpectralSteedState.empty().withBond(900).write(CreatureKind.PALE_STEED);
        tag.putInt("Version", SpectralSteedState.SCHEMA_VERSION + 7);
        assertEquals(SpectralSteedState.empty(), SpectralSteedState.read(tag, CreatureKind.PALE_STEED));
    }

    @Test
    void aMalformedDimensionDropsTheSiteWithoutLosingMaturity() {
        final CompoundTag tag = SpectralSteedState.empty()
            .withBond(500)
            .withRest(Optional.of(SITE), Optional.of(OVERWORLD))
            .write(CreatureKind.PALE_STEED);
        tag.putString("RestDimension", "not a dimension id at all");
        final SpectralSteedState after = SpectralSteedState.read(tag, CreatureKind.PALE_STEED);
        assertFalse(after.rest().isPresent());
        assertEquals(500, after.bond());
    }

    /** Kind mismatch clears what belongs to the other steed and converts no identity. */
    @Test
    void stateSavedByTheOtherSteedKeepsOnlyMaturity() {
        final SpectralSteedState nightmare = SpectralSteedState.empty()
            .withBond(400)
            .withFatigue(300)
            .withRest(Optional.of(SITE), Optional.of(OVERWORLD))
            .withWarningIssued(3, 1);
        final SpectralSteedState asPale =
            SpectralSteedState.read(nightmare.write(CreatureKind.NIGHTMARE), CreatureKind.PALE_STEED);
        assertEquals(400, asPale.bond(), "maturity is mount maturity, not species");
        assertEquals(300, asPale.fatigue());
        assertFalse(asPale.rest().isPresent(), "the other steed's site is not inheritable");
        assertEquals(0, asPale.fearCooldown(),
            "a Pale Steed does not inherit the cooldown of a warning it cannot issue");

        final SpectralSteedState asNightmare =
            SpectralSteedState.read(nightmare.write(CreatureKind.NIGHTMARE), CreatureKind.NIGHTMARE);
        assertTrue(asNightmare.rest().isPresent(), "its own site does survive");
    }

    @Test
    void readingWithSomethingThatIsNotASteedIsRefused() {
        final CompoundTag tag = SpectralSteedState.empty().write(CreatureKind.PALE_STEED);
        assertThrows(IllegalArgumentException.class,
            () -> SpectralSteedState.read(tag, CreatureKind.OWL));
    }

    @Test
    void steppingNeverEndsAnythingItOnlyAdvancesCountdowns() {
        SpectralSteedState state = SpectralSteedState.empty()
            .withGait(Gait.WALK)
            .withWarningIssued(1, 1)
            .withRestCompleted();
        final int fear = state.fearCooldown();
        final int rest = state.restCooldown();
        state = state.step();
        assertEquals(fear - 1, state.fearCooldown());
        assertEquals(rest - 1, state.restCooldown());
        assertEquals(Gait.WALK, state.gait(), "stepping does not change the band");
    }
}


