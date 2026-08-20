package com.kadamitas.warlockery.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class BrazierEffectRulesTest {
    @Test
    void sustainedEffectsFireForEveryCrossedInterval() {
        assertEquals(0, BrazierEffectRules.scheduledActivations(0, 4, 5));
        assertEquals(1, BrazierEffectRules.scheduledActivations(4, 5, 5));
        assertEquals(2, BrazierEffectRules.scheduledActivations(4, 12, 5));
        assertEquals(0, BrazierEffectRules.scheduledActivations(12, 4, 5));
    }

    @Test
    void activationTicksPreserveCadenceAndBoundCatchUpWork() {
        assertEquals(java.util.List.of(5, 10), BrazierEffectRules.activationTicks(4, 12, 5));
        assertEquals(java.util.List.of(), BrazierEffectRules.activationTicks(12, 4, 5));
        assertEquals(java.util.List.of(), BrazierEffectRules.activationTicks(0, 12, 0));

        final var bounded = BrazierEffectRules.activationTicks(0, Integer.MAX_VALUE, 1);
        assertEquals(BrazierEffectRules.MAX_CATCH_UP_ACTIVATIONS, bounded.size());
        assertEquals(Integer.MAX_VALUE, bounded.getLast());
    }

    @Test
    void effectRadiusUsesTheWitcherySquaredDistanceBoundary() {
        assertTrue(BrazierEffectRules.withinRadiusSquared(4.0, 0.0, 0.0, 16.0));
        assertTrue(BrazierEffectRules.withinRadiusSquared(0.0, 0.0, 6.0, 36.0));
        assertFalse(BrazierEffectRules.withinRadiusSquared(4.0, 0.01, 0.0, 16.0));
        assertFalse(BrazierEffectRules.withinRadiusSquared(0.0, 0.0, 6.01, 36.0));
    }

    @Test
    void drainGrowthChecksOneRotatingHeightEveryFiveTicks() {
        assertEquals(0, BrazierEffectRules.drainGrowthOffsetY(0));
        assertEquals(1, BrazierEffectRules.drainGrowthOffsetY(5));
        assertEquals(5, BrazierEffectRules.drainGrowthOffsetY(25));
        assertEquals(0, BrazierEffectRules.drainGrowthOffsetY(30));
    }

    @Test
    void burnCadenceUsesTheLoadedWorldTick() {
        assertTrue(BrazierEffectRules.shouldActivate(300L, 1));
        assertTrue(BrazierEffectRules.shouldActivate(300L, 5));
        assertTrue(BrazierEffectRules.shouldActivate(300L, 60));
        assertTrue(BrazierEffectRules.shouldActivate(300L, 100));
        assertFalse(BrazierEffectRules.shouldActivate(301L, 5));
        assertFalse(BrazierEffectRules.shouldActivate(300L, 0));
    }

    @Test
    void summonAxisOffsetsMatchWitcheryRanges() {
        assertEquals(java.util.List.of(-2, -1, 2), java.util.stream.IntStream.range(0, 3)
            .map(roll -> BrazierEffectRules.summonAxisOffset(roll, 1, 2))
            .boxed()
            .toList());
        assertEquals(java.util.List.of(-10, -9, -8, -7, -6, 7, 8, 9, 10),
            java.util.stream.IntStream.range(0, 9)
                .map(roll -> BrazierEffectRules.summonAxisOffset(roll, 6, 10))
                .boxed()
                .toList());
    }

    @Test
    void drainGrowthHealingIsExclusiveToInjuredLivingUndead() {
        assertTrue(BrazierEffectRules.canReceiveDrainGrowthHealing(true, true, 10.0F, 20.0F));
        assertFalse(BrazierEffectRules.canReceiveDrainGrowthHealing(true, false, 10.0F, 20.0F));
        assertFalse(BrazierEffectRules.canReceiveDrainGrowthHealing(false, true, 10.0F, 20.0F));
        assertFalse(BrazierEffectRules.canReceiveDrainGrowthHealing(true, true, 20.0F, 20.0F));
    }

    @Test
    void ignitionRequiresInputsAStoppedBurnAndSafeSingleAshOutput() {
        assertTrue(BrazierEffectRules.canIgnite(true, true, false));
        assertFalse(BrazierEffectRules.canIgnite(false, true, false));
        assertFalse(BrazierEffectRules.canIgnite(true, false, false));
        assertFalse(BrazierEffectRules.canIgnite(true, true, true));
    }

    @Test
    void redstoneIgnitionRequiresARisingEdge() {
        assertTrue(BrazierEffectRules.isRisingEdge(false, true));
        assertFalse(BrazierEffectRules.isRisingEdge(false, false));
        assertFalse(BrazierEffectRules.isRisingEdge(true, true));
        assertFalse(BrazierEffectRules.isRisingEdge(true, false));
    }

    @Test
    void legacyInventoryMigrationDiscardsSavedIgnition() {
        assertFalse(BrazierEffectRules.restoreIgnitionAfterMigration(true, true));
        assertTrue(BrazierEffectRules.restoreIgnitionAfterMigration(true, false));
        assertFalse(BrazierEffectRules.restoreIgnitionAfterMigration(false, true));
        assertFalse(BrazierEffectRules.restoreIgnitionAfterMigration(false, false));
    }

    @Test
    void burnContinuesOnlyWhileIgnitedAshRemainsPresent() {
        assertTrue(BrazierEffectRules.canContinueBurn(true, true));
        assertFalse(BrazierEffectRules.canContinueBurn(true, false));
        assertFalse(BrazierEffectRules.canContinueBurn(false, true));
        assertFalse(BrazierEffectRules.canContinueBurn(false, false));
    }
}

