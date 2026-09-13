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

final class StormSimianModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/StormSimianModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/storm_simian.png"
    );

    @Test
    void bakesTwoStageWingsAlongsideThePrimateRig() throws Exception {
        assertEquals(128, StormSimianModel.TEXTURE_WIDTH);
        assertEquals(128, StormSimianModel.TEXTURE_HEIGHT);
        final ModelPart root = StormSimianModel.createBodyLayer().bakeRoot();
        final ModelPart leftWing = requiredChild(root, "left_wing");
        final ModelPart rightWing = requiredChild(root, "right_wing");
        assertFalse(leftWing.isEmpty());
        assertFalse(rightWing.isEmpty());
        assertFalse(requiredChild(leftWing, "left_wing_tip").isEmpty());
        assertFalse(requiredChild(rightWing, "right_wing_tip").isEmpty());
        assertTrue(CreatureModelTestSupport.cubes(leftWing).size() >= 9,
            "left wing needs a stepped vane and three separate primary feathers");
        assertTrue(CreatureModelTestSupport.cubes(rightWing).size() >= 9,
            "right wing needs a stepped vane and three separate primary feathers");
        assertFalse(requiredChild(root, "torso").isEmpty());
        assertFalse(requiredChild(root, "tail_base").isEmpty());
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        CreatureModelTestSupport.assertUvsWithin(root, texture.getWidth(), texture.getHeight());
        CreatureModelTestSupport.assertOpaqueUvs(root, texture, cube -> true);
    }

    @Test
    void airborneWingsBeatAndChargedGustFullySpreadsThem() {
        final StormSimianModel neutral = model(new StormSimianModel.State());
        final StormSimianModel.State airborneState = new StormSimianModel.State();
        airborneState.airborne = true;
        airborneState.ageInTicks = 19.0F;
        final StormSimianModel airborne = model(airborneState);
        final StormSimianModel.State gustState = new StormSimianModel.State();
        gustState.airborne = true;
        gustState.ageInTicks = 19.0F;
        gustState.charge = 80;
        gustState.chargedGustReady = true;
        final StormSimianModel gust = model(gustState);
        assertNotEquals(geometrySnapshot(neutral.root()), geometrySnapshot(airborne.root()));
        assertNotEquals(geometrySnapshot(airborne.root()), geometrySnapshot(gust.root()));
        assertEquals(-1.18F, requiredChild(gust.root(), "left_wing").zRot, 0.001F);
        assertEquals(1.18F, requiredChild(gust.root(), "right_wing").zRot, 0.001F);
    }

    @Test
    void wingUvRegionsContainVisibleStormFeathers() throws Exception {
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        assertTrue(opaquePixels(texture, 0, 80, 20, 97) > 300);
        assertTrue(opaquePixels(texture, 28, 80, 46, 95) > 250);
    }

    @Test
    void sourceAnimatesWingsFromActualAirborneAndChargeState() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("state.airborne"));
        assertTrue(source.contains("state.chargedGustReady"));
        assertTrue(source.contains("leftWing.zRot"));
        assertTrue(source.contains("rightWing.zRot"));
        assertTrue(source.contains("entity.presentationCharge()"));
    }

    private static StormSimianModel model(final StormSimianModel.State state) {
        final StormSimianModel model = new StormSimianModel(StormSimianModel.createBodyLayer().bakeRoot());
        model.setupAnim(state);
        return model;
    }

    private static int opaquePixels(
        final BufferedImage image,
        final int left,
        final int top,
        final int right,
        final int bottom
    ) {
        int count = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    count++;
                }
            }
        }
        return count;
    }
}
