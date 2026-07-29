package com.kadamitas.warlockery.item;

public final class NecromancyRules {
    private NecromancyRules() {
    }

    public static UtilityDecision command(final boolean commandable, final boolean boundElsewhere) {
        if (!commandable) {
            return UtilityDecision.failure("invalid_target");
        }
        return boundElsewhere
            ? UtilityDecision.failure("bound_elsewhere")
            : UtilityDecision.success("commanded");
    }

    public static UtilityDecision spectralStone(final boolean spectral, final int stored, final int capacity) {
        if (!spectral) {
            return UtilityDecision.failure("not_spectral");
        }
        return stored >= capacity
            ? UtilityDecision.failure("full")
            : UtilityDecision.success("captured");
    }
}
