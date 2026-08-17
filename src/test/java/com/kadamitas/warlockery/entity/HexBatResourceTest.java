package com.kadamitas.warlockery.entity;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class HexBatResourceTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final List<String> FIXTURE_IDS = List.of(
        "hex_bat_roosts_by_day_and_sorties_at_night",
        "hex_bat_swoop_marks_and_releases_target_safely",
        "murderous_flock_protects_caster_and_calls_locally",
        "hex_bat_save_reload_hazard_and_work_are_bounded"
    );

    @Test
    void roostTagIsExtendibleAndUsesOnlyApprovedSupportFamilies() {
        final JsonObject tag = read(RESOURCES.resolve(
            Path.of("data", "warlockery", "tags", "block", "ai", "hex_bat_roosts.json")
        ));
        assertTrue(tag.has("replace"), "the roost tag declares its merge behavior");
        assertFalse(tag.get("replace").getAsBoolean(), "the roost tag must stay extendible");
        final JsonArray values = tag.getAsJsonArray("values");
        assertTrue(values.size() >= 8, "log, stone, deepslate, cobblestone, stone-brick, brick, tuff, and dripstone families");
        final Set<String> approved = Set.of(
            "#minecraft:logs", "minecraft:stone", "minecraft:deepslate", "minecraft:cobblestone",
            "minecraft:cobbled_deepslate", "minecraft:stone_bricks", "minecraft:deepslate_bricks",
            "minecraft:bricks", "minecraft:tuff", "minecraft:dripstone_block"
        );
        values.forEach(value -> assertTrue(approved.contains(value.getAsString()),
            "unexpected roost support: " + value.getAsString()));
        assertTrue(values.asList().stream().map(v -> v.getAsString()).toList().contains("#minecraft:logs"));
        assertFalse(values.asList().stream().anyMatch(v -> v.getAsString().contains("bookshel")),
            "bookshelves are not a default bat roost");
    }

    @Test
    void protectedBrewLootArthanaAndAliasIdentitiesRemainPresent() {
        for (final Path path : List.of(
            Path.of("data", "warlockery", "loot_table", "entities", "hex_bat.json"),
            Path.of("data", "warlockery", "warlockery_machine", "kettle_brew_murderous_flock.json"),
            Path.of("data", "warlockery", "warlockery_machine", "kettle_ingredient_brew_hitchcock.json"),
            Path.of("data", "warlockery", "custom_brew_component", "effect", "murderous_flock.json"),
            Path.of("data", "warlockery", "tags", "entity_type", "arthana_bat_sources.json"),
            Path.of("data", "warlockery", "tags", "entity_type", "reagent_sources.json")
        )) {
            assertTrue(Files.isRegularFile(RESOURCES.resolve(path)), "protected identity file: " + path);
        }
    }

    @Test
    void publicEnglishNamesRemainExact() {
        final JsonObject lang;
        try {
            lang = JsonParser.parseString(Files.readString(RESOURCES.resolve(
                Path.of("assets", "warlockery", "lang", "en_us.json")
            ))).getAsJsonObject();
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        }
        assertEquals("Hex Bat", lang.get("entity.warlockery.hex_bat").getAsString());
        assertEquals("Hex Bat Spawn Egg", lang.get("item.warlockery.hex_bat_spawn_egg").getAsString());
    }

    @Test
    void isolatedEnvironmentIsANoOpDefinition() {
        final JsonObject environment = read(RESOURCES.resolve(
            Path.of("data", "warlockery", "test_environment", "hex_bat_isolated.json")
        ));
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertEquals(0, environment.getAsJsonArray("definitions").size());
    }

    @Test
    void exactlyFourFixturesBindTheIsolatedEnvironmentAndEmptyStructure() {
        assertEquals(4, FIXTURE_IDS.size());
        for (final String id : FIXTURE_IDS) {
            final JsonObject fixture = read(RESOURCES.resolve(
                Path.of("data", "warlockery", "test_instance", id + ".json")
            ));
            assertEquals("minecraft:function", fixture.get("type").getAsString());
            assertEquals("warlockery:" + id, fixture.get("function").getAsString());
            assertEquals("warlockery:hex_bat_isolated", fixture.get("environment").getAsString());
            assertEquals("forge:empty3x3x3", fixture.get("structure").getAsString());
            assertTrue(fixture.get("max_ticks").getAsInt() <= 400, "fixtures stay time-bounded");
        }
    }

    @Test
    void protectedTextureHashesAndPathsAreUnchanged() {
        assertEquals("13e25b97666b3d1e74c33f3eeebec71343f7b0b41fc233339c618ada58bd9680",
            sha256(RESOURCES.resolve(Path.of("assets", "warlockery", "textures", "entity", "hex_bat.png"))));
        assertEquals("ad48490fe37cfb68c9c508f3c3b5f353b77c6a24fdd92ef49ee68c0e8bd26230",
            sha256(RESOURCES.resolve(Path.of("assets", "warlockery", "textures", "item", "hex_bat_spawn_egg.png"))));
        assertEquals("5baf9b0356bf27846b9b5d6be5ad70eff08c0d4785c731ff3b9eeb99ecbf35db",
            sha256(RESOURCES.resolve(Path.of("assets", "warlockery", "textures", "item", "ingredient_bat_wool.png"))));
    }

    @Test
    void hexBatStaysOutOfProtectedIntegrationTags() {
        for (final String tag : List.of("spectral", "sunlight_vulnerable")) {
            final Path path = RESOURCES.resolve(Path.of("data", "warlockery", "tags", "entity_type", tag + ".json"));
            if (!Files.isRegularFile(path)) continue;
            try {
                assertFalse(Files.readString(path).contains("hex_bat"),
                    "hex_bat must not join " + tag);
            } catch (final IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
    }

    private static JsonObject read(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String sha256(final Path path) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
            );
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        } catch (final NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
