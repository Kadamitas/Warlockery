package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.geometrySnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.MimicryRules.Phase;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class IllusionCreeperModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/IllusionCreeperModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/illusion_creeper.png"
    );

    @Test
    void usesTheNativeCreeperHierarchyAndAtlas() throws Exception {
        assertEquals(64, IllusionCreeperModel.TEXTURE_WIDTH);
        assertEquals(32, IllusionCreeperModel.TEXTURE_HEIGHT);
        final ModelPart root = IllusionCreeperModel.createBodyLayer().bakeRoot();
        for (final String part : List.of(
            "head", "body", "right_hind_leg", "left_hind_leg", "right_front_leg", "left_front_leg"
        )) {
            assertFalse(requiredChild(root, part).isEmpty(), part);
        }
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        assertEquals(64, texture.getWidth());
        assertEquals(32, texture.getHeight());
        CreatureModelTestSupport.assertUvsWithin(root, texture.getWidth(), texture.getHeight());
        CreatureModelTestSupport.assertOpaqueUvs(root, texture, cube -> true);
    }

    @Test
    void fourLegsMeetTheGroundUnderTheBody() {
        final ModelPart root = IllusionCreeperModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        assertEquals(24.0F, bounds.maxY(), 0.001F);
        assertTrue(bounds.minX() >= -4.01F && bounds.maxX() <= 4.01F, bounds.toString());
        assertTrue(bounds.minZ() >= -6.01F && bounds.maxZ() <= 6.01F, bounds.toString());
    }

    @Test
    void walkingAndCollapseRemainDistinctNativeRigPoses() {
        final IllusionCreeperModel neutral = model(new IllusionCreeperModel.State());
        final IllusionCreeperModel.State movingState = new IllusionCreeperModel.State();
        movingState.walkAnimationPos = 2.35F;
        movingState.walkAnimationSpeed = 0.76F;
        movingState.phase = Phase.APPROACH;
        final IllusionCreeperModel moving = model(movingState);
        final IllusionCreeperModel.State collapsedState = new IllusionCreeperModel.State();
        collapsedState.phase = Phase.COLLAPSE;
        final IllusionCreeperModel collapsed = model(collapsedState);
        assertNotEquals(geometrySnapshot(neutral.root()), geometrySnapshot(moving.root()));
        assertNotEquals(geometrySnapshot(neutral.root()), geometrySnapshot(collapsed.root()));
        assertTrue(requiredChild(collapsed.root(), "head").y > requiredChild(neutral.root(), "head").y);
    }

    @Test
    void sourceDelegatesGeometryAndWalkingToMinecraftsCreeperModel() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends CreeperModel"));
        assertTrue(source.contains("CreeperModel.createBodyLayer(CubeDeformation.NONE)"));
        assertTrue(source.contains("super.setupAnim(renderState)"));
        assertTrue(source.contains("final IllusionCreeperEntity entity"));
        assertTrue(source.contains("entity.presentationPhase()"));
        assertFalse(source.contains("front_left_foot"));
        assertFalse(source.contains("tapered_trunk"));
    }

    private static IllusionCreeperModel model(final IllusionCreeperModel.State state) {
        final IllusionCreeperModel model = new IllusionCreeperModel(
            IllusionCreeperModel.createBodyLayer().bakeRoot()
        );
        model.setupAnim(state);
        return model;
    }
}
