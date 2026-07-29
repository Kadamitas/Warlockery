package com.kadamitas.warlockery.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class InfusionIngredientAcquisitionTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data", "warlockery");
    private static final Map<String, String> RECIPES = Map.of(
        "ingredient_ghost_of_the_light", "warlockery:ingredient_ghost_of_the_light",
        "ingredient_happenstance_oil", "warlockery:ingredient_happenstance_oil",
        "ingredient_mystic_unguent", "warlockery:ingredient_mysticunguent",
        "ingredient_soul_of_the_world", "warlockery:ingredient_soul_of_the_world",
        "ingredient_spirit_of_the_veil", "warlockery:ingredient_spirit_of_the_veil",
        "ingredient_flying_ointment", "warlockery:ingredient_flying_ointment",
        "ingredient_brew_congealed_spirit", "warlockery:ingredient_brew_congealed_spirit"
    );
    private static final Map<String, String> TAGS = Map.of(
        "ghost_light_reagents", "warlockery:ingredient_ghost_of_the_light",
        "happenstance_oils", "warlockery:ingredient_happenstance_oil",
        "mystic_unguents", "warlockery:ingredient_mysticunguent",
        "world_souls", "warlockery:ingredient_soul_of_the_world",
        "veil_spirits", "warlockery:ingredient_spirit_of_the_veil",
        "flying_ointments", "warlockery:ingredient_flying_ointment",
        "congealed_spirits", "warlockery:ingredient_brew_congealed_spirit"
    );

    @Test
    void everyFormerlyBlockedIngredientHasAnExactProducer() {
        RECIPES.forEach((recipe, output) -> assertEquals(
            output,
            json(DATA.resolve(Path.of("recipe", recipe + ".json")))
                .getAsJsonObject("result")
                .get("id")
                .getAsString()
        ));
    }

    @Test
    void everyInfusionIngredientPublishesAnExtensionTag() {
        TAGS.forEach((tag, item) -> {
            final var values = json(DATA.resolve(Path.of("tags", "item", tag + ".json")))
                .getAsJsonArray("values");
            assertTrue(values.asList().stream().anyMatch(value -> value.getAsString().equals(item)), tag);
        });
    }

    @Test
    void infusedBrewsDoNotConsumeTheirOwnOutput() {
        assertNonCircular("infuse_brew_grave", "warlockery:ingredient_brew_grave");
        assertNonCircular("infuse_brew_soaring", "warlockery:ingredient_brew_soaring");
    }

    @Test
    void commonInputsRemainInterchangeable() {
        final String recipes = RECIPES.keySet().stream()
            .map(recipe -> read(DATA.resolve(Path.of("recipe", recipe + ".json"))))
            .reduce("", String::concat);
        assertTrue(recipes.contains("#c:dusts/glowstone"));
        assertTrue(recipes.contains("#c:gems/amethyst"));
        assertTrue(recipes.contains("#c:slime_balls"));
        assertTrue(recipes.contains("#c:stones"));
        assertTrue(recipes.contains("#c:ender_pearls"));
        assertTrue(recipes.contains("#c:feathers"));
    }

    private static void assertNonCircular(final String ritual, final String output) {
        final JsonObject json = json(DATA.resolve(Path.of("ritual", ritual + ".json")));
        assertEquals(output, json.get("target").getAsString());
        final var ingredients = json.getAsJsonObject("requirements").getAsJsonArray("ingredients");
        assertFalse(ingredients.asList().stream()
            .map(value -> value.getAsJsonObject().get("ingredient").getAsString())
            .anyMatch(output::equals));
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
