package com.kadamitas.warlockery.item;

public final class ParasyticLouseRules {
    private ParasyticLouseRules() {
    }

    public static InjectionTarget target(final boolean effectPresent, final boolean beneficial, final boolean attackerPresent) {
        if (!effectPresent) {
            return InjectionTarget.NONE;
        }
        if (beneficial) {
            return InjectionTarget.WEARER;
        }
        return attackerPresent ? InjectionTarget.ATTACKER : InjectionTarget.NONE;
    }

    public enum InjectionTarget {
        NONE,
        WEARER,
        ATTACKER
    }
}
