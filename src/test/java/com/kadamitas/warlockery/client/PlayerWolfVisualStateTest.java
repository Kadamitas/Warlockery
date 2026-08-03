package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.network.ModNetwork;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PlayerWolfVisualStateTest {
    @AfterEach
    void clearState() {
        PlayerWolfVisualState.clear();
    }

    @Test
    void payloadActivatesAndClearsOnlyItsPlayer() {
        final UUID wolf = UUID.randomUUID();
        final UUID human = UUID.randomUUID();

        PlayerWolfVisualState.update(new ModNetwork.PlayerWolfVisualPayload(wolf, true));
        PlayerWolfVisualState.update(new ModNetwork.PlayerWolfVisualPayload(human, false));

        assertTrue(PlayerWolfVisualState.isWolf(wolf));
        assertFalse(PlayerWolfVisualState.isWolf(human));

        PlayerWolfVisualState.update(new ModNetwork.PlayerWolfVisualPayload(wolf, false));

        assertFalse(PlayerWolfVisualState.isWolf(wolf));
    }

    @Test
    void repeatedPayloadsRemainIdempotent() {
        final UUID player = UUID.randomUUID();
        final ModNetwork.PlayerWolfVisualPayload active =
            new ModNetwork.PlayerWolfVisualPayload(player, true);

        PlayerWolfVisualState.update(active);
        PlayerWolfVisualState.update(active);

        assertTrue(PlayerWolfVisualState.isWolf(player));
    }
}
