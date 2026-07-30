package com.kadamitas.warlockery.item;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;

public final class DollMendingSchedule {
    public static final int INTERVAL_TICKS = 20;
    private static final Map<MinecraftServer, DollMendingSchedule> SERVERS = Collections.synchronizedMap(
        new WeakHashMap<>()
    );
    private final Map<Claim, Integer> claims = new HashMap<>();
    private int lastShelfScanCycle = Integer.MIN_VALUE;

    public static DollMendingSchedule forServer(final MinecraftServer server) {
        synchronized (SERVERS) {
            return SERVERS.computeIfAbsent(Objects.requireNonNull(server, "server"), ignored -> new DollMendingSchedule());
        }
    }

    public static boolean isMendingTick(final int serverTick) {
        return Math.floorMod(serverTick, INTERVAL_TICKS) == 0;
    }

    public synchronized boolean beginShelfScan(final int serverTick) {
        if (!isMendingTick(serverTick)) {
            return false;
        }
        final int cycle = Math.floorDiv(serverTick, INTERVAL_TICKS);
        if (lastShelfScanCycle == cycle) {
            return false;
        }
        lastShelfScanCycle = cycle;
        discardOldClaims(cycle);
        return true;
    }

    public synchronized boolean claim(
        final UUID playerId,
        final DollAbility.RepairTarget target,
        final int serverTick
    ) {
        final int cycle = Math.floorDiv(serverTick, INTERVAL_TICKS);
        discardOldClaims(cycle);
        final Claim claim = new Claim(playerId, target);
        if (claims.getOrDefault(claim, Integer.MIN_VALUE) == cycle) {
            return false;
        }
        claims.put(claim, cycle);
        return true;
    }

    private void discardOldClaims(final int currentCycle) {
        claims.entrySet().removeIf(entry -> entry.getValue() != currentCycle
            && entry.getValue() != currentCycle - 1);
    }

    private record Claim(UUID playerId, DollAbility.RepairTarget target) {
        private Claim {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(target, "target");
        }
    }
}
