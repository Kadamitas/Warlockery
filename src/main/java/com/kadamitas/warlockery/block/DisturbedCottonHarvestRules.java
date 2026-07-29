package com.kadamitas.warlockery.block;

public final class DisturbedCottonHarvestRules {
    private DisturbedCottonHarvestRules() {
    }

    public static boolean qualifies(
        final boolean night,
        final boolean wakingNightmare,
        final boolean nearbyNightmare
    ) {
        return night || wakingNightmare || nearbyNightmare;
    }
}
