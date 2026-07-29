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
