package com.kadamitas.warlockery.block;

import java.util.List;

public final class DemonHeartRules {
    public static final int DURATION = 2_400;
    public static final List<EffectSpec> EFFECTS = List.of(
        new EffectSpec("hunger", 1),
        new EffectSpec("speed", 2),
        new EffectSpec("health_boost", 0),
        new EffectSpec("strength", 2),
        new EffectSpec("nausea", 0),
        new EffectSpec("regeneration", 1),
        new EffectSpec("fire_resistance", 0)
    );

    private DemonHeartRules() {
    }

    public record EffectSpec(String id, int amplifier) {
    }
}
