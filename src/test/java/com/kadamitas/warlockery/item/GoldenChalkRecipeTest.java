package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GoldenChalkRecipeTest {
    @Test
    void goldenChalkUsesGypsumAndTheYellowDyeTag() throws Exception {
        final var recipe = JsonParser.parseString(Files.readString(Path.of(
            "src/main/resources/data/warlockery/recipe/chalkheart_from_gypsum.json"))).getAsJsonObject();
        final var ingredients = recipe.getAsJsonArray("ingredients").asList().stream()
            .map(value -> value.getAsString()).toList();

        assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
        assertEquals(java.util.Set.of("warlockery:ingredient_gypsum", "#c:dyes/yellow"),
            java.util.Set.copyOf(ingredients));
        assertEquals(2, ingredients.size());
        assertFalse(ingredients.contains("#c:dyes/red"));
    }

    @Test
    void goldenChalkInstructionsUseYellowDyeAndRemoveTheOldFallback() throws Exception {
        try (var paths = Files.list(Path.of("src/main/resources/assets/warlockery/lang"))) {
            for (final Path path : paths.filter(file -> file.toString().endsWith(".json")).toList()) {
                final var translations = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                final String guide = translations.get("manual.warlockery.circles.golden_chalk").getAsString();
                if (path.getFileName().toString().equals("en_us.json")) {
                    assertTrue(guide.contains("1 Yellow Dye"), path.toString());
                }
                assertFalse(guide.contains("Red Dye"), path.toString());
            }
        }
    }

    @Test
    void goldenChalkRecipeProducesOneDurableItemSoMinecraftCanLoadIt() throws Exception {
        final var recipe = JsonParser.parseString(Files.readString(Path.of(
            "src/main/resources/data/warlockery/recipe/chalkheart_from_gypsum.json"))).getAsJsonObject();
        final var result = recipe.getAsJsonObject("result");
        assertEquals("warlockery:chalkheart", result.get("id").getAsString());
        assertEquals(1, result.has("count") ? result.get("count").getAsInt() : 1,
            "Durable chalk has a maximum stack size of one; an oversized recipe output is rejected during loading");
    }
}
