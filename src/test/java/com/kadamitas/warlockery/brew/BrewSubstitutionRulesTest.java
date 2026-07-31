package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class BrewSubstitutionRulesTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void substitutesOnlyTheExactBlockTypeHit() {
        assertTrue(BrewRuntime.canSubstitute(
            Blocks.STONE.defaultBlockState(),
            Blocks.STONE,
            Blocks.DIRT,
            false,
            true
        ));
        assertFalse(BrewRuntime.canSubstitute(
            Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.STONE,
            Blocks.DIRT,
            false,
            true
        ));
    }

    @Test
    void substitutionPreservesBlockEntitiesAndRejectsInvalidReplacements() {
        assertFalse(BrewRuntime.canSubstitute(
            Blocks.STONE.defaultBlockState(),
            Blocks.STONE,
            Blocks.DIRT,
            true,
            true
        ));
        assertFalse(BrewRuntime.canSubstitute(
            Blocks.STONE.defaultBlockState(),
            Blocks.STONE,
            Blocks.DIRT,
            false,
            false
        ));
        assertFalse(BrewRuntime.canSubstitute(
            Blocks.STONE.defaultBlockState(),
            Blocks.STONE,
            Blocks.STONE,
            false,
            true
        ));
    }
}
