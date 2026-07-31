package com.kadamitas.warlockery.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class InfernalPowerTest {
    private static final Path TAGS = Path.of(
        "src", "main", "resources", "data", "warlockery", "tags", "entity_type", "infernal_sacrifices"
    );

    @Test
    void everyInfernalPowerHasAStableRoundTripIdentifier() {
        final Set<String> expected = Set.of(
            "explosion", "projectile", "web", "fire", "speed", "healing",
            "teleport", "leaping", "flight", "aquatic", "undead"
        );
        assertEquals(expected, java.util.Arrays.stream(InfernalPower.values())
            .map(InfernalPower::id)
            .collect(Collectors.toUnmodifiableSet()));
        java.util.Arrays.stream(InfernalPower.values()).forEach(power ->
            assertEquals(power, InfernalPower.find(power.id()).orElseThrow())
        );
        assertTrue(InfernalPower.find("missing").isEmpty());
    }

    @Test
    void creatureSpecificPowersUseExtensibleEntityTags() {
        final Map<String, String> representatives = Map.of(
            "explosion", "minecraft:creeper",
            "projectile", "minecraft:skeleton",
            "web", "minecraft:spider",
            "flight", "minecraft:bat"
        );
        representatives.forEach((power, entity) -> {
            final Path path = TAGS.resolve(power + ".json");
            try {
                assertTrue(JsonParser.parseString(Files.readString(path))
                    .getAsJsonObject()
                    .getAsJsonArray("values")
                    .asList()
                    .stream()
                    .anyMatch(value -> value.getAsString().equals(entity)), power);
            } catch (IOException exception) {
                throw new UncheckedIOException(path.toString(), exception);
            }
        });
    }
}
