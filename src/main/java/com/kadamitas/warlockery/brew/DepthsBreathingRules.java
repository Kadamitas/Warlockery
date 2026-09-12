package com.kadamitas.warlockery.brew;

final class DepthsBreathingRules {
    private DepthsBreathingRules() {
    }

    static Breath tick(final boolean submerged, final int maximumAir, final int currentAir, final int storedAir) {
        if (submerged) {
            return new Breath(maximumAir, false);
        }
        final int remaining = Math.min(currentAir, storedAir) - 40;
        return new Breath(Math.max(0, remaining), remaining <= -20);
    }

    record Breath(int air, boolean drowning) {
    }
}
