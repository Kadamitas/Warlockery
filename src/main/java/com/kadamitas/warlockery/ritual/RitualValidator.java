package com.kadamitas.warlockery.ritual;

import java.util.ArrayList;
import java.util.List;

public final class RitualValidator {
    private RitualValidator() {
    }

    public static List<String> structuralErrors(final RitualDefinition definition) {
        final List<String> errors = new ArrayList<>();
        if (RitualAction.find(definition.action()).isEmpty()) errors.add("unknown action");
        if (definition.power() < 0) errors.add("power must be non-negative");
        if (definition.radius() <= 0) errors.add("radius must be positive");
        if (definition.castingTime() <= 0) errors.add("casting time must be positive");
        if (definition.count() <= 0) errors.add("result count must be positive");
        if (definition.requirements().minimumPlayers() <= 0) errors.add("minimum players must be positive");
        if (definition.nightOnly() && definition.requirements().dayOnly()) errors.add("day and night requirements conflict");
        definition.glyphs().forEach((glyph, count) -> {
            if (glyph.isBlank() || count <= 0) errors.add("glyph requirements must have a name and positive count");
        });
        definition.requirements().ingredients().forEach(ingredient -> {
            if (ingredient.ingredient().isBlank() || ingredient.count() <= 0) {
                errors.add("ingredients must have an id and positive count");
            }
        });
        definition.requirements().entities().forEach(entity -> {
            if (entity.entity().isBlank() || entity.count() <= 0) {
                errors.add("entity requirements must have an id and positive count");
            }
        });
        return List.copyOf(errors);
    }

    public static boolean isStructurallyValid(final RitualDefinition definition) {
        return structuralErrors(definition).isEmpty();
    }
}
