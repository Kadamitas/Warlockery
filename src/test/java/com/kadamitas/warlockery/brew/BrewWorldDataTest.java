package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class BrewWorldDataTest {
    @Test
    void unloadedCellsRemainPendingUntilTheirChunkReturns() {
        assertEquals(
            BrewWorldRestorationRules.ExpiredCellAction.RETAIN,
            BrewWorldRestorationRules.decide(false, false)
        );
    }

    @Test
    void unchangedTemporaryCellsRestore() {
        assertEquals(
            BrewWorldRestorationRules.ExpiredCellAction.RESTORE,
            BrewWorldRestorationRules.decide(true, true)
        );
    }

    @Test
    void playerEditedCellsAreNeverOverwritten() {
        assertEquals(
            BrewWorldRestorationRules.ExpiredCellAction.DROP,
            BrewWorldRestorationRules.decide(true, false)
        );
    }
}
