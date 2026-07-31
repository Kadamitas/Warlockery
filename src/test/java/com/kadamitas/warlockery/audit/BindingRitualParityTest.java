package com.kadamitas.warlockery.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class BindingRitualParityTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data", "warlockery");

    @Test
    void fetishRiteBindsSpectralEntitiesIntoAStatefulItem() {
        final JsonObject ritual = json(DATA.resolve(Path.of("ritual", "bind_fetish.json")));
        assertEquals("bind_fetish", ritual.get("action").getAsString());
        assertEquals("warlockery:scarecrow", ritual.get("target").getAsString());
    }

    @Test
    void patronStatueRiteTargetsTheCraftablePatronStatue() {
        final JsonObject ritual = json(DATA.resolve(Path.of("ritual", "bind_statue_player.json")));
        assertEquals("bind_item", ritual.get("action").getAsString());
        assertEquals("warlockery:statueofworship", ritual.get("target").getAsString());
        final String recipe = read(DATA.resolve(Path.of("recipe", "statue_of_hobgoblin_patron.json")));
        assertTrue(recipe.contains("#c:stones"));
        assertTrue(recipe.contains("#c:ingots/goblinite"));
    }

    private static JsonObject json(final Path path) {
        return JsonParser.parseString(read(path)).getAsJsonObject();
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
