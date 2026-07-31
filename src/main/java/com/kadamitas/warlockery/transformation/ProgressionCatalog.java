package com.kadamitas.warlockery.transformation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;

record ProgressionCatalog<A extends Enum<A>, Q extends ProgressionQuest<A>>(
    List<Q> quests,
    LevelUnlocks<A> abilities
) {
    ProgressionCatalog {
        quests = List.copyOf(quests);
        Objects.requireNonNull(abilities, "abilities");
        if (quests.size() != abilities.maximumLevel()) {
            throw new IllegalArgumentException("Quest count must match the maximum progression level");
        }
    }

    static <A extends Enum<A>, Q extends ProgressionQuest<A>> ProgressionCatalog<A, Q> create(
        final List<Q> quests,
        final Class<A> abilityType
    ) {
        final List<Q> immutableQuests = List.copyOf(quests);
        for (int index = 0; index < immutableQuests.size(); index++) {
            if (immutableQuests.get(index).targetLevel() != index + 1) {
                throw new IllegalArgumentException("Quests must declare every target level in order");
            }
        }
        return new ProgressionCatalog<>(
            immutableQuests,
            unlocks(immutableQuests, abilityType, ProgressionQuest::targetLevel, ProgressionQuest::abilities)
        );
    }

    static <E extends Enum<E>, T> LevelUnlocks<E> unlocks(
        final List<T> tiers,
        final Class<E> type,
        final ToIntFunction<T> level,
        final Function<T, ? extends Collection<E>> values
    ) {
        Objects.requireNonNull(tiers, "tiers");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(values, "values");
        final int maximumLevel = tiers.stream().mapToInt(level).max().orElse(0);
        final List<List<E>> additions = new ArrayList<>(maximumLevel + 1);
        for (int index = 0; index <= maximumLevel; index++) {
            additions.add(new ArrayList<>());
        }
        tiers.forEach(tier -> {
            final int targetLevel = level.applyAsInt(tier);
            if (targetLevel < 1) {
                throw new IllegalArgumentException("Unlock levels must be positive");
            }
            additions.get(targetLevel).addAll(Objects.requireNonNull(values.apply(tier), "values"));
        });
        final List<Set<E>> byLevel = new ArrayList<>(maximumLevel + 1);
        final EnumSet<E> cumulative = EnumSet.noneOf(type);
        final EnumMap<E, Integer> minimumLevels = new EnumMap<>(type);
        byLevel.add(Set.of());
        for (int currentLevel = 1; currentLevel <= maximumLevel; currentLevel++) {
            final int unlockLevel = currentLevel;
            additions.get(currentLevel).forEach(value -> {
                cumulative.add(value);
                minimumLevels.putIfAbsent(value, unlockLevel);
            });
            byLevel.add(ProgressionCollections.immutableEnumSet(type, cumulative));
        }
        return new LevelUnlocks<>(List.copyOf(byLevel), ProgressionCollections.immutableEnumMap(type, minimumLevels));
    }

    Optional<Q> activeQuest(final int currentLevel) {
        final int level = Math.clamp(currentLevel, 0, quests.size());
        return level == quests.size() ? Optional.empty() : Optional.of(quests.get(level));
    }

    Optional<Q> questForTargetLevel(final int targetLevel) {
        return targetLevel < 1 || targetLevel > quests.size()
            ? Optional.empty()
            : Optional.of(quests.get(targetLevel - 1));
    }

    Set<A> abilitiesAt(final int level) {
        return abilities.at(level);
    }

    int minimumLevel(final A ability) {
        return abilities.minimumLevel(ability);
    }

    record LevelUnlocks<E extends Enum<E>>(List<Set<E>> byLevel, Map<E, Integer> minimumLevels) {
        LevelUnlocks {
            byLevel = byLevel.stream().map(Set::copyOf).toList();
            minimumLevels = Map.copyOf(minimumLevels);
        }

        int maximumLevel() {
            return byLevel.size() - 1;
        }

        Set<E> at(final int requestedLevel) {
            return byLevel.get(Math.clamp(requestedLevel, 0, maximumLevel()));
        }

        int minimumLevel(final E value) {
            final Integer level = minimumLevels.get(Objects.requireNonNull(value, "value"));
            if (level == null) {
                throw new IllegalArgumentException("Value is not unlocked by this progression");
            }
            return level;
        }
    }
}
