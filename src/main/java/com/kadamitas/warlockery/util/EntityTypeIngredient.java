package com.kadamitas.warlockery.util;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

public record EntityTypeIngredient(String value, Identifier id, boolean tag) implements RegistryIngredient {
    public static Optional<EntityTypeIngredient> parse(final String value) {
        return RegistryIngredient.parse(value, EntityTypeIngredient::new);
    }

    public boolean matches(final Entity entity) {
        if (tag) {
            return entity.typeHolder().is(TagKey.create(Registries.ENTITY_TYPE, id));
        }
        final EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
        return type != null && entity.getType() == type;
    }

    public boolean isResolvable() {
        return isResolvableIn(BuiltInRegistries.ENTITY_TYPE);
    }
}
