package com.kadamitas.warlockery.compat.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.crafting.MachineProfiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class PackagedJeiCatalogTest {
    private static final Path DATA_ROOT = Path.of("src/main/resources/data/warlockery");
    private static final Path INDEX_ROOT = Path.of("src/main/resources/assets/warlockery/jei_catalog");

    @Test
    void packagedIndexesMatchEveryBuiltInDefinition() throws IOException {
        assertIndexMatches("machines.txt", DATA_ROOT.resolve("warlockery_machine"));
        assertIndexMatches("rituals.txt", DATA_ROOT.resolve("ritual"));
    }

    @Test
    void packagedCatalogDecodesEveryMachineRecipe() {
        final var recipes = PackagedJeiCatalog.machines();
        assertEquals(178, recipes.size());
        assertEquals(recipes.size(), recipes.stream().map(recipe -> recipe.id()).distinct().count());
        assertTrue(recipes.stream().allMatch(recipe -> MachineProfiles.supportsRecipeType(recipe.recipe().machine())));
        assertTrue(recipes.stream().allMatch(recipe -> !recipe.recipe().inputs().isEmpty()));
        assertTrue(recipes.stream().allMatch(recipe -> !recipe.recipe().outputs().isEmpty()));
    }

    @Test
    void packagedCatalogDecodesEveryCircleRite() {
        final var rituals = PackagedJeiCatalog.rituals();
        assertEquals(108, rituals.size());
        assertEquals(rituals.size(), rituals.stream().map(ritual -> ritual.id()).distinct().count());
        assertTrue(rituals.stream().allMatch(ritual -> !ritual.definition().title().isBlank()));
        assertTrue(rituals.stream().allMatch(ritual -> !ritual.definition().description().isBlank()));
    }

    private static void assertIndexMatches(final String indexName, final Path dataDirectory) throws IOException {
        final Set<String> indexed = Files.readAllLines(INDEX_ROOT.resolve(indexName)).stream()
            .map(String::strip)
            .filter(line -> !line.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
        try (var files = Files.list(dataDirectory)) {
            final Set<String> definitions = files
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                .collect(Collectors.toUnmodifiableSet());
            assertEquals(definitions, indexed);
        }
    }
}
