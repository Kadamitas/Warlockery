package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ManualRitualInstructionsTest {
    @SuppressWarnings("unchecked")
    private static List<String> keys(String id, String action, String target) throws Exception {
        final var type = Class.forName("com.kadamitas.warlockery.client.ManualRitualInstructions");
        final var method = type.getDeclaredMethod("keys", String.class, String.class, String.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(null, id, action, target);
    }

    @Test void everyPackagedRiteExplainsTheCommonLiveSiteRules() throws Exception {
        try (var files = Files.list(Path.of("src/main/resources/data/warlockery/ritual"))) {
            final var recipes = files.filter(path -> path.toString().endsWith(".json")).toList();
            assertFalse(recipes.isEmpty());
            for (var path : recipes) {
                final var recipe = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                assertTrue(keys(path.getFileName().toString().replace(".json", ""), recipe.get("action").getAsString(),
                    recipe.has("target") ? recipe.get("target").getAsString() : "").contains("site"), path.toString());
            }
        }
    }

}
