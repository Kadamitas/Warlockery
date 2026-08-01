package com.kadamitas.warlockery.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class GameTestRegistrationIntegrityTest {
    private static final Pattern REGISTRATION = Pattern.compile("REGISTRY\\.register\\(\"([^\"]+)\"");
    private static final Path REGISTRY_SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/registry/ModGameTests.java"
    );
    private static final Path INSTANCES = Path.of("src/main/resources/data/warlockery/test_instance");

    @Test
    void everyRegisteredFunctionHasExactlyOneRunnableInstance() throws IOException {
        final Set<String> registrations = REGISTRATION.matcher(Files.readString(REGISTRY_SOURCE)).results()
            .map(result -> result.group(1))
            .collect(Collectors.toUnmodifiableSet());
        final Map<String, String> functions;
        try (Stream<Path> paths = Files.list(INSTANCES)) {
            functions = paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                .collect(Collectors.toUnmodifiableMap(
                    path -> path.getFileName().toString().replaceFirst("\\.json$", ""),
                    GameTestRegistrationIntegrityTest::function,
                    (left, right) -> left
                ));
        }

        assertEquals(registrations, functions.keySet());
        functions.forEach((name, function) -> assertEquals("warlockery:" + name, function));
    }

    @Test
    void everyInstanceUsesTheFabricGameTestEnvironmentAndEmptyStructure() throws IOException {
        try (Stream<Path> paths = Files.list(INSTANCES)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                .map(GameTestRegistrationIntegrityTest::json)
                .forEach(instance -> {
                    assertEquals("minecraft:function", instance.get("type").getAsString());
                    assertEquals("minecraft:default", instance.get("environment").getAsString());
                    assertEquals("fabric-gametest-api-v1:empty", instance.get("structure").getAsString());
                    assertTrue(instance.get("max_ticks").getAsInt() > 0);
                });
        }
    }

    private static String function(final Path path) {
        return json(path).get("function").getAsString();
    }

    private static JsonObject json(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException(path.toString(), exception);
        }
    }
}
