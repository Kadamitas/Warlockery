package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.AnimalFamiliarRules.SearchOutcome;
import com.kadamitas.warlockery.entity.AnimalFamiliarState.Phase;
import com.kadamitas.warlockery.entity.AnimalFamiliarState.Signature;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class AnimalFamiliarStateTest {

    private static final UUID IDENTITY = UUID.fromString("00000000-0000-0000-0000-00000000dead");
    private static final UUID PREY = UUID.fromString("00000000-0000-0000-0000-00000000beef");
    private static final String OVERWORLD = "minecraft:overworld";

    private static AnimalFamiliarState fresh(final AnimalFamiliarSpecies species) {
        return AnimalFamiliarState.empty(species, IDENTITY, 1_000L);
    }

    // ---- the classification the coordinator asked for ----

    @Test
    void identityCouplingIsEnforcedBecauseDependentFieldsCannotDisagree() {
        final AnimalFamiliarState base = fresh(AnimalFamiliarSpecies.CAT);

        final AnimalFamiliarState homeWithoutDimension =
            base.withHome(Optional.of(new BlockPos(1, 2, 3)), Optional.empty());
        assertTrue(homeWithoutDimension.home().isEmpty(),
            "a position without its dimension is not a place");

        final AnimalFamiliarState dimensionWithoutHome =
            base.withHome(Optional.empty(), Optional.of(OVERWORLD));
        assertTrue(dimensionWithoutHome.homeDimension().isEmpty());

        final AnimalFamiliarState phaseWithoutTarget =
            base.withPhase(Phase.COMMIT, Optional.empty(), 9_000L);
        assertEquals(Phase.NONE, phaseWithoutTarget.phase(),
            "a running phase is a phase against something");
        assertEquals(0L, phaseWithoutTarget.phaseEndsAt());

        final AnimalFamiliarState clearedPhase =
            base.withPhase(Phase.NONE, Optional.of(PREY), 9_000L);
        assertTrue(clearedPhase.phaseTargetId().isEmpty());

        final AnimalFamiliarState leaseWithoutDefender = base.withDefence(Optional.empty(), 9_000L);
        assertEquals(0L, leaseWithoutDefender.defenceLeaseUntil(),
            "a lease with no defender is not a lease");

        final Signature.Forage landmarkless =
            new Signature.Forage(Optional.empty(), Optional.of(OVERWORLD));
        assertTrue(landmarkless.landmarkDimension().isEmpty());
    }

    @Test
    void noConstructorEndsATimedPhaseThatATickBranchOwns() {
        final AnimalFamiliarState telegraphing = fresh(AnimalFamiliarSpecies.OWL)
            .withPhase(Phase.TELEGRAPH, Optional.of(PREY), 0L);
        assertEquals(Phase.TELEGRAPH, telegraphing.phase(),
            "a phase whose deadline already passed is still running; only the tick branch ends it");
        assertTrue(telegraphing.phaseElapsed(5L), "expiry is reported, not acted on");
        assertTrue(telegraphing.phaseTargetId().isPresent(),
            "the frozen target survives so the tick branch can release it deliberately");

        final AnimalFamiliarState leased = fresh(AnimalFamiliarSpecies.TOAD)
            .withDefence(Optional.of(PREY), 10L);
        assertTrue(leased.defenceElapsed(11L));
        assertTrue(leased.defenceTargetId().isPresent(),
            "an expired lease still names its defender until the tick branch releases it");
    }

    @Test
    void thePayloadMustBelongToItsOwnSpeciesOrItIsNotThisFamiliarsPayload() {
        final AnimalFamiliarState cat = fresh(AnimalFamiliarSpecies.CAT);
        final AnimalFamiliarState wrong = cat.withSignature(new Signature.Hunt(2));
        assertTrue(wrong.signature() instanceof Signature.Territory,
            "an owl payload on a cat is corruption, and is replaced by the cat's own empty payload");
        assertEquals(AnimalFamiliarSpecies.CAT, wrong.signature().species());
    }

    @Test
    void everySpeciesGetsItsOwnEmptyPayloadAndNoOthers() {
        assertTrue(AnimalFamiliarState.emptySignature(AnimalFamiliarSpecies.CAT)
            instanceof Signature.Territory);
        assertTrue(AnimalFamiliarState.emptySignature(AnimalFamiliarSpecies.OWL)
            instanceof Signature.Hunt);
        assertTrue(AnimalFamiliarState.emptySignature(AnimalFamiliarSpecies.TOAD)
            instanceof Signature.Forage);
    }

    // ---- payload behaviour ----

    @Test
    void theCatPatrolRingWrapsAndTheOwlMissCounterSaturates() {
        Signature.Territory territory = new Signature.Territory(0);
        for (int step = 0; step < AnimalFamiliarState.PATROL_POINTS; step++) {
            territory = territory.advanced();
        }
        assertEquals(0, territory.patrolIndex(), "four patrol points wrap back to the first");
        assertEquals(1, new Signature.Territory(5).patrolIndex());
        assertEquals(3, new Signature.Territory(-1).patrolIndex());

        Signature.Hunt hunt = new Signature.Hunt(0);
        assertFalse(hunt.discouraged());
        for (int miss = 0; miss < 10; miss++) {
            hunt = hunt.missed();
        }
        assertEquals(AnimalFamiliarState.MAX_TRACKED_MISSES, hunt.consecutiveMisses());
        assertTrue(hunt.discouraged());
        assertFalse(hunt.connected().discouraged(), "one connected pounce restores the owl's nerve");
    }

    // ---- persistence ----

    @Test
    void aRoundTripPreservesTheDurableFactsAndDiscardsTheVolatileOnes() {
        final BlockPos home = new BlockPos(12, 64, -30);
        final AnimalFamiliarState before = fresh(AnimalFamiliarSpecies.TOAD)
            .withHome(Optional.of(home), Optional.of(OVERWORLD))
            .withPhase(Phase.COMMIT, Optional.of(PREY), 1_400L)
            .withDefence(Optional.of(PREY), 1_500L)
            .withSignatureCooldown(1_300L)
            .withActionEpoch(1_200L)
            .withSignature(new Signature.Forage(Optional.of(new BlockPos(3, 64, 4)),
                Optional.of(OVERWORLD)));

        final CompoundTag tag = before.write();
        final AnimalFamiliarState after =
            AnimalFamiliarState.read(tag, AnimalFamiliarSpecies.TOAD, IDENTITY, 1_000L);

        assertEquals(Optional.of(home), after.home(), "the durable home survives the seam");
        assertEquals(Optional.of(OVERWORLD), after.homeDimension());
        assertEquals(1_200L, after.actionEpoch());
        assertEquals(1_300L, after.signatureCooldownUntil());
        assertEquals(new Signature.Forage(Optional.of(new BlockPos(3, 64, 4)),
            Optional.of(OVERWORLD)), after.signature());

        assertEquals(Phase.NONE, after.phase(),
            "an interrupted signature action can never resume or replay across a reload");
        assertTrue(after.phaseTargetId().isEmpty());
        assertTrue(after.defenceTargetId().isEmpty(), "a defence lease does not survive a reload");
        assertEquals(0L, after.nextNavigationAt(), "no stale route survives a reload");
    }

    @Test
    void anOpenBackoffWindowSurvivesTheReloadSeamThatResetsTheAccumulators() {
        final AnimalFamiliarState before = fresh(AnimalFamiliarSpecies.CAT)
            .withHomeSearch(new SearchOutcome(9_999L, 3))
            .withPreySearch(new SearchOutcome(9_999L, 2))
            .withRoute(5_000L, 1_500L, 3);
        final AnimalFamiliarState after =
            AnimalFamiliarState.read(before.write(), AnimalFamiliarSpecies.CAT, IDENTITY, 1_000L);

        assertEquals(3, after.homeSearch().consecutiveFailures(),
            "an episode boundary resets accumulators but must preserve an open backoff window");
        assertEquals(2, after.preySearch().consecutiveFailures());
        assertEquals(1_000L + AnimalFamiliarRules.ROUTE_BACKOFF_TICKS, after.routeBackoffUntil(),
            "the window stays open across the seam, clamped to at most one full backoff from now");
        assertTrue(after.routeBackoffUntil() > 1_000L, "the window is still open");
        assertEquals(0, after.routeFailures(),
            "the per-destination failure count is volatile and does start clean");
        assertNotEquals(9_999L, after.homeSearch().nextDueAt(),
            "the cadence restaggers from identity rather than resuming a stored due time");
        assertTrue(after.homeSearch().nextDueAt() >= 1_000L);
    }

    @Test
    void aConsumedDefenceLeaseArmsAWindowThatSurvivesTheReloadSeam() {
        final AnimalFamiliarState armed = fresh(AnimalFamiliarSpecies.CAT).withDefenceCooldown(1_050L);
        assertFalse(armed.defenceReady(1_000L),
            "the window is what stops the same attack event re-leasing on every tick");
        assertTrue(armed.defenceReady(1_050L), "and it reopens on its own");
        final AnimalFamiliarState after =
            AnimalFamiliarState.read(armed.write(), AnimalFamiliarSpecies.CAT, IDENTITY, 1_000L);
        assertEquals(1_050L, after.defenceCooldownUntil(),
            "an open defence window survives the seam for the same reason the route backoff does");
    }

    @Test
    void aPayloadWrittenByAnotherSpeciesOrAnotherSchemaIsRejectedWholesale() {
        final CompoundTag owlTag = fresh(AnimalFamiliarSpecies.OWL)
            .withHome(Optional.of(new BlockPos(1, 2, 3)), Optional.of(OVERWORLD))
            .write();
        final AnimalFamiliarState asCat =
            AnimalFamiliarState.read(owlTag, AnimalFamiliarSpecies.CAT, IDENTITY, 1_000L);
        assertTrue(asCat.home().isEmpty(), "cross-species payloads are corruption, not migration");
        assertTrue(asCat.signature() instanceof Signature.Territory);

        final CompoundTag wrongVersion = fresh(AnimalFamiliarSpecies.CAT).write();
        wrongVersion.putInt("Version", AnimalFamiliarRules.STATE_SCHEMA_VERSION + 7);
        assertEquals(fresh(AnimalFamiliarSpecies.CAT).home(),
            AnimalFamiliarState.read(wrongVersion, AnimalFamiliarSpecies.CAT, IDENTITY, 1_000L).home());
    }

    @Test
    void loadClampsACorruptFarFutureCooldownBackToTheHorizon() {
        final CompoundTag tag = fresh(AnimalFamiliarSpecies.OWL).write();
        tag.putLong("SignatureCooldownUntil", Long.MAX_VALUE);
        tag.putLong("RouteBackoffUntil", Long.MAX_VALUE);
        final AnimalFamiliarState after =
            AnimalFamiliarState.read(tag, AnimalFamiliarSpecies.OWL, IDENTITY, 1_000L);
        final var profile = AnimalFamiliarRules.profile(AnimalFamiliarSpecies.OWL);
        assertEquals(1_000L + profile.signatureCooldownTicks(), after.signatureCooldownUntil());
        assertEquals(1_000L + AnimalFamiliarRules.ROUTE_BACKOFF_TICKS, after.routeBackoffUntil());
    }

    @Test
    void twoFamiliarsOfTheSameSpeciesDoNotComeOutOfLoadOnTheSameCadence() {
        final UUID other = UUID.fromString("00000000-0000-0000-0000-0000000f00d0");
        final AnimalFamiliarState first = AnimalFamiliarState.empty(AnimalFamiliarSpecies.CAT, IDENTITY, 0L);
        final AnimalFamiliarState second = AnimalFamiliarState.empty(AnimalFamiliarSpecies.CAT, other, 0L);
        assertNotEquals(first.homeSearch().nextDueAt(), second.homeSearch().nextDueAt());
    }

    @Test
    void aFreshStateIsDueToSearchWithinItsOwnIntervalAndHoldsNothingElse() {
        for (final AnimalFamiliarSpecies species : AnimalFamiliarSpecies.values()) {
            final AnimalFamiliarState state = fresh(species);
            final var profile = AnimalFamiliarRules.profile(species);
            assertEquals(Phase.NONE, state.phase());
            assertTrue(state.home().isEmpty());
            assertTrue(state.signatureOffCooldown(1_000L));
            assertTrue(state.homeSearch().nextDueAt()
                < 1_000L + profile.homeSearchIntervalTicks());
            assertEquals(species, state.signature().species());
        }
    }
}
