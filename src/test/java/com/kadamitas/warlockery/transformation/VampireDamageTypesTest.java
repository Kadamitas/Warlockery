package com.kadamitas.warlockery.transformation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class VampireDamageTypesTest {
    @Test
    void publishesOwnedSunlightIdentity() {
        assertEquals("warlockery:vampire_sunlight", VampireDamageTypes.VAMPIRE_SUNLIGHT.identifier().toString());
        assertFalse(read("src/main/resources/data/minecraft/tags/damage_type/is_fire.json")
            .contains("warlockery:vampire_sunlight"));
    }

    @Test
    void everyLocalePublishesBothSunlightDeathMessageBranches() throws IOException {
        final Path languages = Path.of("src/main/resources/assets/warlockery/lang");
        final List<Path> files;
        try (var listed = Files.list(languages)) {
            files = listed.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList();
        }
        assertEquals(12, files.size());
        final JsonObject english = json(languages.resolve("en_us.json"));
        for (final Path file : files) {
            final JsonObject locale = json(file);
            final String ordinary = locale.get("death.attack.vampire_sunlight").getAsString();
            final String credited = locale.get("death.attack.vampire_sunlight.player").getAsString();
            assertTrue(ordinary.contains("%1$s"), file.toString());
            assertTrue(credited.contains("%1$s"), file.toString());
            assertTrue(credited.contains("%2$s"), file.toString());
            if (!file.getFileName().toString().equals("en_us.json")) {
                assertNotEquals(english.get("death.attack.vampire_sunlight").getAsString(), ordinary, file.toString());
                assertNotEquals(english.get("death.attack.vampire_sunlight.player").getAsString(), credited, file.toString());
            }
        }
    }

    private static JsonObject json(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String read(final String path) {
        try {
            final Path file = Path.of(path);
            return Files.exists(file) ? Files.readString(file) : "";
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }
}
