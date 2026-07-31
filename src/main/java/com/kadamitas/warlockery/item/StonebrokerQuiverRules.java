package com.kadamitas.warlockery.item;

public final class StonebrokerQuiverRules {
    public static final double PROJECTILE_VELOCITY_MULTIPLIER = 1.5;
    public static final int WEAKNESS_TICKS = 200;
    public static final int WEAKNESS_AMPLIFIER = 0;
    public static final float GROUNDED_DAMAGE_MULTIPLIER = 1.0F;
    public static final float AIRBORNE_DAMAGE_MULTIPLIER = 1.75F;

    private StonebrokerQuiverRules() {
    }

    public static boolean suppliesEndlessArrow(
        final boolean wearingQuiver,
        final boolean projectileEmpty,
        final boolean ordinaryArrow
    ) {
        return wearingQuiver && (projectileEmpty || ordinaryArrow);
    }

    public static boolean isAirborneTarget(
        final boolean fallFlying,
        final boolean onGround,
        final boolean inWater,
        final boolean passenger
    ) {
        return fallFlying || !onGround && !inWater && !passenger;
    }

    public static float damageMultiplier(final boolean quiverShot, final boolean airborneTarget) {
        if (!quiverShot) {
            return 1.0F;
        }
        return airborneTarget ? AIRBORNE_DAMAGE_MULTIPLIER : GROUNDED_DAMAGE_MULTIPLIER;
    }
}
