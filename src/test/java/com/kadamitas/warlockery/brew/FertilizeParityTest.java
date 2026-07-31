package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class FertilizeParityTest {
    @Test
    void fertilizeUsesBoundedRepeatedGrowthInsteadOfOneProbabilisticBonemeal() throws IOException {
        assertTrue(BrewKind.FERTILIZE.behaviors().contains(BrewBehavior.GROW));
        assertEquals(32, BrewRuntime.MAX_FORCED_GROWTH_STEPS);
        final String runtime = Files.readString(Path.of(
            "src", "main", "java", "com", "kadamitas", "warlockery", "brew", "BrewRuntime.java"
        ));
        assertTrue(runtime.contains("forceToMaturity"));
        assertFalse(runtime.contains("isBonemealSuccess"));
    }
}
