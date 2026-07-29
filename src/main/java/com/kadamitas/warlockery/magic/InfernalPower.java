package com.kadamitas.warlockery.magic;

import java.util.Arrays;
import java.util.Optional;

public enum InfernalPower {
    FIRE("fire"),
    SPEED("speed"),
    HEALING("healing"),
    TELEPORT("teleport"),
    LEAPING("leaping"),
    AQUATIC("aquatic"),
    UNDEAD("undead");

    private final String id;

    InfernalPower(final String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<InfernalPower> find(final String id) {
        return Arrays.stream(values()).filter(power -> power.id.equals(id)).findFirst();
    }
}
