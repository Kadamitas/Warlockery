package com.kadamitas.warlockery.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class UndeadMendingMobEffectTest {
    @Test
    void regenerationCadenceScalesWithAmplifier() {
        assertTrue(UndeadMendingRules.healsThisTick(600, 0));
        assertFalse(UndeadMendingRules.healsThisTick(599, 0));
        assertTrue(UndeadMendingRules.healsThisTick(600, 1));
        assertFalse(UndeadMendingRules.healsThisTick(599, 1));
        assertTrue(UndeadMendingRules.healsThisTick(600, 2));
        assertFalse(UndeadMendingRules.healsThisTick(599, 2));
        assertTrue(UndeadMendingRules.healsThisTick(1, 6));
    }

    @Test
    void healingIsUndeadOnlyAndCannotExceedMaximumHealth() {
        assertEquals(1.0F, UndeadMendingRules.healingAmount(true, 8.0F, 20.0F));
        assertEquals(0.5F, UndeadMendingRules.healingAmount(true, 19.5F, 20.0F));
        assertEquals(0.0F, UndeadMendingRules.healingAmount(true, 20.0F, 20.0F));
        assertEquals(0.0F, UndeadMendingRules.healingAmount(false, 8.0F, 20.0F));
    }
}
