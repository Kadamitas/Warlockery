package com.kadamitas.warlockery.transformation;

import com.kadamitas.warlockery.network.ModNetwork;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

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
        PlayerEvent.StartTracking.BUS.addListener(PlayerWolfVisualSync::handleStartTracking);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(PlayerWolfVisualSync::handleLogin);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(PlayerWolfVisualSync::handleLogout);
    }

    public static void refresh(final ServerPlayer player) {
        final boolean wolf = isWolf(player);
        if (LAST_BROADCAST.changed(player.getUUID(), wolf)) {
            ModNetwork.broadcastPlayerWolfVisual(player, wolf);
        }
    }

    private static void handleStartTracking(final PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer recipient
            && event.getTarget() instanceof ServerPlayer subject) {
            ModNetwork.sendPlayerWolfVisual(recipient, subject, isWolf(subject));
        }
    }

    private static void handleLogin(final PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_BROADCAST.remove(player.getUUID());
            refresh(player);
        }
    }

    private static void handleLogout(final PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_BROADCAST.remove(player.getUUID());
            ModNetwork.clearPlayerWolfVisual(player);
        }
    }

    private static boolean isWolf(final ServerPlayer player) {
        return SupernaturalState.getForm(player) == SupernaturalForm.WEREWOLF
            && SupernaturalProgression.werewolfShape(player) == WerewolfShape.WOLF;
    }

    static final class StateLedger {
        private final ConcurrentMap<UUID, Boolean> values = new ConcurrentHashMap<>();

        boolean changed(final UUID playerId, final boolean wolf) {
            final Boolean previous = values.put(playerId, wolf);
            return previous == null || previous != wolf;
        }

        void remove(final UUID playerId) {
            values.remove(playerId);
        }
    }
}
