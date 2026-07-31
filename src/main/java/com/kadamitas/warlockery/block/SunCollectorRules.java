package com.kadamitas.warlockery.block;

public final class SunCollectorRules {
    public static final int MAX_STRENGTH = 15;
    public static final int DAWN = 0;
    public static final int NOON = 6_000;

    private SunCollectorRules() {
    }

    public static int nextStrength(
        final int storedStrength,
        final int detectorStrength,
        final long dayTime,
        final boolean skyVisible
    ) {
        final int stored = Math.clamp(storedStrength, 0, MAX_STRENGTH);
        final int detector = Math.clamp(detectorStrength, 0, MAX_STRENGTH);
        final long clock = Math.floorMod(dayTime, 24_000L);
        if (clock > NOON || detector == 0 || !skyVisible) {
            return clock <= NOON ? 0 : stored;
        }
        return Math.max(stored, detector);
    }

    public static boolean canCollect(final int storedStrength) {
        return storedStrength > 0;
    }

    public static float baseDamage(final int storedStrength) {
        return 4.0F + Math.clamp(storedStrength, 1, MAX_STRENGTH) * 0.4F;
    }
}
