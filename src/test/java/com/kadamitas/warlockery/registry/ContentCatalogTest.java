package com.kadamitas.warlockery.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ContentCatalogTest {
    @Test
    void modernizesCamelCaseIdentifiers() {
        assertEquals("deepslate_silver_ore", ContentCatalog.modernize("deepslateSilverOre"));
        assertEquals("ingredient_matriarchs_blood", ContentCatalog.ingredientId("matriarchsBlood"));
    }

    @Test
    void generatedRegistryIdsAreUniqueAndValid() {
        assertUniqueAndValid(ContentCatalog.BLOCKS.stream().map(ContentCatalog::modernize).toList());
        assertUniqueAndValid(ContentCatalog.ITEMS.stream().map(ContentCatalog::modernize).toList());
        assertUniqueAndValid(ContentCatalog.INGREDIENTS.stream().map(ContentCatalog::ingredientId).toList());
    }

    @Test
    void rebrandedContentUsesWarlockeryNames() {
        final Set<String> items = ContentCatalog.ITEMS.stream().map(ContentCatalog::modernize).collect(java.util.stream.Collectors.toUnmodifiableSet());
        final Set<String> blocks = ContentCatalog.BLOCKS.stream().map(ContentCatalog::modernize).collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertTrue(items.containsAll(Set.of("ritual_knife", "arcane_focus", "sympathetic_vial")));
        assertFalse(items.contains("silver_repeater"));
        assertFalse(items.contains("arthana"));
        assertFalse(items.contains("handbow"));
        assertFalse(items.contains("taglockkit"));
        assertFalse(items.contains("voodoo_protection_doll"));
        assertFalse(blocks.contains("clever"));
        assertFalse(blocks.contains("slurp"));
    }

    private static void assertUniqueAndValid(final List<String> ids) {
        assertEquals(ids.size(), Set.copyOf(ids).size(), "registry IDs must be unique");
        assertTrue(ids.stream().allMatch(id -> id.matches("[a-z0-9_.]+")), "registry IDs must be namespaced-path safe");
    }
}
