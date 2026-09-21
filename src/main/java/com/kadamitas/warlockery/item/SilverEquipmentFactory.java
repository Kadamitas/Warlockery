package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.SilverMaterials;
import com.kadamitas.warlockery.registry.FactoryCatalog;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorType;

public final class SilverEquipmentFactory {
    private static final FactoryCatalog<Item.Properties, Item> FACTORIES = new FactoryCatalog<>(
        "silver equipment",
        Map.ofEntries(
        FactoryCatalog.entry("silversword", properties -> new Item(properties.sword(SilverMaterials.TOOL, 3.0F, -2.4F))),
        FactoryCatalog.entry("silveraxe", properties -> new Item(properties.axe(SilverMaterials.TOOL, 5.0F, -3.1F))),
        FactoryCatalog.entry("silverpickaxe", properties -> new Item(properties.pickaxe(SilverMaterials.TOOL, 1.0F, -2.8F))),
        FactoryCatalog.entry("silvershovel", properties -> new Item(properties.shovel(SilverMaterials.TOOL, 1.5F, -3.0F))),
        FactoryCatalog.entry("silverhoe", properties -> new Item(properties.hoe(SilverMaterials.TOOL, -2.0F, -1.0F))),
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
