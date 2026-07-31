package com.kadamitas.warlockery.world;

public final class HobgoblinCampRules {
    private HobgoblinCampRules() {
    }

    public static boolean canFound(
        final boolean insideVillage,
        final boolean residentsNearby,
        final boolean clearFootprint,
        final int distanceFromPlayer
    ) {
        return !insideVillage && !residentsNearby && clearFootprint
            && distanceFromPlayer >= 20 && distanceFromPlayer <= 48;
    }

    public static int residents(final int roll) {
        return 2 + Math.floorMod(roll, 3);
    }
}
