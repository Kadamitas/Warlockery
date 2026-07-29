package com.kadamitas.warlockery.block;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public record StatueProfile(String id, Effect effect) {
    private static final List<StatueProfile> PROFILES = List.of(
        new StatueProfile("broken_hexes_statue", Effect.CLEANSE),
        new StatueProfile("statuegoddess", Effect.CLEANSE),
        new StatueProfile("statueofworship", Effect.PATRON_BLESSING),
        new StatueProfile("occluded_summons_statue", Effect.OCCLUDE_RITUALS)
    );
    private static final Map<String, StatueProfile> BY_ID = PROFILES.stream()
        .collect(Collectors.toUnmodifiableMap(StatueProfile::id, Function.identity()));

    public StatueProfile {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A statue profile requires an id");
        }
        id = id.strip();
    }

    public static Optional<StatueProfile> find(final String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static Set<String> ids() {
        return BY_ID.keySet();
    }

    public enum Effect {
        CLEANSE,
        PATRON_BLESSING,
        OCCLUDE_RITUALS
    }
}
