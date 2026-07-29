package com.kadamitas.warlockery.block;

public final class FetishRules {
    public static final int RADIUS = 10;
    public static final int TICK_INTERVAL = 20;
    public static final int SENTINEL_LIFETIME = 20 * 60;

    private FetishRules() {
    }

    public static Diagnostic diagnostic(
        final boolean enabled,
        final FetishMode mode,
        final boolean alarm,
        final boolean hasFocus,
        final boolean emptyHand
    ) {
        return diagnostic(true, enabled, mode, alarm, hasFocus, emptyHand);
    }

    public static Diagnostic diagnostic(
        final boolean bound,
        final boolean enabled,
        final FetishMode mode,
        final boolean alarm,
        final boolean hasFocus,
        final boolean emptyHand
    ) {
        if (!bound) {
            return Diagnostic.UNBOUND;
        }
        if (emptyHand) {
            return enabled
                ? mode == FetishMode.SHRIEKING && alarm ? Diagnostic.ALARM : Diagnostic.READY
                : Diagnostic.DISABLED;
        }
        if (!hasFocus) {
            return Diagnostic.WRONG_FOCUS;
        }
        return enabled ? Diagnostic.WILL_DISABLE : Diagnostic.WILL_ENABLE;
    }

    public static boolean shouldAffect(final boolean enabled, final boolean alive, final boolean immune) {
        return enabled && alive && !immune;
    }

    public enum Diagnostic {
        UNBOUND,
        DISABLED,
        WRONG_FOCUS,
        WILL_ENABLE,
        WILL_DISABLE,
        READY,
        ALARM
    }
}
