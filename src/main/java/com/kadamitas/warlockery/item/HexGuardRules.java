package com.kadamitas.warlockery.item;

public final class HexGuardRules {
    private HexGuardRules() {
    }

    public static Resolution resolve(
        final boolean boundGuardPresent,
        final boolean attackerPresent,
        final boolean attackerIsProtectedTarget
    ) {
        if (!boundGuardPresent) {
            return new Resolution(false, false, Diagnostic.UNPROTECTED);
        }
        final boolean retaliates = attackerPresent && !attackerIsProtectedTarget;
        return new Resolution(true, retaliates, retaliates ? Diagnostic.BLOCKED_AND_RETALIATED : Diagnostic.BLOCKED);
    }

    public static boolean hasRequiredGuards(final int available, final int required) {
        if (available < 0 || required < 1) {
            throw new IllegalArgumentException("Hex guard counts must be nonnegative and require at least one doll");
        }
        return available >= required;
    }

    public enum Diagnostic {
        UNPROTECTED("unprotected"),
        BLOCKED("blocked"),
        BLOCKED_AND_RETALIATED("blocked_and_retaliated");

        private final String id;

        Diagnostic(final String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public record Resolution(boolean blocked, boolean retaliates, Diagnostic diagnostic) {
        public String messageKey() {
            return "message.warlockery.doll.hex_guard." + diagnostic.id();
        }
    }
}
