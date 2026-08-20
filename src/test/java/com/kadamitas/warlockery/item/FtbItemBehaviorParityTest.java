package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class FtbItemBehaviorParityTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void purifiedMilkPrioritizesAHandicapWhenChoosingItsSingleEffect() {
        assertEquals(64, PurifiedMilkItem.STACK_SIZE);
        assertFalse(PurifiedMilkItem.beneficial(new MobEffectInstance(MobEffects.POISON, 200)));
        assertTrue(PurifiedMilkItem.beneficial(new MobEffectInstance(MobEffects.REGENERATION, 200)));
    }

    @Test
    void waterArtichokeBalancesEveryRestoredHungerPointWithThreeSecondsOfHungerThree() {
        assertEquals(0, WaterArtichokeGlobeItem.hungerDuration(20));
        assertEquals(60, WaterArtichokeGlobeItem.hungerDuration(19));
        assertEquals(1_200, WaterArtichokeGlobeItem.hungerDuration(0));
    }

    @Test
    void creeperHeartsRaiseButNeverLowerTreefydHealth() {
        assertEquals(100.0, CreeperHeartItem.boostedHealth(40.0));
        assertEquals(120.0, CreeperHeartItem.boostedHealth(120.0));
    }

    @Test
    void graveyardDustAddsTwoHealthUntilTheFiftyPointCeiling() {
        assertEquals(22.0, GraveyardDustItem.boostedHealth(20.0));
        assertEquals(50.0, GraveyardDustItem.boostedHealth(49.0));
        assertEquals(50.0, GraveyardDustItem.boostedHealth(50.0));
    }

    @Test
    void wormyApplePoisonsWithoutRestoringHunger() {
        assertEquals(0, ResourceFoodItem.Profile.WORMY_APPLE.nutrition());
        assertEquals(0.0F, ResourceFoodItem.Profile.WORMY_APPLE.saturation());
    }
}
