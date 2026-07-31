package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class ExpandedBrewParityTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data");
    private static final Set<String> EXPANDED = Set.of(
        "bats", "blight", "erosion", "fear", "frogs_tongue", "frost", "grow_lily", "ice_shell",
        "ice_world", "infection", "inferno", "ink", "insect_bane", "level_land", "love", "overheating",
        "pulverize_rock", "raise_land", "raising", "sinking", "snow_burst", "spread_debuffs", "steal_buffs",
        "thorns", "transpose", "transpose_ore", "undead_bane", "undeads_curse", "vines", "wasting"
    );
    private static final Map<String, BrewBehavior> REQUIRED_BEHAVIORS = Map.ofEntries(
        Map.entry("bats", BrewBehavior.SUMMON_BATS),
        Map.entry("blight", BrewBehavior.BLIGHT),
        Map.entry("erosion", BrewBehavior.ERODE),
        Map.entry("fear", BrewBehavior.FEAR),
        Map.entry("frogs_tongue", BrewBehavior.PULL_TO_OWNER),
        Map.entry("frost", BrewBehavior.ICE_SHELL),
        Map.entry("grow_lily", BrewBehavior.PLACE_LILIES),
        Map.entry("ice_shell", BrewBehavior.ICE_SHELL),
        Map.entry("ice_world", BrewBehavior.PLACE_SNOW),
        Map.entry("infection", BrewBehavior.APPLY_INFECTION),
        Map.entry("inferno", BrewBehavior.IGNITE),
        Map.entry("insect_bane", BrewBehavior.HARM_INSECTS),
        Map.entry("level_land", BrewBehavior.LEVEL_LAND),
        Map.entry("love", BrewBehavior.BREED_ANIMALS),
        Map.entry("overheating", BrewBehavior.IGNITE),
        Map.entry("pulverize_rock", BrewBehavior.PULVERIZE_ROCK),
        Map.entry("raise_land", BrewBehavior.RAISE_LAND),
        Map.entry("raising", BrewBehavior.RAISE_DEAD),
        Map.entry("snow_burst", BrewBehavior.PLACE_SNOW),
        Map.entry("spread_debuffs", BrewBehavior.SPREAD_HARMFUL),
        Map.entry("steal_buffs", BrewBehavior.STEAL_BENEFICIAL),
        Map.entry("thorns", BrewBehavior.PLACE_THORNS),
        Map.entry("transpose", BrewBehavior.RANDOM_TELEPORT),
        Map.entry("transpose_ore", BrewBehavior.TRANSPOSE_ORES),
        Map.entry("undead_bane", BrewBehavior.HARM_UNDEAD),
        Map.entry("undeads_curse", BrewBehavior.CURSE_UNDEAD),
        Map.entry("vines", BrewBehavior.PLACE_VINES),
        Map.entry("wasting", BrewBehavior.WASTE)
    );

    @Test
    void everyExpandedBrewHasAVisibleRuntimeOutcome() {
        assertEquals(EXPANDED, BrewKind.builtIns().stream()
            .map(BrewKind::id)
            .filter(EXPANDED::contains)
            .collect(Collectors.toUnmodifiableSet()));
        EXPANDED.stream().map(BrewKind::require).forEach(kind ->
            assertFalse(kind.effects().isEmpty() && kind.behaviors().isEmpty(), kind.id())
        );
        REQUIRED_BEHAVIORS.forEach((id, behavior) ->
            assertTrue(BrewKind.require(id).behaviors().contains(behavior), id)
        );
        assertTrue(BrewKind.SINKING.hasPotionEffects());
        assertTrue(BrewKind.INK.hasPotionEffects());
        assertFalse(BrewKind.INK.behaviors().contains(BrewBehavior.PLACE_WEB));
        assertTrue(BrewKind.FROST.behaviors().contains(BrewBehavior.FREEZE));
        assertTrue(BrewKind.VINES.recoversOnMiss());
        assertTrue(BrewKind.THORNS.recoversOnMiss());
        assertTrue(BrewKind.BODEGA.recoversOnMiss());
        assertFalse(BrewKind.BATS.recoversOnMiss());
        assertTrue(BrewRuntime.infectionVariant(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState())
            .filter(state -> state.is(net.minecraft.world.level.block.Blocks.INFESTED_STONE))
            .isPresent());
        assertTrue(BrewRuntime.infectionVariant(net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState())
            .filter(state -> state.is(net.minecraft.world.level.block.Blocks.INFESTED_COBBLESTONE))
            .isPresent());
        assertTrue(BrewRuntime.infectionVariant(net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState())
            .filter(state -> state.is(net.minecraft.world.level.block.Blocks.INFESTED_STONE_BRICKS))
            .isPresent());
    }

    @Test
    void throwableBrewsParticipateInCommonPotionTags() {
        final JsonArray values = json(DATA.resolve("warlockery/tags/item/throwable_brews.json"))
            .getAsJsonArray("values");
        final Set<String> tagged = values.asList().stream()
            .map(element -> element.getAsString())
            .collect(Collectors.toUnmodifiableSet());
        assertEquals(BrewKind.builtIns().size(), tagged.size());
        BrewKind.builtIns().forEach(kind -> assertTrue(tagged.contains("warlockery:" + BrewFactory.itemId(kind))));
        assertTrue(json(DATA.resolve("c/tags/item/potions/bottle.json"))
            .getAsJsonArray("values")
            .asList()
            .stream()
            .anyMatch(element -> element.getAsString().equals("#warlockery:throwable_brews")));
    }

    @Test
    void terrainEffectsExposePrivateOptInTagsBackedByCommonFamilies() {
        final Map<String, String> expected = Map.of(
            "brew_erodible", "#c:stones",
            "brew_levelable_terrain", "#c:stones",
            "brew_pulverizable_rock", "#c:stones",
            "brew_transposable_ores", "#c:ores",
            "brew_thorn_supports", "#minecraft:dirt"
        );
        expected.forEach((tag, common) -> assertTrue(
            json(DATA.resolve("warlockery/tags/block/" + tag + ".json"))
                .getAsJsonArray("values")
                .asList()
                .stream()
                .anyMatch(element -> element.getAsString().equals(common)),
            tag
        ));
    }

    private static JsonObject json(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
