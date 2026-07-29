package com.kadamitas.warlockery.registry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class CommonIngredientCompatibilityTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data", "warlockery");
    private static final Map<String, String> EXACT_COMMON_INGREDIENTS = Map.ofEntries(
        Map.entry("minecraft:bone", "#c:bones"),
        Map.entry("minecraft:bone_meal", "#c:fertilizers"),
        Map.entry("minecraft:chest", "#c:chests/wooden"),
        Map.entry("minecraft:feather", "#c:feathers"),
        Map.entry("minecraft:flint", "#warlockery:crafting/flints"),
        Map.entry("minecraft:gunpowder", "#c:gunpowders")
    );
    private static final Set<String> VANILLA_PRIMITIVES = Set.of(
        "minecraft:clay_ball",
        "minecraft:glass_bottle",
        "minecraft:paper",
        "minecraft:quartz_block",
        "minecraft:sugar"
    );

    @Test
    void recipesAndRitesDoNotRequireExactCommonIngredients() throws IOException {
        try (Stream<Path> files = Stream.of(
            DATA.resolve("recipe"),
            DATA.resolve("ritual"),
            DATA.resolve("warlockery_machine")
        ).flatMap(CommonIngredientCompatibilityTest::files)) {
            final Map<Path, Set<String>> violations = files.collect(Collectors.toUnmodifiableMap(
                path -> path,
                path -> directIngredientStrings(read(path)).stream()
                    .filter(EXACT_COMMON_INGREDIENTS::containsKey)
                    .collect(Collectors.toUnmodifiableSet())
            )).entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
            assertTrue(violations.isEmpty(), violations::toString);
        }
    }

    @Test
    void ordinaryVanillaIngredientsDoNotUsePrivateAliasTags() {
        final Set<String> ingredients = Stream.of(
            DATA.resolve("recipe"),
            DATA.resolve("ritual"),
            DATA.resolve("warlockery_machine")
        ).flatMap(CommonIngredientCompatibilityTest::files)
            .flatMap(path -> directIngredientStrings(read(path)).stream())
            .collect(Collectors.toUnmodifiableSet());
        assertTrue(ingredients.containsAll(VANILLA_PRIMITIVES));
        Set.of("clay_balls", "glass_bottles", "papers", "quartz_blocks", "sugars")
            .forEach(tag -> assertFalse(Files.exists(
                DATA.resolve("tags/item/crafting").resolve(tag + ".json")
            ), tag));
    }

    @Test
    void forgeCommonTagsBackTheStandardInterchangeFamilies() {
        final String allData = Stream.of(
            DATA.resolve("recipe"),
            DATA.resolve("ritual"),
            DATA.resolve("warlockery_machine")
        ).flatMap(CommonIngredientCompatibilityTest::files)
            .map(CommonIngredientCompatibilityTest::read)
            .collect(Collectors.joining());
        Set.copyOf(EXACT_COMMON_INGREDIENTS.values())
            .forEach(tag -> assertTrue(allData.contains(tag), tag));
    }

    private static Set<String> directIngredientStrings(final String json) {
        return ingredientStrings(JsonParser.parseString(json), false)
            .filter(value -> !value.startsWith("#"))
            .collect(Collectors.toUnmodifiableSet());
    }

    private static Stream<String> ingredientStrings(final JsonElement element, final boolean ingredientContext) {
        if (element.isJsonArray()) {
            return element.getAsJsonArray().asList().stream()
                .flatMap(value -> ingredientStrings(value, ingredientContext));
        }
        if (element.isJsonObject()) {
            return element.getAsJsonObject().entrySet().stream()
                .flatMap(entry -> ingredientStrings(
                    entry.getValue(),
                    ingredientContext || Set.of("ingredient", "ingredients", "key").contains(entry.getKey())
                ));
        }
        return ingredientContext && element.isJsonPrimitive()
            ? Stream.of(element.getAsString())
            : Stream.empty();
    }

    private static Stream<Path> files(final Path directory) {
        try {
            return Files.list(directory).filter(path -> path.toString().endsWith(".json"));
        } catch (IOException exception) {
            throw new UncheckedIOException(directory.toString(), exception);
        }
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
