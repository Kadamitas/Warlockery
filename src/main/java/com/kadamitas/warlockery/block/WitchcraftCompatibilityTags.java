package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.Warlockery;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

public final class WitchcraftCompatibilityTags {
    public static final TagKey<Item> CONFIGURATION_FOCI = item("configuration_foci");
    public static final TagKey<Item> FLYING_BROOMS = item("flying_brooms");
    public static final TagKey<Block> DREAM_PROTECTIVE_PLANTS = block("dream_protective_plants");
    public static final TagKey<Fluid> DREAM_PROTECTIVE_FLUIDS = fluid("dream_protective_fluids");
    public static final TagKey<EntityType<?>> FETISH_IMMUNE = entity("fetish_immune");

    private WitchcraftCompatibilityTags() {
    }

    private static TagKey<Item> item(final String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path));
    }

    private static TagKey<Block> block(final String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path));
    }

    private static TagKey<EntityType<?>> entity(final String path) {
        return TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path));
    }

    private static TagKey<Fluid> fluid(final String path) {
        return TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path));
    }
}
