package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;

public final class HazardEscapeRules {
    public enum Hazard {
        FIRE,
        LAVA,
        DROWNING,
        CONTACT
    }

    private HazardEscapeRules() {
    }

    public static boolean isFireResistant(final CreatureKind kind) {
        return kind.isDemonic();
    }

    public static boolean shouldEscape(final CreatureKind kind, final Hazard hazard) {
        if (kind == CreatureKind.CAT || kind == CreatureKind.OWL
            || kind == CreatureKind.TOAD || kind == CreatureKind.FAMILIAR) {
            return false;
        }
        return switch (hazard) {
            case FIRE, LAVA -> !isFireResistant(kind);
            case DROWNING, CONTACT -> true;
        };
    }

    public static int reconsiderationTicks(final Hazard hazard) {
        return switch (hazard) {
            case LAVA, DROWNING -> 2;
            case FIRE, CONTACT -> 5;
        };
    }

    public static double movementSpeed(final Hazard hazard) {
        return switch (hazard) {
            case LAVA, DROWNING -> 1.5;
            case FIRE, CONTACT -> 1.35;
        };
    }
}

