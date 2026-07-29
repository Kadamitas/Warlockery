package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.KobolditeMaterials;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.ArmorType;

public final class KobolditeEquipmentFactory {
    private static final Map<String, Function<Item.Properties, Item>> FACTORIES = Map.ofEntries(
        Map.entry("delvealloysword", properties -> new Item(
            properties.fireResistant().sword(KobolditeMaterials.TOOL, 3.0F, -2.4F)
        )),
        Map.entry("delvealloyaxe", properties -> new AxeItem(
            KobolditeMaterials.TOOL,
            5.0F,
            -3.0F,
            properties.fireResistant()
        )),
        Map.entry("delvealloypickaxe", properties -> new Item(
            properties.fireResistant()
                .component(DataComponents.LORE, new ItemLore(List.of(
                    Component.translatable("tooltip.warlockery.koboldite_pickaxe.hobgoblin"),
                    Component.translatable("tooltip.warlockery.koboldite_pickaxe.ores")
                )))
                .pickaxe(KobolditeMaterials.TOOL, 1.0F, -2.8F)
        )),
        Map.entry("delvealloyshovel", properties -> new ShovelItem(
            KobolditeMaterials.TOOL,
            1.5F,
            -3.0F,
            properties.fireResistant()
        )),
        Map.entry("delvealloyhoe", properties -> new HoeItem(
            KobolditeMaterials.TOOL,
            -4.0F,
            0.0F,
            properties.fireResistant()
        )),
        Map.entry("delvealloyhelm", properties -> new Item(
            properties.fireResistant().humanoidArmor(KobolditeMaterials.ARMOR, ArmorType.HELMET)
        )),
        Map.entry("delvealloychestplate", properties -> new Item(
            properties.fireResistant().humanoidArmor(KobolditeMaterials.ARMOR, ArmorType.CHESTPLATE)
        )),
        Map.entry("delvealloyleggings", properties -> new Item(
            properties.fireResistant().humanoidArmor(KobolditeMaterials.ARMOR, ArmorType.LEGGINGS)
        )),
        Map.entry("delvealloyboots", properties -> new Item(
            properties.fireResistant().humanoidArmor(KobolditeMaterials.ARMOR, ArmorType.BOOTS)
        ))
    );

    private KobolditeEquipmentFactory() {
    }

    public static Set<String> ids() {
        return FACTORIES.keySet();
    }

    public static boolean supports(final String id) {
        return FACTORIES.containsKey(id);
    }

    public static Item create(final Item.Properties properties, final String id) {
        final Function<Item.Properties, Item> factory = FACTORIES.get(id);
        if (factory == null) {
            throw new IllegalArgumentException("Unsupported Koboldite equipment: " + id);
        }
        return factory.apply(properties);
    }
}
