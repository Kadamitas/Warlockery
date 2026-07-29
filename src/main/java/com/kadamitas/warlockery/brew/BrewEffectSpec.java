package com.kadamitas.warlockery.brew;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;

public record BrewEffectSpec(String effect, int duration, int amplifier) {
    public static final Codec<BrewEffectSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("effect").forGetter(BrewEffectSpec::effect),
        Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("duration", 1).forGetter(BrewEffectSpec::duration),
        Codec.intRange(0, 255).optionalFieldOf("amplifier", 0).forGetter(BrewEffectSpec::amplifier)
    ).apply(instance, BrewEffectSpec::new));

    public BrewEffectSpec {
        effect = Objects.requireNonNull(effect, "effect").strip();
        if (!effect.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
            throw new IllegalArgumentException("Invalid effect id: " + effect);
        }
        if (duration < 1) {
            throw new IllegalArgumentException("Effect duration must be positive");
        }
        if (amplifier < 0 || amplifier > 255) {
            throw new IllegalArgumentException("Effect amplifier must be from 0 to 255");
        }
    }

    public MobEffectInstance resolve() {
        return BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(effect))
            .map(holder -> new MobEffectInstance(holder, duration, amplifier))
            .orElseThrow(() -> new IllegalStateException("Unknown mob effect: " + effect));
    }
}
