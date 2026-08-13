package com.kadamitas.warlockery.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class SpouseAmbientTags {
    public static final TagKey<Item> COOKABLE_RAW_MEATS = TagKey.create(
        Registries.ITEM,
        Identifier.fromNamespaceAndPath("c", "foods/raw_meat")
    );
    public static final TagKey<Block> FURNACE_WORKSTATIONS = TagKey.create(
        Registries.BLOCK,
        Identifier.fromNamespaceAndPath("c", "player_workstations/furnaces")
    );

    private SpouseAmbientTags() {
    }
}
