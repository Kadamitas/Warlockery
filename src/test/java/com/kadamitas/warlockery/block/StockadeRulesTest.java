package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class StockadeRulesTest {
    @Test
    void onlyARealFallOntoTheSpikesImpales() {
        assertFalse(StockadeRules.shouldImpale(true, true, false, false, 0.5F));
        assertFalse(StockadeRules.shouldImpale(true, true, true, false, 4.0F));
        assertFalse(StockadeRules.shouldImpale(true, true, false, true, 4.0F));
        assertTrue(StockadeRules.shouldImpale(true, true, false, false, 1.0F));
    }

    @Test
    void higherFallsDealMoreDamageWithinAStableCap() {
        assertEquals(3.0F, StockadeRules.damage(1.0F));
        assertEquals(10.0F, StockadeRules.damage(30.0F));
    }
}
