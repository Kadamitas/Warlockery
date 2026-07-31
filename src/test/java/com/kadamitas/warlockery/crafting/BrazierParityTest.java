package com.kadamitas.warlockery.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class BrazierParityTest {
    private static final Path RECIPES = Path.of(
        "src", "main", "resources", "data", "warlockery", "warlockery_machine"
    );

    @Test
    void everyConjurationHasAThreeIngredientPoweredRecipe() throws IOException {
        for (BrazierEffectRuntime.Effect effect : BrazierEffectRuntime.Effect.values()) {
            final var recipe = JsonParser.parseString(Files.readString(
                RECIPES.resolve(effect.recipePath() + ".json")
            )).getAsJsonObject();
            assertEquals("brazier", recipe.get("machine").getAsString());
            assertEquals(3, recipe.getAsJsonArray("inputs").size());
            if (effect == BrazierEffectRuntime.Effect.DRAIN_GROWTH) {
                assertEquals(0, recipe.get("altar_power").getAsInt());
            } else {
                assertTrue(recipe.get("altar_power").getAsInt() > 0);
            }
            assertEquals("warlockery:ingredient_ash_wood",
                recipe.getAsJsonArray("outputs").get(0).getAsJsonObject().get("item").getAsString());
        }
    }

    @Test
    void recipeDispatchIsCompleteAndRejectsUnrelatedMachines() {
        assertTrue(Arrays.stream(BrazierEffectRuntime.Effect.values()).allMatch(effect ->
            BrazierEffectRuntime.Effect.fromRecipe(
                Identifier.fromNamespaceAndPath("warlockery", effect.recipePath())
            ).orElseThrow() == effect
        ));
        assertFalse(BrazierEffectRuntime.Effect.fromRecipe(
            Identifier.fromNamespaceAndPath("warlockery", "kettle_brew_heal")
        ).isPresent());
    }

    @Test
    void floatingUiDistinguishesMissingIgnitionFromRecipeFailure() {
        assertFalse(MachineStatus.NO_IGNITION.canRun());
        assertFalse(MachineStatus.NO_IGNITION.active());
        assertTrue(MachineStatus.READY.canRun());
    }

    @Test
    void effectResultIsBoundedAndReportsWorldChanges() {
        assertFalse(BrazierEffectRuntime.Result.NONE.changedWorld());
        assertTrue(new BrazierEffectRuntime.Result(1, 0, 0).changedWorld());
        assertTrue(new BrazierEffectRuntime.Result(0, 4, 2).changedWorld());
    }

    @Test
    void ignitionAcceptsVanillaAndTagExtensibleFireSources() throws IOException {
        final String tag = Files.readString(Path.of(
            "src", "main", "resources", "data", "warlockery", "tags", "item", "brazier_igniters.json"
        ));
        assertTrue(tag.contains("minecraft:flint_and_steel"));
        assertTrue(tag.contains("minecraft:fire_charge"));
        assertTrue(tag.contains("warlockery:mysticbranch"));
        assertTrue(Files.readString(Path.of(
            "src", "main", "java", "com", "kadamitas", "warlockery", "block", "MagicMachineBlock.java"
        )).contains("BRAZIER_IGNITERS"));
    }
}
