package com.kadamitas.warlockery.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class RitualDefinitionTest {
    @Test
    void codecDecodesModernRitualData() {
        final var json = JsonParser.parseString("""
            {
              "action": "summon_entity",
              "power": 900,
              "glyphs": {"circleglyphritual": 4},
              "target": "warlockery:imp",
              "requirements": {
                "ingredients": [{"ingredient": "minecraft:amethyst_shard", "count": 2}],
                "entities": [{"entity": "#warlockery:death_binding/banshees", "count": 5, "consume": true}]
              }
            }
            """);
        final RitualDefinition definition = RitualDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        assertEquals("summon_entity", definition.action());
        assertEquals(900, definition.power());
        assertEquals(2, definition.requirements().ingredients().getFirst().count());
        assertEquals(5, definition.requirements().entities().getFirst().count());
        assertTrue(definition.visible());
    }

    @Test
    void recordsDefensivelyCopyCollections() {
        final Map<String, Integer> glyphs = new HashMap<>(Map.of("circle", 1));
        final List<RitualDefinition.Ingredient> ingredients = new ArrayList<>(List.of(
            new RitualDefinition.Ingredient(" minecraft:paper ", 1, true)
        ));
        final RitualDefinition definition = definition("effect", 100, 4, 1, false,
            new RitualDefinition.Requirements(ingredients, false, false, false, false, "", 1), glyphs);
        glyphs.clear();
        ingredients.clear();
        assertEquals(Map.of("circle", 1), definition.glyphs());
        assertEquals("minecraft:paper", definition.requirements().ingredients().getFirst().ingredient());
        assertThrows(UnsupportedOperationException.class, () -> definition.glyphs().put("other", 2));
    }

    @Test
    void validatorReportsConflictingAndInvalidRequirements() {
        final RitualDefinition invalid = definition("unknown", -1, 0, 0, true,
            new RitualDefinition.Requirements(
                List.of(new RitualDefinition.Ingredient("", 0, true)), true, false, false, false, "", 0
            ), Map.of("", 0));
        final List<String> errors = RitualValidator.structuralErrors(invalid);
        assertFalse(errors.isEmpty());
        assertTrue(errors.contains("unknown action"));
        assertTrue(errors.contains("day and night requirements conflict"));
    }

    @Test
    void validatorAcceptsWellFormedDefinition() {
        assertTrue(RitualValidator.isStructurallyValid(definition(
            "effect", 400, 6, 1, false, RitualDefinition.Requirements.EMPTY, Map.of("circle", 1)
        )));
    }

    private static RitualDefinition definition(
        final String action,
        final int power,
        final int radius,
        final int count,
        final boolean nightOnly,
        final RitualDefinition.Requirements requirements,
        final Map<String, Integer> glyphs
    ) {
        return new RitualDefinition(
            action, "minecraft:resistance", power, radius, 200, 0, glyphs, nightOnly, 40,
            "", count, "Test", "Test definition", false, requirements
        );
    }
}
