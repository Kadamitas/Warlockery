package com.kadamitas.warlockery.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ChalkCircleRulesTest {
    @Test
    void supportsTraditionalSmallMediumAndLargeDiameters() {
        assertEquals(7, ChalkCircleRules.SMALL_RADIUS * 2 + 1);
        assertEquals(11, ChalkCircleRules.MEDIUM_RADIUS * 2 + 1);
        assertEquals(15, ChalkCircleRules.LARGE_RADIUS * 2 + 1);
    }
}
