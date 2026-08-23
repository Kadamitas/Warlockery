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

        assertTrue(ledger.changed(player, WerewolfShape.HUMAN));
        assertFalse(ledger.changed(player, WerewolfShape.HUMAN));
        assertTrue(ledger.changed(player, WerewolfShape.WOLF));
        assertFalse(ledger.changed(player, WerewolfShape.WOLF));
        assertTrue(ledger.changed(player, WerewolfShape.WOLFMAN));
    }

    @Test
    void removalForcesFreshStateAfterReconnect() {
        final PlayerWolfVisualSync.StateLedger ledger = new PlayerWolfVisualSync.StateLedger();
        final UUID player = UUID.randomUUID();

        assertTrue(ledger.changed(player, WerewolfShape.WOLFMAN));
        assertFalse(ledger.changed(player, WerewolfShape.WOLFMAN));

        ledger.remove(player);

        assertTrue(ledger.changed(player, WerewolfShape.WOLFMAN));
    }
}
