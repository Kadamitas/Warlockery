package com.kadamitas.warlockery.transformation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VampireSunlightRulesTest {
    @Test
    void requiresAnOptedInSunnyLocalSky() {
        assertTrue(VampireSunlightRules.exposed(new VampireSunlightRules.Exposure(
            true, false, true, true, true, false, false, false
        )));
        assertFalse(VampireSunlightRules.exposed(new VampireSunlightRules.Exposure(
            false, false, true, true, true, false, false, false
        )));
        assertFalse(VampireSunlightRules.exposed(new VampireSunlightRules.Exposure(
            true, true, true, true, true, false, false, false
        )));
        assertFalse(VampireSunlightRules.exposed(new VampireSunlightRules.Exposure(
            true, false, true, true, true, true, false, false
        )));
        assertFalse(VampireSunlightRules.exposed(new VampireSunlightRules.Exposure(
            true, false, true, true, true, false, true, false
        )));
        assertFalse(VampireSunlightRules.exposed(new VampireSunlightRules.Exposure(
            true, false, true, true, true, false, false, true
        )));
    }

    @Test
    void headCoverAndLowLocalBrightnessProtect() {
        assertFalse(VampireSunlightRules.exposed(new VampireSunlightRules.Exposure(
            true, false, false, true, true, false, false, false
        )));
        assertFalse(VampireSunlightRules.exposed(new VampireSunlightRules.Exposure(
            true, false, true, false, true, false, false, false
        )));
        assertFalse(VampireSunlightRules.exposed(new VampireSunlightRules.Exposure(
            true, false, true, true, false, false, false, false
        )));
    }

    @Test
    void levelFiveBloodPaymentPreventsDamageUntilTheReserveCannotPay() {
        final int maximum = VampireProgressionRules.bloodCapacityAt(5);
        final int cost = SupernaturalAbilityRules.sunlightBloodCost(5, maximum);

        assertEquals(
            new VampireSunlightRules.Protection(true, cost),
            VampireSunlightRules.protection(5, cost, maximum, true)
        );
        assertEquals(
            new VampireSunlightRules.Protection(false, 0),
            VampireSunlightRules.protection(5, cost - 1, maximum, true)
        );
        assertEquals(
            new VampireSunlightRules.Protection(true, 0),
            VampireSunlightRules.protection(5, 1, maximum, false)
        );
        assertEquals(
            new VampireSunlightRules.Protection(false, 0),
            VampireSunlightRules.protection(4, maximum, maximum, true)
        );
    }
}
