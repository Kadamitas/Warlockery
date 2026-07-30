package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CreativeTranslationTest {
    private static final Path ITEMS = Path.of("src/main/resources/assets/warlockery/items");
    private static final Path LANGUAGE = Path.of("src/main/resources/assets/warlockery/lang/en_us.json");

    @Test
    void everyCreativeItemDefinitionHasAReadableName() {
        final JsonObject language = json(LANGUAGE);
        try (var files = Files.list(ITEMS)) {
            files.filter(path -> path.toString().endsWith(".json")).forEach(path -> {
                final String id = path.getFileName().toString().replaceFirst("\\.json$", "");
                final String itemKey = "item.warlockery." + id;
                final String blockKey = "block.warlockery." + id;
                final var translation = language.has(itemKey) ? language.get(itemKey) : language.get(blockKey);
                assertNotNull(translation, id);
                assertFalse(translation.getAsString().startsWith("item.warlockery."), id);
                assertFalse(translation.getAsString().startsWith("block.warlockery."), id);
            });
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Test
    void compoundPlayerNamesUseSpaces() {
        final JsonObject language = json(LANGUAGE);
        List.of(
            "alluringskull", "beartrap", "circleglyphinfernal", "circleglyph_veil",
            "circleglyphritual", "crystalball", "demonheart", "filteredfumefunnel",
            "fumefunnel", "garlicgarland", "icestockade", "plantmine", "shadedglass",
            "wickerbundle", "wolfaltar", "wolfhead", "wolftrap"
        ).forEach(id -> assertFalse(
            language.get("item.warlockery." + id).getAsString().equalsIgnoreCase(id.replace("_", "")),
            id
        ));
    }

    private static JsonObject json(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
