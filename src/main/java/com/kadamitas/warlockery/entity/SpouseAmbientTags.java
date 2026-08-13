package com.kadamitas.warlockery.entity;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;

public final class SpouseAmbientTags {
    public static final TagKey<Item> COOKABLE_RAW_MEATS = ConventionalItemTags.RAW_MEAT_FOODS;
    public static final TagKey<Block> FURNACE_WORKSTATIONS = ConventionalBlockTags.PLAYER_WORKSTATIONS_FURNACES;

    private SpouseAmbientTags() {
    }
}


