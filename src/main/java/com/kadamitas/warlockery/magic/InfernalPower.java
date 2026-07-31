package com.kadamitas.warlockery.magic;

import com.kadamitas.warlockery.util.EnumLookup;
import com.kadamitas.warlockery.util.StringIdentified;
import java.util.Optional;

public enum InfernalPower implements StringIdentified {
    EXPLOSION("explosion"),
    PROJECTILE("projectile"),
    WEB("web"),
    FIRE("fire"),
    SPEED("speed"),
    HEALING("healing"),
    TELEPORT("teleport"),
    LEAPING("leaping"),
    FLIGHT("flight"),
    AQUATIC("aquatic"),
    UNDEAD("undead");

    private static final EnumLookup<InfernalPower> LOOKUP = EnumLookup.create("infernal power", values());
    private final String id;

    InfernalPower(final String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<InfernalPower> find(final String id) {
        return LOOKUP.find(id);
    }
}
