package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

final class BrewEffectSpecTest {
    @Test
    void codecDecodesRegistryIdDurationAndAmplifier() {
        final var json = JsonParser.parseString("""
            {
              "effect": "minecraft:regeneration",
              "duration": 900,
              "amplifier": 2
            }
            """);
        final BrewEffectSpec decoded = BrewEffectSpec.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        assertEquals(new BrewEffectSpec("minecraft:regeneration", 900, 2), decoded);
    }

    @Test
    void definitionRejectsInvalidRegistryIdsAndRanges() {
        assertThrows(IllegalArgumentException.class, () -> new BrewEffectSpec("Bad Id", 20, 0));
        assertThrows(IllegalArgumentException.class, () -> new BrewEffectSpec("minecraft:speed", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BrewEffectSpec("minecraft:speed", 20, 256));
    }
}
