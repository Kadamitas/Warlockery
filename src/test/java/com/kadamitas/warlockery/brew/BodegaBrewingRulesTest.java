package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
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

    @Test
    void aBystandersFamiliarCannotAuthorizeTheBrewersRecipe() {
        final UUID brewer = UUID.fromString("10000000-0000-0000-0000-000000000001");
        final UUID bystander = UUID.fromString("20000000-0000-0000-0000-000000000002");

        assertTrue(BodegaBrewingRules.ownedByBrewer(true, Optional.of(brewer), brewer));
        assertFalse(BodegaBrewingRules.ownedByBrewer(true, Optional.of(bystander), brewer));
        assertFalse(BodegaBrewingRules.ownedByBrewer(true, Optional.empty(), brewer));
        assertFalse(BodegaBrewingRules.ownedByBrewer(false, Optional.of(brewer), brewer));
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
