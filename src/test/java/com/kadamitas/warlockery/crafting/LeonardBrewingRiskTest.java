package com.kadamitas.warlockery.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class LeonardBrewingRiskTest {
    @Test
    void noShadeLeavesCauldronBrewingSafeAndOrdinaryRange() {
        final LeonardBrewingRisk.RiskProfile profile = LeonardBrewingRisk.profile(0);

        assertEquals(12, profile.effectRange());
        assertEquals(0.0F, profile.chance());
        assertFalse(LeonardBrewingRisk.shouldBackfire(profile, 0.0F));
    }

    @Test
    void oneShadeExtendsRangeAndIntroducesBoundedRisk() {
        final LeonardBrewingRisk.RiskProfile profile = LeonardBrewingRisk.profile(1);

        assertEquals(20, profile.effectRange());
        assertEquals(0.125F, profile.chance());
        assertTrue(LeonardBrewingRisk.shouldBackfire(profile, 0.124F));
        assertFalse(LeonardBrewingRisk.shouldBackfire(profile, 0.125F));
    }

    @Test
    void multipleShadesAreCapped() {
        assertEquals(LeonardBrewingRisk.profile(2), LeonardBrewingRisk.profile(20));
        assertEquals(28, LeonardBrewingRisk.profile(2).effectRange());
        assertEquals(0.25F, LeonardBrewingRisk.profile(2).chance());
    }

    @Test
    void invalidRandomRollIsRejected() {
        final LeonardBrewingRisk.RiskProfile profile = LeonardBrewingRisk.profile(1);

        assertThrows(IllegalArgumentException.class, () -> LeonardBrewingRisk.shouldBackfire(profile, -0.01F));
        assertThrows(IllegalArgumentException.class, () -> LeonardBrewingRisk.shouldBackfire(profile, 1.0F));
    }
}
