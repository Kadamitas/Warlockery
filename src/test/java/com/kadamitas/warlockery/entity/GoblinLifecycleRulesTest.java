package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import org.junit.jupiter.api.Test;

final class GoblinLifecycleRulesTest {
    @Test
    void reproductionKeepsEachGoblinFamilyDistinct() {
        assertTrue(GoblinLifecycleRules.canReproduce(CreatureKind.GOBLIN, CreatureKind.GOBLIN));
        assertTrue(GoblinLifecycleRules.canReproduce(CreatureKind.HOBGOBLIN, CreatureKind.HOBGOBLIN));
        assertFalse(GoblinLifecycleRules.canReproduce(CreatureKind.GOBLIN, CreatureKind.HOBGOBLIN));
        assertFalse(GoblinLifecycleRules.canReproduce(CreatureKind.STONEBROKER, CreatureKind.STONEBROKER));
    }

    @Test
    void babiesUseSmallerPhysicalAndRenderedForms() {
        assertTrue(GoblinLifecycleRules.BABY_DIMENSION_SCALE > 0.0F);
        assertTrue(GoblinLifecycleRules.BABY_DIMENSION_SCALE < 1.0F);
        assertTrue(GoblinLifecycleRules.BABY_RENDER_SCALE > GoblinLifecycleRules.BABY_DIMENSION_SCALE);
        assertTrue(GoblinLifecycleRules.BABY_RENDER_SCALE < 1.0F);
    }

    @Test
    void onlyOrdinaryHobgoblinsAvoidHumanVillagesAndVillagers() {
        assertFalse(GoblinLifecycleRules.canSpawnNaturally(CreatureKind.HOBGOBLIN, true));
        assertTrue(GoblinLifecycleRules.canSpawnNaturally(CreatureKind.HOBGOBLIN, false));
        assertTrue(GoblinLifecycleRules.canSpawnNaturally(CreatureKind.GOBLIN, true));
        assertTrue(GoblinLifecycleRules.fleesHumanVillagers(CreatureKind.HOBGOBLIN));
        assertFalse(GoblinLifecycleRules.fleesHumanVillagers(CreatureKind.GOBLIN));
        assertFalse(GoblinLifecycleRules.fleesHumanVillagers(CreatureKind.STONEBROKER));
    }
}
