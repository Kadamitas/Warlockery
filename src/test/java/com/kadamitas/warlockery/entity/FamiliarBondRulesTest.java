package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import org.junit.jupiter.api.Test;

final class FamiliarBondRulesTest {
    @Test
    void classicFamiliarsShareMoreDamageWhenNearby() {
        assertEquals(0.1F, FamiliarBondRules.transferredDamageFraction(true, 256.0));
        assertEquals(0.01F, FamiliarBondRules.transferredDamageFraction(true, 256.01));
        assertEquals(0.01F, FamiliarBondRules.transferredDamageFraction(false, 1.0));
    }

    @Test
    void catOwlAndToadReceiveClassicFamiliarRules() {
        assertTrue(FamiliarBondRules.isClassicFamiliar(CreatureKind.CAT));
        assertTrue(FamiliarBondRules.isClassicFamiliar(CreatureKind.OWL));
        assertTrue(FamiliarBondRules.isClassicFamiliar(CreatureKind.TOAD));
        assertFalse(FamiliarBondRules.isClassicFamiliar(CreatureKind.FAMILIAR));
    }

    @Test
    void covenRecruitmentStopsAtSixMages() {
        assertTrue(FamiliarBondRules.canRecruitCovenMage(5));
        assertFalse(FamiliarBondRules.canRecruitCovenMage(6));
        assertFalse(FamiliarBondRules.canRecruitCovenMage(-1));
    }

    @Test
    void theDedicatedCircleMageDecisionReusesTheExactCapSixPrerequisite() {
        assertEquals(FamiliarBondRules.MAX_COVEN_MAGES, CircleMageRules.MAX_COVEN_MAGES);
        final java.util.UUID player = new java.util.UUID(0L, 1L);
        assertEquals(CircleMageRules.RecruitmentResult.RECRUITED,
            CircleMageRules.recruitmentDecision(
                java.util.Optional.empty(), player, true, true,
                FamiliarBondRules.MAX_COVEN_MAGES - 1));
        assertEquals(CircleMageRules.RecruitmentResult.COVEN_FULL,
            CircleMageRules.recruitmentDecision(
                java.util.Optional.empty(), player, true, true,
                FamiliarBondRules.MAX_COVEN_MAGES));
    }

    @Test
    void sameOwnerAndDifferentOwnerRecruitmentOrderingIsExplicit() {
        final java.util.UUID owner = new java.util.UUID(0L, 1L);
        final java.util.UUID stranger = new java.util.UUID(0L, 2L);
        assertEquals(CircleMageRules.RecruitmentResult.ALREADY_BOUND_TO_PLAYER,
            CircleMageRules.recruitmentDecision(
                java.util.Optional.of(owner), owner, true, false, 6));
        assertEquals(CircleMageRules.RecruitmentResult.BOUND_ELSEWHERE,
            CircleMageRules.recruitmentDecision(
                java.util.Optional.of(owner), stranger, true, true, 0));
        assertFalse(CircleMageRules.recruitmentDecision(
            java.util.Optional.of(owner), owner, true, false, 6).consumesOffering(),
            "a same-owner repeat consumes nothing");
        assertFalse(CircleMageRules.recruitmentDecision(
            java.util.Optional.of(owner), stranger, true, true, 0).consumesOffering(),
            "a different owner cannot steal the Mage or spend an offering");
    }
}
