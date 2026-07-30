package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.item.UtilityDecision;

public final class DollShelfRules {
    public static final int CAPACITY = 9;

    private DollShelfRules() {
    }

    public static UtilityDecision diagnose(final int storedDolls, final boolean forcedChunk) {
        if (!forcedChunk) {
            return UtilityDecision.failure("chunk_not_loaded");
        }
        return storedDolls == 0
            ? UtilityDecision.failure("empty")
            : UtilityDecision.success("protecting");
    }

    public static boolean accepts(final boolean supportedContent, final int storedItems) {
        return supportedContent && storedItems < CAPACITY;
    }
}
