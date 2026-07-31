package com.kadamitas.warlockery.crafting;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AltarPowerNetworkRangeTest {
    @Test
    void brazierCanReachAnAltarAtTheArchivedFifteenBlockDistance() {
        assertTrue(AltarPowerNetwork.BASE_HORIZONTAL_RANGE >= 15);
    }
}
