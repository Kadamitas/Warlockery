package com.kadamitas.warlockery.ritual;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum RitualWardType {
    IMPRISONMENT("imprisonment"),
    PROTECTION("protection"),
    SANCTITY("sanctity");

    private static final Map<String, RitualWardType> BY_ID = Arrays.stream(values())
        .collect(Collectors.toUnmodifiableMap(RitualWardType::id, Function.identity()));
    public static final Codec<RitualWardType> CODEC = Codec.STRING.comapFlatMap(
        id -> find(id).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unknown ward: " + id)),
        RitualWardType::id
    );

    private final String id;

    RitualWardType(final String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<RitualWardType> find(final String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }
}
