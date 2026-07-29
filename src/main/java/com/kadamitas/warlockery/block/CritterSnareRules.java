package com.kadamitas.warlockery.block;

import java.util.Optional;

public final class CritterSnareRules {
    private CritterSnareRules() {
    }

    public static Diagnostic diagnostic(
        final CritterSnarePayload stored,
        final Optional<CritterSnarePayload> entering,
        final boolean releaseRequested
    ) {
        if (releaseRequested) {
            return stored.occupied() ? Diagnostic.READY_TO_RELEASE : Diagnostic.EMPTY;
        }
        if (stored.occupied()) {
            return Diagnostic.OCCUPIED;
        }
        return entering.filter(CritterSnarePayload::occupied).isPresent()
            ? Diagnostic.READY_TO_CAPTURE
            : Diagnostic.UNSUPPORTED_CRITTER;
    }

    public enum Diagnostic {
        EMPTY,
        OCCUPIED,
        READY_TO_CAPTURE,
        READY_TO_RELEASE,
        UNSUPPORTED_CRITTER
    }
}
