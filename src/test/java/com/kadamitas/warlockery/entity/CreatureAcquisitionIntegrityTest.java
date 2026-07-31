package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.kadamitas.warlockery.ritual.ChalkCircleLayout;
import com.kadamitas.warlockery.ritual.RitualAction;
import com.kadamitas.warlockery.ritual.RitualDefinition;
import com.kadamitas.warlockery.ritual.RitualManager;
import com.kadamitas.warlockery.ritual.RitualUiState;
import com.kadamitas.warlockery.ritual.RitualValidator;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

final class CreatureAcquisitionIntegrityTest {
    private static final Path RITUALS = Path.of(
        "src", "main", "resources", "data", "warlockery", "ritual"
    );
    private static final List<Route> ROUTES = List.of(
        route("Death", "bind_death", "warlockery:death", "summon_entity"),
        route("Coven Witch", "summon_circle_mage", "warlockery:circle_mage", "summon_entity"),
        route("Familiar", "summon_cat_familiar", "warlockery:familiar_cat", "summon_entity"),
        route("Flying Monkey", "summon_storm_simian", "warlockery:storm_simian", "summon_entity"),
        route("Gulg", "summon_forgewarden", "warlockery:forgewarden", "summon_entity"),
        route("Mog", "summon_stonebroker", "warlockery:stonebroker", "summon_entity"),
        route("Lost Soul", "summon_lost_soul", "warlockery:lost_soul", "summon_entity"),
        route("Parasytic Louse", "summon_parasytic_louse", "warlockery:parasytic_louse", "summon_entity"),
        route("Poltergeist", "summon_poltergeist", "warlockery:poltergeist", "summon_entity"),
        route("Horned Huntsman", "summon_thorned_pursuer", "warlockery:thorned_pursuer", "summon_huntsman")
    );

    @TestFactory
    Stream<DynamicContainer> oneFailureUiAndSuccessSuitePerAcquisitionRoute() {
        return ROUTES.stream().map(route -> DynamicContainer.dynamicContainer(route.page(), List.of(
            DynamicTest.dynamicTest("failure is bounded", () -> failure(route)),
            DynamicTest.dynamicTest("floating UI updates", () -> ui(route)),
            DynamicTest.dynamicTest("success route is executable", () -> success(route))
        )));
    }

    private static void failure(final Route route) {
        final RitualDefinition definition = definition(route);
        assertFalse(definition.glyphs().isEmpty());
        assertFalse(definition.requirements().ingredients().isEmpty());
        assertTrue(definition.requirements().ingredients().stream()
            .noneMatch(ingredient -> ingredient.ingredient().contains("spawn_egg")));
        final List<RitualManager.RequirementStatus> statuses = statuses(definition, true);
        statuses.set(0, new RitualManager.RequirementStatus("chalk", "missing", 1, 0, false));
        assertFalse(RitualUiState.from(option(route, definition, statuses, false)).showGreenCheck());
        if (route.ritual().equals("bind_death")) {
            assertEquals(3, definition.requirements().entities().size());
            assertEquals(15, definition.requirements().entities().stream()
                .mapToInt(RitualDefinition.EntityRequirement::count).sum());
        }
    }

    private static void ui(final Route route) {
        final RitualDefinition definition = definition(route);
        final List<RitualManager.RequirementStatus> ready = statuses(definition, true);
        assertTrue(RitualUiState.from(option(route, definition, ready, true)).showGreenCheck());
        assertFalse(definition.title().isBlank());
        assertFalse(definition.description().isBlank());
    }

    private static void success(final Route route) {
        final RitualDefinition definition = definition(route);
        assertTrue(RitualValidator.isStructurallyValid(definition));
        assertEquals(route.action(), definition.action());
        assertEquals(route.target(), definition.target());
        assertEquals(RitualAction.Outcome.ENTITY_SUMMON, RitualAction.require(definition.action()).outcome());
    }

    private static List<RitualManager.RequirementStatus> statuses(
        final RitualDefinition definition,
        final boolean met
    ) {
        final List<RitualManager.RequirementStatus> statuses = new ArrayList<>();
        ChalkCircleLayout.canonicalGlyphs(definition.glyphs()).forEach((glyph, count) -> statuses.add(
            new RitualManager.RequirementStatus("chalk", glyph, count, met ? count : 0, met)
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
            "power", "altar_power", definition.power(), met ? definition.power() : 0, met
        ));
        return statuses;
    }

    private static RitualManager.RitualOption option(
        final Route route,
        final RitualDefinition definition,
        final List<RitualManager.RequirementStatus> statuses,
        final boolean ready
    ) {
        return new RitualManager.RitualOption(
            "warlockery:" + route.ritual(),
            definition.title(),
            definition.description(),
            definition.power(),
            ready ? definition.power() : 0,
            definition.castingTime(),
            statuses,
            ready
        );
    }

    private static RitualDefinition definition(final Route route) {
        final Path path = RITUALS.resolve(route.ritual() + ".json");
        try {
            return RitualDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(path)))
                .getOrThrow(message -> new IllegalArgumentException(path + ": " + message));
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private static Route route(
        final String page,
        final String ritual,
        final String target,
        final String action
    ) {
        return new Route(page, ritual, target, action);
    }

    private record Route(String page, String ritual, String target, String action) {
    }
}
