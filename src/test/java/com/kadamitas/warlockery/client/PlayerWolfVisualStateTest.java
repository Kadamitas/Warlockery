package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.network.ModNetwork;
import com.kadamitas.warlockery.transformation.WerewolfShape;
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

        PlayerWolfVisualState.update(new ModNetwork.PlayerWolfVisualPayload(wolf, WerewolfShape.WOLF));
        PlayerWolfVisualState.update(new ModNetwork.PlayerWolfVisualPayload(human, WerewolfShape.HUMAN));

        assertTrue(PlayerWolfVisualState.isWolf(wolf));
        assertFalse(PlayerWolfVisualState.isWolf(human));

        PlayerWolfVisualState.update(new ModNetwork.PlayerWolfVisualPayload(wolf, WerewolfShape.HUMAN));

        assertFalse(PlayerWolfVisualState.isWolf(wolf));
    }

    @Test
    void repeatedPayloadsRemainIdempotent() {
        final UUID player = UUID.randomUUID();
        final ModNetwork.PlayerWolfVisualPayload active =
            new ModNetwork.PlayerWolfVisualPayload(player, WerewolfShape.WOLF);

        PlayerWolfVisualState.update(active);
        PlayerWolfVisualState.update(active);

        assertTrue(PlayerWolfVisualState.isWolf(player));
    }
}
