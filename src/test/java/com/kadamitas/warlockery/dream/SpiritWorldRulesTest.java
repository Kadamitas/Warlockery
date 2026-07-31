package com.kadamitas.warlockery.dream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import net.minecraft.resources.Identifier;

final class SpiritWorldRulesTest {
    @Test
    void entryDiagnosticsDistinguishFailureFromSuccess() {
        assertEquals(
            SpiritWorldRules.EntryDiagnostic.CLIENT_SIDE,
            SpiritWorldRules.diagnoseEntry(false, false, false, true)
        );
        assertEquals(
            SpiritWorldRules.EntryDiagnostic.ALREADY_DREAMING,
            SpiritWorldRules.diagnoseEntry(true, true, false, true)
        );
        assertEquals(
            SpiritWorldRules.EntryDiagnostic.DESTINATION_UNAVAILABLE,
            SpiritWorldRules.diagnoseEntry(true, false, false, false)
        );
        assertEquals(
            SpiritWorldRules.EntryDiagnostic.READY,
            SpiritWorldRules.diagnoseEntry(true, false, false, true)
        );
    }

    @Test
    void sourceWeightedEnvironmentOnlyAppliesAroundANightmareWeaver() {
        assertEquals(0.95, SpiritWorldRules.nightmareChance(false, environment(false, 99, 99, 0, 0)), 0.000_001);
        assertEquals(0.45, SpiritWorldRules.nightmareChance(false, environment(true, 0, 0, 0, 0)), 0.000_001);
        assertEquals(0.70, SpiritWorldRules.nightmareChance(false, environment(true, 1, 1, 1, 1)), 0.000_001);
        assertEquals(0.0, SpiritWorldRules.nightmareChance(false, environment(true, 3, 2, 0, 0)), 0.000_001);
        assertEquals(0.15, SpiritWorldRules.nightmareChance(false, environment(true, 30, 0, 0, 0)), 0.000_001);
        assertEquals(0.95, SpiritWorldRules.nightmareChance(false, environment(true, 0, 0, 20, 20)), 0.000_001);
        assertEquals(1.0, SpiritWorldRules.nightmareChance(true, environment(false, 0, 0, 0, 0)), 0.000_001);
        assertTrue(SpiritWorldRules.entersNightmare(1.0, 0.999));
        assertFalse(SpiritWorldRules.entersNightmare(0.0, 0.0));
    }

    @Test
    void clockAndNaturalSourcesUseBoundedDeterministicRules() {
        assertEquals(6_000L, SpiritWorldRules.dreamClockTime(false));
        assertEquals(18_000L, SpiritWorldRules.dreamClockTime(true));
        assertTrue(SpiritWorldRules.naturalSourceScheduled(180L, 20, 200));
        assertFalse(SpiritWorldRules.naturalSourceScheduled(181L, 20, 200));
        assertTrue(SpiritWorldRules.belowNaturalSourceCap(3, 4));
        assertFalse(SpiritWorldRules.belowNaturalSourceCap(4, 4));
        assertFalse(SpiritWorldRules.belowNaturalSourceCap(5, 4));
    }

    @Test
    void fatalDamageWakesBeforeTheDreamerDies() {
        assertFalse(SpiritWorldRules.fatalDreamDamage(10.0F, 9.99F));
        assertTrue(SpiritWorldRules.fatalDreamDamage(10.0F, 10.0F));
    }

    @Test
    void onlyVanillaEndermenAreExcluded() {
        assertTrue(SpiritWorldRules.excludesFromSpiritWorld(Identifier.withDefaultNamespace("enderman")));
        assertFalse(SpiritWorldRules.excludesFromSpiritWorld(Identifier.withDefaultNamespace("zombie")));
        assertFalse(SpiritWorldRules.excludesFromSpiritWorld(
            Identifier.fromNamespaceAndPath("example", "dream_walker")
        ));
    }

    @Test
    void demonicNightmaresRequireEveryBrewOnlyCatalystAndRemainRare() {
        assertTrue(SpiritWorldRules.demonicNightmareEligible(true, true, true, true));
        assertFalse(SpiritWorldRules.demonicNightmareEligible(false, true, true, true));
        assertFalse(SpiritWorldRules.demonicNightmareEligible(true, false, true, true));
        assertFalse(SpiritWorldRules.demonicNightmareEligible(true, true, false, true));
        assertFalse(SpiritWorldRules.demonicNightmareEligible(true, true, true, false));
        assertEquals(0.08, SpiritWorldRules.demonicNightmareChance(true));
        assertEquals(0.0, SpiritWorldRules.demonicNightmareChance(false));
    }

    private static SpiritWorldRules.NightmareEnvironment environment(
        final boolean weaver,
        final int spirit,
        final int cotton,
        final int heart,
        final int fire
    ) {
        return new SpiritWorldRules.NightmareEnvironment(weaver, spirit, cotton, heart, fire);
    }
}
