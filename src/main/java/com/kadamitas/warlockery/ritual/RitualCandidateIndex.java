package com.kadamitas.warlockery.ritual;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

record RitualCandidateIndex<K, V>(Map<K, List<V>> groups) {
    RitualCandidateIndex {
        final LinkedHashMap<K, List<V>> immutableGroups = groups.entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey,
            entry -> List.copyOf(entry.getValue()),
            (_, replacement) -> replacement,
            LinkedHashMap::new
        ));
        groups = Collections.unmodifiableSequencedMap(immutableGroups);
    }

    static <K, V> RitualCandidateIndex<K, V> create(
        final Collection<V> candidates,
        final Function<? super V, ? extends K> classifier
    ) {
        return new RitualCandidateIndex<>(candidates.stream().collect(Collectors.groupingBy(
            classifier,
            LinkedHashMap::new,
            Collectors.toList()
        )));
    }

    Map<K, Integer> counts() {
        return groups.entrySet().stream().collect(Collectors.toUnmodifiableMap(
            Map.Entry::getKey,
            entry -> entry.getValue().size()
        ));
    }

    List<V> candidates(final K key) {
        return groups.getOrDefault(key, List.of());
    }

    boolean anyKey(final Predicate<? super K> predicate) {
        return groups.keySet().stream().anyMatch(predicate);
    }

    Optional<Map.Entry<K, List<V>>> largestMatching(final Predicate<? super K> predicate) {
        return groups.entrySet().stream()
            .filter(entry -> predicate.test(entry.getKey()))
            .max(java.util.Comparator.comparingInt(entry -> entry.getValue().size()));
    }
}
