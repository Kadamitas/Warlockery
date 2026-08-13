package com.kadamitas.warlockery.entity;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.Tags;

public final class SpouseAmbientTags {
    public static final TagKey<Item> COOKABLE_RAW_MEATS = Tags.Items.FOODS_RAW_MEAT;
    public static final TagKey<Block> FURNACE_WORKSTATIONS = Tags.Blocks.PLAYER_WORKSTATIONS_FURNACES;

    private SpouseAmbientTags() {
    }
}
