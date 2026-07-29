package com.kadamitas.warlockery.mutation;

public final class MutatingSprigRules {
    private MutatingSprigRules() {
    }

    public static Transformation transformation(
        final boolean dirt,
        final boolean mycelium,
        final boolean clay,
        final boolean immersed
    ) {
        if (immersed && dirt) {
            return Transformation.CLAY;
        }
        if (immersed && clay) {
            return Transformation.DIRT;
        }
        if (!immersed && dirt) {
            return Transformation.MYCELIUM;
        }
        if (!immersed && mycelium) {
            return Transformation.DIRT;
        }
        return Transformation.NONE;
    }

    public enum Transformation {
        NONE,
        DIRT,
        MYCELIUM,
        CLAY
    }
}
