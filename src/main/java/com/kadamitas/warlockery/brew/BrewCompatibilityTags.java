package com.kadamitas.warlockery.brew;

import com.kadamitas.warlockery.Warlockery;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.Fluid;

public final class BrewCompatibilityTags {
    private BrewCompatibilityTags() {
    }

    public static final class Blocks {
        public static final TagKey<Block> GASES = TagKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "brew_gases")
        );
        public static final TagKey<Block> SUBSTITUTABLE = create("brew_substitutable");
        public static final TagKey<Block> SPROUTING_BRANCHES = create("brew_sprouting_branches");

        private Blocks() {
        }

        private static TagKey<Block> create(final String path) {
            return TagKey.create(
                Registries.BLOCK,
                Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path)
            );
        }
    }

    public static final class DamageTypes {
        public static final TagKey<DamageType> MAGICAL = TagKey.create(
            Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "magical_damage")
        );

        private DamageTypes() {
        }
    }

    public static final class EntityTypes {
        public static final TagKey<EntityType<?>> REINCARNATION_CANDIDATES = TagKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "reincarnation_candidates")
        );
        public static final TagKey<EntityType<?>> GROTESQUE_IMMUNE = create("grotesque_immune");
        public static final TagKey<EntityType<?>> BODEGA_TARGETS = create("bodega_targets");

        private EntityTypes() {
        }

        private static TagKey<EntityType<?>> create(final String path) {
            return TagKey.create(
                Registries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path)
            );
        }
    }

    public static final class Fluids {
        public static final TagKey<Fluid> EROSION = create("erosion_brews");
        public static final TagKey<Fluid> COLORED_WATER = create("colored_brew_water");

        private Fluids() {
        }

        private static TagKey<Fluid> create(final String path) {
            return TagKey.create(
                Registries.FLUID,
                Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path)
            );
        }
    }

    public static final class Biomes {
        public static final TagKey<Biome> SPRING = create("brew_seasons/spring");
        public static final TagKey<Biome> SUMMER = create("brew_seasons/summer");
        public static final TagKey<Biome> AUTUMN = create("brew_seasons/autumn");
        public static final TagKey<Biome> WINTER = create("brew_seasons/winter");

        private Biomes() {
        }

        public static TagKey<Biome> season(final int season) {
            return switch (Math.floorMod(season, 4)) {
                case 0 -> SPRING;
                case 1 -> SUMMER;
                case 2 -> AUTUMN;
                default -> WINTER;
            };
        }

        private static TagKey<Biome> create(final String path) {
            return TagKey.create(
                Registries.BIOME,
                Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path)
            );
        }
    }
}
