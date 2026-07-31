package com.kadamitas.warlockery.entity;

public final class EntRules {
    public static final double MAX_HEALTH = 200.0D;
    public static final float ORDINARY_DAMAGE_CAP = 15.0F;
    public static final float WEAKNESS_MULTIPLIER = 3.0F;
    public static final int FERTILIZE_INTERVAL_TICKS = 100;
    public static final int FERTILIZE_RADIUS = 2;
    public static final int MAX_FERTILIZED_BLOCKS = 8;
    public static final int MIN_HORIZONTAL_SPAWN_DISTANCE = 8;
    public static final int MAX_HORIZONTAL_SPAWN_DISTANCE = 16;
    public static final int MAX_VERTICAL_SPAWN_OFFSET = 6;

    private EntRules() {
    }

    public static float incomingDamage(
        final float amount,
        final boolean axeAttack,
        final boolean nonPlayerMobAttack
    ) {
        final float safeAmount = Float.isFinite(amount) ? Math.max(0.0F, amount) : 0.0F;
        return axeAttack || nonPlayerMobAttack
            ? safeAmount * WEAKNESS_MULTIPLIER
            : Math.min(safeAmount, ORDINARY_DAMAGE_CAP);
    }

    public static double logBreakSpawnChance(final int neighboringLogs) {
        return Math.clamp(neighboringLogs, 0, 100) / 100.0D;
    }

    public static boolean shouldSpawn(final int neighboringLogs, final double roll) {
        return Double.isFinite(roll) && roll >= 0.0D && roll < logBreakSpawnChance(neighboringLogs);
    }

    public static int horizontalOffset(final int distanceRoll, final boolean positive) {
        if (distanceRoll < 0 || distanceRoll > MAX_HORIZONTAL_SPAWN_DISTANCE - MIN_HORIZONTAL_SPAWN_DISTANCE) {
            throw new IllegalArgumentException("Horizontal Ent spawn roll is outside its supported range");
        }
        final int distance = MIN_HORIZONTAL_SPAWN_DISTANCE + distanceRoll;
        return positive ? distance : -distance;
    }

    public static int verticalOffset(final int heightRoll) {
        if (heightRoll < 0 || heightRoll > MAX_VERTICAL_SPAWN_OFFSET) {
            throw new IllegalArgumentException("Vertical Ent spawn roll is outside its supported range");
        }
        return heightRoll;
    }

    public static boolean shouldFertilizeGround(final int tickCount, final int entityId) {
        return Math.floorMod(tickCount + entityId, FERTILIZE_INTERVAL_TICKS) == 0;
    }
}
