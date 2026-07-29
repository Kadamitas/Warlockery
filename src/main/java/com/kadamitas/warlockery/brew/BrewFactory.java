package com.kadamitas.warlockery.brew;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.item.Item;

public final class BrewFactory {
    private static final Map<String, BrewKind> KINDS_BY_ITEM_ID = createKindsByItemId();

    private BrewFactory() {
    }

    public static List<String> ids() {
        return List.copyOf(KINDS_BY_ITEM_ID.keySet());
    }

    public static boolean supports(final String itemId) {
        return KINDS_BY_ITEM_ID.containsKey(itemId);
    }

    public static BrewKind requireKind(final String itemId) {
        final BrewKind kind = KINDS_BY_ITEM_ID.get(itemId);
        if (kind == null) {
            throw new IllegalArgumentException("Unknown brew item: " + itemId);
        }
        return kind;
    }

    public static BrewItem create(final Item.Properties properties, final String itemId) {
        return new BrewItem(Objects.requireNonNull(properties, "properties"), requireKind(itemId));
    }

    public static String itemId(final BrewKind kind) {
        return "brew_" + Objects.requireNonNull(kind, "kind").id();
    }

    private static Map<String, BrewKind> createKindsByItemId() {
        final Map<String, BrewKind> kinds = new LinkedHashMap<>();
        BrewKind.builtIns().forEach(kind -> {
            final String itemId = itemId(kind);
            if (kinds.put(itemId, kind) != null) {
                throw new IllegalStateException("Duplicate brew item id: " + itemId);
            }
        });
        return Collections.unmodifiableMap(kinds);
    }
}
