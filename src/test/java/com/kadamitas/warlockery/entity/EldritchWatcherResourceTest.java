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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class EldritchWatcherResourceTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final List<String> FIXTURE_IDS = List.of(
        "eldritch_watcher_vigil_observes_and_escalates_on_reciprocal_gaze",
        "eldritch_watcher_revelation_is_bound_visible_and_attributed",
        "eldritch_watcher_binding_warning_lure_and_return_remain_local",
        "eldritch_watcher_save_reload_focus_hazard_and_work_are_bounded"
    );

    @Test
    void focusTagContainsExactKnowledgeSitesAndNeverReplaces() {
        final JsonObject tag = read(RESOURCES.resolve(
            Path.of("data", "warlockery", "tags", "block", "ai", "eldritch_watcher_focus.json")
        ));
        assertTrue(tag.has("replace"));
        assertFalse(tag.get("replace").getAsBoolean(), "the focus tag stays extendible");
        final JsonArray values = tag.getAsJsonArray("values");
        final List<String> entries = new ArrayList<>();
        values.forEach(value -> entries.add(value.getAsString()));
        assertEquals(List.of(
            "#c:bookshelves",
            "minecraft:lectern",
            "minecraft:enchanting_table",
            "#warlockery:ambient/arcane_workstations",
            "warlockery:mirrorblock",
            "warlockery:mirrorblock2",
            "warlockery:mirrorwall"
        ), entries);
    }

    @Test
    void spectralIdentityLootAndTextureSurfacesRemainExact() {
        final JsonObject spectral = read(RESOURCES.resolve(
            Path.of("data", "warlockery", "tags", "entity_type", "spectral.json")
        ));
        final List<String> members = new ArrayList<>();
        spectral.getAsJsonArray("values").forEach(value -> members.add(value.getAsString()));
        assertTrue(members.contains("warlockery:eldritch_watcher"),
            "spectral membership remains the capture/lure/device authority");
        final JsonObject loot = read(RESOURCES.resolve(
            Path.of("data", "warlockery", "loot_table", "entities", "eldritch_watcher.json")
        ));
        assertFalse(loot.has("pools") && loot.getAsJsonArray("pools").size() > 0,
            "the ordinary loot table keeps no pools");
        assertTrue(Files.exists(RESOURCES.resolve(
            Path.of("assets", "warlockery", "textures", "entity", "eldritch_watcher.png")
        )), "the exact existing Watcher texture is retained");
        assertTrue(Files.exists(RESOURCES.resolve(
            Path.of("assets", "warlockery", "items", "eldritch_watcher_spawn_egg.json")
        )));
    }

    private static JsonObject read(final Path path) {
        return JsonParser.parseString(readText(path)).getAsJsonObject();
    }

    private static String readText(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
