package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class AmbientActivityTagResourcesTest {
    private static final Path TAGS = Path.of("src/main/resources/data/warlockery/tags/block/ambient");

    @Test
    void arcaneWorkstationsExtendCanonicalCommonWorkstationTags() throws IOException {
        final String tag = Files.readString(TAGS.resolve("arcane_workstations.json"));
        assertTrue(tag.contains("#c:player_workstations/crafting_tables"));
        assertTrue(tag.contains("#c:player_workstations/furnaces"));
    }

    @Test
    void nonCanonicalWorldInteractionsExposeOwnedExtensionTags() throws IOException {
        assertTrue(Files.readString(TAGS.resolve("lightning_rods.json")).contains("minecraft:lightning_rod"));
        assertTrue(Files.readString(TAGS.resolve("soul_lights.json")).contains("minecraft:soul_lantern"));
        assertTrue(Files.readString(TAGS.resolve("thorny_plants.json")).contains("warlockery:bramble"));
        assertTrue(Files.readString(TAGS.resolve("pond_rest_blocks.json")).contains("minecraft:lily_pad"));
    }
}
