package com.kadamitas.warlockery.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class GameTestIsolationContractTest {
    @Test
    void everyFixtureHasAUniqueDelegatingIsolationEnvironment() throws IOException {
        final Set<String> ids = new HashSet<>();
        try (var fixtures = Files.list(Path.of("src/serverGameTest/resources/data/warlockery/test_instance"))) {
            for (final Path path : fixtures.filter(file -> file.toString().endsWith(".json")).toList()) {
                final String id = path.getFileName().toString().replaceFirst("\\.json$", "");
                final var fixture = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                final var environment = fixture.getAsJsonObject("environment");
                assertEquals("warlockery:isolated", environment.get("type").getAsString(), id);
                assertEquals(id, environment.get("id").getAsString(), id);
                assertTrue(environment.has("delegate"), id);
                assertTrue(ids.add(id), id + " isolation id is duplicated");
            }
        }
        assertFalse(ids.isEmpty(), "The development test mod must contain runnable fixtures");
    }
}
