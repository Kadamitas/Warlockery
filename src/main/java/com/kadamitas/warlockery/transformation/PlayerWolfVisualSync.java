package com.kadamitas.warlockery.transformation;

import com.kadamitas.warlockery.network.ModNetwork;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class PlayerWolfVisualSync {
    private static final StateLedger LAST_BROADCAST = new StateLedger();
    private static boolean registered;

    private PlayerWolfVisualSync() {
    }

    public static void registerEvents() {
        if (registered) {
            return;
        }
        registered = true;
        EntityTrackingEvents.START_TRACKING.register(PlayerWolfVisualSync::handleStartTracking);
        ServerPlayerEvents.JOIN.register(PlayerWolfVisualSync::handleLogin);
        ServerPlayerEvents.LEAVE.register(PlayerWolfVisualSync::handleLogout);
    }

    public static void refresh(final ServerPlayer player) {
        final WerewolfShape shape = shape(player);
        if (LAST_BROADCAST.changed(player.getUUID(), shape)) {
            ModNetwork.broadcastPlayerWolfVisual(player, shape);
        }
    }

    private static void handleStartTracking(final Entity trackedEntity, final ServerPlayer recipient) {
        if (trackedEntity instanceof ServerPlayer subject) {
            ModNetwork.sendPlayerWolfVisual(recipient, subject, shape(subject));
        }
    }

    private static void handleLogin(final ServerPlayer player) {
        LAST_BROADCAST.remove(player.getUUID());
        refresh(player);
    }

    private static void handleLogout(final ServerPlayer player) {
        LAST_BROADCAST.remove(player.getUUID());
        ModNetwork.clearPlayerWolfVisual(player);
    }

    private static WerewolfShape shape(final ServerPlayer player) {
        return SupernaturalState.getForm(player) == SupernaturalForm.WEREWOLF
            ? SupernaturalProgression.werewolfShape(player)
            : WerewolfShape.HUMAN;
    }

    static final class StateLedger {
        private final ConcurrentMap<UUID, WerewolfShape> values = new ConcurrentHashMap<>();

        boolean changed(final UUID playerId, final WerewolfShape shape) {
            final WerewolfShape previous = values.put(playerId, shape);
            return previous != shape;
        }

        void remove(final UUID playerId) {
            values.remove(playerId);
        }
    }
}
