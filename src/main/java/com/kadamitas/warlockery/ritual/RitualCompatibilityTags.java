package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.Warlockery;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public final class RitualCompatibilityTags {
    public static final TagKey<EntityType<?>> WARD_IMMUNE = entity("ritual_ward_immune");
    public static final TagKey<EntityType<?>> IMPRISONABLE = entity("ritual_imprisonable");
    public static final TagKey<EntityType<?>> SANCTITY_REPELLED = entity("ritual_sanctity_repelled");

    private RitualCompatibilityTags() {
    }

    private static TagKey<EntityType<?>> entity(final String path) {
        return TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path));
    }
}
