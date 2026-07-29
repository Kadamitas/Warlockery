package com.kadamitas.warlockery.block;

final class MagicalPlantRules {
    static final int MAX_NEARBY_SPREADERS = 5;

    private MagicalPlantRules() {
    }

    static boolean shouldIgnite(
        final boolean living,
        final boolean sneaking,
        final boolean immune,
        final boolean fireImmune
    ) {
        return living && !sneaking && !immune && !fireImmune;
    }

    static boolean canSpreadGlintWeed(
        final boolean targetAir,
        final boolean validGround,
        final boolean stableGround,
        final boolean dry,
        final int brightness,
        final int nearbyPlants
    ) {
        return canSpread(targetAir, validGround, stableGround, dry, brightness, 8, nearbyPlants);
    }

    static boolean canSpreadEmberMoss(
        final boolean targetAir,
        final boolean validGround,
        final boolean stableGround,
        final boolean dry,
        final int nearbyPlants
    ) {
        return canSpread(targetAir, validGround, stableGround, dry, 0, 0, nearbyPlants);
    }

    static boolean canSpreadBramble(
        final boolean targetAir,
        final boolean validGround,
        final boolean stableGround,
        final boolean dry,
        final int nearbyPlants
    ) {
        return canSpread(targetAir, validGround, stableGround, dry, 0, 0, nearbyPlants);
    }

    private static boolean canSpread(
        final boolean targetAir,
        final boolean validGround,
        final boolean stableGround,
        final boolean dry,
        final int brightness,
        final int minimumBrightness,
        final int nearbyPlants
    ) {
        return targetAir
            && validGround
            && stableGround
            && dry
            && brightness >= minimumBrightness
            && nearbyPlants < MAX_NEARBY_SPREADERS;
    }

    static boolean shouldBoost(final boolean living, final int tickCount) {
        return living && Math.floorMod(tickCount, 10) == 0;
    }

    static boolean canTeleport(
        final boolean immune,
        final boolean passenger,
        final boolean validGround,
        final boolean stableGround,
        final boolean feetClear,
        final boolean headClear,
        final boolean boundsClear,
        final boolean dry
    ) {
        return !immune
            && !passenger
            && validGround
            && stableGround
            && feetClear
            && headClear
            && boundsClear
            && dry;
    }
}
