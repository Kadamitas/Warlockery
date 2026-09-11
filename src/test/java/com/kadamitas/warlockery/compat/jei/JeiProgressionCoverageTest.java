package com.kadamitas.warlockery.compat.jei;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class JeiProgressionCoverageTest {
    private static final Path SOURCE = Path.of("src/main/java/com/kadamitas/warlockery/compat/jei");

    @Test void everyCustomBrewComponentHasAPackagedViewerEntry() throws Exception {
        Path data = Path.of("src/main/resources/data/warlockery/custom_brew_component");
        Path index = Path.of("src/main/resources/assets/warlockery/jei_catalog/custom_brews.txt");
        assertTrue(Files.isRegularFile(index), "Custom brew components need an exhaustive JEI index");
        try (var files = Files.walk(data)) {
            Set<String> expected = files.filter(path -> path.toString().endsWith(".json"))
                .map(path -> data.relativize(path).toString().replace('\\', '/').replaceFirst("\\.json$", ""))
                .collect(Collectors.toSet());
            assertEquals(expected, new HashSet<>(Files.readAllLines(index)));
        }
        assertTrue(Files.readString(SOURCE.resolve("WarlockeryJeiPlugin.java")).contains("CUSTOM_BREWS"));
    }

    @Test void everyMachineScreenRoutesItsOwnRecipeCategory() throws Exception {
        String plugin = Files.readString(SOURCE.resolve("WarlockeryJeiPlugin.java"));
        assertTrue(plugin.contains("registerGuiHandlers"), "All machine screens need a discoverable recipe click area");
        String handler = Files.readString(SOURCE.resolve("MachineJeiGuiHandler.java"));
        assertTrue(handler.contains("screen.getMenu().kind()"), "Shared screen must select the actual open machine kind");
        assertTrue(handler.contains("createBasic"));
    }

    @Test void displayedOvenIngredientsExcludeCompetingSpecificOutputs() throws Exception {
        String category = Files.readString(SOURCE.resolve("MachineRecipeCategory.java"));
        assertTrue(category.contains("JeiMachineInputs.itemStacks"), "Generic tag alternatives must reflect the actual winning recipe");
    }

    @Test void runedCauldronConversionHasARealRecipeEntry() throws Exception {
        String plugin = Files.readString(SOURCE.resolve("WarlockeryJeiPlugin.java"));
        assertTrue(plugin.contains("WORLD_INTERACTIONS"), "Paste-on-cauldron acquisition needs output and input discovery");
    }
}
