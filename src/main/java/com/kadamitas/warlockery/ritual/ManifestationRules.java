package com.kadamitas.warlockery.ritual;

public final class ManifestationRules {
    private ManifestationRules() {
    }

    public static Decision decide(
        final boolean boundTargetPresent,
        final boolean sleeping,
        final boolean alreadyManifested
    ) {
        if (!boundTargetPresent) {
            return new Decision(false, Diagnostic.MISSING_BOUND_TARGET);
        }
        if (!sleeping) {
            return new Decision(false, Diagnostic.TARGET_AWAKE);
        }
        if (alreadyManifested) {
            return new Decision(false, Diagnostic.ALREADY_MANIFESTED);
        }
        return new Decision(true, Diagnostic.READY);
    }

    public static int durationTicks(final int baseDuration, final int participants) {
        final long duration = Math.max(20, baseDuration) + (long) Math.max(0, participants - 1) * 500L;
        return (int) Math.min(Integer.MAX_VALUE, duration);
    }

    public enum Diagnostic {
        MISSING_BOUND_TARGET("bound_sleeping_target"),
        TARGET_AWAKE("sleeping_target"),
        ALREADY_MANIFESTED("unmanifested_target"),
        READY("manifestation_ready");

        private final String id;

        Diagnostic(final String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public record Decision(boolean ready, Diagnostic diagnostic) {
    }
}
