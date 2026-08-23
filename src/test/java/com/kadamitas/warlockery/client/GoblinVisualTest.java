package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.client.model.GoblinModel;
import com.kadamitas.warlockery.client.model.HobgoblinModel;
import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.CreatureVisualProfile;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class GoblinVisualTest {
    private static final Path ENTITY_TEXTURES = Path.of("src/main/resources/assets/warlockery/textures/entity");

    @Test
    void goblinfolkKeepTheirLittleCreatureDimensions() {
        final CreatureVisualProfile goblin = CreatureVisualProfile.forKind(CreatureKind.GOBLIN);
        final CreatureVisualProfile hobgoblin = CreatureVisualProfile.forKind(CreatureKind.HOBGOBLIN);
        assertEquals(1.08F, goblin.height());
        assertEquals(1.26F, hobgoblin.height());
        assertTrue(goblin.width() < hobgoblin.width());
    }

    @Test
    void goblinAndHobgoblinOwnDistinctPenguinBodiesAndAtlases() {
        final ModelPart goblinModel = GoblinModel.createBodyLayer().bakeRoot();
        final ModelPart hobgoblinModel = HobgoblinModel.createBodyLayer().bakeRoot();
        assertFalse(goblinModel.getChild("body").getChild("head").getChild("beak").isEmpty());
        assertFalse(hobgoblinModel.getChild("torso").getChild("head").getChild("beak").isEmpty());
        for (final ModelPart root : java.util.List.of(goblinModel, hobgoblinModel)) {
            assertFalse(root.getChild("right_flipper").isEmpty());
            assertFalse(root.getChild("left_flipper").isEmpty());
            assertFalse(root.getChild("right_leg").getChild("right_webbed_foot").isEmpty());
            assertFalse(root.getChild("left_leg").getChild("left_webbed_foot").isEmpty());
        }

        final BufferedImage goblin = read("goblin.png");
        final BufferedImage hobgoblin = read("hobgoblin.png");
        assertEquals(128, goblin.getWidth());
        assertEquals(128, goblin.getHeight());
        assertEquals(192, hobgoblin.getWidth());
        assertEquals(128, hobgoblin.getHeight());
        assertTrue(hasOpaqueAndTransparentPixels(goblin));
        assertTrue(hasOpaqueAndTransparentPixels(hobgoblin));
        assertNotEquals(pixelHash(goblin), pixelHash(hobgoblin));
    }

    @Test
    void synchronizedBabyStateScalesBothDedicatedPenguinRenderers() throws IOException {
        final String renderer = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/client/DedicatedCreatureRenderers.java"
        ));
        assertTrue(renderer.contains("register(\"goblin\", context -> goblin(context))"));
        assertTrue(renderer.contains("register(\"hobgoblin\", context -> hobgoblin(context))"));
        assertTrue(renderer.contains("scaleBaby(state, poseStack)"));
        assertTrue(renderer.contains("DedicatedCreatureRenderers::babyShadow"));
        assertTrue(renderer.contains("GoblinLifecycleRules.BABY_RENDER_SCALE"));
    }

    private static BufferedImage read(final String name) {
        try {
            return ImageIO.read(ENTITY_TEXTURES.resolve(name).toFile());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static boolean hasOpaqueAndTransparentPixels(final BufferedImage image) {
        boolean opaque = false;
        boolean transparent = false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int alpha = image.getRGB(x, y) >>> 24;
                opaque |= alpha == 255;
                transparent |= alpha == 0;
            }
        }
        return opaque && transparent;
    }

    private static int pixelHash(final BufferedImage image) {
        int hash = 1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                hash = 31 * hash + image.getRGB(x, y);
            }
        }
        return hash;
    }
}
