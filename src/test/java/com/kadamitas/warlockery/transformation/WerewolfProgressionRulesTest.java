package com.kadamitas.warlockery.transformation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.transformation.WerewolfProgressionRules.Ability;
import com.kadamitas.warlockery.transformation.WerewolfProgressionRules.Diagnostic;
import com.kadamitas.warlockery.transformation.WerewolfProgressionRules.Metric;
import com.kadamitas.warlockery.transformation.WerewolfProgressionRules.Progress;
import com.kadamitas.warlockery.transformation.WerewolfProgressionRules.Quest;
import com.kadamitas.warlockery.transformation.WerewolfProgressionRules.RequirementKind;
import com.kadamitas.warlockery.transformation.WerewolfProgressionRules.Reward;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class WerewolfProgressionRulesTest {
    @Test
    void catalogContainsTenOrderedModernTrials() {
        final var quests = WerewolfProgressionRules.quests();

        assertEquals(10, quests.size());
        assertEquals(
            IntStream.rangeClosed(1, 10).boxed().toList(),
            quests.stream().map(Quest::targetLevel).toList()
        );
        assertEquals(10, quests.stream().map(Quest::id).collect(java.util.stream.Collectors.toSet()).size());
        assertTrue(quests.stream().allMatch(quest -> !quest.description().isBlank()));
        assertTrue(quests.get(2).description().contains("raw mutton"));
        assertTrue(quests.get(8).description().contains("zombified piglins"));
        assertTrue(quests.stream().map(Quest::description).noneMatch(text -> text.contains("lambchop")));
        assertTrue(quests.stream().map(Quest::description).noneMatch(text -> text.contains("Zombie Pigmen")));
    }

    @TestFactory
    Stream<DynamicContainer> everyTrialHasDeterministicFailureProgressAndSuccessCoverage() {
        return WerewolfProgressionRules.quests().stream().map(quest -> DynamicContainer.dynamicContainer(
            "level " + quest.targetLevel() + " " + quest.id(),
            Stream.of(
                DynamicTest.dynamicTest("failure identifies the missing requirement", () -> {
                    final var evaluation = WerewolfProgressionRules.evaluate(
                        Progress.atLevel(quest.targetLevel() - 1)
                    );

                    assertFalse(evaluation.ready());
                    assertEquals(quest.requirements().getFirst().diagnostic(), evaluation.diagnostic());
                    assertEquals(0, evaluation.satisfiedRequirements());
                    assertEquals(quest.requirements().size(), evaluation.totalRequirements());
                }),
                DynamicTest.dynamicTest("observation reaches an exact ready state", () -> {
                    Progress progress = Progress.atLevel(quest.targetLevel() - 1);
                    for (final var requirement : quest.requirements()) {
                        progress = WerewolfProgressionRules.observe(
                            progress,
                            requirement.metric(),
                            requirement.required()
                        ).progress();
                    }

                    final var evaluation = WerewolfProgressionRules.evaluate(progress);
                    assertTrue(evaluation.ready());
                    assertEquals(Diagnostic.READY_TO_ADVANCE, evaluation.diagnostic());
                    assertEquals(evaluation.totalRequirements(), evaluation.satisfiedRequirements());
                }),
                DynamicTest.dynamicTest("success advances exactly one level", () -> {
                    final Progress ready = readyProgress(quest);
                    final var transition = WerewolfProgressionRules.attemptAdvance(ready);

                    assertTrue(transition.advanced());
                    assertEquals(quest.targetLevel(), transition.after().level());
                    assertEquals(Map.of(), transition.after().counters());
                    assertEquals(quest.abilities(), transition.unlockedAbilities());
                    assertEquals(quest.completionRewards(), transition.completionRewards());
                    assertEquals(
                        quest.targetLevel() == 10 ? Diagnostic.PATH_COMPLETED : Diagnostic.LEVEL_ADVANCED,
                        transition.diagnostic()
                    );
                    assertEquals(
                        quest.requirements().stream()
                            .filter(requirement -> requirement.kind() == RequirementKind.OFFERING)
                            .collect(java.util.stream.Collectors.toMap(
                                WerewolfProgressionRules.Requirement::metric,
                                WerewolfProgressionRules.Requirement::required
                            )),
                        transition.consumedOfferings()
                    );
                })
            )
        ));
    }

    @Test
    void observationsOnlyCountForTheActiveTrialAndSaturateAtItsTarget() {
        final Progress levelTwo = Progress.atLevel(2);
        final var irrelevant = WerewolfProgressionRules.observe(levelTwo, Metric.TONGUES_OF_DOG_OFFERED, 50);
        final var invalid = WerewolfProgressionRules.observe(levelTwo, Metric.RAW_MUTTON_OFFERED_FROM_WOLF_HUNTS, 0);
        final var overfilled = WerewolfProgressionRules.observe(
            levelTwo,
            Metric.RAW_MUTTON_OFFERED_FROM_WOLF_HUNTS,
            Integer.MAX_VALUE
        );

        assertEquals(Diagnostic.IRRELEVANT_OBSERVATION, irrelevant.diagnostic());
        assertFalse(irrelevant.changed());
        assertEquals(Diagnostic.INVALID_AMOUNT, invalid.diagnostic());
        assertFalse(invalid.changed());
        assertEquals(30, overfilled.progress().count(Metric.RAW_MUTTON_OFFERED_FROM_WOLF_HUNTS));
        assertEquals(Diagnostic.READY_TO_ADVANCE, overfilled.diagnostic());
    }

    @Test
    void everyAbilityUnlocksAtItsDeclaredLevelAndRemainsUnlocked() {
        for (final Ability ability : Ability.values()) {
            final int level = WerewolfProgressionRules.minimumLevel(ability);

            assertFalse(WerewolfProgressionRules.abilitiesAt(level - 1).contains(ability));
            assertTrue(WerewolfProgressionRules.abilitiesAt(level).contains(ability));
            assertTrue(WerewolfProgressionRules.abilitiesAt(10).contains(ability));
        }
    }

    @Test
    void levelFiveTrialAdvertisesTheHornAndLevelTwoAwardsTheMoonCharm() {
        final Quest wolfmanTrial = WerewolfProgressionRules.questForTargetLevel(5).orElseThrow();
        final Quest moonCharmTrial = WerewolfProgressionRules.questForTargetLevel(2).orElseThrow();

        assertEquals(Set.of(Reward.HORN_OF_THE_HUNT), wolfmanTrial.preparationRewards());
        assertEquals(Set.of(Reward.MOON_CHARM), moonCharmTrial.completionRewards());
    }

    @Test
    void missingTrialDoesNotAdvanceOrConsumeOfferings() {
        final Progress progress = Progress.atLevel(1);
        final var transition = WerewolfProgressionRules.attemptAdvance(progress);

        assertFalse(transition.advanced());
        assertEquals(progress, transition.after());
        assertEquals(Diagnostic.GOLD_INGOTS_REQUIRED, transition.diagnostic());
        assertEquals(Map.of(), transition.consumedOfferings());
    }

    @Test
    void completedPathCannotAdvanceOrAcceptMoreProgress() {
        final Progress complete = new Progress(10, Map.of(Metric.CURSE_ACCEPTED, 99));
        final var transition = WerewolfProgressionRules.attemptAdvance(complete);
        final var observation = WerewolfProgressionRules.observe(complete, Metric.CURSE_ACCEPTED, 1);

        assertFalse(transition.advanced());
        assertEquals(Diagnostic.PATH_COMPLETE, transition.diagnostic());
        assertEquals(Diagnostic.PATH_COMPLETE, observation.diagnostic());
        assertFalse(observation.changed());
        assertTrue(WerewolfProgressionRules.activeQuest(10).isEmpty());
    }

    @Test
    void progressBoundsCorruptLevelsAndDefensivelyCopiesCounters() {
        final EnumMap<Metric, Integer> source = new EnumMap<>(Metric.class);
        source.put(Metric.CURSE_ACCEPTED, 1);
        source.put(Metric.GOLD_INGOTS_OFFERED, -4);

        final Progress low = new Progress(-100, source);
        final Progress high = Progress.atLevel(100);
        source.put(Metric.CURSE_ACCEPTED, 9);

        assertEquals(0, low.level());
        assertEquals(10, high.level());
        assertEquals(1, low.count(Metric.CURSE_ACCEPTED));
        assertEquals(0, low.count(Metric.GOLD_INGOTS_OFFERED));
        assertThrows(UnsupportedOperationException.class, () -> low.counters().put(Metric.CURSE_ACCEPTED, 3));
    }

    @Test
    void questCollectionsCannotBeMutatedThroughThePublicModel() {
        final Quest quest = WerewolfProgressionRules.quests().getFirst();

        assertThrows(UnsupportedOperationException.class, () -> WerewolfProgressionRules.quests().clear());
        assertThrows(UnsupportedOperationException.class, () -> quest.requirements().clear());
        assertThrows(UnsupportedOperationException.class, () -> quest.abilities().clear());
        assertNotEquals(new HashSet<>(), quest.abilities());
    }

    private static Progress readyProgress(final Quest quest) {
        final EnumMap<Metric, Integer> counters = new EnumMap<>(Metric.class);
        quest.requirements().forEach(requirement -> counters.put(requirement.metric(), requirement.required()));
        return new Progress(quest.targetLevel() - 1, counters);
    }
}
