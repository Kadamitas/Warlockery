package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.FactoryCatalog;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class UtilityDeviceItemFactory {
    private static final FactoryCatalog<Item.Properties, Item> FACTORIES = new FactoryCatalog<>(
        "utility device item",
        Map.of(
            "ingredient_annointing_paste", AnointingPasteItem::new,
            "ingredient_chalice_full", properties -> new FilledChaliceItem(ModBlocks.ALL.get("chalice").get(), properties),
            "ingredient_pentacle", properties -> new BlockItem(ModBlocks.ALL.get("pentacle").get(), properties)
        )
    );
    private static final Set<String> INTERNAL_BLOCKS = Set.of("pentacle");

    private UtilityDeviceItemFactory() {
    }

    public static boolean supports(final String id) {
        return FACTORIES.supports(id);
    }

    public static Set<String> supportedIds() {
        return FACTORIES.ids();
    }

    public static boolean isInternalBlock(final String id) {
        return INTERNAL_BLOCKS.contains(id);
    }

    public static Item create(final Item.Properties properties, final String id) {
        return FACTORIES.create(id, properties);
    }
}
