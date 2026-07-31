package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.util.EnumLookup;
import com.kadamitas.warlockery.util.StringIdentified;
import com.mojang.serialization.Codec;
import java.util.Optional;

public enum RitualWardType implements StringIdentified {
    IMPRISONMENT("imprisonment"),
    PROTECTION("protection"),
    RECHARGE("recharge"),
    SANCTITY("sanctity");

    private static final EnumLookup<RitualWardType> LOOKUP = EnumLookup.create("ward", values());
    public static final Codec<RitualWardType> CODEC = LOOKUP.codec();

    private final String id;

    RitualWardType(final String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<RitualWardType> find(final String id) {
        return LOOKUP.find(id);
    }
}
