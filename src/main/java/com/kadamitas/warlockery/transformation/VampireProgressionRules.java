package com.kadamitas.warlockery.transformation;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class VampireProgressionRules {
    public static final int MAX_LEVEL = 10;
    public static final int MAX_TORN_PAGES = 9;
    public static final int BLOOD_POWER_CHARGES_PER_INFUSION = 5;

    private static final List<Quest> QUESTS = List.of(
        quest(
            1,
            "blood_audience",
            "The Blood Audience",
            "Complete the Blood Audience beneath the night sky and accept Naamah's immortal gift.",
            750,
            List.of(requirement(
                Metric.BLOOD_AUDIENCE_COMPLETED,
                1,
                RequirementKind.MILESTONE,
                Diagnostic.BLOOD_AUDIENCE_REQUIRED
            )),
            Set.of(
                Ability.BLOOD_SUSTENANCE,
                Ability.DRINK_BLOOD,
                Ability.SENSE_BLOOD,
                Ability.POISON_AND_DISEASE_IMMUNITY,
                Ability.SUPERNATURAL_RESILIENCE
            )
        ),
        quest(
            2,
            "brimming_reserve",
            "The Brimming Reserve",
            "Bind the first Torn Page into Observations of an Immortal, then drink until all 750 drops of blood are filled.",
            1_000,
            withManual(1, requirement(
                Metric.BLOOD_STORED,
                750,
                RequirementKind.GAUGE,
                Diagnostic.BLOOD_RESERVE_NOT_FULL
            )),
            Set.of(Ability.TRANSFIX, Ability.NIGHT_VISION)
        ),
        quest(
            3,
            "five_crimson_marks",
            "Five Crimson Marks",
            "Bind the second Torn Page, then leave five different villagers alive after drinking more than half of each blood reserve.",
            1_250,
            withManual(2, requirement(
                Metric.DISTINCT_VILLAGERS_HALF_DRAINED,
                5,
                RequirementKind.UNIQUE,
                Diagnostic.FIVE_DISTINCT_VILLAGERS_REQUIRED
            )),
            Set.of(Ability.KNOCKBACK)
        ),
        quest(
            4,
            "four_unbroken_nights",
            "Four Unbroken Nights",
            "Bind the third Torn Page, keep the completed volume close, and survive four whole nights in succession without dying.",
            1_500,
            withManual(3, requirement(
                Metric.CONSECUTIVE_FULL_NIGHTS_SURVIVED,
                4,
                RequirementKind.COUNTER,
                Diagnostic.FOUR_CONSECUTIVE_NIGHTS_REQUIRED
            )),
            Set.of(Ability.SPEED)
        ),
        quest(
            5,
            "nocturnal_fire",
            "Nocturnal Fire",
            "Bind the fourth Torn Page and endure ten of your own Sun Grenades while the sun is below the horizon.",
            1_750,
            withManual(4, requirement(
                Metric.NIGHTTIME_SUN_GRENADE_BURNS,
                10,
                RequirementKind.COUNTER,
                Diagnostic.TEN_SUN_GRENADE_BURNS_REQUIRED
            )),
            Set.of(Ability.RESIST_SUN)
        ),
        quest(
            6,
            "blazing_reckoning",
            "The Blazing Reckoning",
            "Bind the fifth Torn Page and extinguish twenty blazes with your own hand.",
            2_000,
            withManual(5, requirement(
                Metric.BLAZES_DEFEATED,
                20,
                RequirementKind.COUNTER,
                Diagnostic.TWENTY_BLAZES_REQUIRED
            )),
            Set.of(Ability.SMASH_STONE)
        ),
        quest(
            7,
            "audience_of_naamah",
            "The Audience of Naamah",
            "Bind the sixth Torn Page, call Naamah through another Blood Audience, defeat her trial, and lay a poppy before her.",
            2_250,
            withManual(
                6,
                requirement(
                    Metric.NAAMAH_AUDIENCE_COMPLETED,
                    1,
                    RequirementKind.MILESTONE,
                    Diagnostic.NAAMAH_AUDIENCE_REQUIRED
                ),
                requirement(
                    Metric.NAAMAH_DEFEATED,
                    1,
                    RequirementKind.MILESTONE,
                    Diagnostic.NAAMAH_MUST_BE_DEFEATED
                ),
                requirement(
                    Metric.POPPY_OFFERED_TO_NAAMAH,
                    1,
                    RequirementKind.OFFERING,
                    Diagnostic.POPPY_OFFERING_REQUIRED
                )
            ),
            Set.of(Ability.BATSWARM_FORM)
        ),
        quest(
            8,
            "four_villages_on_wing",
            "Four Villages on the Wing",
            "Bind the seventh Torn Page and cross the centers of four different villages while travelling as a swarm of bats.",
            2_500,
            withManual(7, requirement(
                Metric.DISTINCT_VILLAGES_REACHED_IN_BATSWARM_FORM,
                4,
                RequirementKind.UNIQUE,
                Diagnostic.FOUR_DISTINCT_VILLAGES_REQUIRED
            )),
            Set.of(Ability.MESMERIZE)
        ),
        quest(
            9,
            "five_caged_feasts",
            "Five Caged Feasts",
            "Bind the eighth Torn Page, secure five different villagers in sound cages, and drink roughly half the blood of each captive.",
            3_250,
            withManual(8, requirement(
                Metric.DISTINCT_CAGED_VILLAGERS_HALF_DRAINED,
                5,
                RequirementKind.UNIQUE,
                Diagnostic.FIVE_CAGED_VILLAGERS_REQUIRED
            )),
            Set.of(Ability.CREATE_VAMPIRES)
        ),
        quest(
            10,
            "the_crimson_maker",
            "The Crimson Maker",
            "Bind all nine Torn Pages, mesmerize a fully drained villager, offer a goblet of your own blood beside a nearby coffin, and complete the turning.",
            3_500,
            withManual(
                9,
                requirement(
                    Metric.CREATION_TARGET_FULLY_DRAINED,
                    1,
                    RequirementKind.MILESTONE,
                    Diagnostic.FULLY_DRAINED_TARGET_REQUIRED
                ),
                requirement(
                    Metric.CREATION_TARGET_MESMERIZED,
                    1,
                    RequirementKind.MILESTONE,
                    Diagnostic.MESMERIZED_TARGET_REQUIRED
                ),
                requirement(
                    Metric.OWN_BLOOD_GOBLET_OFFERED,
                    1,
                    RequirementKind.OFFERING,
                    Diagnostic.OWN_BLOOD_GOBLET_REQUIRED
                ),
                requirement(
                    Metric.COFFIN_WITHIN_FOUR_BLOCKS,
                    1,
                    RequirementKind.MILESTONE,
                    Diagnostic.NEARBY_COFFIN_REQUIRED
                ),
                requirement(
                    Metric.VAMPIRE_CREATED_NEAR_COFFIN,
                    1,
                    RequirementKind.MILESTONE,
                    Diagnostic.CREATED_VAMPIRE_REQUIRED
                )
            ),
            Set.of(
                Ability.ZOMBIE_RESPECT,
                Ability.CALL_STORM,
                Ability.TELEPORT,
                Ability.BAT_SWARM
            ),
            Set.of(BloodPower.CALL_STORM, BloodPower.TELEPORT, BloodPower.BAT_SWARM)
        )
    );
    private static final ProgressionCatalog<Ability, Quest> CATALOG = ProgressionCatalog.create(
        QUESTS,
        Ability.class
    );
    private static final ProgressionCatalog.LevelUnlocks<BloodPower> BLOOD_POWER_UNLOCKS =
        ProgressionCatalog.unlocks(
            QUESTS,
            BloodPower.class,
            Quest::targetLevel,
            Quest::bloodPowers
        );

    private VampireProgressionRules() {
    }

    public static List<Quest> quests() {
        return CATALOG.quests();
    }

    public static Optional<Quest> activeQuest(final int currentLevel) {
        return CATALOG.activeQuest(currentLevel);
    }

    public static Optional<Quest> questForTargetLevel(final int targetLevel) {
        return CATALOG.questForTargetLevel(targetLevel);
    }

    public static int bloodCapacityAt(final int level) {
        return VampireBloodCapacityRules.capacity(Math.clamp(level, 0, MAX_LEVEL));
    }

    public static Set<Ability> abilitiesAt(final int level) {
        return CATALOG.abilitiesAt(level);
    }

    public static Set<Ability> abilitiesAt(final int level, final AbilityMode mode) {
        Objects.requireNonNull(mode, "mode");
        return ProgressionCollections.immutableEnumSet(
            Ability.class,
            abilitiesAt(level).stream().filter(ability -> ability.mode() == mode).toList()
        );
    }

    public static int minimumLevel(final Ability ability) {
        return CATALOG.minimumLevel(ability);
    }

    public static Set<BloodPower> bloodPowersAt(final int level) {
        return BLOOD_POWER_UNLOCKS.at(level);
    }

    public static Evaluation evaluate(final Progress progress) {
        Objects.requireNonNull(progress, "progress");
        final Optional<Quest> activeQuest = activeQuest(progress.level());
        if (activeQuest.isEmpty()) {
            return new Evaluation(Optional.empty(), List.of(), false, Diagnostic.PATH_COMPLETE);
        }

        final Quest quest = activeQuest.orElseThrow();
        final List<RequirementStatus> statuses = quest.requirements().stream()
            .map(requirement -> {
                final int count = progress.count(requirement.metric());
                return new RequirementStatus(
                    requirement,
                    Math.min(count, requirement.required()),
                    Math.max(0, requirement.required() - count)
                );
            })
            .toList();
        final Optional<RequirementStatus> missing = statuses.stream()
            .filter(status -> !status.satisfied())
            .findFirst();
        return new Evaluation(
            Optional.of(quest),
            statuses,
            missing.isEmpty(),
            missing.map(status -> status.requirement().diagnostic()).orElse(Diagnostic.READY_TO_ADVANCE)
        );
    }

    public static ObservationResult observe(final Progress progress, final Metric metric, final int amount) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(metric, "metric");
        final Evaluation before = evaluate(progress);
        if (amount <= 0) {
            return unchangedObservation(progress, Diagnostic.INVALID_AMOUNT, before);
        }
        if (before.quest().isEmpty()) {
            return unchangedObservation(progress, Diagnostic.PATH_COMPLETE, before);
        }

        final Optional<Requirement> relevant = requirementFor(before, metric);
        if (relevant.isEmpty()) {
            return unchangedObservation(progress, Diagnostic.IRRELEVANT_OBSERVATION, before);
        }

        final Requirement requirement = relevant.orElseThrow();
        if (requirement.kind() == RequirementKind.GAUGE || requirement.kind() == RequirementKind.UNIQUE) {
            return unchangedObservation(progress, Diagnostic.WRONG_OBSERVATION_MODE, before);
        }
        if (metric == Metric.CONSECUTIVE_FULL_NIGHTS_SURVIVED) {
            final Optional<Diagnostic> prerequisite = missingManualPrerequisite(progress, 3);
            if (prerequisite.isPresent()) {
                return unchangedObservation(progress, prerequisite.orElseThrow(), before);
            }
        }

        final int current = progress.count(metric);
        final int updated = (int) Math.min(requirement.required(), (long) current + amount);
        if (updated == current) {
            return unchangedObservation(progress, before.diagnostic(), before);
        }

        final Progress after = progress.withCounter(metric, updated);
        return changedObservation(after);
    }

    public static ObservationResult observeValue(final Progress progress, final Metric metric, final int value) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(metric, "metric");
        final Evaluation before = evaluate(progress);
        if (value < 0) {
            return unchangedObservation(progress, Diagnostic.INVALID_AMOUNT, before);
        }
        if (before.quest().isEmpty()) {
            return unchangedObservation(progress, Diagnostic.PATH_COMPLETE, before);
        }

        final Optional<Requirement> relevant = requirementFor(before, metric);
        if (relevant.isEmpty()) {
            return unchangedObservation(progress, Diagnostic.IRRELEVANT_OBSERVATION, before);
        }
        if (relevant.orElseThrow().kind() != RequirementKind.GAUGE) {
            return unchangedObservation(progress, Diagnostic.WRONG_OBSERVATION_MODE, before);
        }

        final int updated = metric == Metric.TORN_PAGES_INSERTED
            ? Math.clamp(value, 0, MAX_TORN_PAGES)
            : value;
        if (metric == Metric.TORN_PAGES_INSERTED && updated < progress.count(metric)) {
            return unchangedObservation(progress, Diagnostic.TORN_PAGES_CANNOT_BE_REMOVED, before);
        }
        if (updated == progress.count(metric)) {
            return unchangedObservation(progress, before.diagnostic(), before);
        }

        final Progress after = progress.withCounter(metric, updated);
        return changedObservation(after);
    }

    public static ObservationResult observeUnique(
        final Progress progress,
        final Metric metric,
        final String identity
    ) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(metric, "metric");
        final Evaluation before = evaluate(progress);
        if (before.quest().isEmpty()) {
            return unchangedObservation(progress, Diagnostic.PATH_COMPLETE, before);
        }

        final Optional<Requirement> relevant = requirementFor(before, metric);
        if (relevant.isEmpty()) {
            return unchangedObservation(progress, Diagnostic.IRRELEVANT_OBSERVATION, before);
        }
        if (relevant.orElseThrow().kind() != RequirementKind.UNIQUE) {
            return unchangedObservation(progress, Diagnostic.WRONG_OBSERVATION_MODE, before);
        }
        if (identity == null || identity.isBlank()) {
            return unchangedObservation(progress, Diagnostic.INVALID_IDENTITY, before);
        }

        final String normalizedIdentity = identity.strip().toLowerCase(Locale.ROOT);
        final Set<String> observed = progress.identities(metric);
        if (observed.contains(normalizedIdentity)) {
            return unchangedObservation(progress, Diagnostic.IDENTITY_ALREADY_RECORDED, before);
        }
        if (observed.size() >= relevant.orElseThrow().required()) {
            return unchangedObservation(progress, before.diagnostic(), before);
        }

        final LinkedHashSet<String> updated = new LinkedHashSet<>(observed);
        updated.add(normalizedIdentity);
        return changedObservation(progress.withIdentities(metric, updated));
    }

    public static ObservationResult recordDeath(final Progress progress) {
        Objects.requireNonNull(progress, "progress");
        final Evaluation before = evaluate(progress);
        if (before.quest().isEmpty()) {
            return unchangedObservation(progress, Diagnostic.PATH_COMPLETE, before);
        }
        if (before.quest().orElseThrow().targetLevel() != 4
            || progress.count(Metric.CONSECUTIVE_FULL_NIGHTS_SURVIVED) == 0) {
            return unchangedObservation(progress, Diagnostic.IRRELEVANT_OBSERVATION, before);
        }

        final Progress after = progress.withoutCounter(Metric.CONSECUTIVE_FULL_NIGHTS_SURVIVED);
        return new ObservationResult(after, Diagnostic.NIGHT_STREAK_BROKEN, true, evaluate(after));
    }

    public static Transition attemptAdvance(final Progress progress) {
        Objects.requireNonNull(progress, "progress");
        final Evaluation evaluation = evaluate(progress);
        if (evaluation.quest().isEmpty()) {
            return Transition.unchanged(progress, Diagnostic.PATH_COMPLETE, evaluation);
        }
        if (!evaluation.ready()) {
            return Transition.unchanged(progress, evaluation.diagnostic(), evaluation);
        }

        final Quest quest = evaluation.quest().orElseThrow();
        final Map<Metric, Integer> persistentCounters = ProgressionCollections.immutableEnumMap(
            Metric.class,
            progress.counters(),
            (metric, count) -> metric.persistent()
        );
        final Progress after = new Progress(quest.targetLevel(), persistentCounters, Map.of());
        final EnumMap<Metric, Integer> consumedOfferings = new EnumMap<>(Metric.class);
        quest.requirements().stream()
            .filter(requirement -> requirement.kind() == RequirementKind.OFFERING)
            .forEach(requirement -> consumedOfferings.put(requirement.metric(), requirement.required()));
        return new Transition(
            progress,
            after,
            quest.targetLevel() == MAX_LEVEL ? Diagnostic.PATH_COMPLETED : Diagnostic.LEVEL_ADVANCED,
            true,
            quest.abilities(),
            quest.bloodPowers(),
            consumedOfferings,
            evaluation
        );
    }

    public static PowerTransition chargeBloodPower(
        final int vampireLevel,
        final ChargedBloodPower current,
        final BloodPower power,
        final ChargeIngredient ingredient,
        final boolean bloodCrucibleFull
    ) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(power, "power");
        Objects.requireNonNull(ingredient, "ingredient");
        if (vampireLevel < MAX_LEVEL) {
            return PowerTransition.unchanged(current, Diagnostic.BLOOD_POWER_LOCKED);
        }
        if (!bloodCrucibleFull) {
            return PowerTransition.unchanged(current, Diagnostic.BLOOD_CRUCIBLE_NOT_FULL);
        }
        if (power.ingredient() != ingredient) {
            return PowerTransition.unchanged(current, Diagnostic.WRONG_CHARGING_INGREDIENT);
        }

        final ChargedBloodPower after = ChargedBloodPower.charged(power);
        final boolean replaced = current.power().isPresent() && current.power().orElseThrow() != power;
        return new PowerTransition(
            current,
            after,
            replaced ? Diagnostic.BLOOD_POWER_REPLACED : Diagnostic.BLOOD_POWER_CHARGED,
            !current.equals(after)
        );
    }

    public static PowerTransition useBloodPower(
        final int vampireLevel,
        final ChargedBloodPower current
    ) {
        Objects.requireNonNull(current, "current");
        if (vampireLevel < MAX_LEVEL) {
            return PowerTransition.unchanged(current, Diagnostic.BLOOD_POWER_LOCKED);
        }
        if (current.power().isEmpty() || current.charges() == 0) {
            return PowerTransition.unchanged(current, Diagnostic.BLOOD_POWER_EMPTY);
        }

        final ChargedBloodPower after = current.charges() == 1
            ? ChargedBloodPower.empty()
            : new ChargedBloodPower(current.power(), current.charges() - 1);
        return new PowerTransition(current, after, Diagnostic.BLOOD_POWER_USED, true);
    }

    private static Optional<Requirement> requirementFor(final Evaluation evaluation, final Metric metric) {
        return evaluation.quest().orElseThrow().requirements().stream()
            .filter(requirement -> requirement.metric() == metric)
            .findFirst();
    }

    private static Optional<Diagnostic> missingManualPrerequisite(final Progress progress, final int pages) {
        if (progress.count(Metric.OBSERVATIONS_MANUAL_OWNED) < 1) {
            return Optional.of(Diagnostic.OBSERVATIONS_MANUAL_REQUIRED);
        }
        if (progress.count(Metric.TORN_PAGES_INSERTED) < pages) {
            return Optional.of(Diagnostic.TORN_PAGES_REQUIRED);
        }
        return Optional.empty();
    }

    private static ObservationResult changedObservation(final Progress progress) {
        final Evaluation evaluation = evaluate(progress);
        return new ObservationResult(
            progress,
            evaluation.ready() ? Diagnostic.READY_TO_ADVANCE : Diagnostic.PROGRESS_RECORDED,
            true,
            evaluation
        );
    }

    private static ObservationResult unchangedObservation(
        final Progress progress,
        final Diagnostic diagnostic,
        final Evaluation evaluation
    ) {
        return new ObservationResult(progress, diagnostic, false, evaluation);
    }

    private static List<Requirement> withManual(final int pages, final Requirement... requirements) {
        return Stream.concat(
            Stream.of(
                requirement(
                    Metric.OBSERVATIONS_MANUAL_OWNED,
                    1,
                    RequirementKind.MILESTONE,
                    Diagnostic.OBSERVATIONS_MANUAL_REQUIRED
                ),
                requirement(
                    Metric.TORN_PAGES_INSERTED,
                    pages,
                    RequirementKind.GAUGE,
                    Diagnostic.TORN_PAGES_REQUIRED
                )
            ),
            Arrays.stream(requirements)
        ).toList();
    }

    private static Quest quest(
        final int targetLevel,
        final String id,
        final String title,
        final String description,
        final int bloodCapacity,
        final List<Requirement> requirements,
        final Set<Ability> abilities
    ) {
        return quest(targetLevel, id, title, description, bloodCapacity, requirements, abilities, Set.of());
    }

    private static Quest quest(
        final int targetLevel,
        final String id,
        final String title,
        final String description,
        final int bloodCapacity,
        final List<Requirement> requirements,
        final Set<Ability> abilities,
        final Set<BloodPower> bloodPowers
    ) {
        return new Quest(
            targetLevel,
            id,
            title,
            description,
            bloodCapacity,
            requirements,
            abilities,
            bloodPowers
        );
    }

    private static Requirement requirement(
        final Metric metric,
        final int required,
        final RequirementKind kind,
        final Diagnostic diagnostic
    ) {
        return new Requirement(metric, required, kind, diagnostic);
    }

    public enum AbilityMode {
        ACTIVE,
        PASSIVE,
        CONTEXTUAL,
        CHARGED
    }

    public enum Ability {
        BLOOD_SUSTENANCE(AbilityMode.PASSIVE),
        DRINK_BLOOD(AbilityMode.ACTIVE),
        SENSE_BLOOD(AbilityMode.PASSIVE),
        POISON_AND_DISEASE_IMMUNITY(AbilityMode.PASSIVE),
        SUPERNATURAL_RESILIENCE(AbilityMode.PASSIVE),
        TRANSFIX(AbilityMode.ACTIVE),
        NIGHT_VISION(AbilityMode.ACTIVE),
        KNOCKBACK(AbilityMode.PASSIVE),
        SPEED(AbilityMode.ACTIVE),
        RESIST_SUN(AbilityMode.PASSIVE),
        SMASH_STONE(AbilityMode.CONTEXTUAL),
        BATSWARM_FORM(AbilityMode.ACTIVE),
        MESMERIZE(AbilityMode.ACTIVE),
        CREATE_VAMPIRES(AbilityMode.ACTIVE),
        ZOMBIE_RESPECT(AbilityMode.PASSIVE),
        CALL_STORM(AbilityMode.CHARGED),
        TELEPORT(AbilityMode.CHARGED),
        BAT_SWARM(AbilityMode.CHARGED);

        private final AbilityMode mode;

        Ability(final AbilityMode mode) {
            this.mode = mode;
        }

        public AbilityMode mode() {
            return mode;
        }
    }

    public enum BloodPower {
        CALL_STORM(ChargeIngredient.WATER_ARTICHOKE_GLOBE, Ability.CALL_STORM),
        TELEPORT(ChargeIngredient.BONE, Ability.TELEPORT),
        BAT_SWARM(ChargeIngredient.WOOL_OF_BAT, Ability.BAT_SWARM);

        private final ChargeIngredient ingredient;
        private final Ability ability;

        BloodPower(final ChargeIngredient ingredient, final Ability ability) {
            this.ingredient = ingredient;
            this.ability = ability;
        }

        public ChargeIngredient ingredient() {
            return ingredient;
        }

        public Ability ability() {
            return ability;
        }
    }

    public enum ChargeIngredient {
        WATER_ARTICHOKE_GLOBE,
        BONE,
        WOOL_OF_BAT
    }

    public enum Metric {
        BLOOD_AUDIENCE_COMPLETED(false),
        OBSERVATIONS_MANUAL_OWNED(true),
        TORN_PAGES_INSERTED(true),
        BLOOD_STORED(false),
        DISTINCT_VILLAGERS_HALF_DRAINED(false),
        CONSECUTIVE_FULL_NIGHTS_SURVIVED(false),
        NIGHTTIME_SUN_GRENADE_BURNS(false),
        BLAZES_DEFEATED(false),
        NAAMAH_AUDIENCE_COMPLETED(false),
        NAAMAH_DEFEATED(false),
        POPPY_OFFERED_TO_NAAMAH(false),
        DISTINCT_VILLAGES_REACHED_IN_BATSWARM_FORM(false),
        DISTINCT_CAGED_VILLAGERS_HALF_DRAINED(false),
        CREATION_TARGET_FULLY_DRAINED(false),
        CREATION_TARGET_MESMERIZED(false),
        OWN_BLOOD_GOBLET_OFFERED(false),
        COFFIN_WITHIN_FOUR_BLOCKS(false),
        VAMPIRE_CREATED_NEAR_COFFIN(false);

        private final boolean persistent;

        Metric(final boolean persistent) {
            this.persistent = persistent;
        }

        public boolean persistent() {
            return persistent;
        }
    }

    public enum RequirementKind {
        MILESTONE,
        COUNTER,
        GAUGE,
        UNIQUE,
        OFFERING
    }

    public enum Diagnostic {
        BLOOD_AUDIENCE_REQUIRED("blood_audience_required"),
        OBSERVATIONS_MANUAL_REQUIRED("observations_manual_required"),
        TORN_PAGES_REQUIRED("torn_pages_required"),
        BLOOD_RESERVE_NOT_FULL("blood_reserve_not_full"),
        FIVE_DISTINCT_VILLAGERS_REQUIRED("five_distinct_villagers_required"),
        FOUR_CONSECUTIVE_NIGHTS_REQUIRED("four_consecutive_nights_required"),
        TEN_SUN_GRENADE_BURNS_REQUIRED("ten_sun_grenade_burns_required"),
        TWENTY_BLAZES_REQUIRED("twenty_blazes_required"),
        NAAMAH_AUDIENCE_REQUIRED("naamah_audience_required"),
        NAAMAH_MUST_BE_DEFEATED("naamah_must_be_defeated"),
        POPPY_OFFERING_REQUIRED("poppy_offering_required"),
        FOUR_DISTINCT_VILLAGES_REQUIRED("four_distinct_villages_required"),
        FIVE_CAGED_VILLAGERS_REQUIRED("five_caged_villagers_required"),
        FULLY_DRAINED_TARGET_REQUIRED("fully_drained_target_required"),
        MESMERIZED_TARGET_REQUIRED("mesmerized_target_required"),
        OWN_BLOOD_GOBLET_REQUIRED("own_blood_goblet_required"),
        NEARBY_COFFIN_REQUIRED("nearby_coffin_required"),
        CREATED_VAMPIRE_REQUIRED("created_vampire_required"),
        INVALID_AMOUNT("invalid_amount"),
        INVALID_IDENTITY("invalid_identity"),
        IDENTITY_ALREADY_RECORDED("identity_already_recorded"),
        TORN_PAGES_CANNOT_BE_REMOVED("torn_pages_cannot_be_removed"),
        WRONG_OBSERVATION_MODE("wrong_observation_mode"),
        IRRELEVANT_OBSERVATION("irrelevant_observation"),
        NIGHT_STREAK_BROKEN("night_streak_broken"),
        PROGRESS_RECORDED("progress_recorded"),
        READY_TO_ADVANCE("ready_to_advance"),
        LEVEL_ADVANCED("level_advanced"),
        PATH_COMPLETED("path_completed"),
        PATH_COMPLETE("path_complete"),
        BLOOD_POWER_LOCKED("blood_power_locked"),
        BLOOD_CRUCIBLE_NOT_FULL("blood_crucible_not_full"),
        WRONG_CHARGING_INGREDIENT("wrong_charging_ingredient"),
        BLOOD_POWER_CHARGED("blood_power_charged"),
        BLOOD_POWER_REPLACED("blood_power_replaced"),
        BLOOD_POWER_EMPTY("blood_power_empty"),
        BLOOD_POWER_USED("blood_power_used");

        private final String id;

        Diagnostic(final String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public String messageKey() {
            return "message.warlockery.vampire_progression." + id;
        }
    }

    public record Progress(
        int level,
        Map<Metric, Integer> counters,
        Map<Metric, Set<String>> uniqueObservations
    ) {
        public Progress {
            level = Math.clamp(level, 0, MAX_LEVEL);
            Objects.requireNonNull(uniqueObservations, "uniqueObservations");
            counters = ProgressionCollections.immutablePositiveIntMap(Metric.class, counters);

            final EnumMap<Metric, Set<String>> normalizedUnique = new EnumMap<>(Metric.class);
            uniqueObservations.forEach((metric, identities) -> {
                Objects.requireNonNull(metric, "metric");
                Objects.requireNonNull(identities, "identities");
                final LinkedHashSet<String> normalizedIdentities = new LinkedHashSet<>();
                identities.forEach(identity -> {
                    Objects.requireNonNull(identity, "identity");
                    if (!identity.isBlank()) {
                        normalizedIdentities.add(identity.strip().toLowerCase(Locale.ROOT));
                    }
                });
                if (!normalizedIdentities.isEmpty()) {
                    normalizedUnique.put(metric, Set.copyOf(normalizedIdentities));
                }
            });
            uniqueObservations = ProgressionCollections.immutableEnumMap(Metric.class, normalizedUnique);
        }

        public Progress(final int level, final Map<Metric, Integer> counters) {
            this(level, counters, Map.of());
        }

        public static Progress atLevel(final int level) {
            return new Progress(level, Map.of(), Map.of());
        }

        public int count(final Metric metric) {
            Objects.requireNonNull(metric, "metric");
            final Set<String> identities = uniqueObservations.get(metric);
            return identities == null ? counters.getOrDefault(metric, 0) : identities.size();
        }

        public Set<String> identities(final Metric metric) {
            return uniqueObservations.getOrDefault(Objects.requireNonNull(metric, "metric"), Set.of());
        }

        private Progress withCounter(final Metric metric, final int count) {
            final EnumMap<Metric, Integer> updated = new EnumMap<>(Metric.class);
            updated.putAll(counters);
            if (count > 0) {
                updated.put(metric, count);
            } else {
                updated.remove(metric);
            }
            return new Progress(level, updated, uniqueObservations);
        }

        private Progress withoutCounter(final Metric metric) {
            return withCounter(metric, 0);
        }

        private Progress withIdentities(final Metric metric, final Set<String> identities) {
            final EnumMap<Metric, Set<String>> updated = new EnumMap<>(Metric.class);
            updated.putAll(uniqueObservations);
            updated.put(metric, identities);
            return new Progress(level, counters, updated);
        }
    }

    public record Quest(
        int targetLevel,
        String id,
        String title,
        String description,
        int bloodCapacity,
        List<Requirement> requirements,
        Set<Ability> abilities,
        Set<BloodPower> bloodPowers
    ) implements ProgressionQuest<Ability> {
        public Quest {
            if (targetLevel < 1 || targetLevel > MAX_LEVEL) {
                throw new IllegalArgumentException("Target level must be between 1 and " + MAX_LEVEL);
            }
            id = Objects.requireNonNull(id, "id");
            title = Objects.requireNonNull(title, "title");
            description = Objects.requireNonNull(description, "description");
            if (bloodCapacity != VampireProgressionRules.bloodCapacityAt(targetLevel)) {
                throw new IllegalArgumentException("Blood capacity must match the target level");
            }
            requirements = List.copyOf(requirements);
            abilities = ProgressionCollections.immutableEnumSet(Ability.class, abilities);
            bloodPowers = ProgressionCollections.immutableEnumSet(BloodPower.class, bloodPowers);
            if (id.isBlank() || title.isBlank() || description.isBlank() || requirements.isEmpty()) {
                throw new IllegalArgumentException("Quest identity, prose, and requirements must be present");
            }
        }
    }

    public record Requirement(
        Metric metric,
        int required,
        RequirementKind kind,
        Diagnostic diagnostic
    ) {
        public Requirement {
            Objects.requireNonNull(metric, "metric");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(diagnostic, "diagnostic");
            if (required < 1) {
                throw new IllegalArgumentException("Required progress must be positive");
            }
        }
    }

    public record RequirementStatus(Requirement requirement, int current, int remaining) {
        public RequirementStatus {
            Objects.requireNonNull(requirement, "requirement");
            if (current < 0 || remaining < 0 || current + remaining != requirement.required()) {
                throw new IllegalArgumentException("Requirement status must account for the full requirement");
            }
        }

        public boolean satisfied() {
            return remaining == 0;
        }
    }

    public record Evaluation(
        Optional<Quest> quest,
        List<RequirementStatus> requirements,
        boolean ready,
        Diagnostic diagnostic
    ) {
        public Evaluation {
            quest = Objects.requireNonNull(quest, "quest");
            requirements = List.copyOf(requirements);
            Objects.requireNonNull(diagnostic, "diagnostic");
        }

        public int satisfiedRequirements() {
            return (int) requirements.stream().filter(RequirementStatus::satisfied).count();
        }

        public int totalRequirements() {
            return requirements.size();
        }
    }

    public record ObservationResult(
        Progress progress,
        Diagnostic diagnostic,
        boolean changed,
        Evaluation evaluation
    ) {
        public ObservationResult {
            Objects.requireNonNull(progress, "progress");
            Objects.requireNonNull(diagnostic, "diagnostic");
            Objects.requireNonNull(evaluation, "evaluation");
        }
    }

    public record Transition(
        Progress before,
        Progress after,
        Diagnostic diagnostic,
        boolean advanced,
        Set<Ability> unlockedAbilities,
        Set<BloodPower> unlockedBloodPowers,
        Map<Metric, Integer> consumedOfferings,
        Evaluation evaluation
    ) {
        public Transition {
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
            Objects.requireNonNull(diagnostic, "diagnostic");
            unlockedAbilities = ProgressionCollections.immutableEnumSet(Ability.class, unlockedAbilities);
            unlockedBloodPowers = ProgressionCollections.immutableEnumSet(BloodPower.class, unlockedBloodPowers);
            consumedOfferings = ProgressionCollections.immutableEnumMap(Metric.class, consumedOfferings);
            Objects.requireNonNull(evaluation, "evaluation");
        }

        private static Transition unchanged(
            final Progress progress,
            final Diagnostic diagnostic,
            final Evaluation evaluation
        ) {
            return new Transition(
                progress,
                progress,
                diagnostic,
                false,
                Set.of(),
                Set.of(),
                Map.of(),
                evaluation
            );
        }
    }

    public record ChargedBloodPower(Optional<BloodPower> power, int charges) {
        public ChargedBloodPower {
            power = Objects.requireNonNull(power, "power");
            if (power.isEmpty() && charges != 0) {
                throw new IllegalArgumentException("An empty blood power cannot hold charges");
            }
            if (power.isPresent() && (charges < 1 || charges > BLOOD_POWER_CHARGES_PER_INFUSION)) {
                throw new IllegalArgumentException("A charged blood power must hold between one and five charges");
            }
        }

        public static ChargedBloodPower empty() {
            return new ChargedBloodPower(Optional.empty(), 0);
        }

        public static ChargedBloodPower charged(final BloodPower power) {
            return new ChargedBloodPower(
                Optional.of(Objects.requireNonNull(power, "power")),
                BLOOD_POWER_CHARGES_PER_INFUSION
            );
        }
    }

    public record PowerTransition(
        ChargedBloodPower before,
        ChargedBloodPower after,
        Diagnostic diagnostic,
        boolean changed
    ) {
        public PowerTransition {
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
            Objects.requireNonNull(diagnostic, "diagnostic");
        }

        private static PowerTransition unchanged(
            final ChargedBloodPower power,
            final Diagnostic diagnostic
        ) {
            return new PowerTransition(power, power, diagnostic, false);
        }
    }

}
