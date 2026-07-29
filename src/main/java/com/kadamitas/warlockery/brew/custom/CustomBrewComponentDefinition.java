package com.kadamitas.warlockery.brew.custom;

import com.kadamitas.warlockery.brew.BrewBehavior;
import com.kadamitas.warlockery.brew.BrewEffectSpec;
import com.kadamitas.warlockery.brew.BrewKind;
import com.kadamitas.warlockery.util.ItemIngredient;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.Identifier;

public record CustomBrewComponentDefinition(
    String ingredient,
    CustomBrewComponentRole role,
    int value,
    float multiplier,
    CustomBrewModifier modifier,
    Optional<CustomBrewDelivery> delivery,
    Optional<String> sourceBrew,
    Optional<String> inverseBrew,
    List<BrewEffectSpec> effects,
    List<BrewBehavior> behaviors,
    int capacityCost,
    int color,
    int priority
) {
    public static final Codec<CustomBrewComponentDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("ingredient").forGetter(CustomBrewComponentDefinition::ingredient),
        CustomBrewComponentRole.CODEC.fieldOf("role").forGetter(CustomBrewComponentDefinition::role),
        Codec.intRange(0, 64).optionalFieldOf("value", 0).forGetter(CustomBrewComponentDefinition::value),
        Codec.floatRange(1.0F, 16.0F).optionalFieldOf("multiplier", 1.0F)
            .forGetter(CustomBrewComponentDefinition::multiplier),
        CustomBrewModifier.CODEC.optionalFieldOf("modifier", CustomBrewModifier.NONE)
            .forGetter(CustomBrewComponentDefinition::modifier),
        CustomBrewDelivery.CODEC.optionalFieldOf("delivery").forGetter(CustomBrewComponentDefinition::delivery),
        Codec.STRING.optionalFieldOf("source_brew").forGetter(CustomBrewComponentDefinition::sourceBrew),
        Codec.STRING.optionalFieldOf("inverse_brew").forGetter(CustomBrewComponentDefinition::inverseBrew),
        BrewEffectSpec.CODEC.listOf().optionalFieldOf("effects", List.of())
            .forGetter(CustomBrewComponentDefinition::effects),
        BrewBehavior.CODEC.listOf().optionalFieldOf("behaviors", List.of())
            .forGetter(CustomBrewComponentDefinition::behaviors),
        Codec.intRange(0, 16).optionalFieldOf("capacity_cost", 0)
            .forGetter(CustomBrewComponentDefinition::capacityCost),
        Codec.intRange(0, 0xFFFFFF).optionalFieldOf("color", 0x385A46)
            .forGetter(CustomBrewComponentDefinition::color),
        Codec.intRange(-1000, 1000).optionalFieldOf("priority", 0)
            .forGetter(CustomBrewComponentDefinition::priority)
    ).apply(instance, CustomBrewComponentDefinition::new));

    public CustomBrewComponentDefinition {
        ingredient = Objects.requireNonNull(ingredient, "ingredient").strip();
        role = Objects.requireNonNull(role, "role");
        modifier = Objects.requireNonNull(modifier, "modifier");
        delivery = Objects.requireNonNull(delivery, "delivery");
        sourceBrew = sourceBrew.map(String::strip);
        inverseBrew = inverseBrew.map(String::strip);
        effects = List.copyOf(effects);
        behaviors = List.copyOf(behaviors);
        if (ItemIngredient.parse(ingredient).isEmpty()) {
            throw new IllegalArgumentException("Invalid custom brew ingredient: " + ingredient);
        }
        sourceBrew.ifPresent(id -> {
            if (!id.matches("[a-z0-9_./-]+")) {
                throw new IllegalArgumentException("Invalid source brew: " + id);
            }
        });
        inverseBrew.ifPresent(id -> {
            if (!id.matches("[a-z0-9_./-]+")) {
                throw new IllegalArgumentException("Invalid inverse brew: " + id);
            }
        });
        if (!structurallyValid(role, value, multiplier, modifier, delivery, sourceBrew, effects, behaviors, capacityCost)) {
            throw new IllegalArgumentException("Component fields do not match role " + role.id());
        }
    }

    public boolean structurallyValid() {
        return structurallyValid(
            role,
            value,
            multiplier,
            modifier,
            delivery,
            sourceBrew,
            effects,
            behaviors,
            capacityCost
        );
    }

    private static boolean structurallyValid(
        final CustomBrewComponentRole role,
        final int value,
        final float multiplier,
        final CustomBrewModifier modifier,
        final Optional<CustomBrewDelivery> delivery,
        final Optional<String> sourceBrew,
        final List<BrewEffectSpec> effects,
        final List<BrewBehavior> behaviors,
        final int capacityCost
    ) {
        return switch (role) {
            case CAPACITY, POWER, EXTENT, LINGERING -> value > 0;
            case DURATION -> multiplier > 1.0F;
            case MODIFIER -> modifier != CustomBrewModifier.NONE;
            case DELIVERY -> delivery.isPresent();
            case EFFECT -> capacityCost > 0 && (sourceBrew.isPresent() || !effects.isEmpty() || !behaviors.isEmpty());
            case CONTAINER -> true;
        };
    }

    public boolean resolvable() {
        final boolean item = ItemIngredient.parse(ingredient).filter(ItemIngredient::isResolvable).isPresent();
        final boolean brew = sourceBrew.map(BrewKind::find).map(Optional::isPresent).orElse(true);
        final boolean inverse = inverseBrew.map(BrewKind::find).map(Optional::isPresent).orElse(true);
        return item && brew && inverse && effects.stream().allMatch(effect -> Identifier.tryParse(effect.effect()) != null);
    }

    public boolean hasEffectPayload() {
        return sourceBrew.isPresent() || !effects.isEmpty() || !behaviors.isEmpty();
    }

    public Payload payload(final boolean inverted) {
        final Optional<String> selected = inverted ? inverseBrew : sourceBrew;
        if (inverted && selected.isEmpty()) {
            return Payload.EMPTY;
        }
        return selected.flatMap(BrewKind::find)
            .map(kind -> new Payload(kind.effects(), kind.behaviors(), kind.radius(), kind.potency(), kind.color()))
            .orElseGet(() -> new Payload(effects, behaviors, 4.0F, 1.0F, color));
    }

    public record Payload(
        List<BrewEffectSpec> effects,
        List<BrewBehavior> behaviors,
        float radius,
        float potency,
        int color
    ) {
        public static final Payload EMPTY = new Payload(List.of(), List.of(), 4.0F, 1.0F, 0x385A46);

        public Payload {
            effects = List.copyOf(effects);
            behaviors = List.copyOf(behaviors);
        }

        public boolean empty() {
            return effects.isEmpty() && behaviors.isEmpty();
        }
    }
}
