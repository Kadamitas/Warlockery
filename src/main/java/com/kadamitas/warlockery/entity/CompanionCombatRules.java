package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.util.Set;

public final class CompanionCombatRules {
    private static final Set<CreatureKind> DEDICATED_MELEE = Set.of(
        CreatureKind.CAT,
        CreatureKind.OWL,
        CreatureKind.TOAD
    );

    private CompanionCombatRules() {
    }

    public static boolean requiresDedicatedMeleeGoal(final CreatureKind kind) {
        return DEDICATED_MELEE.contains(kind);
    }
}
