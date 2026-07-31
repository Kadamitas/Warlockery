package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class DamageEventOrderingTest {
    @Test
    void lethalDollsRunAfterEveryWarlockeryDamageReducer() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/Warlockery.java"
        ));
        assertTrue(source.contains(
            "NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, DollItem::handleDamage)"
        ));
        assertFalse(source.contains("NeoForge.EVENT_BUS.addListener(DollItem::handleDamage)"));
        assertTrue(source.indexOf("RitualWardData::handleDamage")
            < source.indexOf("EventPriority.LOWEST, DollItem::handleDamage"));
    }
}
