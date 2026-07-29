package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class ManualProducerIntegrityTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data", "warlockery");
    private static final Path RECIPES = DATA.resolve("recipe");
    private static final Map<String, ExpectedRecipe> EXPECTED = Map.ofEntries(
        expected("cauldronbook", List.of(" D ", "IBQ"),
            "D", "#warlockery:solidifying/dirt_targets", "I", "#c:dyes/black",
            "B", "#warlockery:manual_reagents/books", "Q", "#c:feathers"),
        expected("ingredient_book_herbology", List.of(" F ", "IBQ", " G "),
            "F", "#minecraft:small_flowers", "I", "#c:dyes/black",
            "B", "#warlockery:manual_reagents/books", "Q", "#c:feathers",
            "G", "#minecraft:small_flowers"),
        expected("ingredient_book_wands", List.of(" T ", "IBQ"),
            "T", "#warlockery:brew_reagents/ent_twigs", "I", "#c:dyes/black",
            "B", "#warlockery:manual_reagents/books", "Q", "#c:feathers"),
        expected("ingredient_book_biomes", List.of(" S ", "IBQ", " R "),
            "S", "#minecraft:saplings", "I", "#c:dyes/black",
            "B", "#warlockery:manual_reagents/books", "Q", "#c:feathers", "R", "#c:stones"),
        expected("bookbiomes2", List.of(" S ", "SMS", " S "),
            "S", "#c:stones", "M", "#warlockery:manual_reagents/biome_manuals"),
        expected("ingredient_book_burning", List.of(" A ", "IBQ", " F "),
            "A", "#warlockery:brew_reagents/wood_ashes", "I", "#c:dyes/black",
            "B", "#warlockery:manual_reagents/books", "Q", "#c:feathers",
            "F", "#warlockery:brazier_igniters"),
        expected("ingredient_book_oven", List.of(" L ", "IBQ", " C "),
            "L", "#warlockery:custom_brew/lingering/two", "I", "#c:dyes/black",
            "B", "#warlockery:manual_reagents/books", "Q", "#c:feathers", "C", "#minecraft:coals"),
        expected("ingredient_book_distilling", List.of(" L ", "IBQ", " G "),
            "L", "#warlockery:custom_brew/lingering/two", "I", "#c:dyes/black",
            "B", "#warlockery:manual_reagents/books", "Q", "#c:feathers",
            "G", "#warlockery:manual_reagents/breaths_of_the_goddess"),
        expected("ingredient_book_circle_magic", List.of(" L ", "IBQ", " W "),
            "L", "#warlockery:custom_brew/lingering/two", "I", "#c:dyes/black",
            "B", "#warlockery:manual_reagents/books", "Q", "#c:feathers",
            "W", "#warlockery:brew_reagents/whiffs_of_magic"),
        expected("ingredient_book_infusions", List.of(" L ", "IBQ", " O "),
            "L", "#warlockery:custom_brew/lingering/two", "I", "#c:dyes/black",
            "B", "#warlockery:manual_reagents/books", "Q", "#c:feathers",
            "O", "#warlockery:brew_reagents/odours_of_purity"),
        expected("vampirebook", List.of("WSW", "WBW", "WGW"),
            "W", "#c:crops/nether_wart", "S", "#c:nether_stars",
            "B", "#warlockery:manual_reagents/books", "G", "#warlockery:manual_reagents/garlic")
    );

    @TestFactory
    Stream<DynamicContainer> oneExactProducerSuitePerCraftedManual() {
        return EXPECTED.entrySet().stream().map(entry -> DynamicContainer.dynamicContainer(
            entry.getKey(),
            List.of(
                DynamicTest.dynamicTest("exact formula", () -> exactFormula(entry.getKey(), entry.getValue())),
                DynamicTest.dynamicTest("tag extension points", () -> tagExtensionPoints(entry.getKey()))
            )
        ));
    }

    @Test
    void everyManualProfileHasExactlyOneSurvivalRoute() {
        final Set<String> expectedIds = Stream.concat(EXPECTED.keySet().stream(), Stream.of("ingredient_vbook_page"))
            .collect(Collectors.toUnmodifiableSet());
        assertEquals(expectedIds, ManualProfile.ids());
        EXPECTED.keySet().forEach(id -> assertTrue(Files.exists(RECIPES.resolve(id + ".json")), id));
        assertFalse(Files.exists(RECIPES.resolve("ingredient_vbook_page.json")));
    }

    @Test
    void observationsUsesSixNetherWartAndOneOfEveryCoreReagent() {
        final JsonObject recipe = read(RECIPES.resolve("vampirebook.json"));
        final List<String> pattern = strings(recipe, "pattern");
        assertEquals(6, pattern.stream().flatMapToInt(String::chars).filter(value -> value == 'W').count());
        assertEquals(1, pattern.stream().flatMapToInt(String::chars).filter(value -> value == 'S').count());
        assertEquals(1, pattern.stream().flatMapToInt(String::chars).filter(value -> value == 'B').count());
        assertEquals(1, pattern.stream().flatMapToInt(String::chars).filter(value -> value == 'G').count());
    }

    @Test
    void vampireLootProvidesTornPagesWithLootingSupport() {
        final JsonObject loot = read(DATA.resolve("loot_table/entities/vampire.json"));
        final JsonObject pool = loot.getAsJsonArray("pools").get(0).getAsJsonObject();
        final JsonObject entry = pool.getAsJsonArray("entries").get(0).getAsJsonObject();
        assertEquals("warlockery:ingredient_vbook_page", entry.get("name").getAsString());
        final List<JsonObject> conditions = pool.getAsJsonArray("conditions").asList().stream()
            .map(JsonElement::getAsJsonObject).toList();
        assertTrue(conditions.stream().anyMatch(condition ->
            condition.get("condition").getAsString().equals("minecraft:killed_by_player")));
        final JsonObject chance = conditions.stream().filter(condition ->
            condition.get("condition").getAsString().equals("minecraft:random_chance_with_enchanted_bonus"))
            .findFirst().orElseThrow();
        assertEquals("minecraft:looting", chance.get("enchantment").getAsString());
        assertEquals(0.18, chance.get("unenchanted_chance").getAsDouble());
        assertEquals(0.25, chance.getAsJsonObject("enchanted_chance").get("base").getAsDouble());
    }

    @Test
    void manualExtensionTagsHaveSurvivalDefaults() {
        final Map<String, String> tags = Map.of(
            "books", "minecraft:book",
            "biome_manuals", "warlockery:ingredient_book_biomes",
            "breaths_of_the_goddess", "warlockery:ingredient_breath_of_the_goddess",
            "garlic", "warlockery:garlic"
        );
        tags.forEach((tag, value) -> {
            final JsonObject json = read(DATA.resolve("tags/item/manual_reagents/" + tag + ".json"));
            assertFalse(json.get("replace").getAsBoolean());
            assertEquals(List.of(value), strings(json, "values"));
        });
    }

    private static void exactFormula(final String id, final ExpectedRecipe expected) {
        final JsonObject recipe = read(RECIPES.resolve(id + ".json"));
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals(expected.pattern(), strings(recipe, "pattern"));
        assertEquals(expected.key(), recipe.getAsJsonObject("key").entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().getAsString())));
        assertEquals("warlockery:" + id, recipe.getAsJsonObject("result").get("id").getAsString());
    }

    private static void tagExtensionPoints(final String id) {
        final JsonObject recipe = read(RECIPES.resolve(id + ".json"));
        final List<String> ingredients = recipe.getAsJsonObject("key").entrySet().stream()
            .map(Map.Entry::getValue).map(JsonElement::getAsString).toList();
        assertTrue(ingredients.stream().allMatch(ingredient -> ingredient.startsWith("#")));
        ingredients.stream().filter(ingredient -> ingredient.startsWith("#warlockery:"))
            .map(ingredient -> ingredient.substring("#warlockery:".length()))
            .forEach(tag -> assertTrue(Files.exists(DATA.resolve("tags/item/" + tag + ".json")), tag));
    }

    private static Map.Entry<String, ExpectedRecipe> expected(
        final String id,
        final List<String> pattern,
        final String... key
    ) {
        final Map<String, String> values = java.util.stream.IntStream.range(0, key.length / 2)
            .boxed().collect(Collectors.toUnmodifiableMap(index -> key[index * 2], index -> key[index * 2 + 1]));
        return Map.entry(id, new ExpectedRecipe(pattern, values));
    }

    private static List<String> strings(final JsonObject json, final String member) {
        return json.getAsJsonArray(member).asList().stream().map(JsonElement::getAsString).toList();
    }

    private static JsonObject read(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private record ExpectedRecipe(List<String> pattern, Map<String, String> key) {
    }
}
