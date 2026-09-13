package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class GlassConnectivityAssetTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/warlockery");

    @Test
    void shadedGlassTexturesAreTransparentAndVanillaScale() throws IOException {
        for (final String name : new String[] {"shadedglass.png", "shadedglass_active.png"}) {
            final BufferedImage image = ImageIO.read(ASSETS.resolve("textures/block/" + name).toFile());
            assertEquals(16, image.getWidth());
            assertEquals(16, image.getHeight());
            assertTrue((image.getRGB(8, 8) >>> 24) < 128);
        }
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
