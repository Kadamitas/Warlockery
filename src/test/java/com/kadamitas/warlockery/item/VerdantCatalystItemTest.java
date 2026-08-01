package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class VerdantCatalystItemTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void netherWartMutationExistsOnlyInsideTheSpiritWorld() {
        assertFalse(VerdantCatalystItem.transformations(false).contains("minecraft:nether_wart"));
        assertTrue(VerdantCatalystItem.transformations(true).contains("minecraft:nether_wart"));
    }

    @Test
    void onlyPrimeCatalystTransformsMatureCropsAndTerrain() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/item/VerdantCatalystItem.java"
        ));
        assertTrue(source.contains("prime && (state.getBlock() instanceof CropBlock"));
        assertTrue(source.contains("state.is(Blocks.GRASS_BLOCK)"));
        assertTrue(source.contains("state.is(Blocks.MYCELIUM)"));
        assertTrue(source.contains("state.is(Blocks.DIRT)"));
        assertTrue(source.contains("if (!prime)"));
        assertTrue(source.contains("return java.util.Optional.empty()"));
    }
}
