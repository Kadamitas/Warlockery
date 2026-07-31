package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class WitchLadderParityTest {
    @Test
    void witchLadderIsBothClimbableAndAFetishTarget() throws IOException {
        assertEquals(WitchLadderBlock.class, ModernBlockFactory.implementationType("hex_ladder"));
        final String climbable = Files.readString(Path.of(
            "src", "main", "resources", "data", "minecraft", "tags", "block", "climbable.json"
        ));
        final String binding = Files.readString(Path.of(
            "src", "main", "resources", "data", "warlockery", "ritual", "bind_witch_ladder.json"
        ));
        assertTrue(climbable.contains("warlockery:hex_ladder"));
        assertTrue(binding.contains("\"target\": \"warlockery:hex_ladder\""));
    }
}
