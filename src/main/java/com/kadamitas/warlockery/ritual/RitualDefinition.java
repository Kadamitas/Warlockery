package com.kadamitas.warlockery.ritual;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.kadamitas.warlockery.util.CountedIngredient;
import java.util.List;
import java.util.Map;

public record RitualDefinition(
    String action,
    String effect,
    int power,
    int radius,
    int duration,
    int amplifier,
    Map<String, Integer> glyphs,
    boolean nightOnly,
    int castingTime,
    String target,
    int count,
    String title,
    String description,
    boolean visible,
    Requirements requirements
) {
    public RitualDefinition {
        glyphs = Map.copyOf(glyphs);
    }

    public static final Codec<RitualDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("action").forGetter(RitualDefinition::action),
        Codec.STRING.optionalFieldOf("effect", "").forGetter(RitualDefinition::effect),
        Codec.INT.optionalFieldOf("power", 500).forGetter(RitualDefinition::power),
        Codec.INT.optionalFieldOf("radius", 6).forGetter(RitualDefinition::radius),
        Codec.INT.optionalFieldOf("duration", 600).forGetter(RitualDefinition::duration),
        Codec.INT.optionalFieldOf("amplifier", 0).forGetter(RitualDefinition::amplifier),
        Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("glyphs", Map.of()).forGetter(RitualDefinition::glyphs),
        Codec.BOOL.optionalFieldOf("night_only", false).forGetter(RitualDefinition::nightOnly),
        Codec.INT.optionalFieldOf("casting_time", 80).forGetter(RitualDefinition::castingTime),
        Codec.STRING.optionalFieldOf("target", "").forGetter(RitualDefinition::target),
        Codec.INT.optionalFieldOf("count", 1).forGetter(RitualDefinition::count),
        Codec.STRING.optionalFieldOf("title", "").forGetter(RitualDefinition::title),
        Codec.STRING.optionalFieldOf("description", "").forGetter(RitualDefinition::description),
        Codec.BOOL.optionalFieldOf("visible", true).forGetter(RitualDefinition::visible),
        Requirements.CODEC.optionalFieldOf("requirements", Requirements.EMPTY).forGetter(RitualDefinition::requirements)
    ).apply(instance, RitualDefinition::new));

    public RitualDefinition(
        final String action,
        final String effect,
        final int power,
        final int radius,
        final int duration,
        final int amplifier,
        final Map<String, Integer> glyphs,
        final boolean nightOnly,
        final int castingTime,
        final String target,
        final int count
    ) {
        this(action, effect, power, radius, duration, amplifier, glyphs, nightOnly, castingTime, target, count,
            "", "", true, Requirements.EMPTY);
    }

    public record Requirements(
        List<Ingredient> ingredients,
        List<EntityRequirement> entities,
        boolean dayOnly,
        boolean fullMoon,
        boolean raining,
        boolean thundering,
        String dimension,
        int minimumPlayers
    ) {
        public static final Requirements EMPTY = new Requirements(List.of(), List.of(), false, false, false, false, "", 1);
        public static final Codec<Requirements> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Ingredient.CODEC.listOf().optionalFieldOf("ingredients", List.of()).forGetter(Requirements::ingredients),
            EntityRequirement.CODEC.listOf().optionalFieldOf("entities", List.of()).forGetter(Requirements::entities),
            Codec.BOOL.optionalFieldOf("day_only", false).forGetter(Requirements::dayOnly),
            Codec.BOOL.optionalFieldOf("full_moon", false).forGetter(Requirements::fullMoon),
            Codec.BOOL.optionalFieldOf("raining", false).forGetter(Requirements::raining),
            Codec.BOOL.optionalFieldOf("thundering", false).forGetter(Requirements::thundering),
            Codec.STRING.optionalFieldOf("dimension", "").forGetter(Requirements::dimension),
            Codec.INT.optionalFieldOf("minimum_players", 1).forGetter(Requirements::minimumPlayers)
        ).apply(instance, Requirements::new));

        public Requirements {
            ingredients = List.copyOf(ingredients);
            entities = List.copyOf(entities);
        }

        public Requirements(
            final List<Ingredient> ingredients,
            final boolean dayOnly,
            final boolean fullMoon,
            final boolean raining,
            final boolean thundering,
            final String dimension,
            final int minimumPlayers
        ) {
            this(ingredients, List.of(), dayOnly, fullMoon, raining, thundering, dimension, minimumPlayers);
        }
    }

    public record Ingredient(String ingredient, int count, boolean consume) implements CountedIngredient {
        public static final Codec<Ingredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("ingredient").forGetter(Ingredient::ingredient),
            Codec.INT.optionalFieldOf("count", 1).forGetter(Ingredient::count),
            Codec.BOOL.optionalFieldOf("consume", true).forGetter(Ingredient::consume)
        ).apply(instance, Ingredient::new));

        public Ingredient {
            ingredient = ingredient.strip();
        }
    }

    public record EntityRequirement(String entity, int count, boolean consume) {
        public static final Codec<EntityRequirement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("entity").forGetter(EntityRequirement::entity),
            Codec.INT.optionalFieldOf("count", 1).forGetter(EntityRequirement::count),
            Codec.BOOL.optionalFieldOf("consume", false).forGetter(EntityRequirement::consume)
        ).apply(instance, EntityRequirement::new));

        public EntityRequirement {
            entity = entity.strip();
        }
    }
}
