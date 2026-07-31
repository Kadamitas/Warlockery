package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class FetishBindingRulesTest {
    @Test
    void spectralCombinationsSelectDistinctFetishModes() {
        assertTrue(FetishBindingRules.select(Set.of()).isEmpty());
        assertTrue(FetishBindingRules.select(Set.of("spirit")).isEmpty());
        assertEquals(FetishMode.GHOST_WALKING, FetishBindingRules.select(Map.of(
            "spirit", 3, "spectre", 1, "banshee", 1
        )).orElseThrow());
        assertEquals(FetishMode.DISORIENTATION, FetishBindingRules.select(Map.of(
            "spirit", 3, "poltergeist", 2
        )).orElseThrow());
        assertEquals(FetishMode.SENTINEL, FetishBindingRules.select(Map.of(
            "spirit", 3, "spectre", 3
        )).orElseThrow());
        assertEquals(FetishMode.SHRIEKING, FetishBindingRules.select(Map.of(
            "spirit", 3, "banshee", 2
        )).orElseThrow());
        assertEquals(
            FetishMode.VOODOO_PROTECTION,
            FetishBindingRules.select(Map.of(
                "spirit", 3, "spectre", 1, "banshee", 1, "poltergeist", 1
            )).orElseThrow()
        );
    }

    @Test
    void partialSpectralRecipesNeverBindOrConsumeAWeakerMode() {
        assertTrue(FetishBindingRules.select(Map.of("spirit", 2, "spectre", 3)).isEmpty());
        assertTrue(FetishBindingRules.select(Map.of("spirit", 3, "banshee", 1)).isEmpty());
    }

    @Test
    void boundModeRoundTripsThroughThePlacedItem() {
        final CompoundTag data = new CompoundTag();
        FetishBindingState.write(data, FetishMode.SENTINEL);
        assertEquals(FetishMode.SENTINEL, FetishBindingState.read(data).orElseThrow());
    }

    @Test
    void unboundFetishesExposeAVisibleFailure() {
        assertEquals(
            FetishRules.Diagnostic.UNBOUND,
            FetishRules.diagnostic(false, false, FetishMode.DISORIENTATION, false, true, false)
        );
    }

    @Test
    void disorientationTreatsOnlyEquippedPlayersAsThreats() {
        assertFalse(FetishRules.isPlayerThreat(false, false));
        assertTrue(FetishRules.isPlayerThreat(true, false));
        assertTrue(FetishRules.isPlayerThreat(false, true));
        assertTrue(FetishRules.isPlayerThreat(true, true));
    }
}
