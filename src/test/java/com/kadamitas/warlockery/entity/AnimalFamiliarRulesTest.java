package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.AnimalFamiliarRules.Action;
import com.kadamitas.warlockery.entity.AnimalFamiliarRules.Facts;
import com.kadamitas.warlockery.entity.AnimalFamiliarRules.HomeCandidate;
import com.kadamitas.warlockery.entity.AnimalFamiliarRules.PreyCandidate;
import com.kadamitas.warlockery.entity.AnimalFamiliarRules.Reason;
import com.kadamitas.warlockery.entity.AnimalFamiliarRules.SelectionReason;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class AnimalFamiliarRulesTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OTHER_OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID CANDIDATE = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    private static Facts quiet(final AnimalFamiliarSpecies species) {
        return new Facts(species, false, false, 0.0, false, false, true, false,
            false, false, false, false, false);
    }

    // ---- vocabulary ----

    @Test
    void theThreeActionVocabulariesIntersectInExactlyTheFiveSharedRungs() {
        final Set<Action> cat = AnimalFamiliarRules.vocabulary(AnimalFamiliarSpecies.CAT);
        final Set<Action> owl = AnimalFamiliarRules.vocabulary(AnimalFamiliarSpecies.OWL);
        final Set<Action> toad = AnimalFamiliarRules.vocabulary(AnimalFamiliarSpecies.TOAD);
        final EnumSet<Action> shared = EnumSet.copyOf(cat);
        shared.retainAll(owl);
        shared.retainAll(toad);
        assertEquals(AnimalFamiliarRules.sharedVocabulary(), shared,
            "the only actions all three may emit are the five shared rungs");
        for (final Action action : Action.values()) {
            final int owners = (cat.contains(action) ? 1 : 0)
                + (owl.contains(action) ? 1 : 0)
                + (toad.contains(action) ? 1 : 0);
            assertTrue(owners == 3 || owners == 1,
                action + " must be shared by all three or owned by exactly one");
        }
        assertEquals(8, cat.size());
        assertEquals(8, owl.size());
        assertEquals(8, toad.size());
        assertEquals(Action.values().length, cat.size() + owl.size() + toad.size() - 10,
            "the union of the three vocabularies is the whole enum, counted once");
    }

    @Test
    void noSpeciesMayEverEmitAnotherSpeciesSignatureRestOrActiveAction() {
        for (final AnimalFamiliarSpecies species : AnimalFamiliarSpecies.values()) {
            for (final AnimalFamiliarSpecies other : AnimalFamiliarSpecies.values()) {
                if (species == other) {
                    continue;
                }
                final var profile = AnimalFamiliarRules.profile(other);
                assertFalse(AnimalFamiliarRules.permits(species, profile.signatureAction()));
                assertFalse(AnimalFamiliarRules.permits(species, profile.restAction()));
                assertFalse(AnimalFamiliarRules.permits(species, profile.activeAction()));
            }
        }
    }

    // ---- schedule: three different answers to the same world ----

    @Test
    void theThreeWakingWindowsDisagreeAtMiddayInRainAndAtMiddayInTheClear() {
        final long midday = 6_000L;
        final long midnight = 18_000L;
        assertTrue(AnimalFamiliarRules.awake(AnimalFamiliarSpecies.CAT, midday, false));
        assertFalse(AnimalFamiliarRules.awake(AnimalFamiliarSpecies.OWL, midday, false));
        assertFalse(AnimalFamiliarRules.awake(AnimalFamiliarSpecies.TOAD, midday, false));

        assertTrue(AnimalFamiliarRules.awake(AnimalFamiliarSpecies.CAT, midday, true));
        assertFalse(AnimalFamiliarRules.awake(AnimalFamiliarSpecies.OWL, midday, true),
            "weather is irrelevant to an owl's hunting window");
        assertTrue(AnimalFamiliarRules.awake(AnimalFamiliarSpecies.TOAD, midday, true),
            "the toad is the only one whose window responds to rain");

        assertFalse(AnimalFamiliarRules.awake(AnimalFamiliarSpecies.CAT, midnight, false));
        assertTrue(AnimalFamiliarRules.awake(AnimalFamiliarSpecies.OWL, midnight, false));
        assertTrue(AnimalFamiliarRules.awake(AnimalFamiliarSpecies.TOAD, midnight, false));
    }

    // ---- the ladder ----

    @Test
    void theLadderOrdersDefenceAboveTetherAboveSignatureAboveHomeAboveRoutine() {
        final AnimalFamiliarSpecies species = AnimalFamiliarSpecies.CAT;
        final Facts everythingAtOnce = new Facts(species, false, true, 100_000.0, true,
            true, true, true, true, false, false, true, true);
        assertEquals(Action.DEFEND_OWNER, AnimalFamiliarRules.decide(everythingAtOnce).action());

        final Facts noDefence = new Facts(species, false, true, 100_000.0, false,
            true, true, true, true, false, false, true, true);
        assertEquals(Action.TETHER_RETURN, AnimalFamiliarRules.decide(noDefence).action());

        final Facts inTether = new Facts(species, false, true, 1.0, false,
            true, true, true, true, false, false, true, true);
        assertEquals(Action.STALK_VERMIN, AnimalFamiliarRules.decide(inTether).action());
        assertEquals(Reason.ACTION_RUNNING, AnimalFamiliarRules.decide(inTether).reason());

        final Facts nothingRunning = new Facts(species, false, true, 1.0, false,
            false, true, false, false, false, false, true, true);
        assertEquals(Action.HOME_SEARCH, AnimalFamiliarRules.decide(nothingRunning).action());
    }

    @Test
    void anInvalidBodyDecidesNothingAtAllNoMatterWhatElseIsTrue() {
        final Facts dead = new Facts(AnimalFamiliarSpecies.OWL, true, true, 100_000.0, true,
            true, true, true, true, true, true, true, true);
        assertEquals(Action.IDLE, AnimalFamiliarRules.decide(dead).action());
        assertEquals(Reason.BODY_INVALID, AnimalFamiliarRules.decide(dead).reason());
    }

    @Test
    void aQuietFamiliarWithAHomeRestsAndOneWithoutOneIsSimplyIdle() {
        for (final AnimalFamiliarSpecies species : AnimalFamiliarSpecies.values()) {
            final var profile = AnimalFamiliarRules.profile(species);
            final Facts homeless = quiet(species);
            assertEquals(Action.IDLE, AnimalFamiliarRules.decide(homeless).action());
            assertEquals(Reason.NOTHING_TO_DO, AnimalFamiliarRules.decide(homeless).reason());

            final Facts settled = new Facts(species, false, false, 0.0, false, false, true, false,
                true, true, true, false, false);
            assertEquals(profile.restAction(), AnimalFamiliarRules.decide(settled).action());
            assertEquals(Reason.QUIET_WINDOW, AnimalFamiliarRules.decide(settled).reason());
        }
    }

    @Test
    void theTetherRadiusIsPerSpeciesSoTheSameOwnerDistanceProducesDifferentDecisions() {
        final double distance = 100.0;
        assertFalse(AnimalFamiliarRules.beyondTether(AnimalFamiliarSpecies.CAT, true, distance));
        assertFalse(AnimalFamiliarRules.beyondTether(AnimalFamiliarSpecies.OWL, true, distance));
        assertTrue(AnimalFamiliarRules.beyondTether(AnimalFamiliarSpecies.TOAD, true, distance),
            "a toad works on a much shorter leash than a cat or an owl");
        assertFalse(AnimalFamiliarRules.beyondTether(AnimalFamiliarSpecies.TOAD, false, distance),
            "an unloaded owner never pulls the tether");
    }

    // ---- target legality ----

    @Test
    void theOwnerASiblingFamiliarACreativePlayerAndUnattributedIrritationAreAllRejected() {
        assertFalse(AnimalFamiliarRules.mayDefendAgainst(Optional.of(OWNER), OWNER,
            Optional.empty(), true, false, false, true), "never the owner");
        assertFalse(AnimalFamiliarRules.mayDefendAgainst(Optional.of(OWNER), CANDIDATE,
            Optional.of(OWNER), true, false, false, true), "never a sibling familiar");
        assertFalse(AnimalFamiliarRules.mayDefendAgainst(Optional.of(OWNER), CANDIDATE,
            Optional.empty(), true, false, true, true), "never a creative or spectating player");
        assertFalse(AnimalFamiliarRules.mayDefendAgainst(Optional.of(OWNER), CANDIDATE,
            Optional.empty(), true, false, false, false), "never unattributed irritation");
        assertFalse(AnimalFamiliarRules.mayDefendAgainst(Optional.of(OWNER), CANDIDATE,
            Optional.empty(), false, false, false, true), "never a dead attacker");
        assertFalse(AnimalFamiliarRules.mayDefendAgainst(Optional.of(OWNER), CANDIDATE,
            Optional.empty(), true, true, false, true), "never itself");
        assertTrue(AnimalFamiliarRules.mayDefendAgainst(Optional.of(OWNER), CANDIDATE,
            Optional.of(OTHER_OWNER), true, false, false, true),
            "another player's familiar that actually struck the owner is a legal target");
    }

    // ---- bounded traversal ----

    @Test
    void everyInspectedPreyCandidateIsChargedBeforeAnyFilterCanRejectIt() {
        final List<PreyCandidate> dead = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            dead.add(new PreyCandidate(UUID.nameUUIDFromBytes(("dead" + index).getBytes()),
                index, false, false, true, false, false));
        }
        final var selection = AnimalFamiliarRules.selectPrey(dead, 8, 4);
        assertEquals(8, selection.inspected(),
            "forty entities that cannot possibly qualify still cost the full candidate budget");
        assertEquals(SelectionReason.BUDGET_EXHAUSTED, selection.reason());
        assertTrue(selection.prey().isEmpty());
        assertEquals(0, selection.lineOfSightChecks(),
            "no trace is spent on a candidate the cheap filters already rejected");
    }

    @Test
    void aSpeciesWithNoTraceBudgetNeverRejectsOnVisibility() {
        final PreyCandidate unseen = new PreyCandidate(CANDIDATE, 4.0, true, true, false, true, false);
        assertTrue(AnimalFamiliarRules.selectPrey(List.of(unseen), 12, 0).prey().isPresent(),
            "the cat stalks by scent: a zero trace budget means visibility is not a filter");
        assertTrue(AnimalFamiliarRules.selectPrey(List.of(unseen), 8, 4).prey().isEmpty(),
            "a species that does spend traces does reject an unseen candidate");
    }

    @Test
    void preyTiesBreakOnUnsignedIdentitySoTheChoiceIsStableAcrossRuns() {
        final UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        final UUID high = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        final PreyCandidate first = new PreyCandidate(high, 9.0, true, true, false, true, true);
        final PreyCandidate second = new PreyCandidate(low, 9.0, true, true, false, true, true);
        assertEquals(Optional.of(low), AnimalFamiliarRules.selectPrey(List.of(first, second), 8, 4).prey());
        assertEquals(Optional.of(low), AnimalFamiliarRules.selectPrey(List.of(second, first), 8, 4).prey());
    }

    @Test
    void everyInspectedHomeCandidateIsChargedAndTheNearestQualifyingOneWins() {
        final List<HomeCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            candidates.add(new HomeCandidate(index, 100.0 - index, false, false, false));
        }
        final var exhausted = AnimalFamiliarRules.selectHome(candidates, 20);
        assertEquals(20, exhausted.inspected());
        assertEquals(SelectionReason.BUDGET_EXHAUSTED, exhausted.reason());

        final List<HomeCandidate> mixed = List.of(
            new HomeCandidate(7L, 64.0, true, true, true),
            new HomeCandidate(9L, 16.0, true, true, true),
            new HomeCandidate(11L, 1.0, true, true, false)
        );
        final var selection = AnimalFamiliarRules.selectHome(mixed, 24);
        assertEquals(Optional.of(9L), selection.home(),
            "the nearest position that passes the species predicate wins, not the nearest overall");
        assertEquals(SelectionReason.SELECTED, selection.reason());
        assertEquals(3, selection.inspected());
    }

    @Test
    void anEmptySceneReportsNoCandidateRatherThanAnExhaustedBudget() {
        assertEquals(SelectionReason.NO_CANDIDATE,
            AnimalFamiliarRules.selectPrey(List.of(), 8, 4).reason());
        assertEquals(SelectionReason.NO_CANDIDATE,
            AnimalFamiliarRules.selectHome(List.of(), 24).reason());
    }

    // ---- cadence and backoff ----

    @Test
    void aSearchThatQualifiesNothingStillArmsItsFullCadenceAndRecordsTheFailure() {
        final var failed = AnimalFamiliarRules.recordSearch(1_000L, 300, false, 0);
        assertEquals(1_300L, failed.nextDueAt(), "a fruitless search costs the whole interval");
        assertEquals(1, failed.consecutiveFailures());

        final var second = AnimalFamiliarRules.recordSearch(1_300L, 300, false, failed.consecutiveFailures());
        final var third = AnimalFamiliarRules.recordSearch(1_600L, 300, false, second.consecutiveFailures());
        assertEquals(3, third.consecutiveFailures());
        assertEquals(AnimalFamiliarRules.ROUTE_BACKOFF_TICKS,
            AnimalFamiliarRules.backoffTicks(third.consecutiveFailures()));

        final var recovered = AnimalFamiliarRules.recordSearch(1_900L, 300, true, third.consecutiveFailures());
        assertEquals(0, recovered.consecutiveFailures());
        assertEquals(2_200L, recovered.nextDueAt(), "success arms exactly the same interval");
        assertEquals(0, AnimalFamiliarRules.backoffTicks(recovered.consecutiveFailures()));
    }

    @Test
    void aZeroIntervalStillCostsOneTickRatherThanSpinningInsideOne() {
        assertEquals(101L, AnimalFamiliarRules.recordSearch(100L, 0, false, 0).nextDueAt());
        assertEquals(101L, AnimalFamiliarRules.recordSearch(100L, -5, true, 0).nextDueAt());
    }

    @Test
    void routingIsGatedByBothTheIntervalAndAnOpenBackoffWindow() {
        assertTrue(AnimalFamiliarRules.mayRoute(100L, 100L, 0L));
        assertFalse(AnimalFamiliarRules.mayRoute(99L, 100L, 0L));
        assertFalse(AnimalFamiliarRules.mayRoute(100L, 100L, 150L),
            "an open backoff window refuses the request even when the interval elapsed");
    }

    @Test
    void deadlinesClampIntoTheHorizonAndZeroKeepsMeaningUnset() {
        assertEquals(0L, AnimalFamiliarRules.clampDeadline(0L, 500L, 100L));
        assertEquals(0L, AnimalFamiliarRules.clampDeadline(-9L, 500L, 100L));
        assertEquals(550L, AnimalFamiliarRules.clampDeadline(550L, 500L, 100L));
        assertEquals(600L, AnimalFamiliarRules.clampDeadline(Long.MAX_VALUE, 500L, 100L),
            "a corrupt far-future deadline is pulled back to the horizon, not honoured");
        assertEquals(Long.MAX_VALUE, AnimalFamiliarRules.saturatingAdd(Long.MAX_VALUE, 10L));
    }

    @Test
    void theScheduleOffsetIsStablePerIdentityAndInsideTheSpan() {
        for (int span : new int[] {1, 7, 200, 400}) {
            final int first = AnimalFamiliarRules.stableOffset(OWNER, span);
            assertEquals(first, AnimalFamiliarRules.stableOffset(OWNER, span));
            assertTrue(first >= 0 && first < Math.max(1, span));
        }
        assertNotEquals(
            AnimalFamiliarRules.stableOffset(OWNER, 400),
            AnimalFamiliarRules.stableOffset(OTHER_OWNER, 400),
            "two familiars do not pulse on the same tick"
        );
    }

    @Test
    void everyProfileIsPositivelyBoundedAndTheThreeTuningTablesActuallyDiffer() {
        final var cat = AnimalFamiliarRules.profile(AnimalFamiliarSpecies.CAT);
        final var owl = AnimalFamiliarRules.profile(AnimalFamiliarSpecies.OWL);
        final var toad = AnimalFamiliarRules.profile(AnimalFamiliarSpecies.TOAD);
        assertNotEquals(cat.tetherRadiusSquared(), toad.tetherRadiusSquared());
        assertNotEquals(owl.tetherRadiusSquared(), cat.tetherRadiusSquared());
        assertNotEquals(cat.signatureCooldownTicks(), owl.signatureCooldownTicks());
        assertNotEquals(owl.signatureCooldownTicks(), toad.signatureCooldownTicks());
        assertNotEquals(cat.telegraphTicks(), owl.telegraphTicks());
        assertNotEquals(owl.telegraphTicks(), toad.telegraphTicks());
        assertEquals(0, cat.preyLineOfSightCap());
        assertEquals(4, owl.preyLineOfSightCap());
        assertEquals(4, toad.preyLineOfSightCap());
        assertNotEquals(cat.activeWindow(), owl.activeWindow());
        assertNotEquals(owl.activeWindow(), toad.activeWindow());
        assertNotEquals(cat.activeWindow(), toad.activeWindow());
    }
}
