package com.kadamitas.warlockery.block;

import net.minecraft.util.StringRepresentable;

public enum BearTrapState implements StringRepresentable {
    DISARMED("disarmed"),
    ARMED("armed"),
    SPRUNG("sprung");

    private final String serializedName;

    BearTrapState(final String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
