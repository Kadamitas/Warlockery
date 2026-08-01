package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class DamageEventOrderingTest {
    @Test
    void lethalDollsRunAfterEveryWarlockeryDamageReducer() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/fabric/WarlockeryFabricEvents.java"
        ));
        assertTrue(source.contains("DollItem.handleDamage(context);"));
        assertTrue(source.indexOf("RitualWardData.handleDamage(context);")
            < source.indexOf("DollItem.handleDamage(context);"));
    }
}
