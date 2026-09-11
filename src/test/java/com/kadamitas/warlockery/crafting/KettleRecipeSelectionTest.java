package com.kadamitas.warlockery.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.util.IngredientAllocationPlan;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.TreeMap;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class KettleRecipeSelectionTest {
    private static final Path RECIPES = Path.of(
        "src/main/resources/data/warlockery/warlockery_machine"
    );

    @Test
    void everyKettleRecipeHasDistinctUnorderedInputs() throws IOException {
        final Map<String, List<String>> recipesByInputs = new LinkedHashMap<>();
        try (var paths = Files.list(RECIPES)) {
            for (final Path path : paths.filter(file -> file.toString().endsWith(".json")).sorted().toList()) {
                final JsonObject recipe = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (!recipe.get("machine").getAsString().equals("kettle")) {
                    continue;
                }
                // Kettle slots are unordered. Reordering identical ingredients cannot select another brew.
                final Map<String, Integer> ingredients = new TreeMap<>();
                recipe.getAsJsonArray("inputs").forEach(element -> {
                    final JsonObject input = element.getAsJsonObject();
                    ingredients.merge(input.get("ingredient").getAsString(),
                        input.has("count") ? input.get("count").getAsInt() : 1, Integer::sum);
                });
                final String signature = ingredients + "/" + recipe.get("fluid");
                recipesByInputs.computeIfAbsent(signature, _ -> new ArrayList<>())
                    .add(path.getFileName().toString());
                assertTrue(recipe.getAsJsonArray("inputs").size() <= MachineProfiles.forBlock("kettle").inputSlots(),
                    () -> path + " exceeds the kettle's ingredient slots");
            }
        }
        final List<List<String>> collisions = recipesByInputs.values().stream()
            .filter(ids -> ids.size() > 1).toList();
        assertTrue(collisions.isEmpty(), () -> "Indistinguishable kettle recipes: " + collisions);
    }

    @Test
    void everyBundledIngredientAlternativeSelectsItsDeclaredKettleRecipe() throws IOException {
        final Map<Identifier, MachineRecipeDefinition> definitions = new LinkedHashMap<>();
        try (var paths = Files.list(RECIPES)) {
            for (final Path path : paths.filter(file -> file.toString().endsWith(".json")).sorted().toList()) {
                final JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (!json.get("machine").getAsString().equals("kettle")) {
                    continue;
                }
                final List<MachineRecipeDefinition.Input> inputs = json.getAsJsonArray("inputs").asList().stream()
                    .map(JsonElement::getAsJsonObject)
                    .map(input -> new MachineRecipeDefinition.Input(input.get("ingredient").getAsString(),
                        input.has("count") ? input.get("count").getAsInt() : 1)).toList();
                final List<MachineRecipeDefinition.Output> outputs = json.getAsJsonArray("outputs").asList().stream()
                    .map(JsonElement::getAsJsonObject)
                    .map(output -> new MachineRecipeDefinition.Output(output.get("item").getAsString(),
                        output.has("count") ? output.get("count").getAsInt() : 1)).toList();
                // All packaged kettle recipes use the same fluid; only their item selection differs.
                final JsonObject fluid = json.getAsJsonObject("fluid");
                assertEquals("#minecraft:water", fluid.get("ingredient").getAsString(), path.toString());
                assertEquals(250, fluid.get("amount").getAsInt(), path.toString());
                definitions.put(Identifier.fromNamespaceAndPath("warlockery",
                    path.getFileName().toString().replace(".json", "")), new MachineRecipeDefinition(
                    "kettle", inputs, outputs, json.get("processing_time").getAsInt(), false,
                    Optional.of(new MachineRecipeDefinition.FluidInput("#minecraft:water", 250)), 0
                ));
            }
        }
        final TagAlternatives tags = new TagAlternatives();
        final List<MachineRecipeCatalog.PreparedRecipe> ordered = MachineRecipeCatalog.create(definitions)
            .forMachine("kettle");
        final Map<Identifier, IngredientAllocationPlan<Stack>> plans = new LinkedHashMap<>();
        ordered.forEach(recipe -> plans.put(recipe.id(), IngredientAllocationPlan.create(
            recipe.definition().inputs(), input -> {
                final Set<String> alternatives = tags.resolve(input.ingredient());
                assertFalse(alternatives.isEmpty(), () -> "Empty ingredient " + input.ingredient());
                return stack -> alternatives.contains(stack.item());
            }
        )));
        final List<String> failures = new ArrayList<>();
        for (final MachineRecipeCatalog.PreparedRecipe expected : ordered) {
            final List<List<Stack>> fixtures = new ArrayList<>();
            fixtures.add(List.of());
            for (final MachineRecipeDefinition.Input input : expected.definition().inputs()) {
                final List<List<Stack>> extended = new ArrayList<>();
                for (final List<Stack> prefix : fixtures) {
                    for (final String item : tags.resolve(input.ingredient())) {
                        final List<Stack> fixture = new ArrayList<>(prefix);
                        fixture.add(new Stack(item, input.count()));
                        extended.add(List.copyOf(fixture));
                    }
                }
                fixtures.clear();
                fixtures.addAll(extended);
            }
            for (final List<Stack> fixture : fixtures) {
                // Use the production priority order and counted allocation algorithm. Kettles allow
                // spare ingredients, so a more specific recipe must win over a shorter base recipe.
                final Identifier actual = ordered.stream()
                    .filter(candidate -> candidate.requiredItemCount() <= expected.requiredItemCount())
                    .filter(candidate -> plans.get(candidate.id()).allocate(fixture, Stack::count).complete())
                    .map(MachineRecipeCatalog.PreparedRecipe::id).findFirst().orElse(null);
                if (!expected.id().equals(actual)) {
                    failures.add(expected.id() + " selected " + actual + " for " + fixture);
                    break;
                }
            }
        }
        assertTrue(failures.isEmpty(), () -> "Unreachable declared kettle inputs: " + failures);
    }

    private record Stack(String item, int count) { }

    /** Resolves merged bundled tags, including vanilla and loader resources on the test classpath. */
    private static final class TagAlternatives {
        private final Map<String, Set<String>> cached = new LinkedHashMap<>();

        Set<String> resolve(final String ingredient) {
            if (!ingredient.startsWith("#")) {
                return Set.of(ingredient);
            }
            final Set<String> existing = cached.get(ingredient);
            if (existing != null) {
                return existing;
            }
            final Set<String> values = new LinkedHashSet<>();
            cached.put(ingredient, values);
            final String[] id = ingredient.substring(1).split(":", 2);
            final String resource = "data/" + id[0] + "/tags/item/" + id[1] + ".json";
            try {
                final var resources = KettleRecipeSelectionTest.class.getClassLoader().getResources(resource);
                while (resources.hasMoreElements()) {
                    try (var reader = new InputStreamReader(resources.nextElement().openStream(), StandardCharsets.UTF_8)) {
                        final JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        if (json.has("replace") && json.get("replace").getAsBoolean()) {
                            values.clear();
                        }
                        for (final JsonElement entry : json.getAsJsonArray("values")) {
                            final String value = entry.isJsonPrimitive() ? entry.getAsString()
                                : entry.getAsJsonObject().get("id").getAsString();
                            values.addAll(resolve(value));
                        }
                    }
                }
            } catch (IOException exception) {
                throw new UncheckedIOException(resource, exception);
            }
            return values;
        }
    }
}
