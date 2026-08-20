package com.kadamitas.warlockery.entity;

public record EntState(
    int schema,
    int anchored,
    int anchorX,
    int anchorY,
    int anchorZ,
    int grievance,
    int warnCooldownRemaining,
    int tendCooldownRemaining
) {
    public static final int SCHEMA = 1;
    public static final int WORLD_LIMIT = 30_000_000;

    public static EntState fresh(int x, int y, int z) {
        return new EntState(SCHEMA, 1, x, y, z, 0, 0, 0);
    }

    public static EntState normalize(int schema, int anchored, int x, int y, int z,
                                     int grievance, int warn, int tend, int minY, int maxY) {
        if (schema != SCHEMA) return new EntState(SCHEMA, 0, 0, 0, 0, 0, 0, 0);
        return new EntState(SCHEMA, anchored > 0 ? 1 : 0,
            Math.clamp(x, -WORLD_LIMIT, WORLD_LIMIT), Math.clamp(y, minY, maxY - 1),
            Math.clamp(z, -WORLD_LIMIT, WORLD_LIMIT), Math.clamp(grievance, 0, 100),
            Math.clamp(warn, 0, 600), Math.clamp(tend, 0, 6000));
    }

    public EntState reconcileAnchor(int x, int y, int z) {
        if (anchored == 0 || EntRules.anchorCorrupt(anchorX, anchorY, anchorZ, x, y, z)) {
            return new EntState(schema, 1, x, y, z, grievance, warnCooldownRemaining, tendCooldownRemaining);
        }
        return this;
    }

    public EntState withGrievance(int value) { return new EntState(schema, anchored, anchorX, anchorY, anchorZ, Math.clamp(value, 0, 100), warnCooldownRemaining, tendCooldownRemaining); }
    public EntState withCooldowns(int warn, int tend) { return new EntState(schema, anchored, anchorX, anchorY, anchorZ, grievance, Math.clamp(warn, 0, 600), Math.clamp(tend, 0, 6000)); }
    public EntState reanchored(int x, int y, int z) { return new EntState(schema, 1, x, y, z, grievance, warnCooldownRemaining, tendCooldownRemaining); }
    public static int migrateLegacyCooldown(long expiry, long currentGameTime) {
        if (expiry <= 0) return 0;
        return (int) Math.clamp(expiry - currentGameTime, 0L, 6000L);
    }
}
