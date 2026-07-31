package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class HedgeCroneHatRulesTest {
    @Test
    void evasionRequiresAnInfusionAndUsesAQuarterChance() {
        assertFalse(HedgeCroneHatRules.shouldEvade(false, 0.0F));
        assertTrue(HedgeCroneHatRules.shouldEvade(true, 0.249F));
        assertFalse(HedgeCroneHatRules.shouldEvade(true, 0.25F));
    }
}
