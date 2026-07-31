package com.kadamitas.warlockery.entity;

public final class HobgoblinMiningRules {
    public static final MiningProfile STANDARD = new MiningProfile(1_200, 0.01F, 0.0F, 1);
    public static final MiningProfile ENHANCED = new MiningProfile(20, 0.05F, 0.5F, 3);

    private HobgoblinMiningRules() {
    }

    public static MiningProfile profile(final boolean enhancedTool) {
        return enhancedTool ? ENHANCED : STANDARD;
    }

    public static boolean findsGoblinite(final MiningProfile profile, final float roll) {
        requireRoll(roll);
        return roll < profile.gobliniteChance();
    }

    public static int autoSmeltMultiplier(
        final MiningProfile profile,
        final float smeltingRoll,
        final float yieldRoll
    ) {
        requireRoll(smeltingRoll);
        requireRoll(yieldRoll);
        if (smeltingRoll >= profile.autoSmeltChance()) {
            return 0;
        }
        return 1 + Math.min((int) (yieldRoll * profile.maximumSmeltMultiplier()), profile.maximumSmeltMultiplier() - 1);
    }

    private static void requireRoll(final float roll) {
        if (roll < 0.0F || roll >= 1.0F) {
            throw new IllegalArgumentException("Random roll must be in [0, 1)");
        }
    }

    public record MiningProfile(
        int cooldownTicks,
        float gobliniteChance,
        float autoSmeltChance,
        int maximumSmeltMultiplier
    ) {
        public MiningProfile {
            if (cooldownTicks < 1
                || gobliniteChance < 0.0F || gobliniteChance > 1.0F
                || autoSmeltChance < 0.0F || autoSmeltChance > 1.0F
                || maximumSmeltMultiplier < 1) {
                throw new IllegalArgumentException("Invalid Hobgoblin mining profile");
            }
        }
    }
}
