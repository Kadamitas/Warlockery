package com.kadamitas.warlockery.block;

public final class DreamWeaverRules {
    public static final int SEARCH_RADIUS = 8;
    public static final int REQUIRED_SLEEP_TICKS = 100;

    private DreamWeaverRules() {
    }

    public static boolean canReward(
        final boolean serverSide,
        final int sleepTicks,
        final boolean forcedWake,
        final boolean weaverFound
    ) {
        return serverSide && sleepTicks >= REQUIRED_SLEEP_TICKS && !forcedWake && weaverFound;
    }

    public static WakeReward reward(final DreamWeaverMode mode, final boolean nightmareProtected) {
        return switch (mode) {
            case RESTORATION -> new WakeReward(0, 0.0F, "regeneration", false, false);
            case FASTING -> new WakeReward(8, 1.0F, "saturation", false, false);
            case FLEET_FOOT -> new WakeReward(0, 0.0F, "speed", false, false);
            case INTENSITY -> new WakeReward(0, 0.0F, "night_vision", false, false);
            case IRON_ARM -> new WakeReward(0, 0.0F, "strength", false, false);
            case NIGHTMARES -> new WakeReward(
                0,
                0.0F,
                nightmareProtected ? "absorption" : "darkness",
                !nightmareProtected,
                nightmareProtected
            );
        };
    }

    public record WakeReward(
        int nutrition,
        float saturationModifier,
        String effect,
        boolean spawnNightmare,
        boolean protectedDream
    ) {
        public WakeReward {
            if (nutrition < 0 || saturationModifier < 0.0F || effect.isBlank()) {
                throw new IllegalArgumentException("Wake reward values must be valid");
            }
        }
    }
}
