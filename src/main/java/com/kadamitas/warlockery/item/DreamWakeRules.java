package com.kadamitas.warlockery.item;

public final class DreamWakeRules {
    private DreamWakeRules() {
    }

    public static Diagnostic diagnose(
        final boolean sleeping,
        final boolean wakingNightmare,
        final boolean dreamEffects,
        final boolean nearbyNightmare
    ) {
        return sleeping || wakingNightmare || dreamEffects || nearbyNightmare
            ? Diagnostic.READY
            : Diagnostic.NOT_DREAMING;
    }

    public enum Diagnostic {
        NOT_DREAMING,
        READY
    }
}
