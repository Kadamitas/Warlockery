package com.kadamitas.warlockery.magic;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum MagicPath {
    IMP("imp", 80),
    INFERNAL("infernal", 160),
    GRAVE("grave", 120),
    LIGHT("light", 120),
    OTHERWHERE("otherwhere", 160),
    OVERWORLD("overworld", 140),
    SKY("sky", 120);

    private static final Map<String, MagicPath> BY_ID = Arrays.stream(values())
        .collect(Collectors.toUnmodifiableMap(MagicPath::id, Function.identity()));

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
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static MagicPath require(final String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown magic path: " + id));
    }
}
