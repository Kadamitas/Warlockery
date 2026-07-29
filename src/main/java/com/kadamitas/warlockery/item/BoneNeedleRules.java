package com.kadamitas.warlockery.item;

public final class BoneNeedleRules {
    private BoneNeedleRules() {
    }

    public static Diagnostic diagnostic(
        final boolean hexDoll,
        final boolean bound,
        final boolean remoteTarget,
        final boolean protectedTarget
    ) {
        if (!hexDoll) {
            return Diagnostic.MISSING_HEX_DOLL;
        }
        if (!bound) {
            return Diagnostic.MISSING_BINDING;
        }
        if (!remoteTarget) {
            return Diagnostic.MISSING_REMOTE_TARGET;
        }
        return protectedTarget ? Diagnostic.PROTECTED : Diagnostic.READY;
    }

    public enum Diagnostic {
        MISSING_HEX_DOLL,
        MISSING_BINDING,
        MISSING_REMOTE_TARGET,
        PROTECTED,
        READY
    }
}
