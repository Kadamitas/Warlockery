package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.registry.ContentCatalog;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class DreamWeaverVariantCatalogTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources");

    @Test
    void allSixFunctionalWeaversHaveDistinctObtainableItemsAndModels() {
        assertEquals(6, DreamWeaverMode.VALUES.size());
        assertEquals(6, DreamWeaverMode.VALUES.stream().map(DreamWeaverMode::itemId).collect(
            java.util.stream.Collectors.toSet()).size());

        final JsonObject variants = json("assets/warlockery/blockstates/dreamcatcher.json")
            .getAsJsonObject("variants");
        DreamWeaverMode.VALUES.forEach(mode -> {
            final String itemId = mode.itemId();
            assertTrue(
                mode == DreamWeaverMode.RESTORATION
                    ? ContentCatalog.BLOCKS.contains(itemId)
                    : ContentCatalog.ITEMS.contains(itemId),
                itemId
            );
            assertTrue(exists("assets/warlockery/items/" + itemId + ".json"), itemId);
            assertTrue(exists("assets/warlockery/models/item/" + itemId + ".json"), itemId);
            assertTrue(variants.has("mode=" + mode.getSerializedName()), mode.getSerializedName());
            assertEquals(
                "warlockery:block/" + itemId,
                variants.getAsJsonObject("mode=" + mode.getSerializedName()).get("model").getAsString()
            );
        });
    }

    @Test
    void specializedWeaversHaveRecipesAndReturnTheMatchingItemWhenBroken() {
        final Set<String> specialized = DreamWeaverMode.VALUES.stream()
            .filter(mode -> mode != DreamWeaverMode.RESTORATION)
            .map(DreamWeaverMode::itemId)
            .collect(java.util.stream.Collectors.toSet());
        specialized.forEach(itemId -> {
            final JsonObject recipe = json("data/warlockery/recipe/" + itemId + ".json");
            assertEquals("warlockery:" + itemId,
                recipe.getAsJsonObject("result").get("id").getAsString());
            assertTrue(recipe.getAsJsonArray("ingredients").toString().contains("warlockery:dreamcatcher"));
        });

        final String loot = json("data/warlockery/loot_table/blocks/dreamcatcher.json").toString();
        specialized.forEach(itemId -> assertTrue(loot.contains("warlockery:" + itemId), itemId));
    }

    private static boolean exists(final String relative) {
        return Files.isRegularFile(RESOURCES.resolve(relative));
    }

    private static JsonObject json(final String relative) {
        final Path path = RESOURCES.resolve(relative);
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
