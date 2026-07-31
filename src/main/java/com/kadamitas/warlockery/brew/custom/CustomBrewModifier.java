package com.kadamitas.warlockery.brew.custom;

import com.kadamitas.warlockery.util.EnumLookup;
import com.kadamitas.warlockery.util.StringIdentified;
import com.mojang.serialization.Codec;

public enum CustomBrewModifier implements StringIdentified {
    NONE("none"),
    HIDE_PARTICLES("hide_particles"),
    INVERT_NEXT("invert_next"),
    SKIP_BLOCKS("skip_blocks"),
    SKIP_ENTITIES("skip_entities"),
    COLOR_FROM_INGREDIENT("color_from_ingredient"),
    UNCAPPED_DAMAGE("uncapped_damage"),
    QUAFF("quaff");

    private static final EnumLookup<CustomBrewModifier> LOOKUP = EnumLookup.create("custom brew modifier", values());
    public static final Codec<CustomBrewModifier> CODEC = LOOKUP.codec();

    private final String id;

    CustomBrewModifier(final String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
