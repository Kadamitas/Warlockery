package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

final class MagicalSaplingOvenParityTest {
    private static final Path DATA = Path.of("src/main/resources/data/warlockery");

    @Test
    void everyMagicalSaplingHasItsNamedOvenEssence() {
        Map.of(
            "alder", "ingredient_reek_of_misfortune",
            "hawthorn", "ingredient_odour_of_purity",
            "rowan", "ingredient_whiff_of_magic"
        ).forEach(MagicalSaplingOvenParityTest::assertRecipe);
    }

    private static void assertRecipe(final String wood, final String output) {
        try {
            final JsonObject recipe = json(DATA.resolve("warlockery_machine/oven_" + wood + "_sapling.json"));
            assertEquals(
                "#warlockery:" + wood + "_saplings",
                recipe.getAsJsonArray("inputs").get(0).getAsJsonObject().get("ingredient").getAsString()
            );
            assertTrue(StreamSupport.stream(recipe.getAsJsonArray("outputs").spliterator(), false)
                .map(JsonElement::getAsJsonObject)
                .map(value -> value.get("item").getAsString())
                .anyMatch(("warlockery:" + output)::equals));
            assertTrue(values(DATA.resolve("tags/item/" + wood + "_saplings.json"))
                .contains("warlockery:" + wood + "_sapling"));
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static JsonObject json(final Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static java.util.List<String> values(final Path path) throws IOException {
        return json(path).getAsJsonArray("values").asList().stream().map(JsonElement::getAsString).toList();
    }
}
