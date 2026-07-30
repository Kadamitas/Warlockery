package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class BrewKindTest {
    @Test
    void builtInIdsAreUniqueAndResolvable() {
        final Set<String> ids = new HashSet<>();
        BrewKind.builtIns().forEach(kind -> {
            assertTrue(ids.add(kind.id()));
            assertEquals(kind, BrewKind.require(kind.id()));
        });
        assertEquals(128, ids.size());
        assertTrue(BrewKind.find("missing").isEmpty());
    }

    @Test
    void codecDecodesDataDrivenWorldBehavior() {
        final var json = JsonParser.parseString("""
            {
              "id": "test_brew",
              "color": 1193046,
              "effects": [
                {
                  "effect": "minecraft:speed",
                  "duration": 200,
                  "amplifier": 1
                }
              ],
              "behaviors": ["grow", "push"],
              "radius": 6.0,
              "potency": 2.0
            }
            """);
        final BrewKind decoded = BrewKind.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        assertEquals("test_brew", decoded.id());
        assertEquals(0x123456, decoded.color());
        assertEquals(List.of(new BrewEffectSpec("minecraft:speed", 200, 1)), decoded.effects());
        assertEquals(List.of(BrewBehavior.GROW, BrewBehavior.PUSH), decoded.behaviors());
        assertEquals(6.0F, decoded.radius());
        assertEquals(2.0F, decoded.potency());
    }

    @Test
    void definitionRejectsInvalidIdsAndUnsafeRanges() {
        assertThrows(IllegalArgumentException.class, () -> new BrewKind(
            "Bad Id", 0, List.of(), List.of(), 4.0F, 1.0F
        ));
        assertThrows(IllegalArgumentException.class, () -> new BrewKind(
            "test", 0, List.of(), List.of(), 30.0F, 1.0F
        ));
        assertThrows(IllegalArgumentException.class, () -> new BrewKind(
            "test", 0, List.of(), List.of(), 4.0F, 0.0F
        ));
        assertThrows(IllegalArgumentException.class, () -> new BrewKind(
            "test", 0x1000000, List.of(), List.of(), 4.0F, 1.0F
        ));
    }

    @Test
    void vanillaEquivalentBrewsDescribePotionEffects() {
        assertEffect(BrewKind.HEAL, "minecraft:instant_health");
        assertEffect(BrewKind.HARM, "minecraft:instant_damage");
        assertEffect(BrewKind.ABSORPTION, "minecraft:absorption");
        assertEffect(BrewKind.FAST_MOVEMENT, "minecraft:speed");
        assertEffect(BrewKind.SLOW_MOVEMENT, "minecraft:slowness");
        assertEffect(BrewKind.BLINDNESS, "minecraft:blindness");
        assertEffect(BrewKind.NIGHT_VISION, "minecraft:night_vision");
        assertEffect(BrewKind.WATER_BREATHING, "minecraft:water_breathing");
        assertEffect(BrewKind.FIRE_RESISTANCE, "minecraft:fire_resistance");
        assertEffect(BrewKind.POISON, "minecraft:poison");
        assertEffect(BrewKind.WITHER, "minecraft:wither");
    }

    @Test
    void worldBrewsDoNotPretendToBePotionEffects() {
        assertFalse(BrewKind.FERTILIZE.hasPotionEffects());
        assertFalse(BrewKind.FLAMES.hasPotionEffects());
        assertFalse(BrewKind.HARM_WEREWOLVES.hasPotionEffects());
        assertTrue(BrewKind.FREEZE.hasPotionEffects());
        assertEquals(List.of(BrewBehavior.FREEZE), BrewKind.FREEZE.behaviors());
    }

    private static void assertEffect(final BrewKind kind, final String expected) {
        assertTrue(kind.hasPotionEffects());
        assertTrue(kind.effects().stream().anyMatch(effect -> effect.effect().equals(expected)));
    }
}
