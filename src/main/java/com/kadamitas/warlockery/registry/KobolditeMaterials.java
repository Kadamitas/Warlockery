package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

public final class KobolditeMaterials {
    public static final TagKey<Item> REPAIR_INGOTS = TagKey.create(
        Registries.ITEM,
        Identifier.fromNamespaceAndPath("c", "ingots/koboldite")
    );
    public static final ToolMaterial TOOL = new ToolMaterial(
        BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
        2031,
        9.0F,
        4.0F,
        15,
        REPAIR_INGOTS
    );
    public static final ResourceKey<EquipmentAsset> EQUIPMENT_ASSET = ResourceKey.create(
        EquipmentAssets.ROOT_ID,
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "delvealloy")
    );
    public static final ArmorMaterial ARMOR = new ArmorMaterial(
        37,
        Map.of(
            ArmorType.BOOTS, 3,
            ArmorType.LEGGINGS, 6,
            ArmorType.CHESTPLATE, 8,
            ArmorType.HELMET, 3,
            ArmorType.BODY, 19
        ),
        15,
        SoundEvents.ARMOR_EQUIP_NETHERITE,
        3.0F,
        0.1F,
        REPAIR_INGOTS,
        EQUIPMENT_ASSET
    );

    private KobolditeMaterials() {
    }
}
