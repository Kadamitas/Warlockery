package com.kadamitas.warlockery.transformation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.transformation.VampireProgressionRules.Ability;
import com.kadamitas.warlockery.transformation.VampireProgressionRules.AbilityMode;
import com.kadamitas.warlockery.transformation.VampireProgressionRules.BloodPower;
import com.kadamitas.warlockery.transformation.VampireProgressionRules.ChargeIngredient;
import com.kadamitas.warlockery.transformation.VampireProgressionRules.ChargedBloodPower;
import com.kadamitas.warlockery.transformation.VampireProgressionRules.Diagnostic;
import com.kadamitas.warlockery.transformation.VampireProgressionRules.Metric;
import com.kadamitas.warlockery.transformation.VampireProgressionRules.Progress;
import com.kadamitas.warlockery.transformation.VampireProgressionRules.Quest;
import com.kadamitas.warlockery.transformation.VampireProgressionRules.Requirement;
import com.kadamitas.warlockery.transformation.VampireProgressionRules.RequirementKind;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class VampireProgressionRulesTest {
    @Test
    void catalogContainsTheTenOrderedHistoricalTrialsWithRuntimeBloodCapacities() {
        final var quests = VampireProgressionRules.quests();

        assertEquals(10, quests.size());
        assertEquals(
            IntStream.rangeClosed(1, 10).boxed().toList(),
            quests.stream().map(Quest::targetLevel).toList()
        );
        assertEquals(10, quests.stream().map(Quest::id).collect(java.util.stream.Collectors.toSet()).size());
        assertEquals(
            List.of(750, 1_000, 1_250, 1_500, 1_750, 2_000, 2_250, 2_500, 3_250, 3_500),
            quests.stream().map(Quest::bloodCapacity).toList()
        );
        assertEquals(
            List.of(0, 750, 1_000, 1_250, 1_500, 1_750, 2_000, 2_250, 2_500, 3_250, 3_500),
            IntStream.rangeClosed(0, 10).map(VampireProgressionRules::bloodCapacityAt).boxed().toList()
        );
        assertTrue(quests.stream().allMatch(quest -> !quest.description().isBlank()));
        assertTrue(quests.stream().anyMatch(quest -> quest.description().contains("Naamah")));
        assertTrue(quests.stream().map(Quest::description).noneMatch(text -> text.contains("Lilith")));
    }

    @Test
    void everyPostInitiationTrialRequiresItsOwnTornPageStage() {
        for (int targetLevel = 2; targetLevel <= VampireProgressionRules.MAX_LEVEL; targetLevel++) {
            final Quest quest = VampireProgressionRules.questForTargetLevel(targetLevel).orElseThrow();
            final Requirement manual = quest.requirements().getFirst();
            final Requirement pages = quest.requirements().get(1);

            assertEquals(Metric.OBSERVATIONS_MANUAL_OWNED, manual.metric());
            assertEquals(1, manual.required());
            assertEquals(Metric.TORN_PAGES_INSERTED, pages.metric());
            assertEquals(targetLevel - 1, pages.required());
        }
    }

    @TestFactory
    Stream<DynamicContainer> everyTrialHasDeterministicFailureProgressAndSuccessCoverage() {
        return VampireProgressionRules.quests().stream().map(quest -> DynamicContainer.dynamicContainer(
            "level " + quest.targetLevel() + " " + quest.id(),
            Stream.of(
                DynamicTest.dynamicTest("failure names the first missing condition", () -> {
                    final var evaluation = VampireProgressionRules.evaluate(
                        Progress.atLevel(quest.targetLevel() - 1)
                    );

                    assertFalse(evaluation.ready());
                    assertEquals(quest.requirements().getFirst().diagnostic(), evaluation.diagnostic());
                    assertEquals(0, evaluation.satisfiedRequirements());
                    assertEquals(quest.requirements().size(), evaluation.totalRequirements());
                }),
                DynamicTest.dynamicTest("observations reach one exact ready state", () -> {
                    final Progress progress = readyProgress(quest);
                    final var evaluation = VampireProgressionRules.evaluate(progress);

                    assertTrue(evaluation.ready());
                    assertEquals(Diagnostic.READY_TO_ADVANCE, evaluation.diagnostic());
                    assertEquals(evaluation.totalRequirements(), evaluation.satisfiedRequirements());
                }),
                DynamicTest.dynamicTest("success advances exactly one level", () -> {
                    final Progress ready = readyProgress(quest);
                    final var transition = VampireProgressionRules.attemptAdvance(ready);

                    assertTrue(transition.advanced());
                    assertEquals(quest.targetLevel(), transition.after().level());
                    assertEquals(quest.abilities(), transition.unlockedAbilities());
                    assertEquals(quest.bloodPowers(), transition.unlockedBloodPowers());
                    assertEquals(
                        quest.targetLevel() == 10 ? Diagnostic.PATH_COMPLETED : Diagnostic.LEVEL_ADVANCED,
                        transition.diagnostic()
                    );
                    assertEquals(
                        quest.requirements().stream()
                            .filter(requirement -> requirement.kind() == RequirementKind.OFFERING)
                            .collect(java.util.stream.Collectors.toMap(
                                Requirement::metric,
                                Requirement::required
                            )),
                        transition.consumedOfferings()
                    );
                })
            )
        ));
    }

    @Test
    void currentBloodIsAGaugeRatherThanACumulativeDonation() {
        Progress progress = Progress.atLevel(1);
        progress = VampireProgressionRules.observe(progress, Metric.OBSERVATIONS_MANUAL_OWNED, 1).progress();
        progress = VampireProgressionRules.observeValue(progress, Metric.TORN_PAGES_INSERTED, 1).progress();

        final var full = VampireProgressionRules.observeValue(progress, Metric.BLOOD_STORED, 750);
        final var fallen = VampireProgressionRules.observeValue(full.progress(), Metric.BLOOD_STORED, 749);

        assertTrue(full.evaluation().ready());
        assertEquals(Diagnostic.READY_TO_ADVANCE, full.diagnostic());
        assertFalse(fallen.evaluation().ready());
        assertEquals(749, fallen.progress().count(Metric.BLOOD_STORED));
        assertEquals(Diagnostic.BLOOD_RESERVE_NOT_FULL, fallen.evaluation().diagnostic());
    }

    @Test
    void manualAndInsertedPagesSurviveAdvancementWhileQuestProgressIsCleared() {
        final Quest levelTwo = VampireProgressionRules.questForTargetLevel(2).orElseThrow();
        final Progress ready = readyProgress(levelTwo);
        final Progress after = VampireProgressionRules.attemptAdvance(ready).after();

        assertEquals(2, after.level());
        assertEquals(1, after.count(Metric.OBSERVATIONS_MANUAL_OWNED));
        assertEquals(1, after.count(Metric.TORN_PAGES_INSERTED));
        assertEquals(0, after.count(Metric.BLOOD_STORED));
        assertEquals(Diagnostic.TORN_PAGES_REQUIRED, VampireProgressionRules.evaluate(after).diagnostic());

        final var secondPage = VampireProgressionRules.observeValue(after, Metric.TORN_PAGES_INSERTED, 2);
        assertTrue(secondPage.changed());
        assertEquals(2, secondPage.progress().count(Metric.TORN_PAGES_INSERTED));
    }

    @TestFactory
    Stream<DynamicContainer> uniqueVictimsAndVillageCentersCannotBeCountedTwice() {
        return Stream.of(
            uniqueCase(3, Metric.DISTINCT_VILLAGERS_HALF_DRAINED, 5),
            uniqueCase(8, Metric.DISTINCT_VILLAGES_REACHED_IN_BATSWARM_FORM, 4),
            uniqueCase(9, Metric.DISTINCT_CAGED_VILLAGERS_HALF_DRAINED, 5)
        ).map(testCase -> DynamicContainer.dynamicContainer(
            testCase.metric().name(),
            Stream.of(
                DynamicTest.dynamicTest("identities are case-insensitive and distinct", () -> {
                    Progress progress = progressWithManual(testCase.targetLevel() - 1, testCase.targetLevel() - 1);
                    final var first = VampireProgressionRules.observeUnique(progress, testCase.metric(), "Subject-1");
                    final var duplicate = VampireProgressionRules.observeUnique(
                        first.progress(),
                        testCase.metric(),
                        " subject-1 "
                    );

                    assertTrue(first.changed());
                    assertFalse(duplicate.changed());
                    assertEquals(Diagnostic.IDENTITY_ALREADY_RECORDED, duplicate.diagnostic());
                    assertEquals(1, duplicate.progress().count(testCase.metric()));
                }),
                DynamicTest.dynamicTest("the documented distinct total is exact", () -> {
                    Progress progress = progressWithManual(testCase.targetLevel() - 1, testCase.targetLevel() - 1);
                    for (int index = 1; index <= testCase.required(); index++) {
                        progress = VampireProgressionRules.observeUnique(
                            progress,
                            testCase.metric(),
                            "subject-" + index
                        ).progress();
                    }

                    assertEquals(testCase.required(), progress.count(testCase.metric()));
                    assertTrue(VampireProgressionRules.evaluate(progress).ready());
                })
            )
        ));
    }

    @Test
    void fourNightStreakCannotStartWithoutTheBookAndDeathResetsOnlyTheStreak() {
        Progress progress = Progress.atLevel(3);
        final var withoutBook = VampireProgressionRules.observe(
            progress,
            Metric.CONSECUTIVE_FULL_NIGHTS_SURVIVED,
            1
        );
        progress = VampireProgressionRules.observe(progress, Metric.OBSERVATIONS_MANUAL_OWNED, 1).progress();
        final var withoutPages = VampireProgressionRules.observe(
            progress,
            Metric.CONSECUTIVE_FULL_NIGHTS_SURVIVED,
            1
        );
        progress = VampireProgressionRules.observeValue(progress, Metric.TORN_PAGES_INSERTED, 3).progress();
        progress = VampireProgressionRules.observe(
            progress,
            Metric.CONSECUTIVE_FULL_NIGHTS_SURVIVED,
            2
        ).progress();

        final var death = VampireProgressionRules.recordDeath(progress);

        assertEquals(Diagnostic.OBSERVATIONS_MANUAL_REQUIRED, withoutBook.diagnostic());
        assertEquals(Diagnostic.TORN_PAGES_REQUIRED, withoutPages.diagnostic());
        assertEquals(Diagnostic.NIGHT_STREAK_BROKEN, death.diagnostic());
        assertEquals(0, death.progress().count(Metric.CONSECUTIVE_FULL_NIGHTS_SURVIVED));
        assertEquals(1, death.progress().count(Metric.OBSERVATIONS_MANUAL_OWNED));
        assertEquals(3, death.progress().count(Metric.TORN_PAGES_INSERTED));

        final var complete = VampireProgressionRules.observe(
            death.progress(),
            Metric.CONSECUTIVE_FULL_NIGHTS_SURVIVED,
            4
        );
        assertTrue(complete.evaluation().ready());
    }

    @Test
    void everyAbilityUnlocksAtItsDeclaredLevelRemainsCumulativeAndKeepsItsMode() {
        for (final Ability ability : Ability.values()) {
            final int level = VampireProgressionRules.minimumLevel(ability);

            assertFalse(VampireProgressionRules.abilitiesAt(level - 1).contains(ability));
            assertTrue(VampireProgressionRules.abilitiesAt(level).contains(ability));
            assertTrue(VampireProgressionRules.abilitiesAt(10).contains(ability));
            assertTrue(VampireProgressionRules.abilitiesAt(level, ability.mode()).contains(ability));
        }

        assertEquals(Set.of(Ability.SMASH_STONE), VampireProgressionRules.abilitiesAt(10, AbilityMode.CONTEXTUAL));
        assertEquals(
            Set.of(Ability.CALL_STORM, Ability.TELEPORT, Ability.BAT_SWARM),
            VampireProgressionRules.abilitiesAt(10, AbilityMode.CHARGED)
        );
    }

    @Test
    void levelTenBloodPowersRequireTheRightFullCrucibleAndReplaceEachOther() {
        final ChargedBloodPower empty = ChargedBloodPower.empty();
        final var locked = VampireProgressionRules.chargeBloodPower(
            9,
            empty,
            BloodPower.CALL_STORM,
            ChargeIngredient.WATER_ARTICHOKE_GLOBE,
            true
        );
        final var dry = VampireProgressionRules.chargeBloodPower(
            10,
            empty,
            BloodPower.CALL_STORM,
            ChargeIngredient.WATER_ARTICHOKE_GLOBE,
            false
        );
        final var wrong = VampireProgressionRules.chargeBloodPower(
            10,
            empty,
            BloodPower.CALL_STORM,
            ChargeIngredient.BONE,
            true
        );
        final var charged = VampireProgressionRules.chargeBloodPower(
            10,
            empty,
            BloodPower.CALL_STORM,
            ChargeIngredient.WATER_ARTICHOKE_GLOBE,
            true
        );
        final var replaced = VampireProgressionRules.chargeBloodPower(
            10,
            charged.after(),
            BloodPower.TELEPORT,
            ChargeIngredient.BONE,
            true
        );

        assertEquals(Diagnostic.BLOOD_POWER_LOCKED, locked.diagnostic());
        assertEquals(Diagnostic.BLOOD_CRUCIBLE_NOT_FULL, dry.diagnostic());
        assertEquals(Diagnostic.WRONG_CHARGING_INGREDIENT, wrong.diagnostic());
        assertEquals(Diagnostic.BLOOD_POWER_CHARGED, charged.diagnostic());
        assertEquals(BLOOD_POWER_CHARGES_PER_INFUSION, charged.after().charges());
        assertEquals(Diagnostic.BLOOD_POWER_REPLACED, replaced.diagnostic());
        assertEquals(Optional.of(BloodPower.TELEPORT), replaced.after().power());
        assertEquals(Set.of(), VampireProgressionRules.bloodPowersAt(9));
        assertEquals(Set.of(BloodPower.CALL_STORM, BloodPower.TELEPORT, BloodPower.BAT_SWARM), VampireProgressionRules.bloodPowersAt(10));

        ChargedBloodPower remaining = replaced.after();
        for (int use = 0; use < BLOOD_POWER_CHARGES_PER_INFUSION; use++) {
            final var result = VampireProgressionRules.useBloodPower(10, remaining);
            assertTrue(result.changed());
            remaining = result.after();
        }
        assertEquals(ChargedBloodPower.empty(), remaining);
        assertEquals(
            Diagnostic.BLOOD_POWER_EMPTY,
            VampireProgressionRules.useBloodPower(10, remaining).diagnostic()
        );
    }

    @Test
    void onlyPoppyAndOwnBloodGobletAreConsumedOfferings() {
        final var levelSeven = VampireProgressionRules.attemptAdvance(
            readyProgress(VampireProgressionRules.questForTargetLevel(7).orElseThrow())
        );
        final var levelTen = VampireProgressionRules.attemptAdvance(
            readyProgress(VampireProgressionRules.questForTargetLevel(10).orElseThrow())
        );

        assertEquals(Map.of(Metric.POPPY_OFFERED_TO_NAAMAH, 1), levelSeven.consumedOfferings());
        assertEquals(Map.of(Metric.OWN_BLOOD_GOBLET_OFFERED, 1), levelTen.consumedOfferings());
    }

    @Test
    void wrongObservationApisAndBadIdentitiesProduceStableDiagnostics() {
        final Progress levelTwo = Progress.atLevel(2);
        final var wrongUnique = VampireProgressionRules.observeUnique(
            levelTwo,
            Metric.TORN_PAGES_INSERTED,
            "page"
        );
        final var wrongCounter = VampireProgressionRules.observe(levelTwo, Metric.TORN_PAGES_INSERTED, 1);
        final var invalidIdentity = VampireProgressionRules.observeUnique(
            progressWithManual(2, 2),
            Metric.DISTINCT_VILLAGERS_HALF_DRAINED,
            "  "
        );

        assertEquals(Diagnostic.WRONG_OBSERVATION_MODE, wrongUnique.diagnostic());
        assertEquals(Diagnostic.WRONG_OBSERVATION_MODE, wrongCounter.diagnostic());
        assertEquals(Diagnostic.INVALID_IDENTITY, invalidIdentity.diagnostic());
        assertTrue(Diagnostic.TORN_PAGES_REQUIRED.messageKey().startsWith("message.warlockery.vampire_progression."));
    }

    @Test
    void completedPathIsInertAndPublicCollectionsAreImmutable() {
        final Progress complete = Progress.atLevel(10);
        final var transition = VampireProgressionRules.attemptAdvance(complete);
        final var observation = VampireProgressionRules.observe(complete, Metric.BLAZES_DEFEATED, 1);
        final Quest first = VampireProgressionRules.quests().getFirst();

        assertFalse(transition.advanced());
        assertEquals(Diagnostic.PATH_COMPLETE, transition.diagnostic());
        assertEquals(Diagnostic.PATH_COMPLETE, observation.diagnostic());
        assertFalse(observation.changed());
        assertThrows(UnsupportedOperationException.class, () -> VampireProgressionRules.quests().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.requirements().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.abilities().clear());
        assertNotEquals(new HashSet<>(), first.abilities());
    }

    @Test
    void progressBoundsLevelsCopiesStateAndRefusesToRemoveInsertedPages() {
        final EnumMap<Metric, Integer> counters = new EnumMap<>(Metric.class);
        counters.put(Metric.OBSERVATIONS_MANUAL_OWNED, 1);
        final EnumMap<Metric, Set<String>> identities = new EnumMap<>(Metric.class);
        identities.put(Metric.DISTINCT_VILLAGERS_HALF_DRAINED, Set.of("Villager-A"));

        final Progress low = new Progress(-10, counters, identities);
        counters.put(Metric.OBSERVATIONS_MANUAL_OWNED, 8);
        identities.put(Metric.DISTINCT_VILLAGERS_HALF_DRAINED, Set.of("villager-b"));

        assertEquals(0, low.level());
        assertEquals(10, Progress.atLevel(100).level());
        assertEquals(1, low.count(Metric.OBSERVATIONS_MANUAL_OWNED));
        assertEquals(Set.of("villager-a"), low.identities(Metric.DISTINCT_VILLAGERS_HALF_DRAINED));
        assertThrows(UnsupportedOperationException.class, () -> low.counters().put(Metric.BLAZES_DEFEATED, 1));
        assertThrows(UnsupportedOperationException.class, () -> low.identities(Metric.DISTINCT_VILLAGERS_HALF_DRAINED).clear());

        Progress levelFour = Progress.atLevel(4);
        levelFour = VampireProgressionRules.observe(levelFour, Metric.OBSERVATIONS_MANUAL_OWNED, 1).progress();
        levelFour = VampireProgressionRules.observeValue(levelFour, Metric.TORN_PAGES_INSERTED, 4).progress();
        final var removal = VampireProgressionRules.observeValue(levelFour, Metric.TORN_PAGES_INSERTED, 3);
        assertEquals(Diagnostic.TORN_PAGES_CANNOT_BE_REMOVED, removal.diagnostic());
        assertFalse(removal.changed());
    }

    private static Progress readyProgress(final Quest quest) {
        Progress progress = Progress.atLevel(quest.targetLevel() - 1);
        for (final Requirement requirement : quest.requirements()) {
            progress = switch (requirement.kind()) {
                case GAUGE -> VampireProgressionRules.observeValue(
                    progress,
                    requirement.metric(),
                    requirement.required()
                ).progress();
                case UNIQUE -> addUniqueObservations(progress, requirement);
                case MILESTONE, COUNTER, OFFERING -> VampireProgressionRules.observe(
                    progress,
                    requirement.metric(),
                    requirement.required()
                ).progress();
            };
        }
        return progress;
    }

    private static Progress addUniqueObservations(final Progress progress, final Requirement requirement) {
        Progress updated = progress;
        for (int index = 1; index <= requirement.required(); index++) {
            updated = VampireProgressionRules.observeUnique(
                updated,
                requirement.metric(),
                requirement.metric().name() + '-' + index
            ).progress();
        }
        return updated;
    }

    private static Progress progressWithManual(final int level, final int pages) {
        Progress progress = Progress.atLevel(level);
        progress = VampireProgressionRules.observe(progress, Metric.OBSERVATIONS_MANUAL_OWNED, 1).progress();
        return VampireProgressionRules.observeValue(progress, Metric.TORN_PAGES_INSERTED, pages).progress();
    }

    private static UniqueCase uniqueCase(final int targetLevel, final Metric metric, final int required) {
        return new UniqueCase(targetLevel, metric, required);
    }

    private record UniqueCase(int targetLevel, Metric metric, int required) {
    }

    private static final int BLOOD_POWER_CHARGES_PER_INFUSION =
        VampireProgressionRules.BLOOD_POWER_CHARGES_PER_INFUSION;
}
