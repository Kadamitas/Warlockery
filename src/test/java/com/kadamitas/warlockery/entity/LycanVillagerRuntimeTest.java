package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class LycanVillagerRuntimeTest {
    @Test
    void householdProtectionRequiresBondAndDirectAggression() {
        assertTrue(LycanVillagerRuntime.mayProtectResident(6, true, true, true));
        assertFalse(LycanVillagerRuntime.mayProtectResident(5, true, true, true));
        assertFalse(LycanVillagerRuntime.mayProtectResident(8, false, true, true));
        assertFalse(LycanVillagerRuntime.mayProtectResident(8, true, false, true));
        assertFalse(LycanVillagerRuntime.mayProtectResident(8, true, true, false));
    }

    @Test
    void withdrawalAndHazardAlwaysOutrankCombat() {
        assertTrue(LycanVillagerRuntime.mustWithdraw(7.0F, 20.0F, false, false, false, false));
        assertTrue(LycanVillagerRuntime.mustWithdraw(20.0F, 20.0F, true, false, false, false));
        assertTrue(LycanVillagerRuntime.mustWithdraw(20.0F, 20.0F, false, true, false, false));
        assertFalse(LycanVillagerRuntime.mustWithdraw(20.0F, 20.0F, false, false, false, false));
        assertTrue(LycanVillagerRuntime.hazardHasPriority(true, LycanVillagerRules.Intent.DEFEND));
    }
}
