package com.kadamitas.warlockery.world;

public final class VillageGuardRules {
    private VillageGuardRules() {
    }

    public static boolean canCommission(
        final boolean villageHero,
        final boolean insideVillage,
        final boolean adultVillager,
        final boolean leatherTunic
    ) {
        return villageHero && insideVillage && adultVillager && leatherTunic;
    }
}
