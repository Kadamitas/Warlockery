package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class BiomeNoteStateTest {
    @Test
    void emptyNoteReportsItsMissingBinding() {
        assertTrue(BiomeNoteState.read(new CompoundTag()).isEmpty());
    }

    @Test
    void noteRoundTripsAnyRegisteredNamespaceIdentifier() {
        final CompoundTag tag = new CompoundTag();
        final Identifier biome = Identifier.parse("examplemod:crystal_forest");
        BiomeNoteState.write(tag, biome);
        assertEquals(biome, BiomeNoteState.read(tag).orElseThrow());
    }
}
