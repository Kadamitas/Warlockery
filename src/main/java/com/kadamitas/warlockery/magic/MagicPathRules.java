package com.kadamitas.warlockery.magic;

public final class MagicPathRules {
    private MagicPathRules() {
    }

    public static Decision decide(
        final boolean attuned,
        final int reserve,
        final int cost,
        final boolean validTarget
    ) {
        if (!attuned) {
            return new Decision(false, Diagnostic.NOT_ATTUNED, 0);
        }
        if (!validTarget) {
            return new Decision(false, Diagnostic.INVALID_TARGET, 0);
        }
        if (reserve < cost) {
            return new Decision(false, Diagnostic.INSUFFICIENT_RESERVE, 0);
        }
        return new Decision(true, Diagnostic.READY, cost);
    }

    public static int adjustedReserve(final int current, final int delta, final int maximum) {
        if (maximum < 0) {
            throw new IllegalArgumentException("Maximum reserve must be nonnegative");
        }
        return Math.clamp(current + delta, 0, maximum);
    }

    public enum ActionKind {
        SELF,
        TARGET,
        WORLD
    }

    public enum Diagnostic {
        NOT_ATTUNED("not_attuned"),
        INVALID_TARGET("invalid_target"),
        INSUFFICIENT_RESERVE("insufficient_reserve"),
        READY("ready");

        private final String id;

        Diagnostic(final String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public record Decision(boolean success, Diagnostic diagnostic, int reserveSpent) {
        public String messageKey(final MagicPath path) {
            return "message.warlockery.magic." + path.id() + "." + diagnostic.id();
        }
    }
}
