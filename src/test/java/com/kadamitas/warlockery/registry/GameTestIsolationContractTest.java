package com.kadamitas.warlockery.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class GameTestIsolationContractTest {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Path MAIN_RESOURCES = Path.of("src", "main", "resources");
    private static final Path INSTANCES = MAIN_RESOURCES.resolve("data/warlockery/test_instance");
    private static final Set<String> THREE_CUBE_FIXTURES = Set.of(
        "approach_forms_cross_closed_fortification_and_reveal_inside",
        "circle_talisman_captures_and_restores_full_large_ring",
        "earths_wrath_moves_volcanic_fluid",
        "ent_hazard_escape_and_cancellation_are_deterministic",
        "hellhound_animus_authority_follow_and_guard_are_safe",
        "hobgoblin_village_builds_a_closed_wood_defense",
        "hobgoblins_flee_human_villagers_and_keep_custom_professions",
        "human_village_builds_a_closed_stone_defense",
        "repeated_fortification_does_not_stack_or_duplicate_guards"
    );
    private static final String FORCE_TICKED_FIFTEEN_CUBE_FIXTURE =
        "imp_ranged_lane_windup_and_retreat_are_bounded";

    @Test
    void everyFixtureHasAUniqueDelegatingIsolationEnvironment() throws IOException {
        final Set<String> ids = new HashSet<>();
        try (var fixtures = Files.list(INSTANCES)) {
            for (final Path fixturePath : fixtures.filter(path -> path.toString().endsWith(".json")).toList()) {
                final String fixtureId = fixturePath.getFileName().toString().replace(".json", "");
                final JsonObject fixture = JsonParser.parseString(read(fixturePath)).getAsJsonObject();
                final JsonObject environment = fixture.getAsJsonObject("environment");
                assertEquals("warlockery:isolated", environment.get("type").getAsString(), fixtureId);
                assertEquals(fixtureId, environment.get("id").getAsString(), fixtureId);
                assertTrue(environment.has("delegate"), fixtureId + " must preserve its old environment");
                assertTrue(ids.add(environment.get("id").getAsString()), fixtureId + " isolation id is duplicated");
            }
        }
        assertEquals(368, ids.size(), "every registered Warlockery fixture must form its own batch");
    }

    @Test
    void isolationEnvironmentDelegatesLifecycleAndCleansPlayersAndSurvivingEntities() {
        final String source = read(MAIN_JAVA.resolve(
            "com/kadamitas/warlockery/gametest/IsolatedTestEnvironment.java"
        ));
        assertTrue(source.contains("TestEnvironmentDefinition.activate(delegate.value(), level)"));
        assertTrue(source.contains("activation.teardown();"));
        assertTrue(source.contains("level.getServer() instanceof GameTestServer"),
            "global cleanup must never run from /test on a normal user server");
        assertTrue(source.contains("gameTestServer.getPlayerList().getPlayers()"));
        assertTrue(source.contains(".forEach(GameTestMockPlayers::disconnect);"),
            "mock players require PlayerList removal rather than Entity.discard");
        assertTrue(source.contains("level.getAllEntities()"));
        assertTrue(source.contains(".forEach(entity -> entity.discard());"));

        final String registry = read(MAIN_JAVA.resolve(
            "com/kadamitas/warlockery/registry/ModGameTestEnvironments.java"
        ));
        assertTrue(registry.contains("Registries.TEST_ENVIRONMENT_DEFINITION_TYPE"));
        assertTrue(registry.contains("register(\"isolated\""));
        assertTrue(read(MAIN_JAVA.resolve("com/kadamitas/warlockery/Warlockery.java"))
            .contains("ModGameTestEnvironments.REGISTRY.register(modBus);"));
    }

    @Test
    void harnessUsesNoTransformers() throws IOException {
        final String build = read(Path.of("build.gradle")).toLowerCase(java.util.Locale.ROOT);
        assertFalse(build.contains("mixin"));
        assertFalse(build.contains("accesstransformer"));
        assertFalse(build.contains("coremod"));
        assertFalse(Files.exists(MAIN_RESOURCES.resolve("warlockery.gametest.mixins.json")));
        final Path mixinSources = MAIN_JAVA.resolve("com/kadamitas/warlockery/gametest/mixin");
        if (Files.exists(mixinSources)) {
            try (var files = Files.list(mixinSources)) {
                assertTrue(files.findAny().isEmpty(), "the GameTest mixin source directory must be empty");
            }
        }
        assertFalse(Files.exists(MAIN_RESOURCES.resolve("META-INF/coremods.json")));
    }

    @Test
    void threeCubeFixturesPadCleanupWithoutChangingBarrierGeometry() {
        for (final String fixtureId : THREE_CUBE_FIXTURES) {
            final JsonObject fixture = JsonParser.parseString(
                read(INSTANCES.resolve(fixtureId + ".json"))
            ).getAsJsonObject();
            assertEquals("warlockery:empty3x3x3", fixture.get("structure").getAsString(), fixtureId);
            assertEquals(40, fixture.get("padding").getAsInt(), fixtureId);
        }
        final JsonObject imp = JsonParser.parseString(
            read(INSTANCES.resolve(FORCE_TICKED_FIFTEEN_CUBE_FIXTURE + ".json"))
        ).getAsJsonObject();
        assertEquals("warlockery:empty15x15x15", imp.get("structure").getAsString());
        assertEquals(40, imp.get("padding").getAsInt());
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
