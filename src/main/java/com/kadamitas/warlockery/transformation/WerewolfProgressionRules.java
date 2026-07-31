package com.kadamitas.warlockery.transformation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class WerewolfProgressionRules {
    public static final int MAX_LEVEL = 10;

    private static final List<Quest> QUESTS = List.of(
        quest(
            1,
            "first_moon",
            "The First Moon",
            "Accept the wolf's curse beneath a full moon, then return when the beast stirs within you.",
            List.of(requirement(Metric.CURSE_ACCEPTED, 1, RequirementKind.MILESTONE, Diagnostic.CURSE_REQUIRED)),
            Set.of(
                Ability.FULL_MOON_WOLF_FORM,
                Ability.POISON_AND_DISEASE_IMMUNITY,
                Ability.SUPERNATURAL_RESILIENCE
            )
        ),
        quest(
            2,
            "gilded_leash",
            "The Gilded Leash",
            "Lay three gold ingots upon the wolf altar. Their sunlit gleam teaches the beast to heed the Moon Charm.",
            List.of(requirement(
                Metric.GOLD_INGOTS_OFFERED,
                3,
                RequirementKind.OFFERING,
                Diagnostic.GOLD_INGOTS_REQUIRED
            )),
            Set.of(Ability.CONTROLLED_WOLF_FORM, Ability.MUTTON_HARVEST),
            Set.of(),
            Set.of(Reward.MOON_CHARM)
        ),
        quest(
            3,
            "shepherds_reckoning",
            "The Shepherd's Reckoning",
            "Hunt sheep while wearing the wolf's shape, then offer thirty pieces of their raw mutton at the altar.",
            List.of(requirement(
                Metric.RAW_MUTTON_OFFERED_FROM_WOLF_HUNTS,
                30,
                RequirementKind.OFFERING,
                Diagnostic.RAW_MUTTON_REQUIRED
            )),
            Set.of(Ability.INSTANT_EARTH_DIGGING, Ability.BONE_FINDING)
        ),
        quest(
            4,
            "red_feast",
            "The Red Feast",
            "Offer ten tongues of dog. The altar will teach your transformed body to draw nourishment from a fallen foe.",
            List.of(requirement(
                Metric.TONGUES_OF_DOG_OFFERED,
                10,
                RequirementKind.OFFERING,
                Diagnostic.TONGUES_OF_DOG_REQUIRED
            )),
            Set.of(Ability.FEAST)
        ),
        quest(
            5,
            "hunt_the_huntsman",
            "Hunt the Huntsman",
            "Sound the altar's Horn of the Hunt and defeat the Horned Huntsman it calls from the wild dark.",
            List.of(requirement(
                Metric.HORNED_HUNTSMEN_DEFEATED,
                1,
                RequirementKind.MILESTONE,
                Diagnostic.HORNED_HUNTSMAN_REQUIRED
            )),
            Set.of(Ability.WOLFMAN_FORM),
            Set.of(Reward.HORN_OF_THE_HUNT),
            Set.of()
        ),
        quest(
            6,
            "running_claw",
            "The Running Claw",
            "Defeat ten hostile creatures with airborne killing blows while in wolf or wolfman form, then return to the altar.",
            List.of(requirement(
                Metric.HOSTILES_DEFEATED_WHILE_AIRBORNE,
                10,
                RequirementKind.COUNTER,
                Diagnostic.AIRBORNE_HUNTS_REQUIRED
            )),
            Set.of(Ability.CHARGE_ATTACK)
        ),
        quest(
            7,
            "sixteen_echoes",
            "Sixteen Echoes",
            "Let your howl be heard in sixteen distinct regions so the night learns the reach of your voice.",
            List.of(requirement(
                Metric.DISTINCT_HOWL_REGIONS,
                16,
                RequirementKind.COUNTER,
                Diagnostic.HOWL_REGIONS_REQUIRED
            )),
            Set.of(Ability.STUN_HOWL)
        ),
        quest(
            8,
            "call_the_pack",
            "Call the Pack",
            "In wolf form, befriend six wild wolves with an empty hand and prove that your call can gather a pack.",
            List.of(requirement(
                Metric.WOLVES_BEFRIENDED_IN_WOLF_FORM,
                6,
                RequirementKind.COUNTER,
                Diagnostic.WOLF_ALLIES_REQUIRED
            )),
            Set.of(Ability.PACK_HOWL)
        ),
        quest(
            9,
            "nether_rending",
            "Nether Rending",
            "Defeat thirty zombified piglins in the Nether while transformed. Return with claws tempered by hostile armor.",
            List.of(requirement(
                Metric.ZOMBIFIED_PIGLINS_DEFEATED_IN_NETHER,
                30,
                RequirementKind.COUNTER,
                Diagnostic.ZOMBIFIED_PIGLINS_REQUIRED
            )),
            Set.of(Ability.ARMOR_RENDING)
        ),
        quest(
            10,
            "cursed_bite",
            "The Cursed Bite",
            "While transformed, defeat one villager or a consenting allied player and surrender the last restraint of the beast.",
            List.of(requirement(
                Metric.TRUSTED_PREY_DEFEATED_WHILE_TRANSFORMED,
                1,
                RequirementKind.MILESTONE,
                Diagnostic.TRUSTED_PREY_REQUIRED
            )),
            Set.of(Ability.SPREAD_CURSE)
        )
    );
    private static final ProgressionCatalog<Ability, Quest> CATALOG = ProgressionCatalog.create(
        QUESTS,
        Ability.class
    );

    private WerewolfProgressionRules() {
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

    public static Set<Ability> abilitiesAt(final int level) {
        return CATALOG.abilitiesAt(level);
    }

    public static int minimumLevel(final Ability ability) {
        return CATALOG.minimumLevel(ability);
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
            return new ObservationResult(progress, Diagnostic.INVALID_AMOUNT, false, before);
        }
        if (before.quest().isEmpty()) {
            return new ObservationResult(progress, Diagnostic.PATH_COMPLETE, false, before);
        }

        final Optional<Requirement> relevant = before.quest().orElseThrow().requirements().stream()
            .filter(requirement -> requirement.metric() == metric)
            .findFirst();
        if (relevant.isEmpty()) {
            return new ObservationResult(progress, Diagnostic.IRRELEVANT_OBSERVATION, false, before);
        }

        final Requirement requirement = relevant.orElseThrow();
        final int current = progress.count(metric);
        final int updated = (int) Math.min(requirement.required(), (long) current + amount);
        if (updated == current) {
            return new ObservationResult(progress, before.diagnostic(), false, before);
        }

        final EnumMap<Metric, Integer> counters = new EnumMap<>(Metric.class);
        counters.putAll(progress.counters());
        counters.put(metric, updated);
        final Progress after = new Progress(progress.level(), counters);
        final Evaluation evaluation = evaluate(after);
        return new ObservationResult(
            after,
            evaluation.ready() ? Diagnostic.READY_TO_ADVANCE : Diagnostic.PROGRESS_RECORDED,
            true,
            evaluation
        );
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
        final Progress after = new Progress(quest.targetLevel(), Map.of());
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
            quest.completionRewards(),
            consumedOfferings,
            evaluation
        );
    }

    private static Quest quest(
        final int targetLevel,
        final String id,
        final String title,
        final String description,
        final List<Requirement> requirements,
        final Set<Ability> abilities
    ) {
        return quest(targetLevel, id, title, description, requirements, abilities, Set.of(), Set.of());
    }

    private static Quest quest(
        final int targetLevel,
        final String id,
        final String title,
        final String description,
        final List<Requirement> requirements,
        final Set<Ability> abilities,
        final Set<Reward> preparationRewards,
        final Set<Reward> completionRewards
    ) {
        return new Quest(
            targetLevel,
            id,
            title,
            description,
            requirements,
            abilities,
            preparationRewards,
            completionRewards
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

    public enum Ability {
        FULL_MOON_WOLF_FORM,
        POISON_AND_DISEASE_IMMUNITY,
        SUPERNATURAL_RESILIENCE,
        CONTROLLED_WOLF_FORM,
        MUTTON_HARVEST,
        INSTANT_EARTH_DIGGING,
        BONE_FINDING,
        FEAST,
        WOLFMAN_FORM,
        CHARGE_ATTACK,
        STUN_HOWL,
        PACK_HOWL,
        ARMOR_RENDING,
        SPREAD_CURSE
    }

    public enum Reward {
        MOON_CHARM,
        HORN_OF_THE_HUNT
    }

    public enum Metric {
        CURSE_ACCEPTED,
        GOLD_INGOTS_OFFERED,
        RAW_MUTTON_OFFERED_FROM_WOLF_HUNTS,
        TONGUES_OF_DOG_OFFERED,
        HORNED_HUNTSMEN_DEFEATED,
        HOSTILES_DEFEATED_WHILE_AIRBORNE,
        DISTINCT_HOWL_REGIONS,
        WOLVES_BEFRIENDED_IN_WOLF_FORM,
        ZOMBIFIED_PIGLINS_DEFEATED_IN_NETHER,
        TRUSTED_PREY_DEFEATED_WHILE_TRANSFORMED
    }

    public enum RequirementKind {
        MILESTONE,
        COUNTER,
        OFFERING
    }

    public enum Diagnostic {
        CURSE_REQUIRED("curse_required"),
        GOLD_INGOTS_REQUIRED("gold_ingots_required"),
        RAW_MUTTON_REQUIRED("raw_mutton_required"),
        TONGUES_OF_DOG_REQUIRED("tongues_of_dog_required"),
        HORNED_HUNTSMAN_REQUIRED("horned_huntsman_required"),
        AIRBORNE_HUNTS_REQUIRED("airborne_hunts_required"),
        HOWL_REGIONS_REQUIRED("howl_regions_required"),
        WOLF_ALLIES_REQUIRED("wolf_allies_required"),
        ZOMBIFIED_PIGLINS_REQUIRED("zombified_piglins_required"),
        TRUSTED_PREY_REQUIRED("trusted_prey_required"),
        IRRELEVANT_OBSERVATION("irrelevant_observation"),
        INVALID_AMOUNT("invalid_amount"),
        PROGRESS_RECORDED("progress_recorded"),
        READY_TO_ADVANCE("ready_to_advance"),
        LEVEL_ADVANCED("level_advanced"),
        PATH_COMPLETED("path_completed"),
        PATH_COMPLETE("path_complete");

        private final String id;

        Diagnostic(final String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public String messageKey() {
            return "message.warlockery.werewolf_progression." + id;
        }
    }

    public record Progress(int level, Map<Metric, Integer> counters) {
        public Progress {
            level = Math.clamp(level, 0, MAX_LEVEL);
            counters = ProgressionCollections.immutablePositiveIntMap(Metric.class, counters);
        }

        public static Progress atLevel(final int level) {
            return new Progress(level, Map.of());
        }

        public int count(final Metric metric) {
            return counters.getOrDefault(Objects.requireNonNull(metric, "metric"), 0);
        }
    }

    public record Quest(
        int targetLevel,
        String id,
        String title,
        String description,
        List<Requirement> requirements,
        Set<Ability> abilities,
        Set<Reward> preparationRewards,
        Set<Reward> completionRewards
    ) implements ProgressionQuest<Ability> {
        public Quest {
            if (targetLevel < 1 || targetLevel > MAX_LEVEL) {
                throw new IllegalArgumentException("Target level must be between 1 and " + MAX_LEVEL);
            }
            id = Objects.requireNonNull(id, "id");
            title = Objects.requireNonNull(title, "title");
            description = Objects.requireNonNull(description, "description");
            requirements = List.copyOf(requirements);
            abilities = ProgressionCollections.immutableEnumSet(Ability.class, abilities);
            preparationRewards = ProgressionCollections.immutableEnumSet(Reward.class, preparationRewards);
            completionRewards = ProgressionCollections.immutableEnumSet(Reward.class, completionRewards);
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
        Set<Reward> completionRewards,
        Map<Metric, Integer> consumedOfferings,
        Evaluation evaluation
    ) {
        public Transition {
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
            Objects.requireNonNull(diagnostic, "diagnostic");
            unlockedAbilities = ProgressionCollections.immutableEnumSet(Ability.class, unlockedAbilities);
            completionRewards = ProgressionCollections.immutableEnumSet(Reward.class, completionRewards);
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

}
