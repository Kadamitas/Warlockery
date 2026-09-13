package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.bounds;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.geometrySnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.NaamahEntity;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class NaamahModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/NaamahModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/naamah.png"
    );
    private static final Path NAMI_TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/nami.png"
    );

    @Test
    void pairedSweptHornsAndRegaliaReadAsADemonQueen() throws Exception {
        final ModelPart root = NaamahModel.createBodyLayer().bakeRoot();
        final ModelPart queen = requiredChild(root, "demon_queen");
        final ModelPart torso = requiredChild(queen, "torso");
        final ModelPart head = requiredChild(torso, "head");
        final ModelPart rightHorn = requiredChild(head, "right_horn");
        final ModelPart leftHorn = requiredChild(head, "left_horn");
        assertFalse(requiredChild(requiredChild(rightHorn, "middle"), "tip").isEmpty());
        assertFalse(requiredChild(requiredChild(leftHorn, "middle"), "tip").isEmpty());
        assertTrue(bounds(head).maxX() - bounds(head).minX() >= 15.0F,
            "swept horns must widen the head silhouette");
        assertFalse(requiredChild(torso, "throat_ruby").isEmpty());
        assertFalse(requiredChild(torso, "hair_mantle").isEmpty());
        final ModelPart face = requiredChild(head, "face");
        assertFalse(requiredChild(face, "right_brow").isEmpty());
        assertFalse(requiredChild(face, "left_brow").isEmpty());
        assertFalse(requiredChild(face, "nose_ridge").isEmpty());
        assertFalse(requiredChild(face, "severe_mouth").isEmpty());

        final ModelPart gown = requiredChild(queen, "gown");
        final ModelPart rightSkirt = requiredChild(gown, "right_skirt");
        final ModelPart leftSkirt = requiredChild(gown, "left_skirt");
        assertTrue(bounds(rightSkirt).maxY() - bounds(rightSkirt).minY() >= 9.5F);
        assertTrue(bounds(leftSkirt).maxY() - bounds(leftSkirt).minY() >= 9.5F);
        assertFalse(requiredChild(requiredChild(queen, "demon_tail"), "tail_tip").isEmpty());

        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        assertEquals(NaamahModel.TEXTURE_WIDTH, texture.getWidth());
        assertEquals(NaamahModel.TEXTURE_HEIGHT, texture.getHeight());
        CreatureModelTestSupport.assertUvsWithin(
            root, NaamahModel.TEXTURE_WIDTH, NaamahModel.TEXTURE_HEIGHT
        );
        CreatureModelTestSupport.assertOpaqueUvs(root, texture, cube -> true);
        assertTrue(hasTransparentPixel(texture));
        final int colors = opaqueColors(texture);
        assertTrue(colors >= 6 && colors <= 24, "restrained atlas palette: " + colors);
    }

    @Test
    void courtWaveSurgeAndTailMotionRemainDistinct() {
        final NaamahModel model = new NaamahModel(NaamahModel.createBodyLayer().bakeRoot());
        final NaamahModel.State idle = new NaamahModel.State();
        idle.ageInTicks = 2.0F;
        model.setupAnim(idle);
        final String idlePose = geometrySnapshot(model.root());

        final NaamahModel.State wave = new NaamahModel.State();
        wave.ageInTicks = 2.0F;
        wave.courtWaveProgress = 1.0F;
        model.setupAnim(wave);
        final String wavePose = geometrySnapshot(model.root());

        final NaamahModel.State surgeEarly = new NaamahModel.State();
        surgeEarly.drowningSurgeProgress = 1.0F;
        surgeEarly.ageInTicks = 1.0F;
        model.setupAnim(surgeEarly);
        final String earlyPose = geometrySnapshot(model.root());
        surgeEarly.ageInTicks = 5.0F;
        model.setupAnim(surgeEarly);
        final String latePose = geometrySnapshot(model.root());

        assertNotEquals(idlePose, wavePose);
        assertNotEquals(wavePose, earlyPose);
        assertNotEquals(earlyPose, latePose, "actual surge must animate instead of freezing");
    }

    @Test
    void namiAndNaamahRemainVisuallyAndStructurallyDistinct() throws Exception {
        assertFalse(java.util.Arrays.equals(
            Files.readAllBytes(NAMI_TEXTURE),
            Files.readAllBytes(TEXTURE)
        ));
        final String source = Files.readString(SOURCE);
        assertFalse(source.contains("NamiModel"));
        assertFalse(source.contains("NamiEntity"));
        assertTrue(source.contains("demon_queen"));
        assertTrue(source.contains("right_horn"));
        assertTrue(source.contains("left_horn"));
    }

    @Test
    void exposesTheExistingCourtStateAdapter() {
        assertDoesNotThrow(() -> NaamahModel.class.getDeclaredMethod(
            "extractRenderState", NaamahEntity.class, NaamahModel.State.class, float.class
        ));
    }

    private static boolean hasTransparentPixel(final BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int opaqueColors(final BufferedImage image) {
        final Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int pixel = image.getRGB(x, y);
                if ((pixel >>> 24) == 255) {
                    colors.add(pixel & 0xFFFFFF);
                }
            }
        }
        return colors.size();
    }
}
