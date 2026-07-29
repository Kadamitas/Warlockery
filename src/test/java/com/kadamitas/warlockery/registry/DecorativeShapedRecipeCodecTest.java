package com.kadamitas.warlockery.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.junit.jupiter.api.Test;

final class DecorativeShapedRecipeCodecTest {
    private static final Path RECIPE_DIRECTORY = Path.of("src/main/resources/data/warlockery/recipe");
    private static final Map<String, RecipeExpectation> RECIPES = Map.of(
        "alluringskull", new RecipeExpectation(
            List.of(" B ", "GSG", " N "),
            Map.of(
                "B", "#c:bones",
                "G", "warlockery:ingredient_graveyard_dust",
                "S", "minecraft:skeleton_skull",
                "N", "warlockery:ingredient_necro_stone"
            ),
            "warlockery:alluringskull"
        ),
        "candelabra", new RecipeExpectation(
            List.of("CCC", " G ", "GGG"),
            Map.of("C", "#minecraft:candles", "G", "#c:ingots/gold"),
            "warlockery:candelabra"
        ),
        "chalice", new RecipeExpectation(
            List.of("G G", " G ", "GGG"),
            Map.of("G", "#c:ingots/gold"),
            "warlockery:chalice"
        ),
        "dreamcatcher", new RecipeExpectation(
            List.of(" F ", "FSF", " H "),
            Map.of(
                "F", "warlockery:ingredient_fanciful_thread",
                "S", "#c:rods/wooden",
                "H", "warlockery:ingredient_mellifluous_hunger"
            ),
            "warlockery:dreamcatcher"
        ),
        "ingredient_pentacle", new RecipeExpectation(
            List.of("NNN", "NIN", "NNN"),
            Map.of("N", "#c:nuggets/koboldite", "I", "#c:ingots/koboldite"),
            "warlockery:ingredient_pentacle"
        ),
        "statue_of_hobgoblin_patron", new RecipeExpectation(
            List.of(" S ", "SKS", "SSS"),
            Map.of("S", "#c:stones", "K", "#c:ingots/koboldite"),
            "warlockery:statueofworship"
        )
    );

    @Test
    void decorativeRecipesDecodeWithTheMinecraftCraftingBookSchema() throws IOException {
        for (final var entry : RECIPES.entrySet()) {
            final JsonObject recipe = readRecipe(entry.getKey());
            final CraftingRecipe.CraftingBookInfo bookInfo = CraftingRecipe.CraftingBookInfo.MAP_CODEC
                .codec()
                .parse(JsonOps.INSTANCE, recipe)
                .getOrThrow();

            assertEquals(CraftingBookCategory.MISC, bookInfo.category(), entry.getKey());
            assertEquals("", bookInfo.group(), entry.getKey());
        }
    }

    @Test
    void decorativeRecipeFormulasRemainExact() throws IOException {
        for (final var entry : RECIPES.entrySet()) {
            final JsonObject recipe = readRecipe(entry.getKey());
            final RecipeExpectation expected = entry.getValue();

            assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString(), entry.getKey());
            assertEquals(expected.pattern(), recipe.getAsJsonArray("pattern").asList().stream()
                .map(element -> element.getAsString())
                .toList(), entry.getKey());
            assertEquals(expected.key(), recipe.getAsJsonObject("key").entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey,
                    ingredient -> ingredient.getValue().getAsString()
                )), entry.getKey());
            assertEquals(expected.result(), recipe.getAsJsonObject("result").get("id").getAsString(), entry.getKey());
            assertTrue(!recipe.getAsJsonObject("result").has("count")
                || recipe.getAsJsonObject("result").get("count").getAsInt() == 1, entry.getKey());
        }
    }

    private static JsonObject readRecipe(final String name) throws IOException {
        return JsonParser.parseString(Files.readString(RECIPE_DIRECTORY.resolve(name + ".json"))).getAsJsonObject();
    }

    private record RecipeExpectation(List<String> pattern, Map<String, String> key, String result) {
    }
}
