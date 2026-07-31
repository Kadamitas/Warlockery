package com.kadamitas.warlockery.transformation;

import java.util.List;

public final class VampireBloodCapacityRules {
    private static final List<Integer> CAPACITIES = List.of(
        0,
        750,
        1_000,
        1_250,
        1_500,
        1_750,
        2_000,
        2_250,
        2_500,
        3_250,
        3_500
    );

    private VampireBloodCapacityRules() {
    }

    public static int capacity(final int level) {
        if (level < 0 || level >= CAPACITIES.size()) {
            throw new IllegalArgumentException("Vampire level must be between 0 and 10");
        }
        return CAPACITIES.get(level);
    }

    public static List<Integer> capacities() {
        return CAPACITIES;
    }
}
