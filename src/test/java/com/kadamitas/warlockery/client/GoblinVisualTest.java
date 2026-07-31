package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.CreatureVisualProfile;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class GoblinVisualTest {
    private static final Path ENTITY_TEXTURES = Path.of("src/main/resources/assets/warlockery/textures/entity");

    @Test
    void goblinfolkUseLittleCreatureDimensions() {
        final CreatureVisualProfile goblin = CreatureVisualProfile.forKind(CreatureKind.GOBLIN);
        final CreatureVisualProfile hobgoblin = CreatureVisualProfile.forKind(CreatureKind.HOBGOBLIN);

        assertTrue(goblin.height() <= 1.05F);
        assertTrue(hobgoblin.height() <= 1.15F);
        assertTrue(goblin.width() < hobgoblin.width());
    }

    @Test
    void goblinAndHobgoblinTexturesHaveDistinctSkinPalettesAndClothes() {
        final BufferedImage goblin = read("goblin.png");
        final BufferedImage hobgoblin = read("hobgoblin.png");

        assertEquals(64, goblin.getWidth());
        assertEquals(64, goblin.getHeight());
        assertEquals(64, hobgoblin.getWidth());
        assertEquals(64, hobgoblin.getHeight());
        assertTrue(count(goblin, color -> green(color) > red(color) && green(color) > blue(color)) > 700);
        assertTrue(count(hobgoblin, color -> red(color) > green(color) && green(color) > blue(color)) > 700);
        assertTrue(count(goblin, color -> red(color) == 0x46 && green(color) == 0x52 && blue(color) == 0x5A) > 200);
        assertNotEquals(pixelHash(goblin), pixelHash(hobgoblin));
        assertFrontEyesOnly(goblin);
        assertFrontEyesOnly(hobgoblin);
    }

    @Test
    void synchronizedBabyStateSelectsTheSmallerGoblinModel() throws IOException {
        final String renderer = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/client/TexturedCreatureRenderers.java"
        ));
        assertTrue(renderer.contains("profile.variant() == CreatureModelProfile.Variant.GOBLIN"));
        assertTrue(renderer.contains("profile.variant() == CreatureModelProfile.Variant.HOBGOBLIN"));
        assertTrue(renderer.contains("hasBabyModel && state.isBaby"));
        assertTrue(renderer.contains("GoblinLifecycleRules.BABY_RENDER_SCALE"));
    }

    private static BufferedImage read(final String name) {
        try {
            return ImageIO.read(ENTITY_TEXTURES.resolve(name).toFile());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void assertFrontEyesOnly(final BufferedImage image) {
        final int eyeHighlight = image.getRGB(9, 9);
        assertEquals(eyeHighlight, image.getRGB(13, 9));
        assertNotEquals(eyeHighlight, image.getRGB(24, 9));
        assertNotEquals(eyeHighlight, image.getRGB(28, 9));
        assertEquals(image.getRGB(23, 9), image.getRGB(24, 9));
        assertEquals(image.getRGB(27, 9), image.getRGB(28, 9));
    }

    private static long count(final BufferedImage image, final java.util.function.IntPredicate predicate) {
        return IntStream.range(0, image.getWidth() * image.getHeight())
            .map(index -> image.getRGB(index % image.getWidth(), index / image.getWidth()))
            .filter(predicate)
            .count();
    }

    private static int pixelHash(final BufferedImage image) {
        return IntStream.range(0, image.getWidth() * image.getHeight())
            .map(index -> image.getRGB(index % image.getWidth(), index / image.getWidth()))
            .reduce(1, (hash, color) -> 31 * hash + color);
    }

    private static int red(final int color) {
        return color >>> 16 & 0xFF;
    }

    private static int green(final int color) {
        return color >>> 8 & 0xFF;
    }

    private static int blue(final int color) {
        return color & 0xFF;
    }
}
