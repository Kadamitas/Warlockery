package com.kadamitas.warlockery.ritual;

public final class HellRiftRules {
    public static final int POWER_PER_SECOND = 200;
    public static final int SPAWN_INTERVAL = 60;

    private HellRiftRules() {
    }

    public static boolean active(final long now, final long expiration) {
        return now < expiration;
    }

    public static boolean drainsPower(final long now) {
        return now % 20L == 0L;
    }

    public static long nextSpawn(final long now) {
        return now + SPAWN_INTERVAL;
    }
}
