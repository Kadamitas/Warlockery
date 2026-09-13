package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.bounds;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.geometrySnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.SpiritEntity;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class SpiritModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/SpiritModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/spirit.png"
    );

    @Test
    void floatingHumanoidTapersIntoOneBackwardTrailingWisp() throws Exception {
        final ModelPart root = SpiritModel.createBodyLayer().bakeRoot();
        final ModelPart spirit = requiredChild(root, "spirit");
        final ModelPart head = requiredChild(spirit, "head");
        assertFalse(head.isEmpty());
        assertFalse(requiredChild(head, "right_spectral_eye").isEmpty());
        assertFalse(requiredChild(head, "left_spectral_eye").isEmpty());
        assertFalse(requiredChild(head, "quiet_mouth").isEmpty());
        assertFalse(requiredChild(spirit, "torso").isEmpty());
        assertFalse(requiredChild(spirit, "right_arm").isEmpty());
        assertFalse(requiredChild(spirit, "left_arm").isEmpty());
        final ModelPart lower = requiredChild(spirit, "lower_body");
        final ModelPart trail = requiredChild(lower, "trailing_wisp");
        assertFalse(requiredChild(trail, "wisp_tip").isEmpty());
        assertTrue(bounds(trail).maxZ() - bounds(trail).minZ() >= 10.0F,
            "the single taper must visibly curve backward instead of reading as legs");
        assertTrue(bounds(root).maxY() < 24.0F,
            "the taper must remain visibly suspended above the ground plane");

        final String source = Files.readString(SOURCE);
        assertFalse(source.contains("right_leg"));
        assertFalse(source.contains("left_leg"));
        assertFalse(source.contains("right_trail"));
        assertFalse(source.contains("left_trail"));

        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        assertEquals(SpiritModel.TEXTURE_WIDTH, texture.getWidth());
        assertEquals(SpiritModel.TEXTURE_HEIGHT, texture.getHeight());
        CreatureModelTestSupport.assertUvsWithin(
            root, SpiritModel.TEXTURE_WIDTH, SpiritModel.TEXTURE_HEIGHT
        );
        assertTrue(hasTransparentPixel(texture));
        assertTrue(hasTranslucentPixel(texture));
        assertUsedUvsVisible(root, texture);
    }

    @Test
    void hoverFlightAndShieldingUseSeparateMovingPoses() {
        final SpiritModel model = new SpiritModel(SpiritModel.createBodyLayer().bakeRoot());
        final SpiritModel.State hoverEarly = new SpiritModel.State();
        hoverEarly.ageInTicks = 1.0F;
        model.setupAnim(hoverEarly);
        final String earlyPose = geometrySnapshot(model.root());

        final SpiritModel.State hoverLate = new SpiritModel.State();
        hoverLate.ageInTicks = 8.0F;
        model.setupAnim(hoverLate);
        final String latePose = geometrySnapshot(model.root());

        final SpiritModel.State flying = new SpiritModel.State();
        flying.ageInTicks = 8.0F;
        flying.walkAnimationPos = 3.0F;
        flying.walkAnimationSpeed = 1.0F;
        model.setupAnim(flying);
        final String flyingPose = geometrySnapshot(model.root());

        final SpiritModel.State shielding = new SpiritModel.State();
        shielding.ageInTicks = 8.0F;
        shielding.shielding = true;
        model.setupAnim(shielding);
        final String shieldingPose = geometrySnapshot(model.root());

        assertNotEquals(earlyPose, latePose, "the trailing lower body must drift while hovering");
        assertNotEquals(latePose, flyingPose);
        assertNotEquals(flyingPose, shieldingPose);
    }

    @Test
    void exposesTheExistingGuardianStateAdapter() {
        assertDoesNotThrow(() -> SpiritModel.class.getDeclaredMethod(
            "extractRenderState", SpiritEntity.class, SpiritModel.State.class, float.class
        ));
    }

    private static boolean hasTransparentPixel(final BufferedImage image) {
        return anyPixel(image, alpha -> alpha == 0);
    }

    private static boolean hasTranslucentPixel(final BufferedImage image) {
        return anyPixel(image, alpha -> alpha > 0 && alpha < 255);
    }

    private static boolean anyPixel(
        final BufferedImage image,
        final java.util.function.IntPredicate predicate
    ) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (predicate.test(image.getRGB(x, y) >>> 24)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void assertUsedUvsVisible(final ModelPart root, final BufferedImage texture) {
        for (final CreatureModelTestSupport.CubeVisit visit : CreatureModelTestSupport.cubes(root)) {
            for (final ModelPart.Polygon polygon : visit.polygons()) {
                final int minU = (int) Math.floor(java.util.Arrays.stream(polygon.vertices())
                    .mapToDouble(vertex -> vertex.u() * texture.getWidth()).min().orElseThrow());
                final int maxU = (int) Math.ceil(java.util.Arrays.stream(polygon.vertices())
                    .mapToDouble(vertex -> vertex.u() * texture.getWidth()).max().orElseThrow());
                final int minV = (int) Math.floor(java.util.Arrays.stream(polygon.vertices())
                    .mapToDouble(vertex -> vertex.v() * texture.getHeight()).min().orElseThrow());
                final int maxV = (int) Math.ceil(java.util.Arrays.stream(polygon.vertices())
                    .mapToDouble(vertex -> vertex.v() * texture.getHeight()).max().orElseThrow());
                for (int v = minV; v < maxV; v++) {
                    for (int u = minU; u < maxU; u++) {
                        assertTrue((texture.getRGB(u, v) >>> 24) > 0,
                            visit.path() + " transparent UV at " + u + "," + v);
                    }
                }
            }
        }
    }
}
