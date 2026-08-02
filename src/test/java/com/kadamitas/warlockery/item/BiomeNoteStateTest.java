package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
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

    @Test
    void biomeDisplayNamesUseTranslationsWithReadableModdedFallbacks() {
        final Identifier biome = Identifier.parse("examplemod:crystal_forest");
        assertEquals(
            Component.translatableWithFallback("biome.examplemod.crystal_forest", "Crystal Forest"),
            BiomeNoteState.displayName(biome)
        );
    }
}
