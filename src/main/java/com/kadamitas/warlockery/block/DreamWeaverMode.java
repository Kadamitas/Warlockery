package com.kadamitas.warlockery.block;

import java.util.List;
import net.minecraft.util.StringRepresentable;

public enum DreamWeaverMode implements StringRepresentable {
    RESTORATION("restoration"),
    FASTING("fasting"),
    FLEET_FOOT("fleet_foot"),
    INTENSITY("intensity"),
    IRON_ARM("iron_arm"),
    NIGHTMARES("nightmares");

    public static final List<DreamWeaverMode> VALUES = List.of(values());

    private final String id;

    DreamWeaverMode(final String id) {
        this.id = id;
    }

    public DreamWeaverMode next() {
        return VALUES.get((ordinal() + 1) % VALUES.size());
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
