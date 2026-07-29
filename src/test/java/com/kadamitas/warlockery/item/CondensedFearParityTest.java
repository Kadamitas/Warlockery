package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

final class CondensedFearParityTest {
    private static final String FEAR = "warlockery:ingredient_condensed_fear";
    private static final String FEAR_TAG = "#warlockery:condensed_fears";
    private static final Path DATA = Path.of("src/main/resources/data/warlockery");

    @Test
    void spectralStoneRiteConsumesCondensedFear() throws IOException {
        final JsonArray ingredients = json(DATA.resolve("ritual/spectral_stone.json"))
            .getAsJsonObject("requirements")
            .getAsJsonArray("ingredients");
        assertTrue(contains(ingredients, FEAR_TAG));
        final JsonArray values = json(DATA.resolve("tags/item/condensed_fears.json")).getAsJsonArray("values");
        assertTrue(StreamSupport.stream(values.spliterator(), false)
            .map(JsonElement::getAsString)
            .anyMatch(FEAR::equals));
    }

    @Test
    void drainMagicBrewConsumesCondensedFear() throws IOException {
        final JsonArray inputs = json(DATA.resolve("warlockery_machine/kettle_brew_drain_magic.json"))
            .getAsJsonArray("inputs");
        assertTrue(contains(inputs, FEAR));
    }

    private static boolean contains(final JsonArray values, final String expected) {
        return StreamSupport.stream(values.spliterator(), false)
            .map(JsonElement::getAsJsonObject)
            .map(value -> value.get("ingredient").getAsString())
            .anyMatch(expected::equals);
    }

    private static com.google.gson.JsonObject json(final Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
