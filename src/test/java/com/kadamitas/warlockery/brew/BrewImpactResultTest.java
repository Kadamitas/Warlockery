package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kadamitas.warlockery.brew.BrewRuntime.ImpactResult;
import org.junit.jupiter.api.Test;

final class BrewImpactResultTest {
    @Test
    void resultsComposeAcrossMultipleBehaviors() {
        final ImpactResult result = ImpactResult.entities(3)
            .plus(ImpactResult.blocks(5))
            .plus(ImpactResult.event());
        assertEquals(new ImpactResult(3, 5, 1), result);
    }

    @Test
    void resultCountsCannotBecomeNegative() {
        assertThrows(IllegalArgumentException.class, () -> new ImpactResult(-1, 0, 0));
    }
}
