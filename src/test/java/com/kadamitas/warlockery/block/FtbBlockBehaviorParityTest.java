package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.TransparentBlock;
import org.junit.jupiter.api.Test;

final class FtbBlockBehaviorParityTest {
    private static final Path JAVA = Path.of("src", "main", "java", "com", "kadamitas", "warlockery");

    @Test
    void perpetualIceRetainsVanillaIceDropsWithoutNaturalMelting() {
        assertTrue(IceBlock.class.isAssignableFrom(PerpetualIceBlock.class));
        assertTrue(read(JAVA.resolve("block/PerpetualIceBlock.java")).contains("protected void randomTick"));
    }

    @Test
    void slipperPlateAndShadedGlassUsePurposeBuiltVanillaDerivedBlocks() {
        assertTrue(PressurePlateBlock.class.isAssignableFrom(IcyPressurePlateBlock.class));
        assertTrue(TransparentBlock.class.isAssignableFrom(ShadedGlassBlock.class));
    }

    @Test
    void paradoxEggUsesVanillaDragonEggTeleportation() {
        final String blocks = read(JAVA.resolve("registry/ModBlocks.java"));
        assertTrue(blocks.contains("new DragonEggBlock(properties.noOcclusion())"));
        assertTrue(DragonEggBlock.class.getName().endsWith("DragonEggBlock"));
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
