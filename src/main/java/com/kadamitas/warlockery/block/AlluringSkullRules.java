package com.kadamitas.warlockery.block;

final class AlluringSkullRules {
    private AlluringSkullRules() {
    }

    static Diagnostic diagnostic(final boolean active, final boolean activator, final boolean emptyHand) {
        if (emptyHand) {
            return active ? Diagnostic.ACTIVE : Diagnostic.INACTIVE;
        }
        if (!activator) {
            return Diagnostic.WRONG_FOCUS;
        }
        return active ? Diagnostic.WILL_DISABLE : Diagnostic.WILL_ENABLE;
    }

    static boolean canLure(final boolean active, final boolean tagged, final boolean alive) {
        return active && tagged && alive;
    }

    enum Diagnostic {
        INACTIVE,
        ACTIVE,
        WRONG_FOCUS,
        WILL_ENABLE,
        WILL_DISABLE
    }
}
