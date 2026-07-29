package com.kadamitas.warlockery.crafting;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class MachineRecipePriorityTest {
    @Test
    void familyTagsOutrankBroadVanillaTagsAtEqualInputCount() {
        final MachineRecipeDefinition specific = recipe("#warlockery:alder_saplings");
        final MachineRecipeDefinition broad = recipe("#minecraft:saplings");
        assertTrue(MachineRecipeManager.specificity(specific) > MachineRecipeManager.specificity(broad));
    }

    private static MachineRecipeDefinition recipe(final String ingredient) {
        return new MachineRecipeDefinition(
            "alchemical_oven",
            List.of(
                new MachineRecipeDefinition.Input(ingredient, 1),
                new MachineRecipeDefinition.Input("warlockery:ingredient_clay_jar", 1)
            ),
            List.of(new MachineRecipeDefinition.Output("warlockery:ingredient_ash_wood", 1)),
            160,
            true,
            Optional.empty(),
            0
        );
    }
}
