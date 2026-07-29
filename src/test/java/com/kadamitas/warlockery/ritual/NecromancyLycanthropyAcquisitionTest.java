package com.kadamitas.warlockery.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

final class NecromancyLycanthropyAcquisitionTest {
    private static final Path DATA = Path.of("src/main/resources/data/warlockery");

    @Test
    void spectralStoneRiteUsesObtainableExtensibleInputsWithoutConsumingItsOutput() {
        final JsonObject rite = json("ritual/spectral_stone.json");
        final List<String> ingredients = ingredients(rite);

        assertEquals("summon_item", rite.get("action").getAsString());
        assertEquals("warlockery:spectralstone", rite.get("target").getAsString());
        assertTrue(rite.get("night_only").getAsBoolean());
        assertFalse(ingredients.contains("warlockery:spectralstone"));
        assertTrue(ingredients.contains("#warlockery:spectral_stone_bases"));
        assertTrue(ingredients.contains("#warlockery:spectral_dusts"));
        assertTrue(ingredients.contains("#warlockery:congealed_spirits"));
        assertTrue(ingredients.contains("#warlockery:condensed_fears"));
        assertTagContains("spectral_stone_bases", "warlockery:ingredient_attuned_stone");
        assertTagContains("spectral_dusts", "warlockery:ingredient_spectral_dust");
        assertTagContains("condensed_fears", "warlockery:ingredient_condensed_fear");

        final JsonObject baseRecipe = json("recipe/ingredient_attuned_stone.json");
        assertEquals("warlockery:ingredient_attuned_stone", result(baseRecipe));
        assertEquals("#c:gems/diamond", baseRecipe.getAsJsonObject("key").get("D").getAsString());
        assertEquals("#c:buckets/lava", baseRecipe.getAsJsonObject("key").get("L").getAsString());
    }

    @Test
    void wolfTokenHasAProgressionGatedCrossModRecipeAndFeedsTheFullMoonHex() {
        final JsonObject recipe = json("recipe/wolftoken.json");
        final JsonObject key = recipe.getAsJsonObject("key");

        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals("warlockery:wolftoken", result(recipe));
        assertEquals("#c:ingots/silver", key.get("S").getAsString());
        assertEquals("#warlockery:wolfsbane_reagents", key.get("W").getAsString());
        assertEquals("#warlockery:wolf_token_attunements", key.get("A").getAsString());
        assertEquals("#warlockery:wolf_altar_heads", key.get("H").getAsString());
        assertTagContains("lycanthropy_catalysts", "warlockery:wolftoken");
        assertTagContains("wolfsbane_reagents", "warlockery:ingredient_wolfsbane");
        assertTagContains("wolf_token_attunements", "warlockery:ingredient_attuned_stone_charged");
        assertTagContains("wolf_altar_heads", "warlockery:wolfhead");

        final JsonObject charging = json("ritual/charge_attuned_stone.json");
        assertEquals("warlockery:ingredient_attuned_stone_charged", charging.get("target").getAsString());
        final JsonObject wolfHead = json("recipe/wolf_head.json");
        assertEquals("warlockery:wolfhead", result(wolfHead));
        assertEquals("#c:bones", wolfHead.getAsJsonObject("key").get("B").getAsString());

        final JsonObject hex = json("ritual/hex_wolf.json");
        final JsonObject requirements = hex.getAsJsonObject("requirements");
        final List<String> hexIngredients = ingredients(hex);
        assertTrue(hex.get("night_only").getAsBoolean());
        assertTrue(requirements.get("full_moon").getAsBoolean());
        assertEquals(7, requirements.get("minimum_players").getAsInt());
        assertTrue(hexIngredients.contains("#warlockery:lycanthropy_catalysts"));
        assertTrue(hexIngredients.contains("#warlockery:wolfsbane_reagents"));
        assertTrue(hexIngredients.contains("#warlockery:sympathetic_containers"));
    }

    private static List<String> ingredients(final JsonObject definition) {
        final JsonArray values = definition.getAsJsonObject("requirements").getAsJsonArray("ingredients");
        return StreamSupport.stream(values.spliterator(), false)
            .map(JsonElement::getAsJsonObject)
            .map(value -> value.get("ingredient").getAsString())
            .toList();
    }

    private static void assertTagContains(final String tag, final String expected) {
        final JsonObject definition = json("tags/item/" + tag + ".json");
        assertFalse(definition.get("replace").getAsBoolean());
        final List<String> values = StreamSupport.stream(
            definition.getAsJsonArray("values").spliterator(), false
        ).map(JsonElement::getAsString).toList();
        assertTrue(values.contains(expected));
    }

    private static String result(final JsonObject recipe) {
        return recipe.getAsJsonObject("result").get("id").getAsString();
    }

    private static JsonObject json(final String relative) {
        try {
            return JsonParser.parseString(Files.readString(DATA.resolve(relative))).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(relative, exception);
        }
    }
}
