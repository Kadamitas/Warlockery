package com.kadamitas.warlockery.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class DataParsingTest {
    @Test
    void uuidParsingRejectsMissingAndMalformedStoredValues() {
        assertTrue(DataParsing.uuid(null).isEmpty());
        assertTrue(DataParsing.uuid(" ").isEmpty());
        assertTrue(DataParsing.uuid("not-a-uuid").isEmpty());

        final UUID id = UUID.randomUUID();
        assertEquals(id, DataParsing.uuid(id.toString()).orElseThrow());
    }

    @Test
    void identifierParsingRejectsMissingAndMalformedStoredValues() {
        assertTrue(DataParsing.identifier(null).isEmpty());
        assertTrue(DataParsing.identifier("").isEmpty());
        assertTrue(DataParsing.identifier("missing namespace spaces").isEmpty());
        assertEquals(
            Identifier.fromNamespaceAndPath("warlockery", "ritual"),
            DataParsing.identifier("warlockery:ritual").orElseThrow()
        );
    }
}
