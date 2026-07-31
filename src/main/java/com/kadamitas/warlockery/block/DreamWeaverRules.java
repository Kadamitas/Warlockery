package com.kadamitas.warlockery.block;

import java.util.List;

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
        return reward(mode, nightmareProtected, mode == DreamWeaverMode.NIGHTMARES ? 1 : 0, 0);
    }

    public static WakeReward reward(
        final DreamWeaverMode mode,
        final boolean nightmareProtected,
        final int nightmareWeavers
    ) {
        return reward(mode, nightmareProtected, nightmareWeavers, 0);
    }

    public static WakeReward reward(
        final DreamWeaverMode mode,
        final boolean nightmareProtected,
        final int nightmareWeavers,
        final int intensityWeavers
    ) {
        if (nightmareWeavers < 0 || intensityWeavers < 0) {
            throw new IllegalArgumentException("Dream Weaver counts must be nonnegative");
        }
        final boolean corrupted = nightmareWeavers > 0 && !nightmareProtected;
        final boolean intensified = intensityWeavers > 0 && !corrupted;
        return switch (mode) {
            case RESTORATION -> reward(corrupted ? "poison" : "regeneration", 600, corrupted ? 0 : 1);
            case FASTING -> corrupted
                ? corrupted("hunger", 2_400, 1)
                : new WakeReward(
                    intensified ? 12 : 8,
                    intensified ? 1.5F : 1.0F,
                    List.of(new EffectReward("saturation", 1, intensified ? 2 : 1)),
                    false,
                    false
                );
            case FLEET_FOOT -> corrupted
                ? corrupted("slowness", 2_400, 1)
                : reward("speed", intensified ? 1_800 : 2_400, intensified ? 2 : 1);
            case INTENSITY -> reward(corrupted ? "darkness" : "night_vision", 300, 0);
            case IRON_ARM -> corrupted
                ? corrupted("mining_fatigue", 2_400, 1)
                : reward("haste", intensified ? 1_800 : 2_400, intensified ? 2 : 1);
            case NIGHTMARES -> new WakeReward(
                0,
                0.0F,
                nightmareProtected
                    ? List.of(new EffectReward("absorption", 2_400, 1))
                    : nightmareWeavers >= 2
                        ? List.of(
                            new EffectReward("blindness", 600, 0),
                            new EffectReward("weakness", 2_400, 1)
                        )
                        : List.of(new EffectReward("weakness", 2_400, 1)),
                false,
                nightmareProtected
            );
        };
    }

    private static WakeReward reward(final String effect, final int duration, final int amplifier) {
        return new WakeReward(0, 0.0F, List.of(new EffectReward(effect, duration, amplifier)), false, false);
    }

    private static WakeReward corrupted(final String effect, final int duration, final int amplifier) {
        return new WakeReward(
            0,
            0.0F,
            List.of(
                new EffectReward(effect, duration, amplifier),
                new EffectReward("weakness", 2_400, 1)
            ),
            false,
            false
        );
    }

    public record WakeReward(
        int nutrition,
        float saturationModifier,
        List<EffectReward> effects,
        boolean spawnNightmare,
        boolean protectedDream
    ) {
        public WakeReward(
            final int nutrition,
            final float saturationModifier,
            final String effect,
            final boolean spawnNightmare,
            final boolean protectedDream
        ) {
            this(
                nutrition,
                saturationModifier,
                List.of(new EffectReward(effect, 2_400, 1)),
                spawnNightmare,
                protectedDream
            );
        }

        public WakeReward {
            effects = List.copyOf(effects);
            if (nutrition < 0 || saturationModifier < 0.0F || effects.isEmpty()) {
                throw new IllegalArgumentException("Wake reward values must be valid");
            }
        }

        public String effect() {
            return effects.getFirst().id();
        }
    }

    public record EffectReward(String id, int duration, int amplifier) {
        public EffectReward {
            if (id.isBlank() || duration < 1 || amplifier < 0) {
                throw new IllegalArgumentException("Dream effects require an id, duration, and nonnegative amplifier");
            }
        }
    }
}
