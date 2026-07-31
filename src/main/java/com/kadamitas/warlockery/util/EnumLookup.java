package com.kadamitas.warlockery.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public record EnumLookup<E extends Enum<E> & StringIdentified>(String label, Map<String, E> byId) {
    public EnumLookup {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Enum label must not be blank");
        }
        label = label.strip();
        byId = Map.copyOf(byId);
    }

    public static <E extends Enum<E> & StringIdentified> EnumLookup<E> create(
        final String label,
        final E[] values
    ) {
        return new EnumLookup<>(label, Arrays.stream(values).collect(Collectors.toUnmodifiableMap(
            StringIdentified::id,
            Function.identity()
        )));
    }

    public Optional<E> find(final String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public E findOrElse(final String id, final E fallback) {
        return byId.getOrDefault(id, fallback);
    }

    public Codec<E> codec() {
        return Codec.STRING.comapFlatMap(
            id -> find(id)
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(() -> "Unknown " + label + ": " + id)),
            StringIdentified::id
        );
    }

    public Codec<E> fallbackCodec(final E fallback) {
        return Codec.STRING.xmap(id -> findOrElse(id, fallback), StringIdentified::id);
    }
}
