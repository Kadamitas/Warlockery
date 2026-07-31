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
}
