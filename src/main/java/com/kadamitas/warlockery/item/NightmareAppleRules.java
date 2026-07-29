package com.kadamitas.warlockery.item;

public final class NightmareAppleRules {
    private NightmareAppleRules() {
    }

    public static Outcome outcome(final boolean serverSide, final boolean livingConsumer, final boolean spawnSpace) {
        if (!serverSide || !livingConsumer) {
            return Outcome.NO_ACTION;
        }
        return spawnSpace ? Outcome.NIGHTMARE_SPAWNED : Outcome.NIGHTMARE_MARKED;
    }

    public enum Outcome {
        NO_ACTION,
        NIGHTMARE_MARKED,
        NIGHTMARE_SPAWNED
    }
}
