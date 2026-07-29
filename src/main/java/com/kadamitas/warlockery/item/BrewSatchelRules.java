package com.kadamitas.warlockery.item;

public final class BrewSatchelRules {
    private BrewSatchelRules() {
    }

    public static Diagnostic diagnose(
        final boolean hasSelection,
        final boolean taggedBrew,
        final boolean throwable
    ) {
        if (!hasSelection) {
            return Diagnostic.EMPTY;
        }
        return taggedBrew && throwable ? Diagnostic.READY : Diagnostic.INVALID_BREW;
    }

    public enum Diagnostic {
        EMPTY,
        INVALID_BREW,
        READY
    }
}
