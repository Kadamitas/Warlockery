package com.kadamitas.warlockery.block;

import java.util.List;
import net.minecraft.util.StringRepresentable;

public enum FetishMode implements StringRepresentable {
    DISORIENTATION("disorientation"),
    GHOST_WALKING("ghost_walking"),
    SENTINEL("sentinel"),
    SHRIEKING("shrieking"),
    VOODOO_PROTECTION("voodoo_protection");

    public static final List<FetishMode> VALUES = List.of(values());

    private final String id;

    FetishMode(final String id) {
        this.id = id;
    }

    public FetishMode next() {
        return VALUES.get((ordinal() + 1) % VALUES.size());
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
