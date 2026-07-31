package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class HobgoblinMiningRulesTest {
    @Test
    void enhancedToolIsSixtyTimesFasterAndFindsGobliniteFiveTimesAsOften() {
        assertEquals(1_200, HobgoblinMiningRules.STANDARD.cooldownTicks());
        assertEquals(20, HobgoblinMiningRules.ENHANCED.cooldownTicks());
        assertEquals(0.01F, HobgoblinMiningRules.STANDARD.gobliniteChance());
        assertEquals(0.05F, HobgoblinMiningRules.ENHANCED.gobliniteChance());
        assertEquals(HobgoblinMiningRules.STANDARD, HobgoblinMiningRules.profile(false));
        assertEquals(HobgoblinMiningRules.ENHANCED, HobgoblinMiningRules.profile(true));
    }

    @Test
    void gobliniteChanceUsesStableExclusiveBoundaries() {
        assertTrue(HobgoblinMiningRules.findsGoblinite(HobgoblinMiningRules.STANDARD, 0.009F));
        assertFalse(HobgoblinMiningRules.findsGoblinite(HobgoblinMiningRules.STANDARD, 0.01F));
        assertTrue(HobgoblinMiningRules.findsGoblinite(HobgoblinMiningRules.ENHANCED, 0.049F));
        assertFalse(HobgoblinMiningRules.findsGoblinite(HobgoblinMiningRules.ENHANCED, 0.05F));
    }

    @Test
    void standardToolsNeverAutoSmelt() {
        assertEquals(0, HobgoblinMiningRules.autoSmeltMultiplier(HobgoblinMiningRules.STANDARD, 0.0F, 0.0F));
    }

    @Test
    void enhancedToolAutoSmeltsHalfTheTimeWithOneToThreeOutputs() {
        assertEquals(1, HobgoblinMiningRules.autoSmeltMultiplier(HobgoblinMiningRules.ENHANCED, 0.0F, 0.0F));
        assertEquals(2, HobgoblinMiningRules.autoSmeltMultiplier(HobgoblinMiningRules.ENHANCED, 0.25F, 0.34F));
        assertEquals(3, HobgoblinMiningRules.autoSmeltMultiplier(HobgoblinMiningRules.ENHANCED, 0.49F, 0.99F));
        assertEquals(0, HobgoblinMiningRules.autoSmeltMultiplier(HobgoblinMiningRules.ENHANCED, 0.5F, 0.0F));
    }

    @Test
    void invalidProfilesAndRollsAreRejected() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new HobgoblinMiningRules.MiningProfile(0, 0.01F, 0.5F, 3)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> HobgoblinMiningRules.findsGoblinite(HobgoblinMiningRules.ENHANCED, 1.0F)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> HobgoblinMiningRules.autoSmeltMultiplier(HobgoblinMiningRules.ENHANCED, -0.01F, 0.5F)
        );
    }
}
