package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import java.util.Map;

public final class SilverMaterials {
    public static final TagKey<Item> REPAIR_INGOTS = TagKey.create(
        Registries.ITEM,
        Identifier.fromNamespaceAndPath("c", "ingots/silver")
    );
    public static final ToolMaterial TOOL = new ToolMaterial(
        BlockTags.INCORRECT_FOR_GOLD_TOOL,
        384,
        7.0F,
        2.0F,
        18,
        REPAIR_INGOTS
    );
    public static final ResourceKey<EquipmentAsset> EQUIPMENT_ASSET = ResourceKey.create(
        EquipmentAssets.ROOT_ID,
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "silver")
    );
    public static final ArmorMaterial ARMOR = new ArmorMaterial(
        18,
        Map.of(
            ArmorType.BOOTS, 2,
            ArmorType.LEGGINGS, 5,
            ArmorType.CHESTPLATE, 6,
            ArmorType.HELMET, 2,
            ArmorType.BODY, 15
        ),
        18,
        SoundEvents.ARMOR_EQUIP_CHAIN,
        0.5F,
        0.0F,
        REPAIR_INGOTS,
        EQUIPMENT_ASSET
    );

    private SilverMaterials() {
    }
}
