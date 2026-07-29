package com.kadamitas.warlockery.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SilverDepositRecipeTest {
    private static final Path RECIPES = Path.of("src/main/resources/data/warlockery/recipe");

    @Test
    void silverDepositsUpgradeABeartrapIntoAWolftrap() {
        final JsonObject recipe = recipe("wolftrap.json");

        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals("#c:dusts/silver", recipe.getAsJsonObject("key").get("S").getAsString());
        assertEquals("warlockery:beartrap", recipe.getAsJsonObject("key").get("B").getAsString());
        assertEquals("warlockery:wolftrap", recipe.getAsJsonObject("result").get("id").getAsString());
    }

    @Test
    void silverBoltsUseCommonSilverDustAndVanillaArrows() {
        final JsonObject recipe = recipe("silver_bolts.json");

        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals("#c:dusts/silver", recipe.getAsJsonObject("key").get("S").getAsString());
        assertEquals("#minecraft:arrows", recipe.getAsJsonObject("key").get("A").getAsString());
        assertEquals("warlockery:ingredient_bolt_silver", recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(8, recipe.getAsJsonObject("result").get("count").getAsInt());
    }

    private static JsonObject recipe(final String name) {
        try {
            return JsonParser.parseString(Files.readString(RECIPES.resolve(name))).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(name, exception);
        }
    }
}
