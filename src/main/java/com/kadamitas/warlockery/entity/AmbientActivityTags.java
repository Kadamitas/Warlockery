package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.AmbientActivityProfile.ActivityType;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class AmbientActivityTags {
    public static final TagKey<Block> SHINY_STORAGE_BLOCKS = common("storage_blocks");
    public static final TagKey<Block> BOOKSHELVES = common("bookshelves");
    public static final TagKey<Block> CRAFTING_WORKSTATIONS = common("player_workstations/crafting_tables");
    public static final TagKey<Block> FURNACE_WORKSTATIONS = common("player_workstations/furnaces");
    public static final TagKey<Block> LIGHTNING_RODS = extension("lightning_rods");
    public static final TagKey<Block> THORNY_PLANTS = extension("thorny_plants");
    public static final TagKey<Block> GLASS_BLOCKS = common("glass_blocks");
    public static final TagKey<Block> SOUL_LIGHTS = extension("soul_lights");
    public static final TagKey<Block> HAY_BLOCKS = common("storage_blocks/wheat");
    public static final TagKey<Block> HOME_STORAGE = common("chests");
    public static final TagKey<Block> POND_REST_BLOCKS = extension("pond_rest_blocks");
    public static final TagKey<Block> ARCANE_WORKSTATIONS = extension("arcane_workstations");
    private static final Map<ActivityType, Set<TagKey<Block>>> BY_ACTIVITY = Map.ofEntries(
        Map.entry(ActivityType.SHINY_CURIOSITY, Set.of(SHINY_STORAGE_BLOCKS)),
        Map.entry(ActivityType.NIGHT_PERCH, Set.of(BOOKSHELVES)),
        Map.entry(ActivityType.STORM_ROD, Set.of(LIGHTNING_RODS)),
        Map.entry(ActivityType.ARCANE_STUDY,
            Set.of(BOOKSHELVES, CRAFTING_WORKSTATIONS, FURNACE_WORKSTATIONS, ARCANE_WORKSTATIONS)),
        Map.entry(ActivityType.SOUL_LANTERN_VIGIL, Set.of(SOUL_LIGHTS)),
        Map.entry(ActivityType.HAY_REST, Set.of(HAY_BLOCKS)),
        Map.entry(ActivityType.THORN_GARDEN, Set.of(THORNY_PLANTS)),
        Map.entry(ActivityType.MIRROR_GAZE, Set.of(GLASS_BLOCKS)),
        Map.entry(ActivityType.FAMILIAR_HOME, Set.of(HOME_STORAGE)),
        Map.entry(ActivityType.POND_REST, Set.of(POND_REST_BLOCKS))
    );

    private AmbientActivityTags() {
    }

    public static Set<TagKey<Block>> forActivity(final ActivityType type) {
        return BY_ACTIVITY.getOrDefault(type, Set.of());
    }

    public static boolean matches(final ActivityType type, final net.minecraft.world.level.block.state.BlockState state) {
        return forActivity(type).stream().anyMatch(state::is);
    }

    private static TagKey<Block> common(final String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", path));
    }

    private static TagKey<Block> extension(final String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("warlockery", "ambient/" + path));
    }
}
