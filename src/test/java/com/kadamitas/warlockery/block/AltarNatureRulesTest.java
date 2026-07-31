package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class AltarNatureRulesTest {
    @Test
    void repeatedNaturalSourcesHaveDiminishingReturns() {
        assertEquals(4, AltarNatureRules.contribution(4, 0));
        assertEquals(4, AltarNatureRules.contribution(4, 15));
        assertEquals(1, AltarNatureRules.contribution(4, 16));
        assertEquals(0, AltarNatureRules.contribution(4, 64));
        assertEquals(0, AltarNatureRules.contribution(0, 0));
    }
}
