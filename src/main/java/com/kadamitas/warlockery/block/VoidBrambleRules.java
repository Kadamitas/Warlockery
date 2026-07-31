package com.kadamitas.warlockery.block;

import java.util.UUID;

public final class VoidBrambleRules {
    public static final int MAGIC_SUPPRESSION_RADIUS = 32;
    public static final int TELEPORT_RADIUS = 500;
    public static final int TELEPORT_COOLDOWN_TICKS = 100;

    private VoidBrambleRules() {
    }

    public static boolean suppressesMagic(final double squaredDistance) {
        return squaredDistance <= (double) MAGIC_SUPPRESSION_RADIUS * MAGIC_SUPPRESSION_RADIUS;
    }

    public static int targetCoordinate(final int origin, final int offset) {
        if (Math.abs(offset) > TELEPORT_RADIUS) {
            throw new IllegalArgumentException("Void Bramble offsets must stay inside its teleport radius");
        }
        return origin + offset;
    }

    public static boolean teleportReady(final long gameTime, final long cooldownUntil) {
        return gameTime >= cooldownUntil;
    }

    public static boolean canBreak(final UUID owner, final UUID player, final boolean creative) {
        return creative || owner == null || owner.equals(player);
    }
}
