package com.kadamitas.warlockery.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.crafting.MachineRecipeDefinition;
import com.kadamitas.warlockery.testutil.JsonFixtureLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class SurvivalProducerIntegrityTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data");
    private static final Path MACHINES = DATA.resolve("warlockery/warlockery_machine");
    private static final Path RECIPES = DATA.resolve("warlockery/recipe");
    private static final Map<String, ExpectedMachine> EXPECTED = expectedMachines();
    private static final Map<String, MachineRecipeDefinition> MACHINES_BY_ID = JsonFixtureLoader.load(
        MACHINES, MachineRecipeDefinition.CODEC
    ).stream().collect(Collectors.toUnmodifiableMap(JsonFixtureLoader.Fixture::id, JsonFixtureLoader.Fixture::value));

    @TestFactory
    Stream<DynamicContainer> oneExactSuitePerKettleProducer() {
        return EXPECTED.entrySet().stream().map(entry -> DynamicContainer.dynamicContainer(
            entry.getKey(),
            List.of(
                DynamicTest.dynamicTest("exact formula", () -> exactFormula(entry.getKey(), entry.getValue())),
                DynamicTest.dynamicTest("cross-mod tags", () -> crossModTags(entry.getKey())),
                DynamicTest.dynamicTest("survival output", () -> survivalOutput(entry.getKey(), entry.getValue()))
            )
        ));
    }

    @Test
    void targetProducerGraphIsAcyclic() {
        final Set<String> targetOutputs = EXPECTED.values().stream()
            .map(ExpectedMachine::output)
            .collect(Collectors.toUnmodifiableSet());
        final Map<String, Set<String>> dependencies = EXPECTED.entrySet().stream().collect(Collectors.toUnmodifiableMap(
            entry -> entry.getValue().output(),
            entry -> machine(entry.getKey()).inputs().stream()
                .flatMap(input -> resolve(input.ingredient(), new HashSet<>()).stream())
                .filter(targetOutputs::contains)
                .collect(Collectors.toUnmodifiableSet())
        ));
        assertEquals(
            Set.of("warlockery:ingredient_brew_sprouting"),
            dependencies.get("warlockery:ingredient_brew_hexed_leaping")
        );
        dependencies.entrySet().stream()
            .filter(entry -> !entry.getKey().equals("warlockery:ingredient_brew_hexed_leaping"))
            .forEach(entry -> assertTrue(entry.getValue().isEmpty(), entry::toString));
        final Set<String> complete = new HashSet<>();
        dependencies.keySet().forEach(output -> assertAcyclic(output, dependencies, new HashSet<>(), complete));
    }

    @Test
    void shapedUtilityProducersAreExactAndTagCompatible() {
        assertShaped(
            "ingredient_broom",
            List.of(" S ", " S ", "HHH"),
            Map.of("S", "#c:rods/wooden", "H", "#warlockery:hawthorn_saplings"),
            "warlockery:ingredient_broom"
        );
        assertShaped(
            "ingredient_waystone",
            List.of("FN"),
            Map.of("F", "#warlockery:crafting/flints", "N", "#warlockery:crafting/bone_needles"),
            "warlockery:ingredient_waystone"
        );
        assertEquals(
            Set.of("minecraft:flint"),
            resolve("#warlockery:crafting/flints", new HashSet<>())
        );
        assertEquals(
            Set.of("warlockery:ingredient_bone_needle"),
            resolve("#warlockery:crafting/bone_needles", new HashSet<>())
        );
    }

    @Test
    void privateExtensionTagsHaveSurvivalDefaults() {
        final Map<String, Set<String>> defaults = Map.ofEntries(
            Map.entry("brew_reagents/apples", Set.of("minecraft:apple")),
            Map.entry("brew_reagents/cookies", Set.of("minecraft:cookie")),
            Map.entry("brew_reagents/dog_tongues", Set.of("warlockery:ingredient_dog_tongue")),
            Map.entry("brew_reagents/ender_dews", Set.of("warlockery:ingredient_ender_dew")),
            Map.entry("brew_reagents/ent_twigs", Set.of("warlockery:ingredient_heartwood_splinter")),
            Map.entry("brew_reagents/erosion_brews", Set.of("warlockery:brew_erosion")),
            Map.entry("brew_reagents/foul_fumes", Set.of("warlockery:ingredient_foul_fume")),
            Map.entry("brew_reagents/golden_apples", Set.of("minecraft:golden_apple")),
            Map.entry("brew_reagents/icy_needles", Set.of("warlockery:ingredient_icy_needle")),
            Map.entry("brew_reagents/lily_pads", Set.of("minecraft:lily_pad")),
            Map.entry("brew_reagents/love_brews", Set.of("warlockery:brew_love")),
            Map.entry("brew_reagents/magma_creams", Set.of("minecraft:magma_cream")),
            Map.entry("brew_reagents/mandrake_roots", Set.of("warlockery:ingredient_mandrake_root")),
            Map.entry("brew_reagents/odours_of_purity", Set.of("warlockery:ingredient_odour_of_purity")),
            Map.entry("brew_reagents/poisonous_potatoes", Set.of("minecraft:poisonous_potato")),
            Map.entry("brew_reagents/purified_milks", Set.of("warlockery:ingredient_purified_milk")),
            Map.entry("brew_reagents/spanish_mosses", Set.of("warlockery:spanishmoss")),
            Map.entry("brew_reagents/verdant_catalysts", Set.of("warlockery:ingredient_verdant_catalyst")),
            Map.entry("brew_reagents/water_artichokes", Set.of("warlockery:ingredient_artichoke")),
            Map.entry("brew_reagents/whiffs_of_magic", Set.of("warlockery:ingredient_whiff_of_magic")),
            Map.entry("brew_reagents/wood_ashes", Set.of("warlockery:ingredient_ash_wood")),
            Map.entry("crafting/bone_needles", Set.of("warlockery:ingredient_bone_needle")),
            Map.entry("crafting/flints", Set.of("minecraft:flint")),
            Map.entry("solidifying/dirt_targets", Set.of(
                "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt"
            )),
            Map.entry("solidifying/sand_targets", Set.of("minecraft:sand", "minecraft:red_sand")),
            Map.entry("solidifying/sandstone_targets", Set.of(
                "minecraft:sandstone", "minecraft:red_sandstone"
            ))
        );
        defaults.forEach((tag, values) -> {
            final JsonObject json = read(tagPath("warlockery:" + tag));
            assertFalse(json.get("replace").getAsBoolean(), tag);
            assertEquals(values, strings(json.getAsJsonArray("values")), tag);
        });
    }

    private static void exactFormula(final String id, final ExpectedMachine expected) {
        final MachineRecipeDefinition recipe = machine(id);
        assertEquals("kettle", recipe.machine());
        assertEquals(240, recipe.processingTime());
        assertFalse(recipe.requiresFuel());
        assertEquals("#minecraft:water", recipe.fluid().orElseThrow().ingredient());
        assertEquals(250, recipe.fluid().orElseThrow().amount());
        assertEquals(expected.altarPower(), recipe.altarPower());
        assertEquals(expected.inputs(), recipe.inputs().stream().collect(Collectors.toUnmodifiableMap(
            MachineRecipeDefinition.Input::ingredient,
            MachineRecipeDefinition.Input::count
        )));
    }

    private static void crossModTags(final String id) {
        final MachineRecipeDefinition recipe = machine(id);
        assertTrue(recipe.inputs().stream().allMatch(input -> input.ingredient().startsWith("#")));
        recipe.inputs().stream()
            .map(MachineRecipeDefinition.Input::ingredient)
            .filter(ingredient -> ingredient.startsWith("#warlockery:"))
            .forEach(ingredient -> assertTrue(Files.exists(tagPath(ingredient.substring(1))), ingredient));
    }

    private static void survivalOutput(final String id, final ExpectedMachine expected) {
        final MachineRecipeDefinition recipe = machine(id);
        assertEquals(1, recipe.outputs().size());
        assertEquals(expected.output(), recipe.outputs().getFirst().item());
        assertEquals(3, recipe.outputs().getFirst().count());
        recipe.inputs().forEach(input -> assertFalse(
            resolve(input.ingredient(), new HashSet<>()).contains(expected.output()),
            input.ingredient()
        ));
    }

    private static void assertShaped(
        final String id,
        final List<String> pattern,
        final Map<String, String> key,
        final String output
    ) {
        final JsonObject json = read(RECIPES.resolve(id + ".json"));
        assertEquals("minecraft:crafting_shaped", json.get("type").getAsString());
        assertEquals(pattern, json.getAsJsonArray("pattern").asList().stream()
            .map(JsonElement::getAsString).toList());
        assertEquals(key, json.getAsJsonObject("key").entrySet().stream().collect(Collectors.toUnmodifiableMap(
            Map.Entry::getKey,
            entry -> entry.getValue().getAsString()
        )));
        assertTrue(key.values().stream().allMatch(ingredient -> ingredient.startsWith("#")));
        assertEquals(output, json.getAsJsonObject("result").get("id").getAsString());
    }

    private static MachineRecipeDefinition machine(final String id) {
        final MachineRecipeDefinition recipe = MACHINES_BY_ID.get(id);
        assertTrue(recipe != null, id);
        return recipe;
    }

    private static Set<String> resolve(final String ingredient, final Set<String> visited) {
        if (!ingredient.startsWith("#")) {
            return Set.of(ingredient);
        }
        final String tag = ingredient.substring(1);
        if (!visited.add(tag)) {
            return Set.of();
        }
        final Path path = tagPath(tag);
        if (!Files.exists(path)) {
            return Set.of();
        }
        return strings(read(path).getAsJsonArray("values")).stream()
            .flatMap(value -> resolve(value, visited).stream())
            .collect(Collectors.toUnmodifiableSet());
    }

    private static void assertAcyclic(
        final String current,
        final Map<String, Set<String>> graph,
        final Set<String> visiting,
        final Set<String> complete
    ) {
        if (complete.contains(current)) {
            return;
        }
        if (!visiting.add(current)) {
            fail("Producer cycle at " + current);
        }
        graph.getOrDefault(current, Set.of()).forEach(next -> assertAcyclic(next, graph, visiting, complete));
        visiting.remove(current);
        complete.add(current);
    }

    private static Path tagPath(final String tag) {
        final int separator = tag.indexOf(':');
        final String namespace = tag.substring(0, separator);
        final String path = tag.substring(separator + 1);
        return DATA.resolve(namespace).resolve("tags/item").resolve(path + ".json");
    }

    private static Set<String> strings(final Iterable<JsonElement> values) {
        final Set<String> result = new HashSet<>();
        values.forEach(value -> result.add(value.isJsonPrimitive()
            ? value.getAsString()
            : value.getAsJsonObject().get("id").getAsString()));
        return Set.copyOf(result);
    }

    private static JsonObject read(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private static Map<String, ExpectedMachine> expectedMachines() {
        final Map<String, ExpectedMachine> expected = new LinkedHashMap<>();
        expected.put("kettle_ingredient_brew_depths", machine(
            "warlockery:ingredient_brew_depths", 0,
            "#warlockery:brew_reagents/mandrake_roots", "#warlockery:brew_reagents/water_artichokes",
            "#warlockery:brew_reagents/odours_of_purity", "#warlockery:brazier/tears",
            "#warlockery:brew_reagents/lily_pads", "#c:dyes/black"
        ));
        expected.put("kettle_ingredient_brew_grotesque", machine(
            "warlockery:ingredient_brew_grotesque", 500,
            "#warlockery:mutation/mutandis_extremis", "#warlockery:brew_reagents/mandrake_roots",
            "#warlockery:brew_reagents/water_artichokes", "#warlockery:brew_reagents/dog_tongues",
            "#warlockery:brew_reagents/golden_apples", "#warlockery:brew_reagents/poisonous_potatoes"
        ));
        expected.put("kettle_ingredient_brew_hexed_leaping", machine(
            "warlockery:ingredient_brew_hexed_leaping", 0,
            "#c:bones", "#warlockery:brew_reagents/apples", "#warlockery:plant_mine_payloads/sprouting",
            "#warlockery:custom_brew/lingering/two", "#c:feathers", "#c:foods/raw_fish"
        ));
        expected.put("kettle_ingredient_brew_hitchcock", machine(
            "warlockery:ingredient_brew_hitchcock", 0,
            "#c:mushrooms", "#c:seeds", "#warlockery:bodega_thorn_brews",
            "#warlockery:bat_binding_fibers", "#c:feathers", "#warlockery:bodega_owl_wings"
        ));
        expected.put("kettle_ingredient_brew_sleep", machine(
            "warlockery:ingredient_brew_sleep", 0,
            "#warlockery:brew_reagents/purified_milks", "#warlockery:brew_reagents/cookies",
            "#warlockery:brew_reagents/love_brews", "#warlockery:brew_reagents/whiffs_of_magic",
            "#warlockery:brew_reagents/icy_needles", "#warlockery:brew_reagents/water_artichokes"
        ));
        expected.put("kettle_ingredient_brew_solid_dirt", solid(
            "warlockery:ingredient_brew_solid_dirt", "#warlockery:solidifying/dirt_targets"
        ));
        expected.put("kettle_ingredient_brew_solid_erosion", solid(
            "warlockery:ingredient_brew_solid_erosion", "#warlockery:brew_reagents/erosion_brews"
        ));
        expected.put("kettle_ingredient_brew_solid_sand", solid(
            "warlockery:ingredient_brew_solid_sand", "#warlockery:solidifying/sand_targets"
        ));
        expected.put("kettle_ingredient_brew_solid_sandstone", solid(
            "warlockery:ingredient_brew_solid_sandstone", "#warlockery:solidifying/sandstone_targets"
        ));
        expected.put("kettle_ingredient_brew_solid_stone", solid(
            "warlockery:ingredient_brew_solid_stone", "#c:stones"
        ));
        expected.put("kettle_ingredient_brew_sprouting", machine(
            "warlockery:ingredient_brew_sprouting", 0,
            "#warlockery:rowan_saplings", "#warlockery:alder_saplings", "#warlockery:hawthorn_saplings",
            "#warlockery:brew_reagents/dog_tongues", "#warlockery:brew_reagents/mandrake_roots",
            "#minecraft:small_flowers"
        ));
        expected.put("kettle_ingredient_brew_substitution", new ExpectedMachine(
            "warlockery:ingredient_brew_substitution", 0,
            Map.of(
                "#warlockery:brew_reagents/ender_dews", 2,
                "#warlockery:mutation/mutandis_extremis", 1,
                "#c:eggs", 1,
                "#warlockery:brew_reagents/magma_creams", 1,
                "#warlockery:brew_reagents/ent_twigs", 1
            )
        ));
        return Map.copyOf(expected);
    }

    private static ExpectedMachine solid(final String output, final String target) {
        return machine(
            output, 2000, target, "#warlockery:brew_reagents/foul_fumes",
            "#warlockery:brew_reagents/odours_of_purity", "#warlockery:brew_reagents/verdant_catalysts",
            "#warlockery:brew_reagents/wood_ashes", "#warlockery:brew_reagents/spanish_mosses"
        );
    }

    private static ExpectedMachine machine(final String output, final int altarPower, final String... ingredients) {
        return new ExpectedMachine(
            output,
            altarPower,
            Stream.of(ingredients).collect(Collectors.toUnmodifiableMap(ingredient -> ingredient, ingredient -> 1))
        );
    }

    private record ExpectedMachine(String output, int altarPower, Map<String, Integer> inputs) {
    }
}
