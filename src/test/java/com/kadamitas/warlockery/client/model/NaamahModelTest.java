package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.geometrySnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.imageSnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.softwareSnapshot;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final Path GENERATOR = Path.of(
        "tools/creature_models/generate_naamah.ps1"
    );

    @Test
    void bakesIndependentTallOceanMatriarchHierarchyOnItsOwnAtlas() throws Exception {
        assertEquals(192, NaamahModel.TEXTURE_WIDTH);
        assertEquals(128, NaamahModel.TEXTURE_HEIGHT);
        final ModelPart root = NaamahModel.createBodyLayer().bakeRoot();
        final ModelPart matriarch = requiredChild(root, "ocean_matriarch");
        final ModelPart bodice = requiredChild(matriarch, "tide_bodice");
        final ModelPart head = requiredChild(bodice, "regal_head");
        final ModelPart face = requiredChild(head, "authored_face");
        final ModelPart crown = requiredChild(head, "three_crest_crown");
        final ModelPart hair = requiredChild(head, "architectural_hair");
        assertFalse(face.isEmpty());
        assertFalse(requiredChild(face, "right_ruby_eye").isEmpty());
        assertFalse(requiredChild(face, "left_ruby_eye").isEmpty());
        assertFalse(requiredChild(crown, "center_tide_crest").isEmpty());
        assertFalse(requiredChild(crown, "right_tide_crest").isEmpty());
        assertFalse(requiredChild(crown, "left_tide_crest").isEmpty());
        assertFalse(requiredChild(hair, "right_side_lock").isEmpty());
        assertFalse(requiredChild(hair, "left_side_lock").isEmpty());
        assertFalse(requiredChild(hair, "back_hair_mantle").isEmpty());
        assertFalse(requiredChild(requiredChild(hair, "back_hair_mantle"), "crimson_inner_fin").isEmpty());
        assertFalse(requiredChild(bodice, "tide_ribs").isEmpty());
        assertFalse(requiredChild(bodice, "throat_jewel").isEmpty());
        assertFalse(requiredChild(bodice, "chest_jewel").isEmpty());
        final ModelPart rightArm = requiredChild(bodice, "right_arm");
        final ModelPart leftArm = requiredChild(bodice, "left_arm");
        assertFalse(requiredChild(rightArm, "right_bell_sleeve").isEmpty());
        assertFalse(requiredChild(leftArm, "left_bell_sleeve").isEmpty());
        assertFalse(requiredChild(bodice, "right_wave_crest").isEmpty());
        assertFalse(requiredChild(bodice, "left_wave_crest").isEmpty());
        final ModelPart lower = requiredChild(matriarch, "lower_goddess");
        final ModelPart gown = requiredChild(lower, "split_gown");
        assertFalse(requiredChild(gown, "right_front_skirt").isEmpty());
        assertFalse(requiredChild(gown, "left_front_skirt").isEmpty());
        assertFalse(requiredChild(gown, "oxblood_center_panel").isEmpty());
        final ModelPart rearMantle = requiredChild(gown, "rear_tidal_mantle");
        assertFalse(rearMantle.isEmpty());
        assertFalse(requiredChild(rearMantle, "right_rear_wave").isEmpty());
        assertFalse(requiredChild(rearMantle, "left_rear_wave").isEmpty());
        assertFalse(requiredChild(requiredChild(lower, "right_leg"), "right_thigh_boot").isEmpty());
        assertFalse(requiredChild(requiredChild(lower, "left_leg"), "left_thigh_boot").isEmpty());
        assertTrue(CreatureModelTestSupport.solidPartCount(root) >= 38);
        CreatureModelTestSupport.assertUvsWithin(root, 192, 128);
        CreatureModelTestSupport.assertOpaqueUvs(root, ImageIO.read(TEXTURE.toFile()),
            cube -> cube.path().equals("/ocean_matriarch/tide_bodice"));
    }

    @Test
    void pinsGroundedGoddessGeometryAndReadableFrontSideBackSilhouettes() {
        final ModelPart root = NaamahModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        final String front = view(root, 0.0F);
        final String side = view(root, -1.5707964F);
        final String back = view(root, 3.1415927F);
        final String threeQuarter = view(root, -0.7853982F);
        final int frontWidth = occupiedWidth(renderedView(root, 0.0F));
        final int sideWidth = occupiedWidth(renderedView(root, -1.5707964F));
        assertAll(
            () -> assertEquals(
                "3e332ac7139decf9c48bfd8497db23e58db21ec067733f2901f980ada2e87dc2",
                geometrySnapshot(root)
            ),
            () -> assertEquals(24.007061F, bounds.maxY(), 0.001F),
            () -> assertTrue(bounds.minY() <= -20.0F, "crown must make Naamah visibly tall"),
            () -> assertTrue(bounds.maxX() - bounds.minX() >= 20.0F, "bell sleeves need a regal span"),
            () -> assertTrue(bounds.maxZ() - bounds.minZ() >= 10.0F, "hair and tidal train need authored depth"),
            () -> assertTrue(sideWidth >= frontWidth * 0.58F && sideWidth <= frontWidth * 0.69F,
                "profile width " + sideWidth + " must keep nested bell sleeves, hair mantle, skirt fins, and train within 58%-69% of front width " + frontWidth),
            () -> assertEquals(
                "3ad84e8d5c37b59d3dc70460cfea098c2c7b185d4bacdf1baaf48fef4d407a80",
                front
            ),
            () -> assertEquals(
                "c73f0b2eb0bfce60478d7729252d7683b834b249e537755c5736a8993ab04054",
                side
            ),
            () -> assertEquals(
                "8c505b27397fd1f08f3dedf7e1248613b72c5d82852f64bd2de5947e33fbe75b",
                back
            ),
            () -> assertEquals(
                "c0fdcda7847acbba1d9ce6b0c8478e665cbde6fbfabc90c7471191741aba3341",
                threeQuarter
            ),
            () -> assertNotEquals(front, back, "the asymmetric rear hair clasp must distinguish front and back")
        );
    }

    @Test
    void seaBorneGlideCourtWaveAndDrowningSurgeAreDistinctPinnedPoses() throws Exception {
        final NaamahModel model = new NaamahModel(NaamahModel.createBodyLayer().bakeRoot());
        final NaamahModel.State movement = motionState();
        movement.seaBorne = true;
        model.setupAnim(movement);
        final String movementHash = geometrySnapshot(model.root());

        final NaamahModel.State wave = motionState();
        wave.courtWaveProgress = 0.9F;
        model.setupAnim(wave);
        final String waveHash = geometrySnapshot(model.root());

        final NaamahModel.State surge = motionState();
        surge.drowningSurgeProgress = 0.95F;
        surge.sovereignRefusal = true;
        model.setupAnim(surge);
        final String surgeHash = geometrySnapshot(model.root());
        writeContactSheet(model, wave, surge);

        assertAll(
            () -> assertEquals(
                "406066a6571160588052aca02ebad2add0cd2318f7ec885b5146e02e216fac45",
                movementHash
            ),
            () -> assertEquals(
                "1589b87001423c2f717e45666e442b1ec605a0bc0ab06c0b96b490c70576e632",
                waveHash
            ),
            () -> assertEquals(
                "4a8195178c7d78d2f8191a61bf3a3d134018a8b3c5845c35ef8763f3557da6df",
                surgeHash
            ),
            () -> assertNotEquals(movementHash, waveHash),
            () -> assertNotEquals(waveHash, surgeHash),
            () -> assertNotEquals(movementHash, surgeHash)
        );
    }

    @Test
    void sourceExtractsSynchronizedNaamahPresentationWithoutPlayerRigWeaponOrSharedGeometry() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<NaamahModel.State>"));
        assertTrue(source.contains("public static void extractRenderState("));
        assertTrue(source.contains("final NaamahEntity entity"));
        assertTrue(source.contains("entity.presentationAction()"));
        assertTrue(source.contains("entity.presentationPhase()"));
        assertTrue(source.contains("entity.presentationGazeMending()"));
        assertFalse(source.contains("entity.courtState()"));
        assertFalse(source.contains("entity.regenerationSuppressedUntil()"));
        assertTrue(source.contains("entity.isInWater()"));
        assertTrue(source.contains("Action.COURT_WAVE"));
        assertTrue(source.contains("Action.DROWNING_SURGE"));
        assertTrue(source.contains("Phase.SOVEREIGN_REFUSAL"));
        for (final String forbidden : List.of(
            "ArmedModel", "translateToHand", "HumanoidModel", "PLAYER_SLIM", "SkinnedHumanoid",
            "ArcaneCreatureModel", "CreatureModelProfile", "GeometryHelper", "AnimationHelper",
            "ModelHelper", "RigCatalog", "ModelCatalog", "FamilyAnimator", "extends Warlockery",
            "tentacle", "mermaid", "fish_tail", "held_weapon"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }

        final String generator = Files.readString(GENERATOR);
        assertTrue(generator.contains("naamah.png"));
        assertFalse(generator.contains("nami.png"));
        assertFalse(generator.contains("GenerateOriginalAssets"));
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        assertEquals(192, texture.getWidth());
        assertEquals(128, texture.getHeight());
        assertTrue(hasTransparentPixel(texture));
        assertEquals(
            "12545935712ee93422a5c611202ee03fc186de68df6b275d7e492d945b1f1eca",
            hash(TEXTURE)
        );
    }

    @Test
    void namiSourceTextureAndTestsRemainByteUnchanged() throws Exception {
        final Map<String, String> expected = new LinkedHashMap<>();
        expected.put("src/main/java/com/kadamitas/warlockery/entity/NamiEntity.java", "30e1aedf93173e67347e950a76c766783c6f5cb274a4f5aa5036466bfef275f5");
        expected.put("src/main/java/com/kadamitas/warlockery/entity/NamiLifeState.java", "90176a29495e38a9d6fdbda28a10bfc61cef47f0706159501a38c3d0f70d83b3");
        expected.put("src/main/java/com/kadamitas/warlockery/entity/NamiLifeRuntime.java", "250936941d320fce1c1b650d8572b42b05cd2c411a7b87eb4bd9a6000584e211");
        expected.put("src/main/java/com/kadamitas/warlockery/entity/NamiLifeRules.java", "f6141424df69480e59697d19594bbb8d6b1cd7549c33a8119c0554d2c7dc36a6");
        expected.put("src/main/java/com/kadamitas/warlockery/entity/NamiLifeGameTests.java", "473fe174525c59f9577a8c67bb40ca5659bc964a116a9f31b7f63a86fce53fe2");
        expected.put("src/main/java/com/kadamitas/warlockery/ritual/NamiRitualGameTests.java", "c676ee4a2988ac2573491eac86ff0370f0896a18d411286da908b6706a341a0e");
        expected.put("src/test/java/com/kadamitas/warlockery/entity/NamiLifeStateTest.java", "4d04a01c330b00d26b9380cd94f39be9927deab16d4a56469f615edf5530e533");
        expected.put("src/test/java/com/kadamitas/warlockery/entity/NamiLifeRulesTest.java", "f238932557e2aa55c7aa7cb6ad559fc66bf3166630a527bdfab17dcfd6998d00");
        expected.put("src/main/resources/assets/warlockery/textures/entity/nami.png", "9ffc406b45205ccfb2e9c1faddb9702a0cb430aa14d37bcbad00407a912ca2d0");
        for (final Map.Entry<String, String> entry : expected.entrySet()) {
            assertEquals(entry.getValue(), hash(Path.of(entry.getKey())), entry.getKey());
        }
    }

    private static NaamahModel.State motionState() {
        final NaamahModel.State state = new NaamahModel.State();
        state.yRot = -18.0F;
        state.xRot = 7.0F;
        state.walkAnimationPos = 3.4F;
        state.walkAnimationSpeed = 0.52F;
        state.ageInTicks = 67.0F;
        return state;
    }

    private static String view(final ModelPart root, final float rotation) {
        return imageSnapshot(renderedView(root, rotation));
    }

    private static BufferedImage renderedView(final ModelPart root, final float rotation) {
        root.yRot = rotation;
        final BufferedImage image = softwareSnapshot(
            root, CreatureModelTestSupport.Projection.FRONT, 160, 7
        );
        root.yRot = 0.0F;
        return image;
    }

    private static int occupiedWidth(final BufferedImage image) {
        int minimum = image.getWidth();
        int maximum = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    minimum = Math.min(minimum, x);
                    maximum = Math.max(maximum, x);
                }
            }
        }
        return maximum < minimum ? 0 : maximum - minimum + 1;
    }

    private static String hash(final Path path) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
        );
    }

    private static boolean hasTransparentPixel(final BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) == 0) return true;
            }
        }
        return false;
    }

    private static void writeContactSheet(
        final NaamahModel model,
        final NaamahModel.State wave,
        final NaamahModel.State surge
    ) throws Exception {
        final BufferedImage sheet = new BufferedImage(1120, 176, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new Color(30, 35, 39));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        final float[] turns = {0.0F, 1.5707964F, 3.1415927F, -1.5707964F, -0.7853982F};
        for (int index = 0; index < turns.length; index++) {
            model.setupAnim(new NaamahModel.State());
            model.root().yRot = turns[index];
            graphics.drawImage(
                softwareSnapshot(model.root(), CreatureModelTestSupport.Projection.FRONT, 160, 8),
                index * 160, 0, null
            );
        }
        model.setupAnim(wave);
        graphics.drawImage(
            softwareSnapshot(model.root(), CreatureModelTestSupport.Projection.FRONT, 160, 8),
            800, 0, null
        );
        model.setupAnim(surge);
        graphics.drawImage(
            softwareSnapshot(model.root(), CreatureModelTestSupport.Projection.FRONT, 160, 8),
            960, 0, null
        );
        graphics.dispose();
        final Path output = Path.of(
            "build/reports/visual-audit/creatures/naamah-software-contact-sheet.png"
        );
        Files.createDirectories(output.getParent());
        ImageIO.write(sheet, "png", output.toFile());
    }
}
