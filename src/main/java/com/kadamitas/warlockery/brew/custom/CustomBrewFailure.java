package com.kadamitas.warlockery.brew.custom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;

public enum CustomBrewFailure {
    NONE("none"),
    INVALID_INPUT("invalid_input"),
    WRONG_ORDER("wrong_order"),
    TOO_MANY_COMPONENTS("too_many_components"),
    OVER_CAPACITY("over_capacity"),
    POWER_LIMIT("power_limit"),
    DURATION_LIMIT("duration_limit"),
    DELIVERY_CONFLICT("delivery_conflict"),
    INVALID_MODIFIER("invalid_modifier"),
    MISSING_EFFECT("missing_effect"),
    MISSING_CONTAINER("missing_container"),
    NO_APPLICABLE_EFFECT("no_applicable_effect"),
    MISSING_WATER("missing_water"),
    WRONG_FLUID("wrong_fluid"),
    MISSING_POWER("missing_power"),
    MISSING_HEAT("missing_heat"),
    OUTPUT_BLOCKED("output_blocked");

    public static final Codec<CustomBrewFailure> CODEC = Codec.STRING.comapFlatMap(
        id -> Arrays.stream(values())
            .filter(value -> value.id.equals(id))
            .findFirst()
            .map(DataResult::success)
            .orElseGet(() -> DataResult.error(() -> "Unknown custom brew failure: " + id)),
        CustomBrewFailure::id
    );

    private final String id;

    CustomBrewFailure(final String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
