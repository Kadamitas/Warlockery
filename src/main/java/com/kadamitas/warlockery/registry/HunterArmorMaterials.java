package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

public final class HunterArmorMaterials {
    private static final TagKey<Item> LEATHER = repairTag("leathers");
    private static final TagKey<Item> SILVER = repairTag("ingots/silver");
    private static final TagKey<Item> GOLD = repairTag("ingots/gold");
    public static final ArmorMaterial HUNTER = material(
        15, 2, 6, 5, 2, 15, 0.0F, LEATHER, "werewolf_hunter", SoundEvents.ARMOR_EQUIP_LEATHER
    );
    public static final ArmorMaterial SILVERED = material(
        20, 2, 6, 5, 2, 12, 1.0F, SILVER, "werewolf_hunter_silvered", SoundEvents.ARMOR_EQUIP_CHAIN
    );
    public static final ArmorMaterial DAWN = material(
        33, 3, 8, 6, 3, 10, 2.0F, GOLD, "werewolf_hunter_dawn", SoundEvents.ARMOR_EQUIP_DIAMOND
    );

    private HunterArmorMaterials() {
    }

    public static ArmorMaterial forItem(final String id) {
        if (id.endsWith("_dawn")) {
            return DAWN;
        }
        return id.endsWith("_silvered") ? SILVERED : HUNTER;
    }

    private static ArmorMaterial material(
        final int durability,
        final int helmet,
        final int chestplate,
        final int leggings,
        final int boots,
        final int enchantment,
        final float toughness,
        final TagKey<Item> repair,
        final String asset,
        final net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound
    ) {
        final ResourceKey<EquipmentAsset> equipmentAsset = ResourceKey.create(
            EquipmentAssets.ROOT_ID,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, asset)
        );
        return new ArmorMaterial(
            durability,
            Map.of(
                ArmorType.BOOTS, boots,
                ArmorType.LEGGINGS, leggings,
                ArmorType.CHESTPLATE, chestplate,
                ArmorType.HELMET, helmet,
                ArmorType.BODY, chestplate + leggings
            ),
            enchantment,
            sound,
            toughness,
            0.0F,
            repair,
            equipmentAsset
        );
    }

    private static TagKey<Item> repairTag(final String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
    }
}
