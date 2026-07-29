package com.kadamitas.warlockery.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class MirrorInfusionClosureTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data", "warlockery");

    @Test
    void craftedMirrorBlockIsTheSurvivalSubstrate() throws IOException {
        final JsonObject recipe = json(DATA.resolve(Path.of("recipe", "mirror_block.json")));

        assertEquals("warlockery:mirrorblock", recipe.getAsJsonObject("result").get("id").getAsString());
        assertTrue(recipe.toString().contains("#c:ingots/silver"));
        assertTrue(recipe.toString().contains("#c:glass_blocks/colorless"));
    }

    @Test
    void infusionTransformsTheBlockInsteadOfConsumingItsOwnOutput() throws IOException {
        final JsonObject ritual = json(DATA.resolve(Path.of("ritual", "infuse_mirror.json")));
        final var ingredients = ritual.getAsJsonObject("requirements").getAsJsonArray("ingredients");

        assertEquals("warlockery:mirror", ritual.get("target").getAsString());
        assertTrue(ingredients.asList().stream()
            .anyMatch(value -> value.getAsJsonObject().get("ingredient").getAsString().equals("warlockery:mirrorblock")));
        assertFalse(ingredients.asList().stream()
            .anyMatch(value -> value.getAsJsonObject().get("ingredient").getAsString().equals("warlockery:mirror")));
    }

    private static JsonObject json(final Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
