package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GoblinSoundAssetsTest {
    private static final Path SOUNDS = Path.of("src/main/resources/assets/warlockery/sounds.json");
    private static final Path SOUND_REGISTRY = Path.of("src/main/java/com/kadamitas/warlockery/registry/ModSounds.java");
    private static final Path ENTITY =
        Path.of("src/main/java/com/kadamitas/warlockery/entity/AbstractGoblinMerchantEntity.java");
    private static final List<String> ACTIONS = List.of("ambient", "hurt", "death", "trade", "reject", "work");

    @Test
    void bothFamiliesHaveCompletePianoVoiceSets() {
        final JsonObject sounds = json(SOUNDS);
        for (final String family : List.of("goblin", "hobgoblin")) {
            for (final String action : ACTIONS) {
                final JsonObject event = sounds.getAsJsonObject("entity." + family + "." + action);
                assertTrue(event != null, () -> "missing " + family + " " + action + " sound");
                assertEquals("neutral", event.get("category").getAsString());
                final JsonArray variants = event.getAsJsonArray("sounds");
                assertFalse(variants.isEmpty());
                variants.forEach(variant -> assertEquals(
                    "minecraft:block.note_block.harp",
                    variant.getAsJsonObject().get("name").getAsString()
                ));
            }
        }
        assertNotEquals(
            sounds.getAsJsonObject("entity.goblin.ambient").getAsJsonArray("sounds"),
            sounds.getAsJsonObject("entity.hobgoblin.ambient").getAsJsonArray("sounds")
        );
    }

    @Test
    void creatureCodeUsesOnlyWarlockeryVoiceEvents() {
        final String registry = read(SOUND_REGISTRY);
        final String entity = read(ENTITY);
        assertTrue(registry.contains("registerCreature(\"entity.goblin\")"));
        assertTrue(registry.contains("registerCreature(\"entity.hobgoblin\")"));
        assertTrue(entity.contains("getNotifyTradeSound()"));
        assertTrue(entity.contains("getTradeUpdatedSound(final boolean validTrade)"));
        assertTrue(entity.contains("playWorkSound()"));
        assertFalse(entity.contains("SoundEvents.VILLAGER"));
    }

    private static JsonObject json(final Path path) {
        return JsonParser.parseString(read(path)).getAsJsonObject();
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
