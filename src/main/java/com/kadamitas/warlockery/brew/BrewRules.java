package com.kadamitas.warlockery.brew;

public final class BrewRules {
    private BrewRules() {
    }

    public static boolean canGrow(
        final boolean bonemealable,
        final boolean validTarget,
        final boolean successful
    ) {
        return bonemealable && validTarget && successful;
    }

    public static boolean shouldExtinguish(
        final boolean fire,
        final boolean extinguishable,
        final boolean lit
    ) {
        return fire || extinguishable && lit;
    }

    public static boolean shouldFreeze(
        final boolean replaceable,
        final boolean water,
        final boolean source
    ) {
        return replaceable && water && source;
    }

    public static boolean canPlaceOnSurface(final boolean replaceable, final boolean supported) {
        return replaceable && supported;
    }

    public static boolean shouldHarvest(final boolean crop, final boolean hasAge, final boolean mature) {
        return crop && (!hasAge || mature);
    }

    public static boolean shouldTill(final boolean dirt, final boolean clearAbove) {
        return dirt && clearAbove;
    }

    public static boolean shouldRemoveEffect(final boolean beneficial, final boolean removeBeneficial) {
        return beneficial == removeBeneficial;
    }

    public static boolean shouldPlaceLily(
        final boolean water,
        final boolean source,
        final boolean clearAbove,
        final boolean survives
    ) {
        return water && source && clearAbove && survives;
    }

    public static int extendedDuration(final int duration) {
        return (int) Math.clamp((long) duration * 2L, 1L, 36_000L);
    }

    public static boolean isDarkEnoughForGrue(final int brightness) {
        return brightness < 5;
    }

    public static boolean isMoonlit(final boolean openSky, final float moonBrightness) {
        return openSky && moonBrightness > 0.05F;
    }

    public static boolean canPartFluid(final boolean matchingFluid, final boolean source, final boolean surface) {
        return matchingFluid && source && surface;
    }

    public static boolean shouldSolidify(final boolean hollowTears) {
        return hollowTears;
    }

    public static boolean canErodeBelowHollowTears(
        final boolean hollowTears,
        final boolean air,
        final boolean blockEntity,
        final float destroySpeed
    ) {
        return !hollowTears && !air && !blockEntity && destroySpeed >= 0.0F;
    }
}
