package com.kadamitas.warlockery.brew.custom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum CustomBrewComponentRole {
    CAPACITY("capacity", 0),
    POWER("power", 1),
    DURATION("duration", 2),
    MODIFIER("modifier", 3),
    EXTENT("extent", 4),
    LINGERING("lingering", 4),
    DELIVERY("delivery", 5),
    EFFECT("effect", 6),
    CONTAINER("container", 7);

    private static final Map<String, CustomBrewComponentRole> BY_ID = Arrays.stream(values())
        .collect(Collectors.toUnmodifiableMap(CustomBrewComponentRole::id, Function.identity()));
    public static final Codec<CustomBrewComponentRole> CODEC = Codec.STRING.comapFlatMap(
        id -> find(id).map(DataResult::success)
            .orElseGet(() -> DataResult.error(() -> "Unknown custom brew component role: " + id)),
        CustomBrewComponentRole::id
    );

    private final String id;
    private final int order;

    CustomBrewComponentRole(final String id, final int order) {
        this.id = id;
        this.order = order;
    }

    public String id() {
        return id;
    }

    public int order() {
        return order;
    }

    public static java.util.Optional<CustomBrewComponentRole> find(final String id) {
        return java.util.Optional.ofNullable(BY_ID.get(id));
    }
}
