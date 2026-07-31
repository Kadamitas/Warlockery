package com.kadamitas.warlockery.brew.custom;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class PentacleBrewParityTest {
    @Test
    void pentacleProvidesTheFullLegacyNineteenPointBrewCapacity() throws IOException {
        final Path definition = Path.of(
            "src", "main", "resources", "data", "warlockery", "custom_brew_component", "capacity_pentacle.json"
        );
        final CustomBrewComponentDefinition component = CustomBrewComponentDefinition.CODEC.parse(
            JsonOps.INSTANCE,
            JsonParser.parseString(Files.readString(definition))
        ).getOrThrow();

        assertEquals(CustomBrewComponentRole.CAPACITY, component.role());
        assertEquals(19, component.value());
    }
}
