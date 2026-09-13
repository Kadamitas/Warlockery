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
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class PaleSteedModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/PaleSteedModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/pale_steed.png"
    );

    @Test
    void usesMinecraftsAdultHorseHierarchyAndUvLayout() throws Exception {
        assertEquals(64, PaleSteedModel.TEXTURE_WIDTH);
        assertEquals(64, PaleSteedModel.TEXTURE_HEIGHT);
        final ModelPart root = PaleSteedModel.createBodyLayer().bakeRoot();
        final ModelPart body = requiredChild(root, "body");
        assertFalse(requiredChild(body, "tail").isEmpty());
        final ModelPart headParts = requiredChild(root, "head_parts");
        for (final String part : List.of("head", "mane", "upper_mouth")) {
            assertFalse(requiredChild(headParts, part).isEmpty(), part);
        }
        for (final String leg : List.of(
            "right_hind_leg", "left_hind_leg", "right_front_leg", "left_front_leg"
        )) {
            assertFalse(requiredChild(root, leg).isEmpty(), leg);
        }
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        assertEquals(64, texture.getWidth());
        assertEquals(64, texture.getHeight());
        CreatureModelTestSupport.assertUvsWithin(root, texture.getWidth(), texture.getHeight());
        assertTrue(opaquePixels(texture) > 350, "native skeletal atlas must paint the horse bones");
        assertTrue(opaquePixels(texture) < texture.getWidth() * texture.getHeight(),
            "native skeletal atlas must retain transparent gaps between bones");
    }

    @Test
    void nativeHorseFeetMeetTheModelGroundPlane() {
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(
            PaleSteedModel.createBodyLayer().bakeRoot()
        );
        assertEquals(24.0F, bounds.maxY(), 0.02F);
        assertTrue(bounds.maxZ() - bounds.minZ() >= 25.0F, bounds.toString());
    }

    @Test
    void nativeHorseWalkAndRearAnimationsRemainActive() {
        final PaleSteedModel neutral = model(new PaleSteedModel.State());
        final PaleSteedModel.State walkingState = new PaleSteedModel.State();
        walkingState.walkAnimationPos = 3.4F;
        walkingState.walkAnimationSpeed = 0.86F;
        final PaleSteedModel walking = model(walkingState);
        final PaleSteedModel.State balkingState = new PaleSteedModel.State();
        balkingState.standAnimation = 1.0F;
        balkingState.ageInTicks = 12.0F;
        final PaleSteedModel balking = model(balkingState);
        assertNotEquals(geometrySnapshot(neutral.root()), geometrySnapshot(walking.root()));
        assertNotEquals(geometrySnapshot(walking.root()), geometrySnapshot(balking.root()));
    }

    @Test
    void sourceUsesTheNativeHorseModelAndEquineState() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends HorseModel"));
        assertTrue(source.contains("AbstractEquineModel.createBodyMesh(CubeDeformation.NONE)"));
        assertTrue(source.contains("State extends EquineRenderState"));
        assertTrue(source.contains("final SpectralSteedEntity entity"));
        assertTrue(source.contains("state.standAnimation = state.balking"));
        assertFalse(source.contains("coffin_skull"));
        assertFalse(source.contains("ribbon_tail"));
    }

    private static PaleSteedModel model(final PaleSteedModel.State state) {
        final PaleSteedModel model = new PaleSteedModel(PaleSteedModel.createBodyLayer().bakeRoot());
        model.setupAnim(state);
        return model;
    }

    private static int opaquePixels(final BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    count++;
                }
            }
        }
        return count;
    }
}
