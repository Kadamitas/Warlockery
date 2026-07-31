package com.kadamitas.warlockery.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class FactoryCatalogTest {
    @Test
    void snapshotsFactoriesAndExposesImmutableViews() {
        final Map<String, ContentFactory<Integer, String>> source = new LinkedHashMap<>();
        source.put("double", value -> Integer.toString(value * 2));
        final FactoryCatalog<Integer, String> catalog = new FactoryCatalog<>("number formatter", source);

        source.clear();

        assertEquals("8", catalog.create("double", 4));
        assertEquals(java.util.Set.of("double"), catalog.ids());
        assertThrows(UnsupportedOperationException.class, () -> catalog.factories().clear());
        assertThrows(UnsupportedOperationException.class, () -> catalog.ids().clear());
    }

    @Test
    void reportsUnsupportedIdsWithTheCatalogType() {
        final FactoryCatalog<Integer, String> catalog = new FactoryCatalog<>("number formatter", Map.of());

        final IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> catalog.create("missing", 1)
        );

        assertTrue(exception.getMessage().contains("Unsupported number formatter: missing"));
    }
}
