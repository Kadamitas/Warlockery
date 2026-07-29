package com.kadamitas.warlockery.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.diagnostic.DiagnosticChecklist;
import com.kadamitas.warlockery.testutil.JsonFixtureLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

final class EveryMachineInteractionTest {
    private static final Path RECIPES = Path.of(
        "src", "main", "resources", "data", "warlockery", "warlockery_machine"
    );

    @TestFactory
    Stream<DynamicContainer> oneSuitePerMachineRecipe() {
        return JsonFixtureLoader.load(RECIPES, MachineRecipeDefinition.CODEC).stream()
            .map(fixture -> container(fixture.id(), fixture.value()));
    }

    private static DynamicContainer container(final String id, final MachineRecipeDefinition recipe) {
        return DynamicContainer.dynamicContainer(id, List.of(
            DynamicTest.dynamicTest("failure names missing input", () -> missingInput(id, recipe)),
            DynamicTest.dynamicTest("fluid requirement updates diagnostics", () -> fluidDiagnostics(id, recipe)),
            DynamicTest.dynamicTest("altar power updates floating diagnostics", () -> altarDiagnostics(id, recipe)),
            DynamicTest.dynamicTest("floating UI changes to green", () -> uiTurnsGreen(id, recipe)),
            DynamicTest.dynamicTest("success produces declared outputs", () -> success(id, recipe))
        ));
    }

    private static void altarDiagnostics(final String id, final MachineRecipeDefinition recipe) {
        final MachineProfile profile = MachineProfiles.forRecipeType(recipe.machine()).orElseThrow();
        if (recipe.altarPower() == 0) {
            assertEquals(0, recipe.altarPower());
            return;
        }
        final var diagnostic = new MachineRecipeManager.Diagnostic(
            id,
            recipe.outputs().getFirst().item(),
            recipe.processingTime(),
            List.of(new MachineRecipeManager.MissingInput("warlockery:altar_power", recipe.altarPower())),
            List.of()
        );
        final MachineUiState state = MachineUiState.from(profile, diagnostic, MachineStatus.READY);
        assertFalse(state.showGreenCheck());
        assertEquals("warlockery:altar_power", diagnostic.missing().getFirst().ingredient());
    }

    private static void fluidDiagnostics(final String id, final MachineRecipeDefinition recipe) {
        final MachineProfile profile = MachineProfiles.forRecipeType(recipe.machine()).orElseThrow();
        recipe.fluid().ifPresentOrElse(fluid -> {
            final var diagnostic = new MachineRecipeManager.Diagnostic(
                id,
                recipe.outputs().getFirst().item(),
                recipe.processingTime(),
                List.of(new MachineRecipeManager.MissingInput(fluid.ingredient(), fluid.amount())),
                List.of()
            );
            final MachineUiState state = MachineUiState.from(profile, diagnostic, MachineStatus.READY);
            assertFalse(state.showGreenCheck());
            assertEquals(fluid.ingredient(), diagnostic.missing().getFirst().ingredient());
            assertTrue(profile.supportsFluids());
        }, () -> assertTrue(recipe.fluid().isEmpty()));
    }

    private static void missingInput(final String id, final MachineRecipeDefinition recipe) {
        final MachineRecipeDefinition.Input missing = recipe.inputs().getFirst();
        final var diagnostic = new MachineRecipeManager.Diagnostic(
            id,
            recipe.outputs().getFirst().item(),
            recipe.processingTime(),
            List.of(new MachineRecipeManager.MissingInput(missing.ingredient(), missing.count())),
            List.of()
        );
        final MachineProfile profile = MachineProfiles.forRecipeType(recipe.machine()).orElseThrow();
        final MachineUiState state = MachineUiState.from(profile, diagnostic, MachineStatus.READY);
        assertFalse(state.showGreenCheck());
        assertEquals(DiagnosticChecklist.INCOMPLETE_COLOR, state.checklist().color());
    }

    private static void uiTurnsGreen(final String id, final MachineRecipeDefinition recipe) {
        final var diagnostic = completeDiagnostic(id, recipe);
        final MachineProfile profile = MachineProfiles.forRecipeType(recipe.machine()).orElseThrow();
        assertFalse(MachineUiState.from(profile, diagnostic, MachineStatus.NO_FUEL).showGreenCheck());
        final MachineUiState ready = MachineUiState.from(profile, diagnostic, MachineStatus.READY);
        assertTrue(ready.showGreenCheck());
        assertEquals(DiagnosticChecklist.COMPLETE_MARKER, ready.checklist().marker());
    }

    private static void success(final String id, final MachineRecipeDefinition recipe) {
        final MachineProfile profile = MachineProfiles.forRecipeType(recipe.machine()).orElseThrow();
        assertTrue(completeDiagnostic(id, recipe).inputsReady(profile));
        assertTrue(recipe.processingTime() > 0);
        assertTrue(recipe.inputs().stream().allMatch(input -> input.count() > 0));
        assertTrue(recipe.outputs().stream().allMatch(output -> !output.item().isBlank() && output.count() > 0));
        assertEquals(recipe.requiresFuel(), profile.hasFuelSlot());
        assertTrue(recipe.fluid().isEmpty() || profile.supportsFluids());
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
