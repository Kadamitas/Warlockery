package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.Warlockery;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ResourceCompatibilityTags {
    private ResourceCompatibilityTags() {
    }

    public static final class Blocks {
        public static final TagKey<Block> ALTAR_POWER_HEARTS = create("altar_power_hearts");

        private Blocks() {
        }

        private static TagKey<Block> create(final String path) {
            return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path));
        }
    }

    public static final class EntityTypes {
        public static final TagKey<EntityType<?>> CRITTER_SNARE_TARGETS = create("critter_snare_targets");
        public static final TagKey<EntityType<?>> PLANT_GUARDIANS = create("plant_guardians");

        private EntityTypes() {
        }

        private static TagKey<EntityType<?>> create(final String path) {
            return TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path));
        }
    }

    public static final class Items {
        public static final TagKey<Item> BLOOD_SOURCES = create("blood_sources");
        public static final TagKey<Item> SAFE_MAGICAL_PLANT_TOOLS = create("safe_magical_plant_tools");

        private Items() {
        }

        private static TagKey<Item> create(final String path) {
            return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path));
        }
    }
}
