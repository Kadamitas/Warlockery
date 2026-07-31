package com.kadamitas.warlockery.block;

import java.util.List;
import net.minecraft.util.StringRepresentable;

public enum DreamWeaverMode implements StringRepresentable {
    RESTORATION("restoration", "dreamcatcher"),
    FASTING("fasting", "dream_weaver_fasting"),
    FLEET_FOOT("fleet_foot", "dream_weaver_fleet_foot"),
    INTENSITY("intensity", "dream_weaver_intensity"),
    IRON_ARM("iron_arm", "dream_weaver_iron_arm"),
    NIGHTMARES("nightmares", "dream_weaver_nightmares");

    public static final List<DreamWeaverMode> VALUES = List.of(values());

    private final String id;
    private final String itemId;

    DreamWeaverMode(final String id, final String itemId) {
        this.id = id;
        this.itemId = itemId;
    }

    public DreamWeaverMode next() {
        return VALUES.get((ordinal() + 1) % VALUES.size());
    }

    public String itemId() {
        return itemId;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
