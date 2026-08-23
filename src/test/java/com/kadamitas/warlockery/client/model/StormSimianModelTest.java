package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.geometrySnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.imageSnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.softwareSnapshot;
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
import java.util.List;
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
    void bakesAnIndependentStormChargedArborealPrimateHierarchy() throws Exception {
        assertEquals(128, StormSimianModel.TEXTURE_WIDTH);
        assertEquals(128, StormSimianModel.TEXTURE_HEIGHT);
        final ModelPart root = StormSimianModel.createBodyLayer().bakeRoot();
        final ModelPart head = requiredChild(root, "head");
        for (final String feature : java.util.List.of(
            "muzzle", "left_ear", "right_ear", "storm_crown"
        )) {
            assertFalse(requiredChild(head, feature).isEmpty(), feature);
        }
        assertFalse(requiredChild(root, "torso").isEmpty());
        assertFalse(requiredChild(root, "storm_band").isEmpty());
        assertThreeStageLimb(root, "left_arm", "left_forearm", "left_hand");
        assertThreeStageLimb(root, "right_arm", "right_forearm", "right_hand");
        assertThreeStageLimb(root, "left_leg", "left_shin", "left_foot");
        assertThreeStageLimb(root, "right_leg", "right_shin", "right_foot");
        final ModelPart tail = requiredChild(root, "tail_base");
        assertFalse(requiredChild(requiredChild(tail, "tail_mid"), "tail_tip").isEmpty());
        CreatureModelTestSupport.assertUvsWithin(
            root, StormSimianModel.TEXTURE_WIDTH, StormSimianModel.TEXTURE_HEIGHT
        );
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        CreatureModelTestSupport.assertOpaqueUvs(root, texture, cube -> true);
        assertEquals(0, texture.getRGB(127, 127) >>> 24, "unused atlas corner stays transparent");
    }

    @Test
    void conceptTopologyCarriesStormAccentsWeightBearingLimbsAndArchedTail() {
        final ModelPart root = StormSimianModel.createBodyLayer().bakeRoot();
        assertFalse(requiredChild(requiredChild(root, "head"), "storm_crown").isEmpty());
        assertFalse(requiredChild(root, "storm_band").isEmpty());
        for (final String side : List.of("left", "right")) {
            assertThreeStageLimb(root, side + "_arm", side + "_forearm", side + "_hand");
            assertThreeStageLimb(root, side + "_leg", side + "_shin", side + "_foot");
        }
        final ModelPart tailMid = requiredChild(requiredChild(root, "tail_base"), "tail_mid");
        assertFalse(requiredChild(tailMid, "tail_tip").isEmpty());
    }

    @Test
    void neutralSilhouetteTracksTheFoldedStormSimianConceptEnvelope() {
        final ModelPart root = StormSimianModel.createBodyLayer().bakeRoot();
        final double frontAspect = silhouetteAspect(
            root, CreatureModelTestSupport.Projection.FRONT
        );
        final double sideAspect = silhouetteAspect(
            root, CreatureModelTestSupport.Projection.SIDE
        );
        assertTrue(frontAspect >= 0.60 && frontAspect <= 0.72,
            "compact arboreal-primate front must stay balanced, got " + frontAspect);
        assertTrue(sideAspect >= 0.78 && sideAspect <= 0.92,
            "arched tail and folded legs must retain profile depth, got " + sideAspect);
    }

    @Test
    void transformedBoundsMeetTheGroundAndSilhouettesStayApproved() {
        final ModelPart root = StormSimianModel.createBodyLayer().bakeRoot();
        assertEquals("48352607d2ac1f237e4bef84eeb32d60c4d679404d499f87e4920e089eb1cb60",
            geometrySnapshot(root));
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        assertEquals(27.041658F, bounds.maxY(), 0.001F);
        final float foldedAspect = (bounds.maxX() - bounds.minX())
            / (bounds.maxY() - bounds.minY());
        assertTrue(foldedAspect >= 0.60F && foldedAspect <= 0.72F,
            "neutral limbs must remain inside the compact primate envelope: "
                + foldedAspect);
        assertEquals(new CreatureModelTestSupport.Bounds(
            -6.470356F, 7.345602F, -6.2330847F,
            6.470356F, 27.041658F, 10.567145F
        ), bounds);
        assertEquals("a8e9187477e64123df6cec796b97fc40da0cb130bb86fab67d29523bc9e5ff69",
            imageSnapshot(softwareSnapshot(
            root, CreatureModelTestSupport.Projection.FRONT, 192, 6
        )));
        assertEquals("8a1dd56b1f063303dc60120012b8e29436346f545fc691aafb4538d378d88df7",
            imageSnapshot(softwareSnapshot(
            root, CreatureModelTestSupport.Projection.SIDE, 192, 6
        )));
        final ModelPart threeQuarter = StormSimianModel.createBodyLayer().bakeRoot();
        threeQuarter.yRot = 0.7853982F;
        assertEquals("eac7805f11ae5ebb7aa7efafbf81704a99b47ce69119094c7c630e33523bee6e",
            imageSnapshot(softwareSnapshot(
            threeQuarter, CreatureModelTestSupport.Projection.FRONT, 192, 6
        )));
    }

    @Test
    void frontViewShowsBroadSailSurfaceRatherThanBareSpars() {
        final BufferedImage front = softwareSnapshot(
            StormSimianModel.createBodyLayer().bakeRoot(),
            CreatureModelTestSupport.Projection.FRONT,
            192,
            6
        );
        assertTrue(opaquePixelCount(front) > 6_000,
            "front view needs broad storm-sail area, not only long leading spars");
    }

    @Test
    void neutralLopingAndGustPosesStayDistinctAndApproved() {
        final StormSimianModel neutral = new StormSimianModel(
            StormSimianModel.createBodyLayer().bakeRoot()
        );
        neutral.setupAnim(new StormSimianModel.State());
        assertEquals("991211200f2aa271981255f87b584d08f7583223a03b6152616e8fee8d413bd7",
            geometrySnapshot(neutral.root()));

        final StormSimianModel moving = new StormSimianModel(
            StormSimianModel.createBodyLayer().bakeRoot()
        );
        final StormSimianModel.State movingState = motionState();
        moving.setupAnim(movingState);
        assertEquals("131e785b3ec44bc4ad3edc0b43ae05ad4ed6870649244dff0c47384766b910e4",
            geometrySnapshot(moving.root()));

        final StormSimianModel special = new StormSimianModel(
            StormSimianModel.createBodyLayer().bakeRoot()
        );
        final StormSimianModel.State specialState = motionState();
        specialState.charge = 80;
        specialState.chargedGustReady = true;
        special.setupAnim(specialState);
        assertEquals("18f22fa27923717c42a5b934b57642e53bfcc53d9f409947bfd62f3f761c5b52",
            geometrySnapshot(special.root()));
    }

    @Test
    void stormChargeAndGustAnimateAccentsAndArmsIndependently() {
        final StormSimianModel neutral = new StormSimianModel(
            StormSimianModel.createBodyLayer().bakeRoot()
        );
        final StormSimianModel.State neutralState = new StormSimianModel.State();
        neutralState.ageInTicks = 7.0F;
        neutral.setupAnim(neutralState);

        final StormSimianModel airborne = new StormSimianModel(
            StormSimianModel.createBodyLayer().bakeRoot()
        );
        final StormSimianModel.State airborneState = new StormSimianModel.State();
        airborneState.ageInTicks = 19.0F;
        airborneState.airborne = true;
        airborneState.charge = 40;
        airborne.setupAnim(airborneState);

        final ModelPart neutralCrown = requiredChild(requiredChild(neutral.root(), "head"), "storm_crown");
        final ModelPart chargedCrown = requiredChild(requiredChild(airborne.root(), "head"), "storm_crown");
        assertTrue(chargedCrown.y < neutralCrown.y, "charge must lift the storm crown");
        assertNotEquals(requiredChild(neutral.root(), "storm_band").yRot,
            requiredChild(airborne.root(), "storm_band").yRot,
            "charge must stir the storm band");

        final StormSimianModel gust = new StormSimianModel(
            StormSimianModel.createBodyLayer().bakeRoot()
        );
        final StormSimianModel.State gustState = new StormSimianModel.State();
        gustState.ageInTicks = 19.0F;
        gustState.airborne = true;
        gustState.charge = 80;
        gustState.chargedGustReady = true;
        gust.setupAnim(gustState);
        final ModelPart gustLeft = requiredChild(gust.root(), "left_arm");
        final ModelPart gustRight = requiredChild(gust.root(), "right_arm");
        assertTrue(gustLeft.xRot < -1.0F, "gust pose must raise the left arm");
        assertTrue(gustRight.xRot < -1.0F, "gust pose must raise the right arm");
        assertFalse(requiredChild(gust.root(), "left_arm").isEmpty());
        assertFalse(requiredChild(gust.root(), "right_arm").isEmpty());
    }

    @Test
    void sourceOwnsItsRigAndAtlasIsPinned() throws Exception {
        final String source = Files.readString(SOURCE);
        for (final String forbidden : java.util.List.of(
            "ArcaneCreatureModel", "CreatureModelProfile", "WarlockeryModel",
            "AnimationHelper", "GeometryHelper", "ModelHelper", "ImpModel"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        assertFalse(source.contains("import com.kadamitas.warlockery.client"));
        assertFalse(source.contains("static void add"));
        assertEquals(true, source.contains("extractRenderState"));
        assertEquals(true, source.contains("entity.presentationCharge()"));
        assertEquals(true, source.contains("entity.presentationHasGrip()"));
        assertFalse(source.contains("entity.stormSimianState()"));
        assertEquals(128, ImageIO.read(TEXTURE.toFile()).getWidth());
        assertEquals(128, ImageIO.read(TEXTURE.toFile()).getHeight());
        assertEquals("16e562ae8beee0de28b71dc54b69e013eba91542a87a3ed22b93033f99369e48",
            textureHash());
    }

    @Test
    void writesApprovedSoftwareContactSheet() throws Exception {
        final ModelPart neutral = StormSimianModel.createBodyLayer().bakeRoot();
        final ModelPart threeQuarter = StormSimianModel.createBodyLayer().bakeRoot();
        threeQuarter.yRot = 0.7853982F;
        final StormSimianModel moving = new StormSimianModel(
            StormSimianModel.createBodyLayer().bakeRoot()
        );
        moving.setupAnim(motionState());
        final StormSimianModel special = new StormSimianModel(
            StormSimianModel.createBodyLayer().bakeRoot()
        );
        final StormSimianModel.State specialState = motionState();
        specialState.charge = 80;
        specialState.chargedGustReady = true;
        special.setupAnim(specialState);
        writeContactSheet(List.of(
            softwareSnapshot(neutral, CreatureModelTestSupport.Projection.FRONT, 192, 10),
            softwareSnapshot(neutral, CreatureModelTestSupport.Projection.SIDE, 192, 10),
            softwareSnapshot(threeQuarter, CreatureModelTestSupport.Projection.FRONT, 192, 10),
            softwareSnapshot(moving.root(), CreatureModelTestSupport.Projection.FRONT, 192, 10),
            softwareSnapshot(special.root(), CreatureModelTestSupport.Projection.FRONT, 192, 10),
            softwareSnapshot(special.root(), CreatureModelTestSupport.Projection.SIDE, 192, 10)
        ), Path.of("build/reports/visual-audit/creatures/storm_simian-software-contact-sheet.png"));
    }

    private static void assertThreeStageLimb(
        final ModelPart root,
        final String proximalName,
        final String middleName,
        final String distalName
    ) {
        final ModelPart proximal = requiredChild(root, proximalName);
        final ModelPart middle = requiredChild(proximal, middleName);
        assertFalse(proximal.isEmpty());
        assertFalse(middle.isEmpty());
        assertFalse(requiredChild(middle, distalName).isEmpty());
    }

    private static StormSimianModel.State motionState() {
        final StormSimianModel.State state = new StormSimianModel.State();
        state.yRot = -28.0F;
        state.xRot = 9.0F;
        state.walkAnimationPos = 3.15F;
        state.walkAnimationSpeed = 0.82F;
        state.ageInTicks = 47.5F;
        state.airborne = true;
        return state;
    }

    private static String textureHash() throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(TEXTURE)));
    }

    private static long opaquePixelCount(final BufferedImage image) {
        long count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private static double silhouetteAspect(
        final ModelPart root,
        final CreatureModelTestSupport.Projection projection
    ) {
        final BufferedImage image = softwareSnapshot(root, projection, 256, 8);
        int minimumX = image.getWidth();
        int minimumY = image.getHeight();
        int maximumX = -1;
        int maximumY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    minimumX = Math.min(minimumX, x);
                    minimumY = Math.min(minimumY, y);
                    maximumX = Math.max(maximumX, x);
                    maximumY = Math.max(maximumY, y);
                }
            }
        }
        assertTrue(maximumX >= minimumX && maximumY >= minimumY, "empty silhouette");
        return (maximumX - minimumX + 1.0) / (maximumY - minimumY + 1.0);
    }

    private static void writeContactSheet(final List<BufferedImage> views, final Path output)
        throws Exception {
        final BufferedImage sheet = new BufferedImage(576, 384, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = sheet.createGraphics();
        try {
            graphics.setColor(new Color(22, 39, 56));
            graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            for (int index = 0; index < views.size(); index++) {
                graphics.drawImage(views.get(index), index % 3 * 192, index / 3 * 192, null);
            }
        } finally {
            graphics.dispose();
        }
        Files.createDirectories(output.getParent());
        ImageIO.write(sheet, "PNG", output.toFile());
    }
}
