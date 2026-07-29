package com.kadamitas.warlockery.block;

final class AltarChaliceRules {
    private AltarChaliceRules() {
    }

    static Diagnostic diagnostic(final boolean filled, final boolean filler, final boolean emptyHand) {
        if (filled) {
            return Diagnostic.FILLED;
        }
        if (emptyHand) {
            return Diagnostic.EMPTY;
        }
        return filler ? Diagnostic.CAN_FILL : Diagnostic.WRONG_FILLER;
    }

    enum Diagnostic {
        EMPTY,
        WRONG_FILLER,
        CAN_FILL,
        FILLED
    }
}
