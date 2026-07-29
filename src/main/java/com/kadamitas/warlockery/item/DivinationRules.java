package com.kadamitas.warlockery.item;

public final class DivinationRules {
    private DivinationRules() {
    }

    public static UtilityDecision crystalBall(final boolean catalyst, final boolean boundTarget) {
        if (boundTarget) {
            return UtilityDecision.success("remote_view");
        }
        return catalyst
            ? UtilityDecision.success("prediction")
            : UtilityDecision.failure("missing_focus");
    }

    public static UtilityDecision seerStone(final boolean ownerKnown, final int activePaths) {
        if (!ownerKnown) {
            return UtilityDecision.failure("missing_owner");
        }
        return UtilityDecision.success(activePaths == 0 ? "mundane" : "progression");
    }

    public static UtilityDecision babaYagaEncounter(
        final boolean summoner,
        final boolean night,
        final boolean alreadyPresent
    ) {
        if (!summoner) {
            return UtilityDecision.failure("missing_encounter_catalyst");
        }
        if (!night) {
            return UtilityDecision.failure("night_required");
        }
        return alreadyPresent
            ? UtilityDecision.failure("encounter_active")
            : UtilityDecision.success("baba_yaga_arrives");
    }

    public enum Prediction {
        STORM,
        FULL_MOON,
        NIGHT,
        DAY
    }
}
