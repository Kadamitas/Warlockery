package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.Warlockery;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class CreatureBehaviorTags {
    private CreatureBehaviorTags() {
    }

    public static final class Items {
        public static final TagKey<Item> BANSHEE_EMPOWERMENT = create("creature_interactions/banshee_empowerment");
        public static final TagKey<Item> COMPANION_BINDERS = create("creature_interactions/companion_binders");
        public static final TagKey<Item> COVEN_OFFERINGS = create("creature_interactions/coven_offerings");
        public static final TagKey<Item> HOBGOBLIN_CONTRACTS = create("creature_interactions/hobgoblin_contracts");
        public static final TagKey<Item> HOBGOBLIN_COLLECTIBLES = create("creature_interactions/hobgoblin_collectibles");
        public static final TagKey<Item> DEMON_BARTER = create("creature_interactions/demon_barter");
        public static final TagKey<Item> INFERNAL_CONTRACTS = create("creature_interactions/infernal_contracts");
        public static final TagKey<Item> BOUND_WAYSTONES = create("creature_interactions/bound_waystones");
        public static final TagKey<Item> HEART_OFFERINGS = create("creature_interactions/heart_offerings");
        public static final TagKey<Item> VAMPIRE_INITIATION = create("creature_interactions/vampire_initiation");
        public static final TagKey<Item> PALE_STEED_BONDING = create("creature_interactions/pale_steed_bonding");
        public static final TagKey<Item> NIGHTMARE_BONDING = create("creature_interactions/nightmare_bonding");
        public static final TagKey<Item> SPECTRAL_ORE_SAMPLES = create("creature_interactions/spectral_ore_samples");
        public static final TagKey<Item> BROOMS = create("creature_interactions/brooms");
        public static final TagKey<Item> SPIRIT_BINDERS = create("creature_interactions/spirit_binders");
        public static final TagKey<Item> LOUSE_REDIRECTING_ARMOR = create("creature_interactions/louse_redirecting_armor");

        private Items() {
        }

        private static TagKey<Item> create(final String path) {
            return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path));
        }
    }

    public static final class Blocks {
        public static final TagKey<Block> LIVING_GROUND = create("creature_habitats/living_ground");
        public static final TagKey<Block> MAGICAL_CAULDRONS = create("creature_habitats/magical_cauldrons");
        public static final TagKey<Block> SPECTRAL_ORES = create("creature_habitats/spectral_ores");
        public static final TagKey<Block> HOBGOBLIN_DEPOSIT_CONTAINERS = create("creature_habitats/hobgoblin_deposit_containers");

        private Blocks() {
        }

        private static TagKey<Block> create(final String path) {
            return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path));
        }
    }

    public static final class EntityTypes {
        public static final TagKey<EntityType<?>> FAMILIARS = create("creature_families/familiars");
        public static final TagKey<EntityType<?>> GOBLINS = create("creature_families/goblins");
        public static final TagKey<EntityType<?>> CAULDRON_RANGE_EXTENDERS = create("creature_families/cauldron_range_extenders");

        private EntityTypes() {
        }

        private static TagKey<EntityType<?>> create(final String path) {
            return TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path));
        }
    }
}
