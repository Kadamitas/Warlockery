package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ThornSpearRulesTest {
    @Test
    void guardWolfChanceUsesTheLegacyQuarterThreshold() {
        assertTrue(ThornSpearRules.summonsGuardWolf(0.249F));
        assertFalse(ThornSpearRules.summonsGuardWolf(0.25F));
    }

    @Test
    void spiritWorldDoublesSpearDamage() {
        assertEquals(12.0F, ThornSpearRules.spiritWorldDamage(6.0F, true));
        assertEquals(6.0F, ThornSpearRules.spiritWorldDamage(6.0F, false));
    }
}
