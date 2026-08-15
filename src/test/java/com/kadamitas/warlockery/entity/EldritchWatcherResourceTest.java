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

    @Test
    void exactlyFourFixturesBindTheIsolatedEnvironmentAndEmptyStructure() {
        assertEquals(4, FIXTURE_IDS.size());
        final JsonObject environment = read(RESOURCES.resolve(
            Path.of("data", "warlockery", "test_environment", "eldritch_watcher_isolated.json")
        ));
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertEquals(0, environment.getAsJsonArray("definitions").size(),
            "the isolated F14 environment must not mutate shared world state");
        for (final String id : FIXTURE_IDS) {
            final JsonObject fixture = read(RESOURCES.resolve(
                Path.of("data", "warlockery", "test_instance", id + ".json")
            ));
            assertEquals("minecraft:function", fixture.get("type").getAsString(), id);
            assertEquals("warlockery:" + id, fixture.get("function").getAsString(), id);
            assertEquals("warlockery:eldritch_watcher_isolated",
                fixture.get("environment").getAsString(), id);
            assertEquals("forge:empty3x3x3", fixture.get("structure").getAsString(), id);
            assertTrue(fixture.get("max_ticks").getAsInt() > 0, id);
        }
    }

    @Test
    void everyFixtureHasACompiledGameTestMethod() {
        final String source = readText(MAIN_JAVA.resolve(
            Path.of("com", "kadamitas", "warlockery", "entity", "EldritchWatcherGameTests.java")
        ));
        for (final String method : List.of(
            "vigilObservesAndEscalatesOnReciprocalGaze",
            "revelationIsBoundVisibleAndAttributed",
            "bindingWarningLureAndReturnRemainLocal",
            "saveReloadFocusHazardAndWorkAreBounded"
        )) {
            assertTrue(source.contains("public static void " + method + "("),
                "GameTest method must exist: " + method);
        }
    }

    @Test
    void alluringSkullPreservesEveryNonWatcherOutcomeAtTheSourceLevel() {
        final String source = readText(MAIN_JAVA.resolve(
            Path.of("com", "kadamitas", "warlockery", "block", "AlluringSkullBlock.java")
        ));
        assertTrue(source.contains("instanceof EldritchWatcherEntity watcher"),
            "only the exact dedicated Watcher class receives the semantic lure branch");
        assertTrue(source.contains("watcher.acceptExternalLure(level, pos)"),
            "the Watcher branch submits a bounded semantic lure");
        assertTrue(source.contains("mob.getNavigation().moveTo("),
            "every non-Watcher target keeps the byte-for-byte navigation outcome");
        assertTrue(source.contains("private static final int LURE_INTERVAL = 20"),
            "the existing lure cadence is preserved");
        assertTrue(source.contains("private static final int LURE_RADIUS = 16"),
            "the existing lure radius is preserved");
        assertTrue(source.contains("ALLURING_SKULL_TARGETS"),
            "target filtering keeps the existing entity-type tag");
    }

    @Test
    void productionRuntimeAvoidsForbiddenWorldMutationApis() {
        for (final String file : List.of(
            "EldritchWatcherRuntime.java", "EldritchWatcherEntity.java",
            "EldritchWatcherRules.java", "EldritchWatcherState.java"
        )) {
            final String source = readText(MAIN_JAVA.resolve(
                Path.of("com", "kadamitas", "warlockery", "entity", file)
            ));
            for (final String forbidden : List.of(
                "setBlock", "destroyBlock", "removeBlock", "addFreshEntity",
                "ChunkTicket", "getChunkSource().addRegionTicket", "getAllEntities",
                "setItemSlot(EquipmentSlot.MAINHAND, new ItemStack("
            )) {
                assertFalse(source.contains(forbidden),
                    file + " must not use forbidden world mutation API: " + forbidden);
            }
        }
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
