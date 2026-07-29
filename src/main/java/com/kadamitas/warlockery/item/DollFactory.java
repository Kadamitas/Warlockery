package com.kadamitas.warlockery.item;

import net.minecraft.world.item.Item;

public final class DollFactory {
    private DollFactory() {
    }

    public static DollItem create(final Item.Properties baseProperties, final DollKind kind) {
        Item.Properties properties = baseProperties.stacksTo(1);
        if (kind.definition().durability() > 0) {
            properties = properties.durability(kind.definition().durability());
        }
        return new DollItem(properties, kind);
    }

    public static DollItem create(final Item.Properties baseProperties, final String id) {
        return create(baseProperties, DollKind.find(id).orElseThrow(
            () -> new IllegalArgumentException("Unknown doll: " + id)
        ));
    }

    public static boolean supports(final String id) {
        return DollKind.find(id).isPresent();
    }
}
