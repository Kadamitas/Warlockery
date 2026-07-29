package com.kadamitas.warlockery.item;

final class AnointingPasteRules {
    private AnointingPasteRules() {
    }

    static Diagnostic diagnostic(final boolean anointable, final boolean alreadyAnointed) {
        if (alreadyAnointed) {
            return Diagnostic.ALREADY_ANOINTED;
        }
        return anointable ? Diagnostic.READY : Diagnostic.NOT_ANOINTABLE;
    }

    enum Diagnostic {
        NOT_ANOINTABLE,
        ALREADY_ANOINTED,
        READY
    }
}
