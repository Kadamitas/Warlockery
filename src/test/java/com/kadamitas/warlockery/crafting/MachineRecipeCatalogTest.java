package com.kadamitas.warlockery.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class MachineRecipeCatalogTest {
    @Test
    void factoryBuildsImmutableMachineIndexesInMatchPriorityOrder() {
        final MachineRecipeDefinition broad = recipe("#minecraft:logs", 1);
        final MachineRecipeDefinition specific = recipe("minecraft:oak_log", 1);
        final MachineRecipeDefinition numerous = recipe("minecraft:birch_log", 2);
        final Identifier broadId = Identifier.parse("warlockery:broad");
        final Identifier specificId = Identifier.parse("warlockery:specific");
        final Identifier numerousId = Identifier.parse("warlockery:numerous");

        final MachineRecipeCatalog catalog = MachineRecipeCatalog.create(Map.of(
            broadId, broad,
            specificId, specific,
            numerousId, numerous
        ));

        assertEquals(
            List.of(numerousId, specificId, broadId),
            catalog.forMachine("alchemical_oven").stream()
                .map(MachineRecipeCatalog.PreparedRecipe::id)
                .toList()
        );
        assertEquals(3, catalog.inputsFor("alchemical_oven").size());
        assertThrows(UnsupportedOperationException.class, () -> catalog.definitions().clear());
        assertThrows(UnsupportedOperationException.class, () -> catalog.forMachine("alchemical_oven").clear());
    }

    @Test
    void factoryReusesCompiledAllocationPlansForLoadedRecipes() {
        final MachineRecipeDefinition recipe = recipe("minecraft:oak_log", 1);
        final MachineRecipeCatalog catalog = MachineRecipeCatalog.create(Map.of(
            Identifier.parse("warlockery:oak"), recipe
        ));

        assertSame(catalog.allocationPlan(recipe), catalog.allocationPlan(recipe));
    }

    private static MachineRecipeDefinition recipe(final String ingredient, final int count) {
        return new MachineRecipeDefinition(
            "alchemical_oven",
            List.of(new MachineRecipeDefinition.Input(ingredient, count)),
            List.of(new MachineRecipeDefinition.Output("minecraft:charcoal", 1)),
            80,
            true,
            Optional.empty(),
            0
        );
    }
}
