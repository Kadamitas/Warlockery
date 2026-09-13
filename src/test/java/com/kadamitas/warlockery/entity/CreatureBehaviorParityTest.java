package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class CreatureBehaviorParityTest {
    private static final UUID OWNER = new UUID(0, 1);
    private static final UUID OTHER = new UUID(0, 2);

    @Test void bindingAndMountingRespectOwnershipAndOfferings() {
        assertFalse(CreatureBehaviorRules.canBind(Optional.empty(), OWNER, false));
        assertTrue(CreatureBehaviorRules.canBind(Optional.empty(), OWNER, true));
        assertFalse(CreatureBehaviorRules.canMount(Optional.of(OTHER), OWNER));
        assertTrue(CreatureBehaviorRules.canMount(Optional.of(OWNER), OWNER));
        assertFalse(CreatureBehaviorRules.canRecruit(Optional.empty(), OWNER, true, false));
        assertTrue(CreatureBehaviorRules.canRecruit(Optional.empty(), OWNER, true, true));
    }

    @Test void pulseAndSummonGatesRequireTheirActualConditions() {
        assertFalse(CreatureBehaviorRules.shouldPulse(19, 0, 20));
        assertTrue(CreatureBehaviorRules.shouldPulse(20, 0, 20));
        assertFalse(CreatureBehaviorRules.shouldSummonWolves(20, 20, 0, 400));
        assertTrue(CreatureBehaviorRules.shouldSummonWolves(10, 20, 0, 400));
        assertFalse(CreatureBehaviorRules.canRedirectEffect(true, false, true, true));
        assertTrue(CreatureBehaviorRules.canRedirectEffect(true, true, true, true));
    }

    @Test void empowermentAndCauldronRangeScaleWithSuppliedLevel() {
        assertEquals(1, CreatureBehaviorRules.empoweredLevel(0, 1));
        assertEquals(0, CreatureBehaviorRules.cauldronRangeBonus(0));
        assertEquals(16, CreatureBehaviorRules.cauldronRangeBonus(4));
    }

    @Test void waterAndRainPutOutTheSunForSunlightWeakCreatures() {
        assertTrue(CreatureBehaviorRules.shouldBurnInSun(true, true, false, false));
        assertFalse(CreatureBehaviorRules.shouldBurnInSun(true, true, false, true));
        assertFalse(CreatureBehaviorRules.shouldBurnInSun(false, true, false, false));
        assertFalse(CreatureBehaviorRules.shouldBurnInSun(true, false, false, false));
        assertFalse(CreatureBehaviorRules.shouldBurnInSun(true, true, true, false));
    }
}
