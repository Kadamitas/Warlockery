package com.kadamitas.warlockery.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.testutil.JsonFixtureLoader;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class MachineRecipeResourceContractTest {
    private static final Path RECIPES = Path.of(
        "src", "main", "resources", "data", "warlockery", "warlockery_machine"
    );

    @Test
    void packagedRecipesFitTheirMachineCapabilities() {
        final var fixtures = JsonFixtureLoader.load(RECIPES, MachineRecipeDefinition.CODEC);
        assertFalse(fixtures.isEmpty());
        for (final var fixture : fixtures) {
            final var recipe = fixture.value();
            final var profile = MachineProfiles.forRecipeType(recipe.machine()).orElseThrow();
            assertTrue(recipe.processingTime() > 0, fixture.id());
            assertFalse(recipe.inputs().isEmpty(), fixture.id());
            assertFalse(recipe.outputs().isEmpty(), fixture.id());
            assertTrue(recipe.inputs().stream().allMatch(input -> input.count() > 0), fixture.id());
            assertTrue(recipe.outputs().stream().allMatch(output -> !output.item().isBlank() && output.count() > 0), fixture.id());
            assertEquals(recipe.requiresFuel(), profile.hasFuelSlot(), fixture.id());
            assertTrue(recipe.fluid().isEmpty() || profile.supportsFluids(), fixture.id());
        }
    }

    @Test
    void readinessRequiresBothInputsAndRunnableMachineState() {
        final var recipe = JsonFixtureLoader.load(RECIPES, MachineRecipeDefinition.CODEC).getFirst().value();
        final var profile = MachineProfiles.forRecipeType(recipe.machine()).orElseThrow();
        final var missing = new MachineRecipeManager.Diagnostic("test", recipe.outputs().getFirst().item(),
            recipe.processingTime(), List.of(new MachineRecipeManager.MissingInput("minecraft:stone", 1)), List.of());
        assertFalse(MachineUiState.from(profile, missing, MachineStatus.READY).showGreenCheck());
        final var ready = completeDiagnostic("test", recipe);
        assertFalse(MachineUiState.from(profile, ready, MachineStatus.NO_FUEL).showGreenCheck());
        assertTrue(MachineUiState.from(profile, ready, MachineStatus.READY).showGreenCheck());
    }

    private static MachineRecipeManager.Diagnostic completeDiagnostic(
        final String id,
        final MachineRecipeDefinition recipe
    ) {
        return new MachineRecipeManager.Diagnostic(
            id, recipe.outputs().getFirst().item(), recipe.processingTime(), List.of(), List.of()
        );
    }

}
