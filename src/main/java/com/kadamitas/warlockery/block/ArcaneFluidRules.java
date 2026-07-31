package com.kadamitas.warlockery.block;

public final class ArcaneFluidRules {
    private ArcaneFluidRules() {
    }

    public static Outcome hollowTearsOutcome(
        final boolean living,
        final boolean beneficiary,
        final boolean victim
    ) {
        if (!living) {
            return Outcome.NONE;
        }
        if (beneficiary) {
            return Outcome.BENEFIT;
        }
        return victim ? Outcome.HARM : Outcome.NONE;
    }

    public static Outcome flowingSpiritOutcome(
        final boolean living,
        final boolean nightmare,
        final boolean undeadOrDemonic
    ) {
        if (!living) {
            return Outcome.NONE;
        }
        return nightmare || undeadOrDemonic ? Outcome.HARM : Outcome.BENEFIT;
    }

    public enum Outcome {
        BENEFIT,
        HARM,
        NONE
    }
}
