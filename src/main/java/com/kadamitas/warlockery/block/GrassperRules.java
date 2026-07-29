package com.kadamitas.warlockery.block;

public final class GrassperRules {
    private GrassperRules() {
    }

    public static Diagnostic diagnostic(final boolean occupied, final boolean heldItem) {
        if (occupied) {
            return Diagnostic.READY_TO_RETURN;
        }
        return heldItem ? Diagnostic.READY_TO_STORE : Diagnostic.MISSING_ITEM;
    }

    public enum Diagnostic {
        MISSING_ITEM,
        READY_TO_RETURN,
        READY_TO_STORE
    }
}
