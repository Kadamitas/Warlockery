package com.kadamitas.warlockery.util;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record ItemIngredient(String value, Identifier id, boolean tag) {
    public static Optional<ItemIngredient> parse(final String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        final boolean tag = value.startsWith("#");
        final Identifier id = Identifier.tryParse(tag ? value.substring(1) : value);
        return id == null ? Optional.empty() : Optional.of(new ItemIngredient(value, id, tag));
    }

    public boolean matches(final ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (tag) {
            return stack.is(TagKey.create(Registries.ITEM, id));
        }
        final Item item = BuiltInRegistries.ITEM.getValue(id);
        return item != null && stack.is(item);
    }

    public boolean isResolvable() {
        return tag || BuiltInRegistries.ITEM.containsKey(id);
    }
}
