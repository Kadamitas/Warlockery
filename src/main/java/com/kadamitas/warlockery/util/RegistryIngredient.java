package com.kadamitas.warlockery.util;

import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

public interface RegistryIngredient {
    String value();

    Identifier id();

    boolean tag();

    default boolean isResolvableIn(final Registry<?> registry) {
        return tag() || registry.containsKey(id());
    }

    static <T extends RegistryIngredient> Optional<T> parse(
        final String value,
        final Factory<T> factory
    ) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        final boolean tag = value.startsWith("#");
        final Identifier id = Identifier.tryParse(tag ? value.substring(1) : value);
        return id == null ? Optional.empty() : Optional.of(factory.create(value, id, tag));
    }

    @FunctionalInterface
    interface Factory<T extends RegistryIngredient> {
        T create(String value, Identifier id, boolean tag);
    }
}
