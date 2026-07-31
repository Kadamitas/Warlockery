package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class StonebrokerQuiverRulesTest {
    @Test
    void quiverSuppliesAnIndependentArrowWheneverOrdinaryAmmoRunsOut() {
        assertTrue(StonebrokerQuiverRules.suppliesEndlessArrow(true, true, false));
        assertTrue(StonebrokerQuiverRules.suppliesEndlessArrow(true, false, true));
        assertFalse(StonebrokerQuiverRules.suppliesEndlessArrow(true, false, false));
        assertFalse(StonebrokerQuiverRules.suppliesEndlessArrow(false, true, false));
    }

    @Test
    void jumpingAndFlyingTargetsCountAsAirborne() {
        assertTrue(StonebrokerQuiverRules.isAirborneTarget(true, true, false, false));
        assertTrue(StonebrokerQuiverRules.isAirborneTarget(false, false, false, false));
        assertFalse(StonebrokerQuiverRules.isAirborneTarget(false, true, false, false));
        assertFalse(StonebrokerQuiverRules.isAirborneTarget(false, false, true, false));
        assertFalse(StonebrokerQuiverRules.isAirborneTarget(false, false, false, true));
    }

    @Test
    void quiverShotsAreFastAndPunishAirborneTargets() {
        assertTrue(StonebrokerQuiverRules.PROJECTILE_VELOCITY_MULTIPLIER > 1.0);
        assertEquals(200, StonebrokerQuiverRules.WEAKNESS_TICKS);
        assertEquals(1.0F, StonebrokerQuiverRules.damageMultiplier(false, true));
        assertEquals(1.0F, StonebrokerQuiverRules.damageMultiplier(true, false));
        assertEquals(1.75F, StonebrokerQuiverRules.damageMultiplier(true, true));
    }
}
