package com.kadamitas.warlockery.ritual;

public final class HexbreakingRules {
    private HexbreakingRules() {
    }

    public static Decision decide(
        final boolean boundTarget,
        final boolean selectedHexPresent,
        final int altarPower,
        final int requiredPower
    ) {
        if (!boundTarget) {
            return new Decision(false, Diagnostic.MISSING_BOUND_TARGET);
        }
        if (!selectedHexPresent) {
            return new Decision(false, Diagnostic.SELECTED_HEX_ABSENT);
        }
        if (altarPower < requiredPower) {
            return new Decision(false, Diagnostic.INSUFFICIENT_POWER);
        }
        return new Decision(true, Diagnostic.READY);
    }

    public enum Diagnostic {
        MISSING_BOUND_TARGET("bound_hex_target"),
        SELECTED_HEX_ABSENT("selected_hex_present"),
        INSUFFICIENT_POWER("altar_power"),
        READY("hexbreaking_ready");

        private final String id;

        Diagnostic(final String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public record Decision(boolean ready, Diagnostic diagnostic) {
    }
}
