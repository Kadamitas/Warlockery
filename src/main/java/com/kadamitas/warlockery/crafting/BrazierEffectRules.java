package com.kadamitas.warlockery.crafting;

import java.util.ArrayList;
import java.util.List;

public final class BrazierEffectRules {
    public static final int MAX_CATCH_UP_ACTIVATIONS = 256;

    private BrazierEffectRules() {
    }

    public static int scheduledActivations(
        final int previousProgress,
        final int currentProgress,
        final int interval
    ) {
        return activationTicks(previousProgress, currentProgress, interval).size();
    }

    public static List<Integer> activationTicks(
        final int previousProgress,
        final int currentProgress,
        final int interval
    ) {
        if (interval <= 0 || currentProgress <= previousProgress || currentProgress <= 0) {
            return List.of();
        }
        final long previous = Math.max(0, previousProgress);
        final long current = currentProgress;
        final long last = current / interval * interval;
        if (last <= previous) {
            return List.of();
        }
        final long first = (previous / interval + 1L) * interval;
        final long available = (last - first) / interval + 1L;
        final int count = (int) Math.min(MAX_CATCH_UP_ACTIVATIONS, available);
        final long boundedFirst = last - (long) (count - 1) * interval;
        final List<Integer> activations = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            activations.add((int) (boundedFirst + (long) index * interval));
        }
        return List.copyOf(activations);
    }

    public static boolean withinRadiusSquared(
        final double offsetX,
        final double offsetY,
        final double offsetZ,
        final double radiusSquared
    ) {
        return radiusSquared >= 0.0
            && offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ <= radiusSquared;
    }

    public static boolean shouldActivate(final long loadedTick, final int interval) {
        return interval > 0 && Math.floorMod(loadedTick, interval) == 0;
    }

    public static int drainGrowthOffsetY(final long activationTick) {
        return Math.floorMod(activationTick, 30) / 5;
    }

    public static int summonAxisOffset(final int roll, final int minimumRange, final int maximumRange) {
        if (minimumRange < 0 || maximumRange < minimumRange) {
            throw new IllegalArgumentException("Invalid summon range");
        }
        final int activeRadius = maximumRange - minimumRange;
        final int bound = activeRadius * 2 + 1;
        if (roll < 0 || roll >= bound) {
            throw new IllegalArgumentException("Summon roll outside range");
        }
        final int shiftedRoll = roll > activeRadius ? roll + minimumRange * 2 : roll;
        return -maximumRange + shiftedRoll;
    }

    public static boolean canIgnite(
        final boolean hasIngredient,
        final boolean outputAcceptsAsh,
        final boolean alreadyIgnited
    ) {
        return hasIngredient && outputAcceptsAsh && !alreadyIgnited;
    }

    public static boolean isRisingEdge(final boolean previouslyPowered, final boolean currentlyPowered) {
        return !previouslyPowered && currentlyPowered;
    }

    public static boolean restoreIgnitionAfterMigration(
        final boolean savedIgnited,
        final boolean migratedFromVersionZero
    ) {
        return savedIgnited && !migratedFromVersionZero;
    }

    public static boolean canContinueBurn(final boolean ignited, final boolean hasAsh) {
        return ignited && hasAsh;
    }

    public static boolean canReceiveDrainGrowthHealing(
        final boolean alive,
        final boolean undead,
        final float health,
        final float maximumHealth
    ) {
        return alive && undead && maximumHealth > 0.0F && health < maximumHealth;
    }
}

