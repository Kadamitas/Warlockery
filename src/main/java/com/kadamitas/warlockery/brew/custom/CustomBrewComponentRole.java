package com.kadamitas.warlockery.brew.custom;

import com.kadamitas.warlockery.util.EnumLookup;
import com.kadamitas.warlockery.util.StringIdentified;
import com.mojang.serialization.Codec;

public enum CustomBrewComponentRole implements StringIdentified {
    CAPACITY("capacity", 0),
    POWER("power", 1),
    DURATION("duration", 2),
    MODIFIER("modifier", 3),
    EXTENT("extent", 4),
    LINGERING("lingering", 4),
    DELIVERY("delivery", 5),
    EFFECT("effect", 6),
    CONTAINER("container", 7);

    private static final EnumLookup<CustomBrewComponentRole> LOOKUP = EnumLookup.create(
        "custom brew component role",
        values()
    );
    public static final Codec<CustomBrewComponentRole> CODEC = LOOKUP.codec();

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
        return LOOKUP.find(id);
    }
}
