package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.util.Set;

public final class GoblinLifecycleRules {
    public static final float BABY_DIMENSION_SCALE = 0.55F;
    public static final float BABY_RENDER_SCALE = 0.62F;
    private static final Set<CreatureKind> REPRODUCTIVE_KINDS = Set.of(CreatureKind.GOBLIN, CreatureKind.HOBGOBLIN);

    private GoblinLifecycleRules() {
    }

    public static boolean canReproduce(final CreatureKind first, final CreatureKind second) {
        return first == second && REPRODUCTIVE_KINDS.contains(first);
    }

    public static boolean canSpawnNaturally(final CreatureKind kind, final boolean humanVillage) {
        return kind != CreatureKind.HOBGOBLIN || !humanVillage;
    }

    public static boolean fleesHumanVillagers(final CreatureKind kind) {
        return kind == CreatureKind.HOBGOBLIN;
    }

}
