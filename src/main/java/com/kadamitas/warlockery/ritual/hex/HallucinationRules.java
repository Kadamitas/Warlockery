package com.kadamitas.warlockery.ritual.hex;

public final class HallucinationRules {
    public static final ThreatProfile INSANITY = new ThreatProfile(200, 2, 400);
    public static final ThreatProfile WAKING_NIGHTMARE = new ThreatProfile(100, 3, 500);

    private HallucinationRules() {
    }

    public static boolean shouldSpawn(final ThreatProfile profile, final int tickCount, final int activeThreats) {
        return shouldSpawn(profile, tickCount, activeThreats, true);
    }

    public static boolean shouldSpawn(
        final ThreatProfile profile,
        final int tickCount,
        final int activeThreats,
        final boolean eligible
    ) {
        return eligible
            && activeThreats < profile.maximumThreats()
            && Math.floorMod(tickCount, profile.intervalTicks()) == 0;
    }

    public record ThreatProfile(int intervalTicks, int maximumThreats, int lifetimeTicks) {
        public ThreatProfile {
            if (intervalTicks < 1 || maximumThreats < 1 || lifetimeTicks < 1) {
                throw new IllegalArgumentException("Invalid hallucination threat profile");
            }
        }
    }
}
