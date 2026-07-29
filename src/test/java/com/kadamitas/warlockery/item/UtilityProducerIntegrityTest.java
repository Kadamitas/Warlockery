package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.crafting.MachineRecipeDefinition;
import com.kadamitas.warlockery.testutil.JsonFixtureLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class UtilityProducerIntegrityTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data");
    private static final Path RECIPES = DATA.resolve("warlockery/recipe");
    private static final Path MACHINES = DATA.resolve("warlockery/warlockery_machine");
    private static final Map<String, ExpectedRecipe> SHAPED = Map.ofEntries(
        expected("divinerwater", List.of("BAB", "BAB", "ATA"),
            "B", "#warlockery:utility_reagents/water_bottles", "A", "#c:rods/wooden",
            "T", "#warlockery:brazier/tears"),
        expected("divinerlava", List.of(" R ", " W ", "R R"),
            "R", "#c:rods/blaze", "W", "#warlockery:utility_reagents/water_diviners"),
        expected("shelfcompass", List.of("GDG", "DCD", "GNG"),
            "G", "#c:ingots/gold", "D", "#c:gems/diamond",
            "C", "#warlockery:utility_reagents/clocks", "N", "#warlockery:utility_reagents/null_catalysts"),
        expected("circletalisman", List.of("NGN", "GDG", "NGN"),
            "N", "#c:nuggets/gold", "G", "#c:ingots/gold", "D", "#c:gems/diamond"),
        expected("canesword", List.of(" DC", "DSD", "DD "),
            "D", "#warlockery:utility_reagents/dark_cloths", "C", "#c:ingots/gold",
            "S", "#warlockery:utility_reagents/diamond_swords"),
        expected("witchhat", List.of(" I ", "TIT", "IGI"),
            "I", "#warlockery:utility_reagents/impregnated_leathers",
            "T", "#warlockery:utility_reagents/golden_threads", "G", "#c:dusts/glowstone"),
        expected("witchrobe", List.of("ITI", "ICI", "III"),
            "I", "#warlockery:utility_reagents/impregnated_leathers",
            "T", "#warlockery:utility_reagents/golden_threads",
            "C", "#warlockery:utility_reagents/creeper_hearts"),
        expected("necromancerrobe", List.of("ITI", "INI", "III"),
            "I", "#warlockery:utility_reagents/impregnated_leathers",
            "T", "#warlockery:utility_reagents/golden_threads", "N", "#warlockery:necromantic_foci"),
        expected("seepingshoes", List.of("ITI", "DRD", "MMM"),
            "I", "#warlockery:utility_reagents/impregnated_leathers",
            "T", "#warlockery:utility_reagents/golden_threads", "D", "#warlockery:death_weapons",
            "R", "#warlockery:utility_reagents/redstone_soups", "M", "#c:buckets/milk"),
        expected("barkbelt", List.of("SES", "TBT", "SCS"),
            "S", "#warlockery:distillery/spirit_fluid_containers", "E", "#c:gems/emerald",
            "T", "#warlockery:brew_reagents/ent_twigs", "B", "#warlockery:utility_reagents/biting_belts",
            "C", "#warlockery:utility_reagents/creeper_hearts"),
        expected("ingredient_bolt_splitting", List.of(" S ", "AAA", " F "),
            "S", "#c:strings", "A", "#warlockery:projectile_reagents/standard_arrows",
            "F", "#c:feathers"),
        expected("sungrenade", List.of(" G ", "GQG", " F "), 2,
            "G", "#c:dusts/glowstone", "Q", "#warlockery:solar_chargeables",
            "F", "#warlockery:utility_reagents/sunflowers")
    );

    @TestFactory
    Stream<DynamicContainer> oneExactSuitePerShapedProducer() {
        return SHAPED.entrySet().stream().map(entry -> DynamicContainer.dynamicContainer(
            entry.getKey(),
            List.of(
                DynamicTest.dynamicTest("exact formula", () -> exactShaped(entry.getKey(), entry.getValue())),
                DynamicTest.dynamicTest("tag extension points", () -> tagCompatible(entry.getKey())),
                DynamicTest.dynamicTest("non-circular output", () -> nonCircular(entry.getKey()))
            )
        ));
    }

    @Test
    void antiMagicProjectilesUseModernSpectralArrowBases() {
        final JsonObject recipe = read(RECIPES.resolve("ingredient_bolt_anti_magic.json"));
        assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
        final Map<String, Long> inputs = strings(recipe, "ingredients").stream()
            .collect(Collectors.groupingBy(value -> value, Collectors.counting()));
        assertEquals(Map.of(
            "#warlockery:utility_reagents/null_catalysts", 1L,
            "#warlockery:custom_brew/lingering/two", 2L,
            "#warlockery:projectile_reagents/holy_arrows", 3L
        ), inputs);
        assertTrue(inputs.keySet().stream().allMatch(input -> input.startsWith("#")));
        assertResult(recipe, "ingredient_bolt_anti_magic", 3);
    }

    @Test
    void playerCompassUsesTheModernCauldronRoute() {
        final MachineRecipeDefinition recipe = JsonFixtureLoader.load(MACHINES, MachineRecipeDefinition.CODEC).stream()
            .filter(fixture -> fixture.id().equals("cauldron_playercompass"))
            .map(JsonFixtureLoader.Fixture::value)
            .findFirst().orElseThrow();
        assertEquals("cauldron", recipe.machine());
        assertEquals(360, recipe.processingTime());
        assertEquals(5000, recipe.altarPower());
        assertEquals("#minecraft:water", recipe.fluid().orElseThrow().ingredient());
        assertEquals(250, recipe.fluid().orElseThrow().amount());
        assertEquals(Map.of(
            "#warlockery:utility_reagents/vanilla_compasses", 1,
            "#c:crops/nether_wart", 1,
            "#warlockery:brazier/tears", 1,
            "#warlockery:utility_reagents/vines", 1,
            "#warlockery:brazier/spider_eyes", 1
        ), recipe.inputs().stream().collect(Collectors.toUnmodifiableMap(
            MachineRecipeDefinition.Input::ingredient,
            MachineRecipeDefinition.Input::count
        )));
        assertEquals(List.of(new MachineRecipeDefinition.Output("warlockery:playercompass", 1)), recipe.outputs());
    }

    @Test
    void onlyIntentionalTargetDependencyIsWaterToLavaDiviner() {
        final Set<String> targetOutputs = Stream.concat(SHAPED.keySet().stream(), Stream.of(
            "ingredient_bolt_anti_magic", "playercompass"
        )).map(id -> "warlockery:" + id).collect(Collectors.toUnmodifiableSet());
        final Map<String, Set<String>> dependencies = SHAPED.keySet().stream().collect(Collectors.toUnmodifiableMap(
            id -> "warlockery:" + id,
            id -> keyValues(read(RECIPES.resolve(id + ".json"))).stream()
                .flatMap(input -> resolve(input, new HashSet<>()).stream())
                .filter(targetOutputs::contains)
                .collect(Collectors.toUnmodifiableSet())
        ));
        assertEquals(Set.of("warlockery:divinerwater"), dependencies.get("warlockery:divinerlava"));
        dependencies.entrySet().stream().filter(entry -> !entry.getKey().equals("warlockery:divinerlava"))
            .forEach(entry -> assertTrue(entry.getValue().isEmpty(), entry::toString));
    }

    @Test
    void privateProducerTagsHaveSurvivalDefaults() {
        final Map<String, String> defaults = Map.ofEntries(
            Map.entry("utility_reagents/water_bottles", "minecraft:potion"),
            Map.entry("utility_reagents/water_diviners", "warlockery:divinerwater"),
            Map.entry("utility_reagents/vanilla_compasses", "minecraft:compass"),
            Map.entry("utility_reagents/clocks", "minecraft:clock"),
            Map.entry("utility_reagents/null_catalysts", "warlockery:ingredient_nullcatalyst"),
            Map.entry("utility_reagents/dark_cloths", "warlockery:ingredient_woven_cruor"),
            Map.entry("utility_reagents/diamond_swords", "minecraft:diamond_sword"),
            Map.entry("utility_reagents/impregnated_leathers", "warlockery:ingredient_impregnated_leather"),
            Map.entry("utility_reagents/golden_threads", "warlockery:ingredient_golden_thread"),
            Map.entry("utility_reagents/creeper_hearts", "warlockery:ingredient_creeper_heart"),
            Map.entry("utility_reagents/redstone_soups", "warlockery:ingredient_redstone_soup"),
            Map.entry("utility_reagents/biting_belts", "warlockery:bitingbelt"),
            Map.entry("utility_reagents/vines", "minecraft:vine"),
            Map.entry("utility_reagents/sunflowers", "minecraft:sunflower"),
            Map.entry("projectile_reagents/standard_arrows", "minecraft:arrow"),
            Map.entry("projectile_reagents/holy_arrows", "minecraft:spectral_arrow")
        );
        defaults.forEach((tag, value) -> {
            final JsonObject json = read(DATA.resolve("warlockery/tags/item/" + tag + ".json"));
            assertFalse(json.get("replace").getAsBoolean());
            assertEquals(List.of(value), strings(json, "values"));
        });
    }

    private static void exactShaped(final String id, final ExpectedRecipe expected) {
        final JsonObject recipe = read(RECIPES.resolve(id + ".json"));
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals(expected.pattern(), strings(recipe, "pattern"));
        assertEquals(expected.key(), key(recipe));
        assertResult(recipe, id, expected.count());
    }

    private static void tagCompatible(final String id) {
        final Set<String> values = keyValues(read(RECIPES.resolve(id + ".json")));
        assertTrue(values.stream().allMatch(value -> value.startsWith("#")));
        values.stream().filter(value -> value.startsWith("#warlockery:"))
            .map(value -> value.substring("#warlockery:".length()))
            .forEach(tag -> assertTrue(Files.exists(DATA.resolve("warlockery/tags/item/" + tag + ".json")), tag));
    }

    private static void nonCircular(final String id) {
        final String output = "warlockery:" + id;
        keyValues(read(RECIPES.resolve(id + ".json"))).forEach(input ->
            assertFalse(resolve(input, new HashSet<>()).contains(output), input));
    }

    private static void assertResult(final JsonObject recipe, final String id, final int count) {
        final JsonObject result = recipe.getAsJsonObject("result");
        assertEquals("warlockery:" + id, result.get("id").getAsString());
        assertEquals(count, result.has("count") ? result.get("count").getAsInt() : 1);
    }

    private static Map.Entry<String, ExpectedRecipe> expected(
        final String id,
        final List<String> pattern,
        final String... key
    ) {
        return expected(id, pattern, 1, key);
    }

    private static Map.Entry<String, ExpectedRecipe> expected(
        final String id,
        final List<String> pattern,
        final int count,
        final String... key
    ) {
        final Map<String, String> values = java.util.stream.IntStream.range(0, key.length / 2)
            .boxed().collect(Collectors.toUnmodifiableMap(index -> key[index * 2], index -> key[index * 2 + 1]));
        return Map.entry(id, new ExpectedRecipe(pattern, values, count));
    }

    private static Set<String> keyValues(final JsonObject recipe) {
        return Set.copyOf(key(recipe).values());
    }

    private static Map<String, String> key(final JsonObject recipe) {
        return recipe.getAsJsonObject("key").entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().getAsString()));
    }

    private static Set<String> resolve(final String input, final Set<String> visited) {
        if (!input.startsWith("#")) {
            return Set.of(input);
        }
        final String tag = input.substring(1);
        if (!visited.add(tag)) {
            return Set.of();
        }
        final int separator = tag.indexOf(':');
        final Path path = DATA.resolve(tag.substring(0, separator)).resolve("tags/item")
            .resolve(tag.substring(separator + 1) + ".json");
        if (!Files.exists(path)) {
            return Set.of();
        }
        return strings(read(path), "values").stream()
            .flatMap(value -> resolve(value, visited).stream())
            .collect(Collectors.toUnmodifiableSet());
    }

    private static List<String> strings(final JsonObject json, final String member) {
        return json.getAsJsonArray(member).asList().stream().map(value -> value.isJsonPrimitive()
            ? value.getAsString()
            : value.getAsJsonObject().get("id").getAsString()).toList();
    }

    private static JsonObject read(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private record ExpectedRecipe(List<String> pattern, Map<String, String> key, int count) {
    }
}
