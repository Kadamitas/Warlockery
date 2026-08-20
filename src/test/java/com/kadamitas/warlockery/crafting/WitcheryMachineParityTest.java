package com.kadamitas.warlockery.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class WitcheryMachineParityTest {
    private static final Path RECIPES = Path.of(
        "src", "main", "resources", "data", "warlockery", "warlockery_machine"
    );

    @Test
    void spinningWheelUsesWitcheryTimingPowerAndCobwebRecipe() throws IOException {
        final List<JsonObject> recipes = recipesWithPrefix("spin_");
        assertEquals(4, recipes.size());
        recipes.forEach(recipe -> assertContinuousRecipe(recipe, "spinningwheel", 300, 180));

        final JsonObject cobweb = recipe("spin_wool");
        assertEquals(8, inputCount(cobweb, "#c:strings"));
        assertEquals(1, outputCount(cobweb, "minecraft:cobweb"));
        assertEquals(0, outputCount(cobweb, "minecraft:white_wool"));
    }

    @Test
    void distilleryUsesWitcheryRolesTimingRecipesAndByproducts() throws IOException {
        final List<JsonObject> recipes = recipesWithPrefix("distill_");
        assertEquals(9, recipes.size());
        recipes.forEach(recipe -> {
            assertContinuousRecipe(recipe, "distillery", 800, 480);
            assertFalse(recipe.has("requires_fuel") && recipe.get("requires_fuel").getAsBoolean());
            assertTrue(inputCount(recipe, "warlockery:ingredient_clay_jar") > 0);
        });

        assertEquals(1, outputCount(recipe("distill_vitriol"), "minecraft:slime_ball"));
        assertEquals(1, outputCount(recipe("distill_magic"), "minecraft:slime_ball"));

        assertOutputs(
            recipe("distill_diamond_vapour"),
            output("warlockery:ingredient_diamond_vapour"),
            output("warlockery:ingredient_diamond_vapour"),
            output("warlockery:ingredient_odour_of_purity")
        );
        assertOutputs(
            recipe("distill_ender_dew"),
            output("warlockery:ingredient_ender_dew", 2),
            output("warlockery:ingredient_ender_dew", 2),
            output("warlockery:ingredient_ender_dew"),
            output("warlockery:ingredient_whiff_of_magic")
        );

        final JsonObject glowstone = recipe("distill_glowstone");
        assertEquals(1, inputCount(glowstone, "minecraft:blaze_powder"));
        assertEquals(1, inputCount(glowstone, "#c:gunpowders"));
        assertOutputs(
            glowstone,
            output("minecraft:glowstone_dust"),
            output("minecraft:glowstone_dust"),
            output("warlockery:ingredient_reek_of_misfortune")
        );

        assertOutputs(
            recipe("distill_infernal_blood"),
            output("warlockery:ingredient_infernal_blood", 2),
            output("warlockery:ingredient_infernal_blood", 2),
            output("warlockery:ingredient_refined_evil")
        );

        final JsonObject netherrack = recipe("distill_infernal_blood_from_netherrack");
        assertEquals(1, inputCount(netherrack, "warlockery:demonheart"));
        assertEquals(1, inputCount(netherrack, "minecraft:netherrack"));
        assertOutputs(
            netherrack,
            output("minecraft:soul_sand"),
            output("warlockery:ingredient_infernal_blood"),
            output("warlockery:ingredient_infernal_blood")
        );
    }

    @Test
    void condensedFearUsesIntentionalBucketBasedModernization() throws IOException {
        final JsonObject condensedFear = recipe("distill_condensed_fear");
        assertFalse(condensedFear.has("fluid"));
        assertEquals(1, inputCount(condensedFear, "warlockery:bucketspirit"));
        assertOutputs(
            condensedFear,
            output("warlockery:ingredient_focused_will"),
            output("warlockery:ingredient_condensed_fear"),
            output("warlockery:buckethollowtears"),
            output("minecraft:bucket")
        );
    }

    private static void assertContinuousRecipe(
        final JsonObject recipe,
        final String machine,
        final int processingTime,
        final int altarPower
    ) {
        assertEquals(machine, recipe.get("machine").getAsString());
        assertEquals(processingTime, recipe.get("processing_time").getAsInt());
        assertEquals(altarPower, recipe.get("altar_power").getAsInt());
        assertEquals("continuous", recipe.get("power_mode").getAsString());
    }

    private static List<JsonObject> recipesWithPrefix(final String prefix) throws IOException {
        try (var paths = Files.list(RECIPES)) {
            return paths
                .filter(path -> path.getFileName().toString().startsWith(prefix))
                .sorted()
                .map(path -> {
                    try {
                        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .toList();
        }
    }

    private static JsonObject recipe(final String name) throws IOException {
        return JsonParser.parseString(Files.readString(RECIPES.resolve(name + ".json"))).getAsJsonObject();
    }

    private static int inputCount(final JsonObject recipe, final String ingredient) {
        return recipe.getAsJsonArray("inputs").asList().stream()
            .map(element -> element.getAsJsonObject())
            .filter(input -> ingredient.equals(input.get("ingredient").getAsString()))
            .mapToInt(input -> input.has("count") ? input.get("count").getAsInt() : 1)
            .sum();
    }

    private static int outputCount(final JsonObject recipe, final String item) {
        return recipe.getAsJsonArray("outputs").asList().stream()
            .map(element -> element.getAsJsonObject())
            .filter(output -> item.equals(output.get("item").getAsString()))
            .mapToInt(output -> output.has("count") ? output.get("count").getAsInt() : 1)
            .sum();
    }

    private static void assertOutputs(final JsonObject recipe, final ExpectedOutput... expected) {
        final List<JsonObject> actual = recipe.getAsJsonArray("outputs").asList().stream()
            .map(element -> element.getAsJsonObject())
            .toList();
        assertEquals(expected.length, actual.size());
        for (int slot = 0; slot < expected.length; slot++) {
            final JsonObject output = actual.get(slot);
            assertEquals(expected[slot].item(), output.get("item").getAsString(), "output slot " + slot);
            assertEquals(
                expected[slot].count(),
                output.has("count") ? output.get("count").getAsInt() : 1,
                "output slot " + slot
            );
        }
    }

    private static ExpectedOutput output(final String item) {
        return output(item, 1);
    }

    private static ExpectedOutput output(final String item, final int count) {
        return new ExpectedOutput(item, count);
    }

    private record ExpectedOutput(String item, int count) {
    }
}

