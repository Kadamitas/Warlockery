package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class EnderBrambleParityTest {
    @Test
    void teleportRangeMatchesTheArchivedLongRangeBehavior() {
        assertEquals(500, MagicalPlantBlock.TELEPORT_RADIUS);
        assertTrue(MagicalPlantBlock.TELEPORT_ATTEMPTS >= 16);
    }

    @Test
    void anAxeIsTheDocumentedEfficientHarvestTool() throws Exception {
        assertTrue(Files.readString(Path.of(
            "src/main/resources/data/minecraft/tags/block/mineable/axe.json"
        )).contains("warlockery:bramble"));
    }
}
