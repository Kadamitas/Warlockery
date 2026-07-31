package com.kadamitas.warlockery.mutation;

import com.kadamitas.warlockery.Warlockery;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

public final class AdvancedMutationTags {
    private AdvancedMutationTags() {
    }

    public static final class Blocks {
        public static final TagKey<Block> COBWEBS = create("mutation/cobwebs");
        public static final TagKey<Block> GRASSPERS = create("mutation/grasspers");
        public static final TagKey<Block> SLIME_SNARES = create("mutation/toad/slime_snares");
        public static final TagKey<Block> BAT_SNARES = create("mutation/owl/bat_snares");
        public static final TagKey<Block> MANDRAKE_CROPS = create("mutation/minedrake/mandrake_crops");
        public static final TagKey<Block> SPRIG_DIRT = create("mutation/sprig/dirt");
        public static final TagKey<Block> SPRIG_MYCELIUM = create("mutation/sprig/mycelium");
        public static final TagKey<Block> SPRIG_CLAY = create("mutation/sprig/clay");

        private Blocks() {
        }

        private static TagKey<Block> create(final String path) {
            return TagKey.create(Registries.BLOCK, id(path));
        }
    }

    public static final class Items {
        public static final TagKey<Item> MUTANDIS_EXTREMIS = create("mutation/mutandis_extremis");
        public static final TagKey<Item> FOCUSED_WILL = create("mutation/focused_will");
        public static final TagKey<Item> CHARGED_ATTUNED_STONES = create("mutation/charged_attuned_stones");

        private Items() {
        }

        private static TagKey<Item> create(final String path) {
            return TagKey.create(Registries.ITEM, id(path));
        }
    }

    public static final class EntityTypes {
        public static final TagKey<EntityType<?>> TOAD_HOSTS = create("mutation/toad/hosts");
        public static final TagKey<EntityType<?>> OWL_HOSTS = create("mutation/owl/hosts");
        public static final TagKey<EntityType<?>> CREEPER_HOSTS = create("mutation/minedrake/creeper_hosts");
        public static final TagKey<EntityType<?>> LIVING_MANDRAKES = create("mutation/minedrake/living_mandrakes");

        private EntityTypes() {
        }

        private static TagKey<EntityType<?>> create(final String path) {
            return TagKey.create(Registries.ENTITY_TYPE, id(path));
        }
    }

    public static final class Fluids {
        public static final TagKey<Fluid> MUTATION_WATER = TagKey.create(
            Registries.FLUID,
            id("mutation/water")
        );

        private Fluids() {
        }
    }

    private static Identifier id(final String path) {
        return Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path);
    }
}
