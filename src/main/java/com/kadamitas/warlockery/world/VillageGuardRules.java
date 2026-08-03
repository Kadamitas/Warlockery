package com.kadamitas.warlockery.world;

public final class VillageGuardRules {
    public static final int TARGET_RADIUS = 32;
    public static final int RANGED_COOLDOWN_TICKS = 35;
    public static final double MAX_RANGED_DISTANCE_SQUARED = 30.0 * 30.0;

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

    public static boolean isSilverClassifiedAttack(final boolean settlementGuard) {
        return settlementGuard;
    }

    public static boolean shouldRetaliate(
        final boolean playerAttacker,
        final boolean protectedResident,
        final boolean creativeOrSpectator
    ) {
        return playerAttacker && protectedResident && !creativeOrSpectator;
    }

    public static boolean shouldFireSilverBolt(
        final boolean settlementGuard,
        final boolean targetAlive,
        final double distanceSquared,
        final int ticksSinceShot
    ) {
        return settlementGuard
            && targetAlive
            && distanceSquared <= MAX_RANGED_DISTANCE_SQUARED
            && ticksSinceShot >= RANGED_COOLDOWN_TICKS;
    }
}
