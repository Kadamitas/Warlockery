package com.kadamitas.warlockery.registry;

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
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class ResourceAcquisitionResourcesTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data", "warlockery");
    private static final Path BLOCK_LOOT = DATA.resolve("loot_table/blocks");
    private static final Path RECIPES = DATA.resolve("recipe");
    private static final Path MACHINES = DATA.resolve("warlockery_machine");
    private static final List<OreExpectation> ORES = List.of(
        ore("silver_ore", "warlockery:silver_ore", "warlockery:raw_silver"),
        ore("deepslate_silver_ore", "warlockery:deepslate_silver_ore", "warlockery:raw_silver"),
        ore("delvealloy_ore", "warlockery:delvealloy_ore", "warlockery:raw_delvealloy"),
        ore("deepslate_delvealloy_ore", "warlockery:deepslate_delvealloy_ore", "warlockery:raw_delvealloy")
    );
    private static final List<String> FORTUNE_CROPS = List.of(
        "artichoke", "belladonna", "dreamroot", "garlicplant",
        "mandrake", "snowbell", "wolfsbane", "wormwood"
    );
    private static final List<RecipeExpectation> CRAFTING_ACQUISITION = List.of(
        recipe("ingredient_bone_needle", "warlockery:ingredient_bone_needle"),
        recipe("ingredient_clay_jar_soft", "warlockery:ingredient_clay_jar_soft"),
        recipe("ingredient_clay_jar_from_smelting", "warlockery:ingredient_clay_jar"),
        recipe("ingredient_quicklime", "warlockery:ingredient_quicklime"),
        recipe("ingredient_apple_wormy", "warlockery:ingredient_apple_wormy"),
        recipe("ingredient_frozen_heart", "warlockery:ingredient_frozen_heart"),
        recipe("ingredient_graveyard_dust", "warlockery:ingredient_graveyard_dust"),
        recipe("ingredient_nullcatalyst", "warlockery:ingredient_nullcatalyst"),
        recipe("ingredient_nullifiedleather", "warlockery:ingredient_nullifiedleather"),
        recipe("ingredient_quartz_sphere", "warlockery:ingredient_quartz_sphere"),
        recipe("ingredient_contract", "warlockery:ingredient_contract"),
        recipe("ingredient_contract_fiery_touch", "warlockery:ingredient_contract_fiery_touch"),
        recipe("ingredient_contract_evaporate", "warlockery:ingredient_contract_evaporate"),
        recipe("ingredient_contract_resist_fire", "warlockery:ingredient_contract_resist_fire"),
        recipe("ingredient_contract_smelting", "warlockery:ingredient_contract_smelting"),
        recipe("ingredient_contract_blaze", "warlockery:ingredient_contract_blaze"),
        recipe("ingredient_contract_torment", "warlockery:ingredient_contract_torment"),
        recipe("ingredient_bolt_holy", "warlockery:ingredient_bolt_holy"),
        recipe("ingredient_bolt_stake", "warlockery:ingredient_bolt_stake"),
        recipe("ingredient_impregnated_leather", "warlockery:ingredient_impregnated_leather"),
        recipe("ingredient_purified_milk", "warlockery:ingredient_purified_milk"),
        recipe("ingredient_annointing_paste", "warlockery:ingredient_annointing_paste"),
        recipe("ingredient_attuned_stone", "warlockery:ingredient_attuned_stone"),
        recipe("ingredient_fume_filter", "warlockery:ingredient_fume_filter"),
        recipe("ingredient_bramble_colossus_seed", "warlockery:ingredient_bramble_colossus_seed"),
        recipe("ingredient_odd_porkchop_raw", "warlockery:ingredient_odd_porkchop_raw"),
        recipe("ingredient_web", "warlockery:ingredient_web"),
        recipe("ingredient_sleeping_apple", "warlockery:ingredient_sleeping_apple"),
        recipe("gunpowder_from_creeper_heart", "minecraft:gunpowder")
    );
    private static final List<MachineExpectation> MACHINE_ACQUISITION = List.of(
        machine("oven_fume_breath_of_the_goddess", "warlockery:ingredient_breath_of_the_goddess"),
        machine("oven_fume_hint_of_rebirth", "warlockery:ingredient_hint_of_rebirth"),
        machine("distill_ender_dew", "warlockery:ingredient_ender_dew"),
        machine("distill_infernal_blood", "warlockery:ingredient_infernal_blood"),
        machine("distill_condensed_fear", "warlockery:ingredient_condensed_fear")
    );

    @TestFactory
    Stream<DynamicTest> everyOreUsesSilkTouchAndFortune() {
        return ORES.stream().map(expectation -> DynamicTest.dynamicTest(
            expectation.id(),
            () -> {
                final String json = readString(BLOCK_LOOT.resolve(expectation.id() + ".json"));
                assertTrue(json.contains("minecraft:silk_touch"));
                assertTrue(json.contains("minecraft:fortune"));
                assertTrue(json.contains(expectation.silkDrop()));
                assertTrue(json.contains(expectation.rawDrop()));
            }
        ));
    }

    @TestFactory
    Stream<DynamicTest> everyCropUsesMaturityAndFortune() {
        return FORTUNE_CROPS.stream().map(id -> DynamicTest.dynamicTest(id, () -> {
            final String json = readString(BLOCK_LOOT.resolve(id + ".json"));
            assertTrue(json.contains("minecraft:match_block"));
            assertTrue(json.contains("\"age\": \"7\""));
            assertTrue(json.contains("minecraft:fortune"));
            assertTrue(json.contains("minecraft:explosion_decay"));
        }));
    }

    @Test
    void magicalLeavesUseModernHarvestAndFortuneRules() {
        final String json = readString(BLOCK_LOOT.resolve("hex_leaves.json"));
        assertTrue(json.contains("minecraft:shears"));
        assertTrue(json.contains("minecraft:silk_touch"));
        assertTrue(json.contains("minecraft:fortune"));
        assertTrue(json.contains("warlockery:hex_sapling"));
        assertTrue(json.contains("warlockery:ingredient_berries_rowan"));
    }

    @Test
    void shearOnlyPlantsRequireShears() {
        assertTrue(readString(BLOCK_LOOT.resolve("embermoss.json")).contains("minecraft:shears"));
        assertTrue(readString(BLOCK_LOOT.resolve("spanishmoss.json")).contains("minecraft:shears"));
    }

    @TestFactory
    Stream<DynamicTest> everyDocumentedCraftingRouteProducesItsReagent() {
        return CRAFTING_ACQUISITION.stream().map(expectation -> DynamicTest.dynamicTest(
            expectation.id(),
            () -> assertEquals(
                expectation.output(),
                read(RECIPES.resolve(expectation.id() + ".json"))
                    .getAsJsonObject("result")
                    .get("id")
                    .getAsString()
            )
        ));
    }

    @Test
    void impSpellsAndSpecialBoltsHaveNonCircularDiscoverableBookRoutes() {
        final List<String> ids = Stream.concat(
            java.util.Arrays.stream(com.kadamitas.warlockery.magic.ImpContractRules.Spell.values())
                .map(com.kadamitas.warlockery.magic.ImpContractRules.Spell::itemId),
            Stream.of("ingredient_bolt_holy", "ingredient_bolt_stake")
        ).toList();
        assertEquals(8, ids.size());
        for (final String id : ids) {
            final boolean contract = id.startsWith("ingredient_contract_");
            final String recipeId = "warlockery:" + id;
            final JsonObject recipe = read(RECIPES.resolve(id + ".json"));
            assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString(), id);
            final List<String> ingredients = recipe.getAsJsonArray("ingredients").asList().stream()
                .map(JsonElement::getAsString).toList();
            assertFalse(ingredients.contains(recipeId), id + " cannot consume its own unavailable output");
            assertTrue(ingredients.stream().allMatch(ingredient -> ingredient.startsWith("minecraft:")
                || ingredient.equals("#minecraft:planks") || ingredient.equals("#c:bones")
                || ingredient.equals("warlockery:ingredient_contract")), id);
            if (contract) {
                assertEquals(1, ingredients.stream().filter("warlockery:ingredient_contract"::equals).count(), id);
                assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt(), id);
            } else {
                assertEquals(ingredients.stream().filter("minecraft:arrow"::equals).count(),
                    recipe.getAsJsonObject("result").get("count").getAsInt(), id);
            }
            final JsonObject advancement = read(DATA.resolve("advancement/recipes/misc/" + id + ".json"));
            assertEquals("minecraft:recipe_unlocked", advancement.getAsJsonObject("criteria")
                .getAsJsonObject("has_the_recipe").get("trigger").getAsString(), id);
            assertEquals(recipeId, advancement.getAsJsonObject("criteria").getAsJsonObject("has_the_recipe")
                .getAsJsonObject("conditions").get("recipes").getAsString(), id);
            assertEquals(List.of(recipeId), advancement.getAsJsonObject("rewards").getAsJsonArray("recipes")
                .asList().stream().map(JsonElement::getAsString).toList(), id);
            final Set<String> inventoryIngredients = advancement.getAsJsonObject("criteria").entrySet().stream()
                .map(entry -> entry.getValue().getAsJsonObject())
                .filter(criterion -> criterion.get("trigger").getAsString().equals("minecraft:inventory_changed"))
                .flatMap(criterion -> criterion.getAsJsonObject("conditions").getAsJsonArray("items").asList().stream())
                .map(JsonElement::getAsJsonObject).map(item -> item.get("items").getAsString())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
            assertEquals(Set.copyOf(ingredients), inventoryIngredients, id);
            assertEquals(1, advancement.getAsJsonArray("requirements").size(), id);
            assertEquals(advancement.getAsJsonObject("criteria").keySet(),
                advancement.getAsJsonArray("requirements").get(0).getAsJsonArray().asList().stream()
                    .map(JsonElement::getAsString).collect(java.util.stream.Collectors.toUnmodifiableSet()), id);
            final var manual = com.kadamitas.warlockery.item.ManualProfile.find(
                contract ? "ingredient_book_burning" : "ingredient_book_infusions").orElseThrow();
            assertTrue(manual.sections().containsAll(List.of("crafting_" + id, id)), id);
            assertEquals(manual.chapterFor(id), manual.chapterFor("crafting_" + id), id);
        }
    }

    @TestFactory
    Stream<DynamicTest> everyDocumentedMachineRouteProducesItsReagent() {
        return MACHINE_ACQUISITION.stream().map(expectation -> DynamicTest.dynamicTest(
            expectation.id(),
            () -> {
                final JsonArray outputs = read(MACHINES.resolve(expectation.id() + ".json"))
                    .getAsJsonArray("outputs");
                final Set<String> ids = outputs.asList().stream()
                    .map(JsonElement::getAsJsonObject)
                    .map(output -> output.get("item").getAsString())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
                assertTrue(ids.contains(expectation.output()));
            }
        ));
    }

    @Test
    void machineSourceTagsDeclarePrivateExtensionPoints() {
        assertTag(
            "tags/item/alchemical_oven/fume_sources/breath_of_the_goddess.json",
            "minecraft:birch_sapling"
        );
        assertTag(
            "tags/item/alchemical_oven/fume_sources/hint_of_rebirth.json",
            "minecraft:spruce_sapling"
        );
        assertTag(
            "tags/item/distillery/spirit_fluid_containers.json",
            "warlockery:bucketspirit"
        );
        assertTrue(readString(MACHINES.resolve("oven_fume_breath_of_the_goddess.json"))
            .contains("minecraft:birch_sapling"));
        final String condensedFear = readString(MACHINES.resolve("distill_condensed_fear.json"));
        assertTrue(condensedFear.contains("warlockery:bucketspirit"));
        assertTrue(condensedFear.contains("minecraft:bucket"));
    }

    @Test
    void acquisitionResourcesUseCanonicalNamespaces() throws IOException {
        final List<Path> roots = List.of(BLOCK_LOOT, RECIPES, MACHINES, DATA.resolve("tags"));
        for (final Path root : roots) {
            try (var paths = Files.walk(root)) {
                assertTrue(paths
                    .filter(path -> path.toString().endsWith(".json"))
                    .noneMatch(path -> readString(path).contains("forge:")));
            }
        }
        assertTrue(readString(RECIPES.resolve("ingredient_bone_needle.json")).contains("#c:bones"));
        assertTrue(readString(RECIPES.resolve("ingredient_nullcatalyst.json")).contains("#c:nether_stars"));
        assertTrue(readString(RECIPES.resolve("ingredient_nullifiedleather.json")).contains("#c:leathers"));
        assertTrue(readString(RECIPES.resolve("ingredient_quartz_sphere.json")).contains("#c:gems/quartz"));
    }

    private static void assertTag(final String relative, final String expected) {
        final JsonObject tag = read(DATA.resolve(relative));
        assertFalse(tag.get("replace").getAsBoolean());
        assertTrue(tag.getAsJsonArray("values").asList().stream()
            .map(JsonElement::getAsString)
            .anyMatch(expected::equals));
    }

    private static JsonObject read(final Path path) {
        return JsonParser.parseString(readString(path)).getAsJsonObject();
    }

    private static String readString(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private static OreExpectation ore(final String id, final String silkDrop, final String rawDrop) {
        return new OreExpectation(id, silkDrop, rawDrop);
    }

    private static RecipeExpectation recipe(final String id, final String output) {
        return new RecipeExpectation(id, output);
    }

    private static MachineExpectation machine(final String id, final String output) {
        return new MachineExpectation(id, output);
    }

    private record OreExpectation(String id, String silkDrop, String rawDrop) {
    }

    private record RecipeExpectation(String id, String output) {
    }

    private record MachineExpectation(String id, String output) {
    }
}
