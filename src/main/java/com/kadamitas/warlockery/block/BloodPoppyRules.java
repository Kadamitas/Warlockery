package com.kadamitas.warlockery.block;

public final class BloodPoppyRules {
    public static final long SAMPLE_LIFETIME_TICKS = 1_200L;

    private BloodPoppyRules() {
    }

    public static Diagnostic diagnostic(
        final boolean samplingVial,
        final boolean recentVictim,
        final boolean safeHarvestTool
    ) {
        if (samplingVial) {
            return recentVictim ? Diagnostic.SAMPLE_READY : Diagnostic.MISSING_SAMPLE;
        }
        return safeHarvestTool ? Diagnostic.SAFE_HARVEST : Diagnostic.UNSAFE_HARVEST;
    }

    public static boolean sampleIsFresh(final long currentTime, final long markedTime) {
        return markedTime >= 0L && currentTime - markedTime <= SAMPLE_LIFETIME_TICKS;
    }

    public enum Diagnostic {
        MISSING_SAMPLE,
        SAMPLE_READY,
        SAFE_HARVEST,
        UNSAFE_HARVEST
    }
}
