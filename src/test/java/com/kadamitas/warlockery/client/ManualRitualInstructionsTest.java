package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ManualRitualInstructionsTest {
    @SuppressWarnings("unchecked")
    private static List<String> keys(String id, String action, String target) throws Exception {
        final var type = Class.forName("com.kadamitas.warlockery.client.ManualRitualInstructions");
        final var method = type.getDeclaredMethod("keys", String.class, String.class, String.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(null, id, action, target);
    }

    @Test void everyPackagedRiteExplainsTheCommonLiveSiteRules() throws Exception {
        try (var files = Files.list(Path.of("src/main/resources/data/warlockery/ritual"))) {
            final var recipes = files.filter(path -> path.toString().endsWith(".json")).toList();
            assertEquals(109, recipes.size());
            for (var path : recipes) {
                final var recipe = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                assertTrue(keys(path.getFileName().toString().replace(".json", ""), recipe.get("action").getAsString(),
                    recipe.has("target") ? recipe.get("target").getAsString() : "").contains("site"), path.toString());
            }
        }
    }

    @Test void runtimeOnlyRequirementsHaveSpecificInstructions() throws Exception {
        for (String action : List.of("summon_entity", "prior_incarnation", "manifest", "cleanse", "call_familiar",
            "bind_item", "marriage", "divorce", "earths_wrath", "climate_shift", "transform_nami")) {
            assertTrue(keys("example", action, "").contains(action), action);
        }
        assertTrue(keys("bind_familiar", "bind_entity", "familiar").contains("bind_familiar"));
        assertTrue(keys("bind_spectral", "bind_entity", "spectral").contains("bind_spectral"));
        assertTrue(keys("hex_wolf", "transform_werewolf", "").contains("owned_familiar"));
        assertTrue(keys("corrupt_doll", "hex", "corrupt_doll").contains("owned_familiar"));
        assertFalse(keys("hex_heat_metal", "hex", "heat_metal").contains("owned_familiar"));
    }

    @Test void dispatchTargetsHaveUsablePreparationDirections() throws Exception {
        for (String action : List.of("bind_waystone", "copy_waystone", "teleport_waystone", "teleport_entity", "bind_circle", "hex")) {
            assertTrue(keys("example", action, "").contains(action), action);
        }
    }
}
