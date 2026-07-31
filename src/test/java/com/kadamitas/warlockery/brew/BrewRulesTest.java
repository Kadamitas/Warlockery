package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class BrewRulesTest {
    @Test
    void growthRequiresAValidSuccessfulBonemealTarget() {
        assertTrue(BrewRules.canGrow(true, true, true));
        assertFalse(BrewRules.canGrow(false, true, true));
        assertFalse(BrewRules.canGrow(true, false, true));
        assertFalse(BrewRules.canGrow(true, true, false));
    }

    @Test
    void extinguishAcceptsFireOrLitFireBearingBlocks() {
        assertTrue(BrewRules.shouldExtinguish(true, false, false));
        assertTrue(BrewRules.shouldExtinguish(false, true, true));
        assertFalse(BrewRules.shouldExtinguish(false, true, false));
        assertFalse(BrewRules.shouldExtinguish(false, false, true));
    }

    @Test
    void freezeOnlyReplacesSourceWater() {
        assertTrue(BrewRules.shouldFreeze(true, true, true));
        assertFalse(BrewRules.shouldFreeze(false, true, true));
        assertFalse(BrewRules.shouldFreeze(true, false, true));
        assertFalse(BrewRules.shouldFreeze(true, true, false));
    }

    @Test
    void webAndFirePlacementRequiresReplaceableSupportedSpace() {
        assertTrue(BrewRules.canPlaceOnSurface(true, true));
        assertFalse(BrewRules.canPlaceOnSurface(false, true));
        assertFalse(BrewRules.canPlaceOnSurface(true, false));
    }

    @Test
    void cropHarvestRequiresMaturityWhenAgeIsAvailable() {
        assertTrue(BrewRules.shouldHarvest(true, true, true));
        assertFalse(BrewRules.shouldHarvest(true, true, false));
        assertTrue(BrewRules.shouldHarvest(true, false, false));
        assertFalse(BrewRules.shouldHarvest(false, false, true));
    }

    @Test
    void tillingRequiresTaggedDirtAndClearSpace() {
        assertTrue(BrewRules.shouldTill(true, true));
        assertFalse(BrewRules.shouldTill(false, true));
        assertFalse(BrewRules.shouldTill(true, false));
    }

    @Test
    void effectRemovalSelectsTheRequestedCategory() {
        assertTrue(BrewRules.shouldRemoveEffect(true, true));
        assertTrue(BrewRules.shouldRemoveEffect(false, false));
        assertFalse(BrewRules.shouldRemoveEffect(true, false));
        assertFalse(BrewRules.shouldRemoveEffect(false, true));
    }

    @Test
    void lilyPlacementRequiresSourceWaterClearanceAndSurvival() {
        assertTrue(BrewRules.shouldPlaceLily(true, true, true, true));
        assertFalse(BrewRules.shouldPlaceLily(false, true, true, true));
        assertFalse(BrewRules.shouldPlaceLily(true, false, true, true));
        assertFalse(BrewRules.shouldPlaceLily(true, true, false, true));
        assertFalse(BrewRules.shouldPlaceLily(true, true, true, false));
    }

    @Test
    void durationExtensionIsBoundedAndOverflowSafe() {
        org.junit.jupiter.api.Assertions.assertEquals(400, BrewRules.extendedDuration(200));
        org.junit.jupiter.api.Assertions.assertEquals(36_000, BrewRules.extendedDuration(30_000));
        org.junit.jupiter.api.Assertions.assertEquals(36_000, BrewRules.extendedDuration(Integer.MAX_VALUE));
    }

    @Test
    void darknessAndMoonlightRulesExposeTheirConditions() {
        assertTrue(BrewRules.isDarkEnoughForGrue(4));
        assertFalse(BrewRules.isDarkEnoughForGrue(5));
        assertTrue(BrewRules.isMoonlit(true, 0.5F));
        assertFalse(BrewRules.isMoonlit(false, 0.5F));
        assertFalse(BrewRules.isMoonlit(true, 0.0F));
    }

    @Test
    void fluidPartingRequiresMatchingSourceAtTheSurface() {
        assertTrue(BrewRules.canPartFluid(true, true, true));
        assertFalse(BrewRules.canPartFluid(false, true, true));
        assertFalse(BrewRules.canPartFluid(true, false, true));
        assertFalse(BrewRules.canPartFluid(true, true, false));
    }

    @Test
    void solidifyingAcceptsEveryHollowTearsFluidState() {
        assertTrue(BrewRules.shouldSolidify(true));
        assertFalse(BrewRules.shouldSolidify(false));
    }

    @Test
    void erosionOnlyRemovesSafeTerrainBelowHollowTears() {
        assertTrue(BrewRules.canErodeBelowHollowTears(false, false, false, 1.0F));
        assertFalse(BrewRules.canErodeBelowHollowTears(true, false, false, 1.0F));
        assertFalse(BrewRules.canErodeBelowHollowTears(false, true, false, 1.0F));
        assertFalse(BrewRules.canErodeBelowHollowTears(false, false, true, 1.0F));
        assertFalse(BrewRules.canErodeBelowHollowTears(false, false, false, -1.0F));
    }
}
