package com.kadamitas.warlockery.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class VillageLegacyParityTest {
    @Test
    void guardsRequireHeroicVillageStandingAndLeatherTunic() {
        assertTrue(VillageGuardRules.canCommission(true, true, true, true));
        assertFalse(VillageGuardRules.canCommission(false, true, true, true));
        assertFalse(VillageGuardRules.canCommission(true, false, true, true));
        assertFalse(VillageGuardRules.canCommission(true, true, false, true));
        assertFalse(VillageGuardRules.canCommission(true, true, true, false));
    }

    @Test
    void hobgoblinHutsStayOutsideVillagesAndRemainSmallCamps() {
        assertTrue(HobgoblinCampRules.canFound(false, false, true, 32));
        assertFalse(HobgoblinCampRules.canFound(true, false, true, 32));
        assertFalse(HobgoblinCampRules.canFound(false, true, true, 32));
        assertEquals(2, HobgoblinCampRules.residents(0));
        assertEquals(4, HobgoblinCampRules.residents(2));
    }

}
