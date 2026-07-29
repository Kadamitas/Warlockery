package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.transformation.SupernaturalForm;
import org.junit.jupiter.api.Test;

final class BloodGobletInitiationTest {
    @Test
    void emptyGobletCannotBeDrunk() {
        final UtilityDecision decision = BloodGobletRules.drink(false, SupernaturalForm.VAMPIRE);

        assertFalse(decision.success());
        assertEquals("empty", decision.diagnostic());
    }

    @Test
    void fullGobletDoesNotBypassMortalInitiation() {
        final UtilityDecision decision = BloodGobletRules.drink(true, SupernaturalForm.NONE);

        assertFalse(decision.success());
        assertEquals("initiation_required", decision.diagnostic());
    }

    @Test
    void vampireCanConsumeFullGobletAsBloodReserve() {
        final UtilityDecision decision = BloodGobletRules.drink(true, SupernaturalForm.VAMPIRE);

        assertTrue(decision.success());
        assertEquals("consumed", decision.diagnostic());
    }
}
