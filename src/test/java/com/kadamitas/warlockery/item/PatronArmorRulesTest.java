package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PatronArmorRulesTest {
    @Test
    void girdleRestoresFixedLegacyUnarmedDamage() {
        assertEquals(4.0F, PatronArmorRules.unarmedDamage(1.0F, true, true));
        assertEquals(7.0F, PatronArmorRules.unarmedDamage(7.0F, true, true));
        assertEquals(1.0F, PatronArmorRules.unarmedDamage(1.0F, false, true));
    }

    @Test
    void resistanceNeedsOppositePatronGarments() {
        assertTrue(PatronArmorRules.sharesResistance(true, false, false, true));
        assertTrue(PatronArmorRules.sharesResistance(false, true, true, false));
        assertFalse(PatronArmorRules.sharesResistance(true, false, true, false));
    }
}
