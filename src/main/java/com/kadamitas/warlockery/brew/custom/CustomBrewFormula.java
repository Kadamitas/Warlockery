package com.kadamitas.warlockery.brew.custom;

import com.kadamitas.warlockery.brew.BrewBehavior;
import com.kadamitas.warlockery.brew.BrewEffectSpec;
import com.kadamitas.warlockery.brew.BrewKind;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.PotionContents;

public record CustomBrewFormula(
    List<String> components,
    List<String> selectedEffects,
    CustomBrewDelivery delivery,
    List<BrewEffectSpec> effects,
    List<BrewBehavior> behaviors,
    int capacity,
    int capacityCost,
    int powerLevel,
    int durationMultiplier,
    int extent,
    int lingering,
    int altarPower,
    int color,
    float radius,
    float potency,
    boolean hideParticles,
    boolean skipBlocks,
    boolean skipEntities,
    boolean uncappedDamage,
    int quaff
) {
    public static final int MAX_COMPONENTS = 24;
    public static final int MAX_PAYLOADS = 12;
    public static final Codec<CustomBrewFormula> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.listOf().fieldOf("components").forGetter(CustomBrewFormula::components),
        Codec.STRING.listOf().fieldOf("selected_effects").forGetter(CustomBrewFormula::selectedEffects),
        CustomBrewDelivery.CODEC.fieldOf("delivery").forGetter(CustomBrewFormula::delivery),
        Payload.CODEC.fieldOf("payload").forGetter(CustomBrewFormula::payload),
        Metrics.CODEC.fieldOf("metrics").forGetter(CustomBrewFormula::metrics),
        Options.CODEC.fieldOf("options").forGetter(CustomBrewFormula::options)
    ).apply(instance, CustomBrewFormula::fromParts));

    public CustomBrewFormula {
        components = List.copyOf(components);
        selectedEffects = List.copyOf(selectedEffects);
        effects = List.copyOf(effects);
        behaviors = List.copyOf(behaviors);
        if (components.size() > MAX_COMPONENTS || effects.size() > MAX_PAYLOADS || behaviors.size() > MAX_PAYLOADS) {
            throw new IllegalArgumentException("Custom brew formula exceeds execution bounds");
        }
    }

    public PotionContents potionContents() {
        final List<MobEffectInstance> resolved = effects.stream().map(effect -> {
            final MobEffectInstance instance = effect.resolve();
            return new MobEffectInstance(
                instance.getEffect(),
                instance.getDuration(),
                instance.getAmplifier(),
                false,
                !hideParticles,
                true
            );
        }).toList();
        return new PotionContents(Optional.empty(), Optional.of(color), resolved, Optional.of("warlockery_custom"));
    }

    public BrewKind behaviorKind() {
        return new BrewKind(
            "custom/composed",
            color,
            List.of(),
            behaviors.stream()
                .filter(behavior -> behavior != BrewBehavior.BOTTLE_YIELD)
                .filter(behavior -> CustomBrewBehaviorTargets.allows(behavior, skipBlocks, skipEntities))
                .toList(),
            radius,
            potency
        );
    }

    public int outputCount() {
        return behaviors.contains(BrewBehavior.BOTTLE_YIELD) ? Math.clamp(3 + powerLevel, 3, 8) : 1;
    }

    public float drinkSeconds() {
        return Math.max(0.4F, 1.6F - quaff * 0.2F);
    }

    private Payload payload() {
        return new Payload(effects, behaviors, color, radius, potency);
    }

    private Metrics metrics() {
        return new Metrics(capacity, capacityCost, powerLevel, durationMultiplier, extent, lingering, altarPower);
    }

    private Options options() {
        return new Options(hideParticles, skipBlocks, skipEntities, uncappedDamage, quaff);
    }

    private static CustomBrewFormula fromParts(
        final List<String> components,
        final List<String> selectedEffects,
        final CustomBrewDelivery delivery,
        final Payload payload,
        final Metrics metrics,
        final Options options
    ) {
        return new CustomBrewFormula(
            components,
            selectedEffects,
            delivery,
            payload.effects(),
            payload.behaviors(),
            metrics.capacity(),
            metrics.capacityCost(),
            metrics.powerLevel(),
            metrics.durationMultiplier(),
            metrics.extent(),
            metrics.lingering(),
            metrics.altarPower(),
            payload.color(),
            payload.radius(),
            payload.potency(),
            options.hideParticles(),
            options.skipBlocks(),
            options.skipEntities(),
            options.uncappedDamage(),
            options.quaff()
        );
    }

    private record Payload(
        List<BrewEffectSpec> effects,
        List<BrewBehavior> behaviors,
        int color,
        float radius,
        float potency
    ) {
        private static final Codec<Payload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BrewEffectSpec.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(Payload::effects),
            BrewBehavior.CODEC.listOf().optionalFieldOf("behaviors", List.of()).forGetter(Payload::behaviors),
            Codec.intRange(0, 0xFFFFFF).fieldOf("color").forGetter(Payload::color),
            Codec.floatRange(0.5F, 12.0F).fieldOf("radius").forGetter(Payload::radius),
            Codec.floatRange(0.1F, 8.0F).fieldOf("potency").forGetter(Payload::potency)
        ).apply(instance, Payload::new));
    }

    private record Metrics(
        int capacity,
        int capacityCost,
        int powerLevel,
        int durationMultiplier,
        int extent,
        int lingering,
        int altarPower
    ) {
        private static final Codec<Metrics> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 64).fieldOf("capacity").forGetter(Metrics::capacity),
            Codec.intRange(0, 64).fieldOf("capacity_cost").forGetter(Metrics::capacityCost),
            Codec.intRange(0, 8).fieldOf("power_level").forGetter(Metrics::powerLevel),
            Codec.intRange(1, 16).fieldOf("duration_multiplier").forGetter(Metrics::durationMultiplier),
            Codec.intRange(1, 4).fieldOf("extent").forGetter(Metrics::extent),
            Codec.intRange(1, 4).fieldOf("lingering").forGetter(Metrics::lingering),
            Codec.intRange(0, 50_000).fieldOf("altar_power").forGetter(Metrics::altarPower)
        ).apply(instance, Metrics::new));
    }

    private record Options(
        boolean hideParticles,
        boolean skipBlocks,
        boolean skipEntities,
        boolean uncappedDamage,
        int quaff
    ) {
        private static final Codec<Options> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("hide_particles", false).forGetter(Options::hideParticles),
            Codec.BOOL.optionalFieldOf("skip_blocks", false).forGetter(Options::skipBlocks),
            Codec.BOOL.optionalFieldOf("skip_entities", false).forGetter(Options::skipEntities),
            Codec.BOOL.optionalFieldOf("uncapped_damage", false).forGetter(Options::uncappedDamage),
            Codec.intRange(0, 6).optionalFieldOf("quaff", 0).forGetter(Options::quaff)
        ).apply(instance, Options::new));
    }
}
