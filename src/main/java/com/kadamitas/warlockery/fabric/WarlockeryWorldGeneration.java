package com.kadamitas.warlockery.fabric;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.List;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public final class WarlockeryWorldGeneration {
    private static final List<ResourceKey<Biome>> FOREST_CREATURE_BIOMES = List.of(
        Biomes.FOREST,
        Biomes.FLOWER_FOREST,
        Biomes.BIRCH_FOREST,
        Biomes.OLD_GROWTH_BIRCH_FOREST,
        Biomes.DARK_FOREST,
        Biomes.CHERRY_GROVE,
        Biomes.PALE_GARDEN,
        Biomes.JUNGLE,
        Biomes.SPARSE_JUNGLE,
        Biomes.BAMBOO_JUNGLE,
        Biomes.TAIGA,
        Biomes.OLD_GROWTH_SPRUCE_TAIGA,
        Biomes.SAVANNA,
        Biomes.MANGROVE_SWAMP,
        Biomes.SWAMP,
        Biomes.PLAINS
    );

    private WarlockeryWorldGeneration() {
    }

    public static void initialize() {
        addFeature(WarlockeryTags.Biomes.HAS_ALDER_TREES, "alder_tree", GenerationStep.Decoration.VEGETAL_DECORATION);
        addFeature(WarlockeryTags.Biomes.HAS_HAWTHORN_TREES, "hawthorn_tree", GenerationStep.Decoration.VEGETAL_DECORATION);
        addFeature(WarlockeryTags.Biomes.HAS_ROWAN_TREES, "rowan_tree", GenerationStep.Decoration.VEGETAL_DECORATION);
        BiomeModifications.addFeature(
            BiomeSelectors.foundInOverworld(),
            GenerationStep.Decoration.UNDERGROUND_ORES,
            placedFeature("silver_ore")
        );
        BiomeModifications.addFeature(
            BiomeSelectors.foundInOverworld(),
            GenerationStep.Decoration.UNDERGROUND_ORES,
            placedFeature("delvealloy_ore")
        );

        addSpawn(BiomeSelectors.foundInOverworld(), "mandrake", 1, 1, 1);
        final var forests = BiomeSelectors.includeByKey(FOREST_CREATURE_BIOMES);
        addSpawn(forests, "ent", 2, 1, 1);
        addSpawn(forests, "goblin", 3, 1, 3);
        addSpawn(forests, "hobgoblin", 5, 1, 3);
        addSpawn(BiomeSelectors.foundInTheNether(), "hellhound", 4, 1, 3);
        addSpawn(BiomeSelectors.tag(WarlockeryTags.Biomes.SPIRIT_HABITATS), "spirit", 2, 1, 3);
    }

    private static void addFeature(
        final net.minecraft.tags.TagKey<Biome> biomes,
        final String feature,
        final GenerationStep.Decoration step
    ) {
        BiomeModifications.addFeature(BiomeSelectors.tag(biomes), step, placedFeature(feature));
    }

    private static void addSpawn(
        final java.util.function.Predicate<net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext> biomes,
        final String entityId,
        final int weight,
        final int minimum,
        final int maximum
    ) {
        final EntityType<?> type = ModEntities.ALL.get(entityId).get();
        BiomeModifications.addSpawn(biomes, type.getCategory(), type, weight, minimum, maximum);
    }

    private static ResourceKey<PlacedFeature> placedFeature(final String id) {
        return ResourceKey.create(
            Registries.PLACED_FEATURE,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, id)
        );
    }
}
