package com.kadamitas.warlockery.brew.custom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;

public enum CustomBrewModifier {
    NONE("none"),
    HIDE_PARTICLES("hide_particles"),
    INVERT_NEXT("invert_next"),
    SKIP_BLOCKS("skip_blocks"),
    SKIP_ENTITIES("skip_entities"),
    COLOR_FROM_INGREDIENT("color_from_ingredient"),
    UNCAPPED_DAMAGE("uncapped_damage"),
    QUAFF("quaff");

    public static final Codec<CustomBrewModifier> CODEC = Codec.STRING.comapFlatMap(
        id -> Arrays.stream(values())
            .filter(value -> value.id.equals(id))
            .findFirst()
            .map(DataResult::success)
            .orElseGet(() -> DataResult.error(() -> "Unknown custom brew modifier: " + id)),
        CustomBrewModifier::id
    );

    private final String id;

    CustomBrewModifier(final String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
