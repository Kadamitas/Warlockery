package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import org.junit.jupiter.api.Test;

final class GoblinBossRulesTest {
    @Test
    void pairedPatronsShieldOneAnotherAtDocumentedDistances() {
        assertEquals(0.2F, GoblinBossRules.pairedDamageMultiplier(36.0));
        assertEquals(0.5F, GoblinBossRules.pairedDamageMultiplier(81.0));
        assertEquals(0.8F, GoblinBossRules.pairedDamageMultiplier(256.0));
        assertEquals(1.0F, GoblinBossRules.pairedDamageMultiplier(256.01));
    }

    @Test
    void forgewardenAndStonebrokerAreCounterparts() {
        assertEquals(400.0, GoblinBossRules.combatProfile(CreatureKind.FORGEWARDEN).orElseThrow().health());
        assertEquals(400.0, GoblinBossRules.combatProfile(CreatureKind.STONEBROKER).orElseThrow().health());
        assertEquals(CreatureKind.STONEBROKER, GoblinBossRules.counterpart(CreatureKind.FORGEWARDEN).orElseThrow());
        assertEquals(CreatureKind.FORGEWARDEN, GoblinBossRules.counterpart(CreatureKind.STONEBROKER).orElseThrow());
        assertTrue(GoblinBossRules.counterpart(CreatureKind.GOBLIN).isEmpty());
    }

    @Test
    void pairAlsoStrengthensCloseMeleeAttacks() {
        assertEquals(12.0F, GoblinBossRules.pairedAttackBonus(16.0));
        assertEquals(8.0F, GoblinBossRules.pairedAttackBonus(64.0));
        assertEquals(4.0F, GoblinBossRules.pairedAttackBonus(196.0));
        assertEquals(0.0F, GoblinBossRules.pairedAttackBonus(400.0));
    }
}
