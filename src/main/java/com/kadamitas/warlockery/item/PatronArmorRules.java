package com.kadamitas.warlockery.item;

public final class PatronArmorRules {
    public static final float GIRDLE_UNARMED_DAMAGE = 4.0F;

    private PatronArmorRules() {
    }

    public static float unarmedDamage(final float ordinaryDamage, final boolean emptyHand, final boolean wearingGirdle) {
        return emptyHand && wearingGirdle ? Math.max(ordinaryDamage, GIRDLE_UNARMED_DAMAGE) : ordinaryDamage;
    }

    public static boolean sharesResistance(
        final boolean wearingGirdle,
        final boolean wearingQuiver,
        final boolean nearbyGirdle,
        final boolean nearbyQuiver
    ) {
        return wearingGirdle && nearbyQuiver || wearingQuiver && nearbyGirdle;
    }
}
