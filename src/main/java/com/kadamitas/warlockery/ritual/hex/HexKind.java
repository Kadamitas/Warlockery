package com.kadamitas.warlockery.ritual.hex;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

public enum HexKind {
    MISFORTUNE("misfortune", effect(MobEffects.UNLUCK, 1)),
    INSANITY("insanity", effect(MobEffects.NAUSEA, 0), effect(MobEffects.DARKNESS, 0)),
    OVERHEATING("overheating", effect(MobEffects.WEAKNESS, 1)),
    SINKING("sinking", effect(MobEffects.SLOWNESS, 1), effect(MobEffects.MINING_FATIGUE, 1)),
    WAKING_NIGHTMARE("nightmare", effect(MobEffects.DARKNESS, 0), effect(MobEffects.HUNGER, 1));

    private static final Map<String, HexKind> BY_ID = Arrays.stream(values())
        .collect(Collectors.toUnmodifiableMap(HexKind::id, Function.identity()));

    private final String id;
    private final List<EffectSpec> markerEffects;

    HexKind(final String id, final EffectSpec... markerEffects) {
        this.id = id;
        this.markerEffects = List.of(markerEffects);
    }

    public String id() {
        return id;
    }

    public List<EffectSpec> markerEffects() {
        return markerEffects;
    }

    public static Optional<HexKind> find(final String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    private static EffectSpec effect(final Holder<MobEffect> effect, final int amplifier) {
        return new EffectSpec(effect, amplifier);
    }

    public record EffectSpec(Holder<MobEffect> effect, int amplifier) {
    }
}
