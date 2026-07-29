package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class FetishBindingRulesTest {
    @Test
    void spectralCombinationsSelectDistinctFetishModes() {
        assertTrue(FetishBindingRules.select(Set.of()).isEmpty());
        assertEquals(FetishMode.GHOST_WALKING, FetishBindingRules.select(Set.of("spirit")).orElseThrow());
        assertEquals(FetishMode.DISORIENTATION, FetishBindingRules.select(Set.of("poltergeist")).orElseThrow());
        assertEquals(FetishMode.SENTINEL, FetishBindingRules.select(Set.of("spectre")).orElseThrow());
        assertEquals(FetishMode.SHRIEKING, FetishBindingRules.select(Set.of("banshee")).orElseThrow());
        assertEquals(
            FetishMode.VOODOO_PROTECTION,
            FetishBindingRules.select(Set.of("spectre", "banshee", "poltergeist")).orElseThrow()
        );
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
}
