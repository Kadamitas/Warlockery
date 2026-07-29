package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.ModBlocks;
import java.util.Set;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class UtilityDeviceItemFactory {
    private static final Set<String> SUPPORTED = Set.of(
        "ingredient_annointing_paste",
        "ingredient_chalice_full",
        "ingredient_pentacle"
    );
    private static final Set<String> INTERNAL_BLOCKS = Set.of("pentacle");

    private UtilityDeviceItemFactory() {
    }

    public static boolean supports(final String id) {
        return SUPPORTED.contains(id);
    }

    public static Set<String> supportedIds() {
        return SUPPORTED;
    }

    public static boolean isInternalBlock(final String id) {
        return INTERNAL_BLOCKS.contains(id);
    }

    public static Item create(final Item.Properties properties, final String id) {
        return switch (id) {
            case "ingredient_annointing_paste" -> new AnointingPasteItem(properties);
            case "ingredient_chalice_full" -> new FilledChaliceItem(ModBlocks.ALL.get("chalice").get(), properties);
            case "ingredient_pentacle" -> new BlockItem(ModBlocks.ALL.get("pentacle").get(), properties);
            default -> throw new IllegalArgumentException("Unsupported utility device item: " + id);
        };
    }
}
