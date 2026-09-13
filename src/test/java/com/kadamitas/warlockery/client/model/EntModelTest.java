package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.geometrySnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class EntModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/EntModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/ent.png"
    );

    @Test
    void bakesRootFeetAndThreeLeafCanopies() throws Exception {
        assertEquals(256, EntModel.TEXTURE_WIDTH);
        assertEquals(128, EntModel.TEXTURE_HEIGHT);
        final ModelPart root = EntModel.createBodyLayer().bakeRoot();
        final ModelPart trunk = requiredChild(root, "trunk_base");
        final ModelPart split = requiredChild(trunk, "split_trunk");
        final ModelPart crown = requiredChild(split, "branch_crown");
        for (final String canopy : java.util.List.of(
            "crown_canopy_high", "crown_canopy_left", "crown_canopy_right"
        )) {
            assertFalse(requiredChild(crown, canopy).isEmpty(), canopy);
        }
        assertFalse(requiredChild(requiredChild(root, "right_root_leg"), "right_root_foot").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_root_leg"), "left_root_foot").isEmpty());
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        CreatureModelTestSupport.assertUvsWithin(root, texture.getWidth(), texture.getHeight());
        CreatureModelTestSupport.assertOpaqueUvs(root, texture, cube -> true);
    }

    @Test
    void rootFeetMeetTheMinecraftModelGroundPlane() {
        final ModelPart root = EntModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds left = CreatureModelTestSupport.bounds(
            requiredChild(root, "left_root_leg")
        );
        final CreatureModelTestSupport.Bounds right = CreatureModelTestSupport.bounds(
            requiredChild(root, "right_root_leg")
        );
        assertEquals(24.0F, left.maxY(), 0.002F, left.toString());
        assertTrue(right.maxY() <= 24.0F && right.maxY() >= 23.5F, right.toString());
    }

    @Test
    void canopyUvBandContainsVisibleGreenLeaves() throws Exception {
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        int greenLeaves = 0;
        for (int y = 92; y < texture.getHeight(); y++) {
            for (int x = 0; x < texture.getWidth(); x++) {
                final int argb = texture.getRGB(x, y);
                if ((argb >>> 24) != 0) {
                    final int red = argb >> 16 & 255;
                    final int green = argb >> 8 & 255;
                    final int blue = argb & 255;
                    if (green > red && green > blue) {
                        greenLeaves++;
                    }
                }
            }
        }
        assertTrue(greenLeaves > 700, "canopy needs a substantial visible leaf mass: " + greenLeaves);
    }

    @Test
    void rootedStrideAndRousedAttackRemainDistinct() {
        final EntModel.State walkingState = new EntModel.State();
        walkingState.walkAnimationPos = 2.35F;
        walkingState.walkAnimationSpeed = 0.58F;
        final EntModel walking = model(walkingState);
        final EntModel.State rousedState = new EntModel.State();
        rousedState.walkAnimationPos = 2.35F;
        rousedState.walkAnimationSpeed = 0.58F;
        rousedState.roused = true;
        rousedState.attackProgress = 0.82F;
        final EntModel roused = model(rousedState);
        assertNotEquals(geometrySnapshot(walking.root()), geometrySnapshot(roused.root()));
    }

    @Test
    void sourceGroundsTheWholeRigWithoutRendererTranslation() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("GROUNDING_OFFSET"));
        assertTrue(source.contains("final EntEntity entity"));
        assertTrue(source.contains("entity.variant().tint()"));
        assertFalse(source.contains("BrambleColossusModel"));
    }

    private static EntModel model(final EntModel.State state) {
        final EntModel model = new EntModel(EntModel.createBodyLayer().bakeRoot());
        model.setupAnim(state);
        return model;
    }
}
