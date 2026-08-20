package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Characterizes the pre-F19 shared temperament helper and pins the exact facts the dedicated
 * F19 runtimes inherited from it. The helper itself stays byte-semantically unchanged so no
 * other family that may later reuse it is disturbed.
 */
final class SpiritTemperamentRulesTest {
    @Test
    void theRetainedFleeContractIsExactlyTwelveBlocksWhileUnbound() {
        assertEquals(144.0D, SpiritTemperamentRules.FLEE_DISTANCE_SQUARED);
        assertTrue(SpiritTemperamentRules.shouldFlee(false, true, 144.0D));
        assertFalse(SpiritTemperamentRules.shouldFlee(false, true, 144.1D));
        assertFalse(SpiritTemperamentRules.shouldFlee(true, true, 4.0D));
        assertFalse(SpiritTemperamentRules.shouldFlee(false, false, 4.0D));
    }

    @Test
    void theRetainedAttackContractRequiredBothOwnershipAndAnOwnerThreat() {
        assertTrue(SpiritTemperamentRules.canAttack(true, true));
        assertFalse(SpiritTemperamentRules.canAttack(true, false));
        assertFalse(SpiritTemperamentRules.canAttack(false, true));
        assertFalse(SpiritTemperamentRules.canAttack(false, false));
    }

    @Test
    void theDedicatedSpiritRulesPreserveTheWaryRadiusAndTightenTheAttackContract() {
        assertEquals(SpiritTemperamentRules.FLEE_DISTANCE_SQUARED, SpiritRules.WARY_RANGE_SQUARED,
            "the dedicated Spirit keeps the audited twelve block wary radius exactly");
        assertEquals(
            SpiritTemperamentRules.shouldFlee(false, true, 100.0D),
            SpiritRules.shouldWithdraw(false, true, true, 100.0D, 0),
            "an unbound Spirit still withdraws from a living player inside the radius");
        assertEquals(
            SpiritTemperamentRules.shouldFlee(true, true, 100.0D),
            SpiritRules.shouldWithdraw(true, true, true, 100.0D, 0),
            "binding still stops avoidance atomically");
        assertFalse(SpiritRules.canAttack(true, false, true),
            "the dedicated contract adds a warned, bounded defence window the old helper lacked");
        assertTrue(SpiritRules.canAttack(true, true, true));
    }

    @Test
    void theDedicatedLostSoulRulesDeliberatelyDropTheSharedAttackContract() {
        assertTrue(SpiritTemperamentRules.canAttack(true, true));
        assertFalse(LostSoulRules.canAttack(true, true),
            "the Lost Soul is not a defender: the shared owner-threat clause does not apply to it");
    }
}
