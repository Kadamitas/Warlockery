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

import com.kadamitas.warlockery.entity.MimicryRules.Phase;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class IllusionSpiderModelTest {
    private static final List<String> LEGS = List.of(
        "front_left_leg", "middle_front_left_leg", "middle_rear_left_leg", "rear_left_leg",
        "front_right_leg", "middle_front_right_leg", "middle_rear_right_leg", "rear_right_leg"
    );
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/IllusionSpiderModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/illusion_spider.png"
    );
    private static final Path CONTACT_SHEET = Path.of(
        "build/reports/visual-audit/creatures/illusion_spider-software-contact-sheet.png"
    );

    @Test
    void bakesTheIndependentEightLeggedThresholdWeaverAndPaintsEveryFace() throws Exception {
        assertEquals(64, IllusionSpiderModel.TEXTURE_WIDTH);
        assertEquals(64, IllusionSpiderModel.TEXTURE_HEIGHT);
        final IllusionSpiderModel model = model(new IllusionSpiderModel.State());
        final ModelPart root = model.root();
        final ModelPart shield = requiredChild(root, "shield_cephalothorax");
        assertFalse(requiredChild(shield, "armored_brow").isEmpty());
        assertFalse(requiredChild(shield, "left_mandible").isEmpty());
        assertFalse(requiredChild(shield, "right_mandible").isEmpty());
        assertFalse(requiredChild(shield, "sensory_cluster").isEmpty());
        final ModelPart abdomen = requiredChild(root, "split_wedge_abdomen");
        assertFalse(requiredChild(abdomen, "left_abdomen_wedge").isEmpty());
        assertFalse(requiredChild(abdomen, "right_abdomen_wedge").isEmpty());
        for (final String leg : LEGS) {
            assertThreeStageLeg(root, leg);
        }
        assertTrue(CreatureModelTestSupport.solidPartCount(root) >= 32);
        final ModelPart completeGeometry = IllusionSpiderModel.createBodyLayer().bakeRoot();
        CreatureModelTestSupport.assertUvsWithin(
            completeGeometry, IllusionSpiderModel.TEXTURE_WIDTH, IllusionSpiderModel.TEXTURE_HEIGHT
        );
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        CreatureModelTestSupport.assertOpaqueUvs(completeGeometry, texture, cube -> true);
        assertEquals(0, texture.getRGB(63, 63) >>> 24, "unused atlas remains transparent");
    }

    @Test
    void pinsLowGroundedGeometryAndFourTurnaroundSilhouettes() {
        final IllusionSpiderModel model = model(new IllusionSpiderModel.State());
        final ModelPart root = model.root();
        assertAll(
            () -> assertEquals("d9480bb32af4d802bd0445e28e6d57c3de38f30fe29806c0c36613181db6bcf0", geometrySnapshot(root)),
            () -> assertEquals(new CreatureModelTestSupport.Bounds(
                -10.119495F, 4.6741467F, -19.550697F, 10.119495F, 24.019838F, 18.143047F
            ),
                CreatureModelTestSupport.bounds(root)),
            () -> assertEquals(24.019838F, CreatureModelTestSupport.bounds(root).maxY(), 0.001F),
            () -> assertEquals("de549399d125d406bd54553ce07a9dd8431246290c757ef8e456940bf099680b", viewHash(root, 0.0F)),
            () -> assertEquals("be9a5cc7947608351fb5ba80c80e3b2165e8f22cd916b62eeeebe310c0876142", viewHash(root, -1.5707964F)),
            () -> assertEquals("d8f0e21ab415964ffd507697936cdb800aa113d5ba1379998a12296e45e44ba0", viewHash(root, 3.1415927F)),
            () -> assertEquals("07b6816d29eed3ca69880b9f3b6288a1063a8311ab732bf4113c0a90475f337c", viewHash(root, -0.7853982F))
        );
    }

    @Test
    void neutralSkitterAndOneTimeSnareAreDistinctPinnedPoses() {
        final IllusionSpiderModel neutral = model(new IllusionSpiderModel.State());
        final IllusionSpiderModel moving = model(motionState());
        final IllusionSpiderModel.State snareState = motionState();
        snareState.walkAnimationSpeed = 0.0F;
        snareState.phase = Phase.SNARE;
        final IllusionSpiderModel snare = model(snareState);
        assertAll(
            () -> assertEquals("d9480bb32af4d802bd0445e28e6d57c3de38f30fe29806c0c36613181db6bcf0", geometrySnapshot(neutral.root())),
            () -> assertEquals("1dc36ff5fa040583cfa4fd93a3940343a2f56b9716e5e044a87035e1a105127e", geometrySnapshot(moving.root())),
            () -> assertEquals("0b5470ae89ccc55d63ca0510624818aa9e8058c48c7fd6f06d467e8a5dd34d0f", geometrySnapshot(snare.root())),
            () -> assertNotEquals(geometrySnapshot(neutral.root()), geometrySnapshot(moving.root())),
            () -> assertNotEquals(geometrySnapshot(moving.root()), geometrySnapshot(snare.root()))
        );
        assertTrue(requiredChild(snare.root(), "left_snare_strand").visible);
        assertTrue(requiredChild(snare.root(), "right_snare_strand").visible);
        assertFalse(requiredChild(neutral.root(), "left_snare_strand").visible);
        assertFalse(requiredChild(neutral.root(), "right_snare_strand").visible);
        for (final String leg : LEGS) {
            assertNotEquals(requiredChild(neutral.root(), leg).yRot, requiredChild(snare.root(), leg).yRot,
                leg + " must independently close around the threshold");
        }
    }

    @Test
    void snareStrandsCrossBeneathTheThresholdInTheFrontPlane() {
        final IllusionSpiderModel.State state = new IllusionSpiderModel.State();
        state.phase = Phase.SNARE;
        final ModelPart root = model(state).root();
        final ModelPart left = requiredChild(root, "left_snare_strand");
        final ModelPart right = requiredChild(root, "right_snare_strand");
        assertTrue(left.visible);
        assertTrue(right.visible);
        assertTrue(left.zRot > 0.35F, "left strand must rise across the frontal threshold");
        assertTrue(right.zRot < -0.35F, "right strand must cross it in the opposite direction");
        assertEquals(0.0F, left.yRot, 0.0001F, "strand must not lie flat on the floor");
        assertEquals(0.0F, right.yRot, 0.0001F, "strand must not lie flat on the floor");
    }

    @Test
    void neutralSoftwareSilhouetteSuppressesTheOneTimeSnareGeometry() {
        final ModelPart neutralRoot = model(new IllusionSpiderModel.State()).root();
        final ModelPart neutralLeft = requiredChild(neutralRoot, "left_snare_strand");
        final ModelPart neutralRight = requiredChild(neutralRoot, "right_snare_strand");
        assertTrue(neutralLeft.skipDraw, "neutral visitor must skip the left one-time strand");
        assertTrue(neutralRight.skipDraw, "neutral visitor must skip the right one-time strand");
        assertEquals(0.0F, neutralLeft.xScale, 0.0001F,
            "neutral geometry visitor must receive a collapsed left strand");
        assertEquals(0.0F, neutralLeft.yScale, 0.0001F);
        assertEquals(0.0F, neutralLeft.zScale, 0.0001F);
        assertEquals(0.0F, neutralRight.xScale, 0.0001F,
            "neutral geometry visitor must receive a collapsed right strand");
        assertEquals(0.0F, neutralRight.yScale, 0.0001F);
        assertEquals(0.0F, neutralRight.zScale, 0.0001F);

        final IllusionSpiderModel.State snareState = new IllusionSpiderModel.State();
        snareState.phase = Phase.SNARE;
        final ModelPart snareRoot = model(snareState).root();
        assertFalse(requiredChild(snareRoot, "left_snare_strand").skipDraw);
        assertFalse(requiredChild(snareRoot, "right_snare_strand").skipDraw);
        assertEquals(1.0F, requiredChild(snareRoot, "left_snare_strand").xScale, 0.0001F);
        assertEquals(1.0F, requiredChild(snareRoot, "right_snare_strand").xScale, 0.0001F);
    }

    @Test
    void sourceOwnsItsEntityStateRigAnimationAndAtlas() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<IllusionSpiderModel.State>"));
        assertTrue(source.contains("new MeshDefinition()"));
        assertTrue(source.contains("CubeListBuilder"));
        assertTrue(source.contains("PartPose"));
        assertTrue(source.contains("public static void extractRenderState("));
        assertTrue(source.contains("final IllusionSpiderEntity entity"));
        assertTrue(source.contains("entity.presentationPhase()"));
        assertFalse(source.contains("entity.mimicCore()"));
        for (final String forbidden : List.of(
            "ArcaneCreatureModel", "CreatureModelProfile", "WarlockeryModel", "GeometryHelper",
            "AnimationHelper", "ModelHelper", "RigCatalog", "IllusionCreeperModel",
            "IllusionZombieModel", "extends SpiderModel", "net.minecraft.client.model.SpiderModel"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        assertEquals(64, texture.getWidth());
        assertEquals(64, texture.getHeight());
        assertTrue(hasTransparentPixel(texture));
        assertEquals("acc17a7eee9dc3fd0ba01cb33f34fc13f8b1f80561562a425a0775da5db01116", hash(TEXTURE));
    }

    @Test
    void writesFrontSideRearThreeQuarterSkitterAndSnareSheet() throws Exception {
        final IllusionSpiderModel.State idle = new IllusionSpiderModel.State();
        final IllusionSpiderModel.State snare = motionState();
        snare.walkAnimationSpeed = 0.0F;
        snare.phase = Phase.SNARE;
        writeContactSheet(List.of(
            view(idle, 0.0F),
            view(idle, -1.5707964F),
            view(idle, 3.1415927F),
            view(idle, -0.7853982F),
            view(motionState(), 0.0F),
            view(snare, 0.0F)
        ));
        final BufferedImage sheet = ImageIO.read(CONTACT_SHEET.toFile());
        assertEquals(768, sheet.getWidth());
        assertEquals(160, sheet.getHeight());
    }

    private static void assertThreeStageLeg(final ModelPart root, final String legName) {
        final ModelPart upper = requiredChild(root, legName);
        final ModelPart lower = requiredChild(upper, legName + "_lower");
        assertFalse(upper.isEmpty(), legName);
        assertFalse(lower.isEmpty(), legName + "_lower");
        assertFalse(requiredChild(lower, legName + "_hook").isEmpty(), legName + "_hook");
    }

    private static IllusionSpiderModel model(final IllusionSpiderModel.State state) {
        final IllusionSpiderModel model = new IllusionSpiderModel(
            IllusionSpiderModel.createBodyLayer().bakeRoot()
        );
        model.setupAnim(state);
        return model;
    }

    private static IllusionSpiderModel.State motionState() {
        final IllusionSpiderModel.State state = new IllusionSpiderModel.State();
        state.yRot = -9.0F;
        state.xRot = 3.0F;
        state.walkAnimationPos = 3.1F;
        state.walkAnimationSpeed = 0.72F;
        state.ageInTicks = 28.5F;
        state.phase = Phase.LURE;
        return state;
    }

    private static String viewHash(final ModelPart root, final float rotation) {
        root.yRot = rotation;
        final String hash = imageSnapshot(softwareSnapshot(
            root, CreatureModelTestSupport.Projection.FRONT, 160, 6
        ));
        root.yRot = 0.0F;
        return hash;
    }

    private static BufferedImage view(final IllusionSpiderModel.State state, final float rotation) {
        final ModelPart root = model(state).root();
        root.yRot = rotation;
        return softwareSnapshot(root, CreatureModelTestSupport.Projection.FRONT, 128, 7);
    }

    private static void writeContactSheet(final List<BufferedImage> views) throws Exception {
        final BufferedImage sheet = new BufferedImage(768, 160, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = sheet.createGraphics();
        try {
            graphics.setColor(new Color(27, 27, 31));
            graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            for (int index = 0; index < views.size(); index++) {
                graphics.drawImage(views.get(index), index * 128, 16, null);
            }
        } finally {
            graphics.dispose();
        }
        Files.createDirectories(CONTACT_SHEET.getParent());
        ImageIO.write(sheet, "png", CONTACT_SHEET.toFile());
    }

    private static boolean hasTransparentPixel(final BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) == 0) return true;
            }
        }
        return false;
    }

    private static String hash(final Path path) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
        );
    }
}
