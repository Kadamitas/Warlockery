package com.kadamitas.warlockery.util;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

public record EntityTypeIngredient(String value, Identifier id, boolean tag) {
    public static Optional<EntityTypeIngredient> parse(final String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        final boolean tag = value.startsWith("#");
        final Identifier id = Identifier.tryParse(tag ? value.substring(1) : value);
        return id == null ? Optional.empty() : Optional.of(new EntityTypeIngredient(value, id, tag));
    }

    public boolean matches(final Entity entity) {
        if (tag) {
            return entity.typeHolder().is(TagKey.create(Registries.ENTITY_TYPE, id));
        }
        final EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
        return type != null && entity.getType() == type;
    }

    public boolean isResolvable() {
        return tag || BuiltInRegistries.ENTITY_TYPE.containsKey(id);
    }
}
