package com.kadamitas.warlockery.entity;

import java.util.Optional;
import java.util.UUID;

public final class CreatureBehaviorRules {
    public static final int MAX_EMPOWERMENT = 5;
    public static final double OWNER_FOLLOW_DISTANCE_SQUARED = 144.0;
    public static final double OWNER_TELEPORT_DISTANCE_SQUARED = 1_024.0;
    public static final double ENT_INTRUSION_DISTANCE_SQUARED = 64.0;

    private CreatureBehaviorRules() {
    }

    public static boolean shouldPulse(final int tickCount, final int entityId, final int intervalTicks) {
        if (intervalTicks < 1) {
            throw new IllegalArgumentException("Pulse interval must be positive");
        }
        return Math.floorMod(tickCount + entityId, intervalTicks) == 0;
    }

    public static boolean canBind(
        final Optional<UUID> currentOwner,
        final UUID playerId,
        final boolean hasOffering
    ) {
        return hasOffering && currentOwner.map(playerId::equals).orElse(true);
    }

    public static boolean canRecruit(
        final Optional<UUID> currentOwner,
        final UUID playerId,
        final boolean hasOffering,
        final boolean familiarPresent
    ) {
        return familiarPresent && canBind(currentOwner, playerId, hasOffering);
    }

    public static boolean canMount(final Optional<UUID> currentOwner, final UUID playerId) {
        return currentOwner.filter(playerId::equals).isPresent();
    }

    public static int empoweredLevel(final int current, final int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Empowerment amount must be nonnegative");
        }
        return Math.clamp(current + amount, 0, MAX_EMPOWERMENT);
    }

    public static boolean shouldSummonWolves(
        final float health,
        final float maximumHealth,
        final int nearbyWolves,
        final int tickCount
    ) {
        return maximumHealth > 0.0F
            && health <= maximumHealth * 0.5F
            && nearbyWolves < 4
            && Math.floorMod(tickCount, 400) == 0;
    }

    /**
     * Water puts the sun out. Vanilla's own undead stop burning the moment they are in water or
     * standing in rain, and a sunlight-weak creature that kept burning while submerged would be
     * unable to survive the one place Naamah's line is at home.
     */
    public static boolean shouldBurnInSun(
        final boolean daylight,
        final boolean skyVisible,
        final boolean fireResistant,
        final boolean wet
    ) {
        return daylight && skyVisible && !fireResistant && !wet;
    }

    public static int cauldronRangeBonus(final int nearbyCauldrons) {
        return Math.clamp(nearbyCauldrons, 0, 4) * 4;
    }

    public static int altarSearchRange(final int baseRange, final int nearbyRangeExtenders) {
        if (baseRange < 1 || nearbyRangeExtenders < 0) {
            throw new IllegalArgumentException("Altar search range inputs must be valid");
        }
        return baseRange + Math.clamp(nearbyRangeExtenders, 0, 2) * 8;
    }

    public static boolean shouldUseRangedAttack(final double distanceSquared, final boolean lineOfSight) {
        return lineOfSight && distanceSquared >= 25.0 && distanceSquared <= 196.0;
    }

    public static boolean canRedirectEffect(
        final boolean ownerPresent,
        final boolean armorPresent,
        final boolean attackerPresent,
        final boolean effectStored
    ) {
        return ownerPresent && armorPresent && attackerPresent && effectStored;
    }
}
