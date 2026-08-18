package com.kadamitas.warlockery.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Turns an ingredient identifier into the name a player reads. Lives outside the client package because
 * server-side messages name ingredients too, and the component it returns is resolved by whoever displays it.
 */
public final class ItemDisplayNames {
    private ItemDisplayNames() {
    }

    public static Component component(final String ingredient) {
        if (ingredient == null || ingredient.isBlank() || ingredient.startsWith("#")) {
            return Component.literal(ingredient == null || ingredient.isBlank() ? "?" : ingredient);
        }
        final Identifier id = Identifier.tryParse(ingredient);
        if (id == null) {
            return Component.literal(ingredient);
        }
        return BuiltInRegistries.ITEM.get(id)
            .map(holder -> new ItemStack(holder.value()).getHoverName())
            .orElse(Component.literal(ingredient));
    }

    public static String text(final String ingredient) {
        return component(ingredient).getString();
    }
}
