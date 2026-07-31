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
            "LivingDamageEvent.BUS.addListener(Priority.LOWEST, DollItem::handleDamage)"
        ));
        assertFalse(source.contains("LivingDamageEvent.BUS.addListener(DollItem::handleDamage)"));
        assertTrue(source.indexOf("RitualWardData::handleDamage")
            < source.indexOf("Priority.LOWEST, DollItem::handleDamage"));
    }
}
