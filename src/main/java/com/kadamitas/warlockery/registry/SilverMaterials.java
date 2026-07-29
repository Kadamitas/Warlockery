package com.kadamitas.warlockery.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

public final class SilverMaterials {
    public static final TagKey<Item> REPAIR_INGOTS = TagKey.create(
        Registries.ITEM,
        Identifier.fromNamespaceAndPath("c", "ingots/silver")
    );
    public static final ToolMaterial TOOL = new ToolMaterial(
        BlockTags.INCORRECT_FOR_GOLD_TOOL,
        32,
        12.0F,
        0.0F,
        9,
        REPAIR_INGOTS
    );

    private SilverMaterials() {
    }
}
