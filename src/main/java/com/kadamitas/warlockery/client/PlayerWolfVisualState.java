package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.network.ModNetwork;
import com.kadamitas.warlockery.transformation.WerewolfShape;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerWolfVisualState {
    private static final ConcurrentMap<UUID, WerewolfShape> SHAPES = new ConcurrentHashMap<>();

    private PlayerWolfVisualState() {
    }

    public static boolean isWolf(final UUID playerId) {
        return shape(playerId) == WerewolfShape.WOLF;
    }

    public static WerewolfShape shape(final UUID playerId) {
        return SHAPES.getOrDefault(playerId, WerewolfShape.HUMAN);
    }

    public static void update(final ModNetwork.PlayerWolfVisualPayload payload) {
        if (payload.shape() == WerewolfShape.HUMAN) {
            SHAPES.remove(payload.playerId());
        } else {
            SHAPES.put(payload.playerId(), payload.shape());
        }
    }

    public static void clear() {
        SHAPES.clear();
    }
}
