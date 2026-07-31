package com.kadamitas.warlockery.transformation;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;

final class ProgressionCollections {
    private ProgressionCollections() {
    }

    static <E extends Enum<E>> Set<E> immutableEnumSet(
        final Class<E> type,
        final Collection<E> values
    ) {
        Objects.requireNonNull(values, "values");
        final EnumSet<E> copy = EnumSet.noneOf(type);
        copy.addAll(values);
        return Collections.unmodifiableSet(copy);
    }

    static <K extends Enum<K>, V> Map<K, V> immutableEnumMap(
        final Class<K> keyType,
        final Map<K, ? extends V> values
    ) {
        Objects.requireNonNull(values, "values");
        final EnumMap<K, V> copy = new EnumMap<>(keyType);
        values.forEach((key, value) -> copy.put(
            Objects.requireNonNull(key, "key"),
            Objects.requireNonNull(value, "value")
        ));
        return Collections.unmodifiableMap(copy);
    }

    static <K extends Enum<K>, V> Map<K, V> immutableEnumMap(
        final Class<K> keyType,
        final Map<K, ? extends V> values,
        final BiPredicate<K, V> retained
    ) {
        Objects.requireNonNull(retained, "retained");
        final EnumMap<K, V> copy = new EnumMap<>(keyType);
        values.forEach((key, value) -> {
            final K checkedKey = Objects.requireNonNull(key, "key");
            final V checkedValue = Objects.requireNonNull(value, "value");
            if (retained.test(checkedKey, checkedValue)) {
                copy.put(checkedKey, checkedValue);
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    static <K extends Enum<K>> Map<K, Integer> immutablePositiveIntMap(
        final Class<K> keyType,
        final Map<K, Integer> values
    ) {
        return immutableEnumMap(keyType, values, (key, value) -> value > 0);
    }
}
