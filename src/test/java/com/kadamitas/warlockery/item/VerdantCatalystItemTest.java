package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
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
    void onlyPrimeCatalystTransformsMatureCropsAndTerrain() {
        assertFalse(VerdantCatalystItem.canTransform(Blocks.WHEAT.defaultBlockState(), false));
        assertTrue(VerdantCatalystItem.canTransform(Blocks.WHEAT.defaultBlockState(), true));
        assertFalse(VerdantCatalystItem.canTransform(Blocks.DIRT.defaultBlockState(), false));
        assertTrue(VerdantCatalystItem.canTransform(Blocks.DIRT.defaultBlockState(), true));
    }
}
