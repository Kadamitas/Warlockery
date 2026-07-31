package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.GobliniteMaterials;
import com.kadamitas.warlockery.registry.FactoryCatalog;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.ArmorType;

public final class GobliniteEquipmentFactory {
    private static final FactoryCatalog<Item.Properties, Item> FACTORIES = new FactoryCatalog<>(
        "Goblinite equipment",
        Map.ofEntries(
        FactoryCatalog.entry("delvealloysword", properties -> new Item(
            properties.fireResistant().sword(GobliniteMaterials.TOOL, 3.0F, -2.4F)
        )),
        FactoryCatalog.entry("delvealloyaxe", properties -> new AxeItem(
            GobliniteMaterials.TOOL,
            5.0F,
            -3.0F,
            properties.fireResistant()
        )),
        FactoryCatalog.entry("delvealloypickaxe", properties -> new Item(
            properties.fireResistant()
                .component(DataComponents.LORE, new ItemLore(List.of(
                    Component.translatable("tooltip.warlockery.goblinite_pickaxe.hobgoblin"),
                    Component.translatable("tooltip.warlockery.goblinite_pickaxe.ores")
                )))
                .pickaxe(GobliniteMaterials.TOOL, 1.0F, -2.8F)
        )),
        FactoryCatalog.entry("delvealloyshovel", properties -> new ShovelItem(
            GobliniteMaterials.TOOL,
            1.5F,
            -3.0F,
            properties.fireResistant()
        )),
        FactoryCatalog.entry("delvealloyhoe", properties -> new HoeItem(
            GobliniteMaterials.TOOL,
            -4.0F,
            0.0F,
            properties.fireResistant()
        )),
        FactoryCatalog.entry("delvealloyhelm", properties -> new Item(
            properties.fireResistant().humanoidArmor(GobliniteMaterials.ARMOR, ArmorType.HELMET)
        )),
        FactoryCatalog.entry("delvealloychestplate", properties -> new Item(
            properties.fireResistant().humanoidArmor(GobliniteMaterials.ARMOR, ArmorType.CHESTPLATE)
        )),
        FactoryCatalog.entry("delvealloyleggings", properties -> new Item(
            properties.fireResistant().humanoidArmor(GobliniteMaterials.ARMOR, ArmorType.LEGGINGS)
        )),
        FactoryCatalog.entry("delvealloyboots", properties -> new Item(
            properties.fireResistant().humanoidArmor(GobliniteMaterials.ARMOR, ArmorType.BOOTS)
        ))
    ));

    private GobliniteEquipmentFactory() {
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
