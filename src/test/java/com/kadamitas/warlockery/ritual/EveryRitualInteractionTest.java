package com.kadamitas.warlockery.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.diagnostic.DiagnosticChecklist;
import com.kadamitas.warlockery.testutil.JsonFixtureLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

final class EveryRitualInteractionTest {
    private static final Path RITUALS = Path.of("src", "main", "resources", "data", "warlockery", "ritual");

    @TestFactory
    Stream<DynamicContainer> oneSuitePerRitual() {
        return JsonFixtureLoader.load(RITUALS, RitualDefinition.CODEC).stream()
            .map(fixture -> container(fixture.id(), fixture.value()));
    }

    private static DynamicContainer container(final String id, final RitualDefinition definition) {
        return DynamicContainer.dynamicContainer(id, List.of(
            DynamicTest.dynamicTest("failure reports the missing condition", () -> failureUpdatesDiagnostics(id, definition)),
            DynamicTest.dynamicTest("floating UI changes to a green check", () -> uiTurnsGreen(id, definition)),
            DynamicTest.dynamicTest("success dispatches the declared outcome", () -> successContract(id, definition))
        ));
    }

    private static void failureUpdatesDiagnostics(final String id, final RitualDefinition definition) {
        final List<RitualManager.RequirementStatus> requirements = requirementStatuses(definition, true);
        requirements.set(0, new RitualManager.RequirementStatus("glyph", "missing", 1, 0, false));
        final RitualUiState state = RitualUiState.from(option(id, definition, requirements, false));
        assertFalse(state.checklist().complete());
        assertFalse(state.showGreenCheck());
        assertEquals(DiagnosticChecklist.INCOMPLETE_MARKER, state.checklist().marker());
        assertEquals(DiagnosticChecklist.INCOMPLETE_COLOR, state.checklist().color());
    }

    private static void uiTurnsGreen(final String id, final RitualDefinition definition) {
        final List<RitualManager.RequirementStatus> failedRequirements = requirementStatuses(definition, false);
        final List<RitualManager.RequirementStatus> readyRequirements = requirementStatuses(definition, true);
        final RitualUiState failed = RitualUiState.from(option(id, definition, failedRequirements, false));
        final RitualUiState ready = RitualUiState.from(option(id, definition, readyRequirements, true));
        assertFalse(failed.showGreenCheck());
        assertTrue(ready.showGreenCheck());
        assertEquals(readyRequirements.size(), ready.checklist().total());
        assertEquals(DiagnosticChecklist.COMPLETE_MARKER, ready.checklist().marker());
        assertEquals(DiagnosticChecklist.COMPLETE_COLOR, ready.checklist().color());
    }

    private static void successContract(final String id, final RitualDefinition definition) {
        assertTrue(RitualValidator.isStructurallyValid(definition), id + " must be executable");
        final RitualAction action = RitualAction.require(definition.action());
        assertNotNull(action.outcome(), id + " must declare its visible success outcome");
        assertFalse(definition.title().isBlank(), id + " must name the successful rite");
        assertFalse(definition.description().isBlank(), id + " must explain the successful result");
    }

    private static RitualManager.RitualOption option(
        final String id,
        final RitualDefinition definition,
        final List<RitualManager.RequirementStatus> requirements,
        final boolean ready
    ) {
        return new RitualManager.RitualOption(
            id,
            definition.title(),
            definition.description(),
            definition.power(),
            ready ? definition.power() : 0,
            definition.castingTime(),
            requirements,
            ready
        );
    }

    private static List<RitualManager.RequirementStatus> requirementStatuses(
        final RitualDefinition definition,
        final boolean met
    ) {
        final List<RitualManager.RequirementStatus> statuses = new ArrayList<>();
        definition.glyphs().forEach((glyph, count) -> statuses.add(
            new RitualManager.RequirementStatus("glyph", glyph, count, met ? count : 0, met)
        ));
        definition.requirements().ingredients().forEach(ingredient -> statuses.add(
            new RitualManager.RequirementStatus(
                "ingredient", ingredient.ingredient(), ingredient.count(), met ? ingredient.count() : 0, met
            )
        ));
        definition.requirements().entities().forEach(entity -> statuses.add(
            new RitualManager.RequirementStatus(
                "entity", entity.entity(), entity.count(), met ? entity.count() : 0, met
            )
        ));
        statuses.add(new RitualManager.RequirementStatus(
            "power", "altar", definition.power(), met ? definition.power() : 0, met
        ));
        if (definition.nightOnly()) statuses.add(environment("night", met));
        if (definition.requirements().dayOnly()) statuses.add(environment("day", met));
        if (definition.requirements().fullMoon()) statuses.add(environment("full_moon", met));
        if (definition.requirements().raining()) statuses.add(environment("rain", met));
        if (definition.requirements().thundering()) statuses.add(environment("thunder", met));
        if (!definition.requirements().dimension().isBlank()) statuses.add(environment("dimension", met));
        if (definition.requirements().minimumPlayers() > 1) {
            final int required = definition.requirements().minimumPlayers();
            statuses.add(new RitualManager.RequirementStatus("players", "participants", required, met ? required : 0, met));
        }
        return statuses;
    }

    private static RitualManager.RequirementStatus environment(final String label, final boolean met) {
        return new RitualManager.RequirementStatus("environment", label, 1, met ? 1 : 0, met);
    }

}
