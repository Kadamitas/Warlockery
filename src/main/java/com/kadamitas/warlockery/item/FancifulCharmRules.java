package com.kadamitas.warlockery.item;

public final class FancifulCharmRules {
    private FancifulCharmRules() {
    }

    public static Outcome resolve(final boolean nightmareAttack, final boolean charmCarried) {
        if (!nightmareAttack) {
            return Outcome.NOT_NIGHTMARE;
        }
        return charmCarried ? Outcome.BLOCKED : Outcome.SIDE_EFFECTS;
    }

    public enum Outcome {
        NOT_NIGHTMARE,
        BLOCKED,
        SIDE_EFFECTS
    }
}
