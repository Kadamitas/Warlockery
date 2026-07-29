package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class BodegaTargetingTest {
    @Test
    void passiveAndOwnedCreaturesAreRejected() {
        assertFalse(BrewRuntime.isBodegaTarget(false, false, false, false));
        assertFalse(BrewRuntime.isBodegaTarget(true, true, true, true));
    }

    @Test
    void taggedEnemyAndAggressiveTargetsAreAccepted() {
        assertTrue(BrewRuntime.isBodegaTarget(true, false, false, false));
        assertTrue(BrewRuntime.isBodegaTarget(false, true, false, false));
        assertTrue(BrewRuntime.isBodegaTarget(false, false, true, false));
    }
}
