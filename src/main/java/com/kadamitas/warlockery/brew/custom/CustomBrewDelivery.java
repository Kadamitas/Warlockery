package com.kadamitas.warlockery.brew.custom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;

public enum CustomBrewDelivery {
    DRINKABLE("drinkable"),
    THROWABLE("throwable");

    public static final Codec<CustomBrewDelivery> CODEC = Codec.STRING.comapFlatMap(
        id -> Arrays.stream(values())
            .filter(value -> value.id.equals(id))
            .findFirst()
            .map(DataResult::success)
            .orElseGet(() -> DataResult.error(() -> "Unknown custom brew delivery: " + id)),
        CustomBrewDelivery::id
    );

    private final String id;

    CustomBrewDelivery(final String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
