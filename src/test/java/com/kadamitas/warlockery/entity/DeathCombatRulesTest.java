package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class DeathCombatRulesTest {
    @Test
    void deathUsesItsLegacyHealthDamageAndIncomingCap() {
        assertEquals(1_000.0, DeathCombatRules.MAX_HEALTH);
        assertEquals(15.0F, DeathCombatRules.meleeDamage(100.0F), 0.0001F);
        assertEquals(3.0F, DeathCombatRules.meleeDamage(20.0F), 0.0001F);
        assertEquals(15.0F, DeathCombatRules.capIncoming(80.0F));
        assertEquals(4.0F, DeathCombatRules.capIncoming(4.0F));
    }
}
