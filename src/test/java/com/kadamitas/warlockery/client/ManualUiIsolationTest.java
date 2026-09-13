package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ManualUiIsolationTest {
    private static final Path LANGUAGE = Path.of(
        "src/main/resources/assets/warlockery/lang/en_us.json"
    );

    @Test
    void playerFacingBooksDoNotReadLikeImplementationNotes() {
        final JsonObject language = JsonParser.parseString(read(LANGUAGE)).getAsJsonObject();
        final List<String> keys = List.of(
            "manual.warlockery.codex.custom_brews",
            "manual.warlockery.codex.delivery",
            "manual.warlockery.codex.diagnostics",
            "manual.warlockery.biomes.biome_notes",
            "manual.warlockery.immortal.initiation"
        );
        keys.stream().map(key -> language.get(key).getAsString().toLowerCase()).forEach(text -> {
            assertFalse(text.contains("tagged"));
            assertFalse(text.contains("data-driven"));
            assertFalse(text.contains("registry"));
            assertFalse(text.contains("capability"));
        });
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
