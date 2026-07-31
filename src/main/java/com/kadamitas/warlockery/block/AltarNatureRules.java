package com.kadamitas.warlockery.block;

public final class AltarNatureRules {
    private static final int FULL_VALUE_LIMIT = 16;
    private static final int DIMINISHED_VALUE_LIMIT = 64;

    private AltarNatureRules() {
    }

    public static int contribution(final int sourceValue, final int existingSourcesOfKind) {
        if (sourceValue <= 0 || existingSourcesOfKind >= DIMINISHED_VALUE_LIMIT) {
            return 0;
        }
        return existingSourcesOfKind < FULL_VALUE_LIMIT ? sourceValue : Math.max(1, sourceValue / 4);
    }

    public enum Source {
        HEART,
        LOG,
        LEAF,
        FLOWER,
        SAPLING,
        CROP,
        GROUND,
        WATER,
        OTHER_NATURAL
    }
}
