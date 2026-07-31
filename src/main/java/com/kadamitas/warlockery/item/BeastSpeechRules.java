package com.kadamitas.warlockery.item;

public final class BeastSpeechRules {
    private BeastSpeechRules() {
    }

    public static UtilityDecision diagnose(
        final boolean infernalCharm,
        final Audience audience,
        final boolean acceptedOffering
    ) {
        if (audience == Audience.INVALID || audience == Audience.DEMON && !infernalCharm) {
            return UtilityDecision.failure("cannot_understand");
        }
        return acceptedOffering
            ? UtilityDecision.success("trade_complete")
            : UtilityDecision.failure("wrong_offering");
    }

    public static int durability(final boolean infernalCharm) {
        return infernalCharm ? 10 : 50;
    }

    public enum Audience {
        ANIMAL,
        DEMON,
        INVALID
    }
}
