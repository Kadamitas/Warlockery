package com.kadamitas.warlockery.magic;

import com.kadamitas.warlockery.util.EnumLookup;
import com.kadamitas.warlockery.util.StringIdentified;
import java.util.Optional;

public enum MagicPath implements StringIdentified {
    IMP("imp", 80),
    INFERNAL("infernal", 160),
    GRAVE("grave", 120),
    LIGHT("light", 120),
    OTHERWHERE("otherwhere", 160),
    OVERWORLD("overworld", 140),
    SKY("sky", 120);

    private static final EnumLookup<MagicPath> LOOKUP = EnumLookup.create("magic path", values());

    private final String id;
    private final int maximumReserve;

    MagicPath(final String id, final int maximumReserve) {
        this.id = id;
        this.maximumReserve = maximumReserve;
    }

    public String id() {
        return id;
    }

    public int maximumReserve() {
        return maximumReserve;
    }

    public static Optional<MagicPath> find(final String id) {
        return LOOKUP.find(id);
    }

    public static MagicPath require(final String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown magic path: " + id));
    }
}
