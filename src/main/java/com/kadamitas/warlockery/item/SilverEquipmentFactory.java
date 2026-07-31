package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.SilverMaterials;
import com.kadamitas.warlockery.registry.FactoryCatalog;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.equipment.ArmorType;

public final class SilverEquipmentFactory {
    private static final FactoryCatalog<Item.Properties, Item> FACTORIES = new FactoryCatalog<>(
        "silver equipment",
        Map.ofEntries(
        FactoryCatalog.entry("silversword", properties -> new Item(properties.sword(SilverMaterials.TOOL, 3.0F, -2.4F))),
        FactoryCatalog.entry("silveraxe", properties -> new AxeItem(SilverMaterials.TOOL, 5.0F, -3.1F, properties)),
        FactoryCatalog.entry("silverpickaxe", properties -> new Item(properties.pickaxe(SilverMaterials.TOOL, 1.0F, -2.8F))),
        FactoryCatalog.entry("silvershovel", properties -> new ShovelItem(SilverMaterials.TOOL, 1.5F, -3.0F, properties)),
        FactoryCatalog.entry("silverhoe", properties -> new HoeItem(SilverMaterials.TOOL, -2.0F, -1.0F, properties)),
        FactoryCatalog.entry("silverhelm", properties -> new Item(properties.humanoidArmor(SilverMaterials.ARMOR, ArmorType.HELMET))),
        FactoryCatalog.entry("silverchestplate", properties -> new Item(properties.humanoidArmor(SilverMaterials.ARMOR, ArmorType.CHESTPLATE))),
        FactoryCatalog.entry("silverleggings", properties -> new Item(properties.humanoidArmor(SilverMaterials.ARMOR, ArmorType.LEGGINGS))),
        FactoryCatalog.entry("silverboots", properties ->
            new Item(properties.humanoidArmor(SilverMaterials.ARMOR, ArmorType.BOOTS)))
    ));

    private SilverEquipmentFactory() {
    }

    public static Set<String> ids() {
        return FACTORIES.ids();
    }

    public static boolean supports(final String id) {
        return FACTORIES.supports(id);
    }

    public static Item create(final Item.Properties properties, final String id) {
        return FACTORIES.create(id, properties);
    }
}
