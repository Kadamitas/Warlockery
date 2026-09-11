package com.kadamitas.warlockery.compat.jei;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class JeiDiscoveryRegistrationTest {
    private static final Path SOURCE = Path.of("src/main/java/com/kadamitas/warlockery/compat/jei");

    @Test
    void craftingAndHarvestToolsExposeTheirInstructionsInIngredientLookup() throws IOException {
        final String plugin = Files.readString(SOURCE.resolve("WarlockeryJeiPlugin.java"));
        final String information = plugin.substring(plugin.indexOf("private static void registerInformation"));
        for (final String item : List.of("ingredient_gypsum", "chalkheart", "chalkritual", "chalkinfernal",
            "chalk_veil", "arcane_focus", "ritual_knife", "ingredient_bat_wool", "ingredient_dog_tongue",
            "ingredient_owlets_wing", "ingredient_toe_of_frog", "ingredient_creeper_heart", "ingredient_spectral_dust")) {
            assertTrue(information.contains("\"" + item + "\""), item + " needs JEI acquisition/use information");
        }
        for (final String key : List.of("manual.warlockery.circles.golden_chalk", "manual.warlockery.circles.arthana",
            "jei.warlockery.info.arthana_harvest")) {
            assertTrue(information.contains(key), "JEI must register " + key);
        }
    }

    @Test
    void goldenChalkOpensRitualRecipesAsACraftingStation() throws IOException {
        final String plugin = Files.readString(SOURCE.resolve("WarlockeryJeiPlugin.java"));
        final String catalysts = plugin.substring(plugin.indexOf("public void registerRecipeCatalysts"),
            plugin.indexOf("public synchronized void onRuntimeAvailable"));
        assertTrue(catalysts.contains("ModItems.ALL.get(\"chalkheart\")"), "Golden Chalk needs ritual usage lookup");
    }

    @Test
    void ritualStationSlotsUseObtainableChalkAndExplainMarkCounts() throws IOException {
        final String category = Files.readString(SOURCE.resolve("RitualRecipeCategory.java"));
        for (final String chalk : List.of("chalkheart", "chalkritual", "chalkinfernal", "chalk_veil")) {
            assertTrue(category.contains("\"" + chalk + "\""), "Ritual layout needs the obtainable " + chalk + " tool");
        }
        assertTrue(category.contains("jei.warlockery.ritual.chalk_marks"), "Mark counts must not imply stacks of chalk tools");
        assertTrue(category.contains("ring.requiredCount()"), "Tooltip must use the actual canonical ring size");
    }

    @Test
    void itemBindingRitesExposeTheirProducedItemsForRecipeLookup() throws IOException {
        final String category = Files.readString(SOURCE.resolve("RitualRecipeCategory.java"));
        final String outputs = category.substring(category.indexOf("private static Optional<ItemStack> output"),
            category.indexOf("private static Component conditions"));
        for (final String action : List.of("bind_waystone", "copy_waystone", "bind_fetish")) {
            assertTrue(outputs.contains("\"" + action + "\""), action + " must expose its resulting item");
        }
        assertTrue(outputs.contains("warlockery:ingredient_waystone_bound"), "Waystone rites produce the bound item ID");
    }
}
