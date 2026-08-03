package com.kadamitas.warlockery.transformation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerWolfVisualSyncTest {
    @Test
    void ledgerBroadcastsOnlyInitialAndChangedStates() {
        final PlayerWolfVisualSync.StateLedger ledger = new PlayerWolfVisualSync.StateLedger();
        final UUID player = UUID.randomUUID();

        assertTrue(ledger.changed(player, false));
        assertFalse(ledger.changed(player, false));
        assertTrue(ledger.changed(player, true));
        assertFalse(ledger.changed(player, true));
        assertTrue(ledger.changed(player, false));
    }

    @Test
    void removalForcesFreshStateAfterReconnect() {
        final PlayerWolfVisualSync.StateLedger ledger = new PlayerWolfVisualSync.StateLedger();
        final UUID player = UUID.randomUUID();

        assertTrue(ledger.changed(player, true));
        assertFalse(ledger.changed(player, true));

        ledger.remove(player);

        assertTrue(ledger.changed(player, true));
    }
}
