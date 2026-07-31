package com.kadamitas.warlockery.transformation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleUnaryOperator;

public final class SupernaturalAbilityRules {
    public static final int BLOOD_POWER_CHARGES = 5;
    public static final int BATSWARM_VISUAL_COUNT = 3;
    public static final int ATTACKING_BAT_COUNT = 15;
    public static final int MAX_BLOOD_RUSH_AMPLIFIER = 9;
    private static final List<SupernaturalPower> BLOOD_POWERS = List.of(
        SupernaturalPower.CALL_STORM,
        SupernaturalPower.TELEPORT,
        SupernaturalPower.SUMMON_BATS
    );

    private SupernaturalAbilityRules() {
    }

    public static boolean vampireKnockbackActive(
        final int vampireLevel,
        final boolean sneaking,
        final boolean directMeleeAttack
    ) {
        return vampireLevel >= 3 && sneaking && directMeleeAttack;
    }

    public static int nextBloodRushAmplifier(final int vampireLevel, final int currentAmplifier) {
        if (vampireLevel < 4) {
            return -1;
        }
        final int currentTier = Math.clamp(currentAmplifier + 1, 0, MAX_BLOOD_RUSH_AMPLIFIER + 1);
        final int tiersPerUse = vampireLevel >= 8 ? 2 : 1;
        final int nextTier = Math.min(MAX_BLOOD_RUSH_AMPLIFIER + 1, currentTier + tiersPerUse);
        return nextTier - 1;
    }

    public static int bloodRushDurationTicks(final int amplifier) {
        return 200 + Math.max(1, amplifier + 1) * 60;
    }

    public static boolean resistsSun(final int vampireLevel) {
        return vampireLevel >= 5;
    }

    public static int sunToleranceTicks(final int vampireLevel) {
        if (!resistsSun(vampireLevel)) {
            return 0;
        }
        return 400 + Math.min(5, vampireLevel - 5) * 320;
    }

    public static int sunlightBloodCost(final int vampireLevel, final int maximumBlood) {
        if (!resistsSun(vampireLevel) || maximumBlood <= 0) {
            return 0;
        }
        final int drainCycles = Math.max(1, sunToleranceTicks(vampireLevel) / 40);
        return Math.max(1, (maximumBlood + drainCycles - 1) / drainCycles);
    }

    public static boolean batSwarmFormActive(
        final int vampireLevel,
        final long expiresAt,
        final long gameTime
    ) {
        return vampireLevel >= 7 && expiresAt > gameTime;
    }

    public static int bloodSipAmount(final int ordinaryAmount, final boolean batSwarmForm) {
        if (ordinaryAmount <= 0) {
            return 0;
        }
        return batSwarmForm ? Math.max(1, ordinaryAmount / 3) : ordinaryAmount;
    }

    public static BatCommandTarget batCommandTarget(
        final boolean hasGazedTarget,
        final boolean hasRetaliationTarget
    ) {
        if (hasGazedTarget) {
            return BatCommandTarget.GAZE;
        }
        return hasRetaliationTarget ? BatCommandTarget.RETALIATION : BatCommandTarget.NONE;
    }

    public static List<SupernaturalPower> bloodPowers() {
        return BLOOD_POWERS;
    }

    public static BloodPowerCharge replaceBloodPower(
        final Map<SupernaturalPower, Integer> currentCharges,
        final SupernaturalPower selectedPower
    ) {
        Objects.requireNonNull(currentCharges, "currentCharges");
        Objects.requireNonNull(selectedPower, "selectedPower");
        if (!BLOOD_POWERS.contains(selectedPower)) {
            throw new IllegalArgumentException("Selected power is not charged by the Blood Crucible");
        }
        final boolean replaced = BLOOD_POWERS.stream().anyMatch(power -> power != selectedPower
            && currentCharges.getOrDefault(power, 0) > 0);
        final EnumMap<SupernaturalPower, Integer> after = new EnumMap<>(SupernaturalPower.class);
        BLOOD_POWERS.forEach(power -> after.put(
            power,
            power == selectedPower ? BLOOD_POWER_CHARGES : 0
        ));
        return new BloodPowerCharge(Map.copyOf(after), replaced);
    }

    public static float sprintingDamageBonus(
        final int werewolfLevel,
        final WerewolfShape shape,
        final boolean sprinting,
        final boolean directMeleeAttack
    ) {
        return werewolfLevel >= 6
            && shape != WerewolfShape.HUMAN
            && sprinting
            && directMeleeAttack
            ? 2.0F + werewolfLevel * 0.35F
            : 0.0F;
    }

    public static boolean armorRendingActive(final int werewolfLevel, final WerewolfShape shape) {
        return werewolfLevel >= 9 && shape == WerewolfShape.WOLFMAN;
    }

    public static double armorPiercingInputDamage(
        final double intendedDamage,
        final DoubleUnaryOperator damageAfterArmor
    ) {
        Objects.requireNonNull(damageAfterArmor, "damageAfterArmor");
        if (!Double.isFinite(intendedDamage) || intendedDamage <= 0.0) {
            return 0.0;
        }
        double lower = intendedDamage;
        double upper = intendedDamage;
        for (int expansion = 0; expansion < 12 && damageAfterArmor.applyAsDouble(upper) < intendedDamage; expansion++) {
            upper *= 2.0;
        }
        for (int refinement = 0; refinement < 24; refinement++) {
            final double candidate = (lower + upper) * 0.5;
            if (damageAfterArmor.applyAsDouble(candidate) < intendedDamage) {
                lower = candidate;
            } else {
                upper = candidate;
            }
        }
        return upper;
    }

    public static boolean canSpreadWerewolfCurse(
        final int werewolfLevel,
        final WerewolfShape shape,
        final boolean directMeleeAttack,
        final boolean nearFatalDamage,
        final boolean eligibleTarget,
        final boolean hunterProtected,
        final boolean infectionEnabled
    ) {
        return werewolfLevel >= 10
            && shape != WerewolfShape.HUMAN
            && directMeleeAttack
            && nearFatalDamage
            && eligibleTarget
            && !hunterProtected
            && infectionEnabled;
    }

    public static boolean wolfTrapParalyzes(
        final SupernaturalForm form,
        final WerewolfShape shape,
        final boolean fullMoon
    ) {
        return form == SupernaturalForm.WEREWOLF && shape != WerewolfShape.HUMAN && fullMoon;
    }

    public enum BatCommandTarget {
        GAZE,
        RETALIATION,
        NONE
    }

    public record BloodPowerCharge(Map<SupernaturalPower, Integer> charges, boolean replaced) {
        public BloodPowerCharge {
            charges = ProgressionCollections.immutableEnumMap(SupernaturalPower.class, charges);
        }
    }
}
