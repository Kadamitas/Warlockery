package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.network.ModNetwork;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerWolfVisualState {
    private static final Set<UUID> WOLF_PLAYERS = ConcurrentHashMap.newKeySet();

    private PlayerWolfVisualState() {
    }

    public static boolean isWolf(final UUID playerId) {
        return WOLF_PLAYERS.contains(playerId);
    }

    public static void update(final ModNetwork.PlayerWolfVisualPayload payload) {
        if (payload.wolf()) {
            WOLF_PLAYERS.add(payload.playerId());
        } else {
            WOLF_PLAYERS.remove(payload.playerId());
        }
    }

    public static void clear() {
        WOLF_PLAYERS.clear();
    }
}
