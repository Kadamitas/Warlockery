package com.kadamitas.warlockery.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GameTestDimensionPresetTest {
    @Test
    void dedicatedTestsUseRealModDimensionsAlongsideVanillaFlatDimensions() throws Exception {
        // 26.3 GameTestServer intentionally ignores datapack dimensions and uses this preset.
        final var dimensions = JsonParser.parseString(Files.readString(Path.of(
            "src/serverGameTest/resources/data/minecraft/worldgen/world_preset/flat_all_dimensions.json")))
            .getAsJsonObject().getAsJsonObject("dimensions");
        assertEquals(5, dimensions.size());
        for (final String id : new String[] {"overworld", "the_nether", "the_end"})
            assertTrue(dimensions.has("minecraft:" + id));
        for (final String id : new String[] {"spirit_world", "abyss"})
            assertEquals(JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/warlockery/dimension/" + id + ".json"))),
                dimensions.get("warlockery:" + id), "test world must use the production dimension definition");
    }
}
