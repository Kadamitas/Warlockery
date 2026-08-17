package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards the twelve exact F09 descriptor resources, the isolated no-op environment, and the
 * one-to-one mapping between descriptors and registered live-fixture methods.
 */
final class HellhoundResourceContractTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data", "warlockery");
    private static final Path ENVIRONMENT = DATA.resolve("test_environment/hellhound_isolated.json");
    private static final Path INSTANCES = DATA.resolve("test_instance");
    private static final Path GAME_TESTS = Path.of(
        "src", "main", "java", "com", "kadamitas", "warlockery", "entity", "HellhoundLifeGameTests.java"
    );
    static final List<String> DESCRIPTOR_IDS = List.of(
        "hellhound_acquisition_and_zombie_variants_are_contained",
        "hellhound_natural_group_pack_identity_excludes_outsiders",
        "hellhound_warning_commit_leash_and_return_are_bounded",
        "hellhound_scent_evidence_expires_without_omniscience",
        "hellhound_pack_roles_calls_and_member_loss_are_bounded",
        "hellhound_blocked_sectors_and_route_failures_back_off",
        "hellhound_bite_fire_recovery_and_ally_safety_are_exact",
        "hellhound_retreat_regroup_and_isolation_hysteresis_hold",
        "hellhound_fire_water_contact_and_conversion_contracts_hold",
        "hellhound_heat_rest_never_edits_world",
        "hellhound_animus_authority_follow_and_guard_are_safe",
        "hellhound_cure_is_transactional_and_preserves_exact_rules"
    );

    @Test
    void theIsolatedEnvironmentExistsAndMutatesNoSharedWorldState() {
        assertTrue(Files.exists(ENVIRONMENT),
            "the isolated F09 Hellhound environment resource must exist");
        final JsonObject environment = JsonParser.parseString(read(ENVIRONMENT)).getAsJsonObject();
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertTrue(environment.getAsJsonArray("definitions").isEmpty(),
            "the isolated F09 environment must not mutate shared world state");
    }

    @Test
    void exactlyTwelveDescriptorsExistWithTheApprovedShape() {
        assertEquals(12, DESCRIPTOR_IDS.size());
        for (final String id : DESCRIPTOR_IDS) {
            final Path fixture = INSTANCES.resolve(id + ".json");
            assertTrue(Files.exists(fixture), "missing descriptor: " + id);
            final JsonObject descriptor = JsonParser.parseString(read(fixture)).getAsJsonObject();
            assertEquals("minecraft:function", descriptor.get("type").getAsString(), id);
            assertEquals("warlockery:" + id, descriptor.get("function").getAsString(), id);
            assertEquals("warlockery:hellhound_isolated",
                descriptor.get("environment").getAsString(), id);
            assertEquals("forge:empty3x3x3", descriptor.get("structure").getAsString(), id);
            assertTrue(descriptor.get("max_ticks").getAsInt() > 0, id);
        }
    }

    @Test
    void everyDescriptorHasExactlyOneLiveFixtureMethod() {
        final String source = read(GAME_TESTS);
        for (final String id : DESCRIPTOR_IDS) {
            final String method = camelCase(id.substring("hellhound_".length()));
            assertTrue(source.contains("public static void " + method + "(final GameTestHelper helper)"),
                "missing live fixture method " + method + " for descriptor " + id);
        }
    }

    @Test
    void noStrayHellhoundDescriptorExists() {
        try (var files = Files.list(INSTANCES)) {
            final List<String> hellhoundFixtures = files
                .map(path -> path.getFileName().toString())
                .filter(name -> name.startsWith("hellhound_") && name.endsWith(".json"))
                .map(name -> name.replaceFirst("\\.json$", ""))
                .sorted()
                .toList();
            assertEquals(DESCRIPTOR_IDS.stream().sorted().toList(), hellhoundFixtures,
                "descriptor registration stays one-to-one with the twelve approved IDs");
        } catch (final IOException exception) {
            throw new UncheckedIOException("Unable to list GameTest fixtures", exception);
        }
    }

    private static String camelCase(final String snake) {
        final StringBuilder builder = new StringBuilder();
        boolean upper = false;
        for (final char letter : snake.toCharArray()) {
            if (letter == '_') {
                upper = true;
            } else {
                builder.append(upper ? Character.toUpperCase(letter) : letter);
                upper = false;
            }
        }
        return builder.toString();
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
