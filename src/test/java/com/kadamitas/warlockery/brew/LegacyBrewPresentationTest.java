package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.registry.ContentCatalog;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

final class LegacyBrewPresentationTest {
    @Test
    void everyLegacyIngredientBrewUsesAWorkingModernFormula() {
        final Set<String> expected = ContentCatalog.INGREDIENTS.stream()
            .filter(name -> name.startsWith("brew"))
            .filter(name -> !name.equals("brewGrave"))
            .map(ContentCatalog::ingredientId)
            .collect(Collectors.toUnmodifiableSet());
        assertFalse(expected.isEmpty());
        assertTrue(expected.stream().allMatch(BrewFactory::supportsLegacy));
        assertTrue(expected.stream().map(BrewFactory::legacyKind).allMatch(java.util.Optional::isPresent));
        assertTrue(expected.stream().map(BrewFactory::legacyKind).map(java.util.Optional::orElseThrow)
            .allMatch(kind -> kind.hasPotionEffects() || !kind.behaviors().isEmpty()));
    }

    @Test
    void murderousFlockLegacyAndModernItemsShareTheFlockBehavior() {
        assertEquals(BrewKind.MURDEROUS_FLOCK, BrewFactory.legacyKind("ingredient_brew_hitchcock").orElseThrow());
        assertEquals(BrewKind.MURDEROUS_FLOCK, BrewFactory.requireKind("brew_murderous_flock"));
        assertEquals(List.of(BrewBehavior.SUMMON_MURDEROUS_FLOCK), BrewKind.MURDEROUS_FLOCK.behaviors());
    }

    @Test
    void brewNamesUseTheirItemTranslationInsteadOfPotionEffectEmpty() {
        final Component name = BrewItem.displayName("item.warlockery.brew_animal_attraction");
        assertEquals(Component.translatable("item.warlockery.brew_animal_attraction"), name);
        assertFalse(name.getString().contains("effect.empty"));
    }
}
