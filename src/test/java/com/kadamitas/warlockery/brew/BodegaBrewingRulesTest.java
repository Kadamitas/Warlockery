package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class BodegaBrewingRulesTest {
    private static final Path RECIPE = Path.of(
        "src", "main", "resources", "data", "warlockery", "warlockery_machine", "kettle_brew_bodega.json"
    );

    @Test
    void legacyFamiliarBrewsRequireTheirMatchingBoundCompanion() {
        assertTrue(BodegaBrewingRules.requiresFamiliar(Identifier.parse("warlockery:kettle_brew_bodega")));
        assertTrue(BodegaBrewingRules.requiresFamiliar(Identifier.parse("warlockery:kettle_brew_cursed_leaping")));
        assertTrue(BodegaBrewingRules.requiresFamiliar(Identifier.parse("warlockery:kettle_brew_frogs_tongue")));
        assertTrue(BodegaBrewingRules.requiredFamiliar(Identifier.parse("warlockery:kettle_brew_bodega"))
            .filter("owl"::equals).isPresent());
        assertTrue(BodegaBrewingRules.requiredFamiliar(Identifier.parse("warlockery:kettle_brew_cursed_leaping"))
            .filter("familiar_cat"::equals).isPresent());
        assertTrue(BodegaBrewingRules.requiredFamiliar(Identifier.parse("warlockery:kettle_brew_frogs_tongue"))
            .filter("toad"::equals).isPresent());
        assertFalse(BodegaBrewingRules.requiresFamiliar(Identifier.parse("warlockery:kettle_brew_thorns")));
        assertFalse(BodegaBrewingRules.ready(true, false));
        assertTrue(BodegaBrewingRules.ready(true, true));
    }

    @Test
    void documentedIngredientsUseInteroperabilityTags() {
        final String recipe = read(RECIPE);
        assertTrue(recipe.contains("#c:mushrooms"));
        assertTrue(recipe.contains("#warlockery:bat_binding_fibers"));
        assertTrue(recipe.contains("#c:seeds"));
        assertTrue(recipe.contains("#c:feathers"));
        assertTrue(recipe.contains("#warlockery:bodega_thorn_brews"));
        assertTrue(recipe.contains("#warlockery:bodega_owl_wings"));
        assertFalse(recipe.contains("ingredient_brew_hitchcock"));
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
