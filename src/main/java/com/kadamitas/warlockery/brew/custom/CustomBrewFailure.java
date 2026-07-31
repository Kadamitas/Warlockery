package com.kadamitas.warlockery.brew.custom;

import com.kadamitas.warlockery.util.EnumLookup;
import com.kadamitas.warlockery.util.StringIdentified;
import com.mojang.serialization.Codec;

public enum CustomBrewFailure implements StringIdentified {
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

    private static final EnumLookup<CustomBrewFailure> LOOKUP = EnumLookup.create("custom brew failure", values());
    public static final Codec<CustomBrewFailure> CODEC = LOOKUP.codec();

    private final String id;

    CustomBrewFailure(final String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
