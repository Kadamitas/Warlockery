package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.geometrySnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.imageSnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.softwareSnapshot;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class OwlModelTest {
    private static final Path SOURCE = Path.of("src/main/java/com/kadamitas/warlockery/client/model/OwlModel.java");
    private static final Path TEXTURE = Path.of("src/main/resources/assets/warlockery/textures/entity/owl.png");

    @Test
    void bakesIndependentHeartFacedPerchingOwl() throws Exception {
        assertEquals(128, OwlModel.TEXTURE_WIDTH);
        assertEquals(128, OwlModel.TEXTURE_HEIGHT);
        assertTrue(EntityModel.class.isAssignableFrom(OwlModel.class));
        assertTrue(Modifier.isPublic(OwlModel.State.class.getModifiers()));
        final ModelPart root = OwlModel.createBodyLayer().bakeRoot();
        final ModelPart head = requiredChild(root, "head");
        for (final String feature : List.of("face_disk", "left_eye", "right_eye", "beak")) {
            assertFalse(requiredChild(head, feature).isEmpty());
        }
        assertFalse(requiredChild(requiredChild(root, "body"), "belly").isEmpty());
        for (final String side : List.of("left", "right")) {
            assertFalse(requiredChild(root, side + "_wing").isEmpty());
            assertFalse(requiredChild(requiredChild(root, side + "_leg"), side + "_talon").isEmpty());
        }
        assertFalse(requiredChild(root, "tail").isEmpty());
        CreatureModelTestSupport.assertUvsWithin(root, 128, 128);
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        CreatureModelTestSupport.assertOpaqueUvs(root, texture, cube -> true);
        assertEquals(0, texture.getRGB(127, 127) >>> 24);
    }

    @Test
    void neutralConceptPoseKeepsAnUprightTaperInsideFoldedWingMantles() {
        final ModelPart root = OwlModel.createBodyLayer().bakeRoot();
        assertFalse(requiredChild(requiredChild(root, "body"), "belly").isEmpty(),
            "the rounded breast must retain its distinct belly plate");
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        final float height = bounds.maxY() - bounds.minY();
        final float width = bounds.maxX() - bounds.minX();
        final float depth = bounds.maxZ() - bounds.minZ();
        final float frontAspect = width / height;
        final float sideAspect = depth / height;
        assertAll("folded Owl concept proportions",
            () -> assertTrue(frontAspect >= 0.62F && frontAspect <= 0.82F,
                "front must track concept aspect 0.711: " + frontAspect),
            () -> assertTrue(sideAspect >= 0.68F && sideAspect <= 0.90F,
                "left profile must track concept aspect 0.783: " + sideAspect),
            () -> assertTrue(width <= 18.0F,
                "folded primaries must remain inside an upright mantle span: " + width),
            () -> assertEquals(22.5F, bounds.maxY(), 0.001F)
        );
    }

    @Test
    void perchedBoundsAndApprovedViewsArePinned() {
        final ModelPart root = OwlModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        assertEquals(22.5F, bounds.maxY(), 0.001F);
        assertTrue(bounds.maxY() - bounds.minY() >= 18.0F);
        assertAll("approved Owl bake and views",
            () -> assertEquals("db254a5ea09fcea97ee2837c020cd83bb7d68e519f8198387bdb3b12093bef8a", geometrySnapshot(root)),
            () -> assertViews(root,
                "1f63d5b9212237e6339cdecca0509cb78f32d57fc74e044534281a8513983771",
                "f3154f19adeb3251642e7d753afb83f721fea1839a8d6780673fd122db82d39d",
                "be75cc209d19a53d093c2d5da2c1052293827b272e9998b9efc8b5d15c59c137"));
    }

    @Test
    void perchedFlightAndBankingPosesAreDistinctAndPinned() {
        final String neutral = poseHash(false, false);
        final String flying = poseHash(true, false);
        final String banking = poseHash(true, true);
        assertNotEquals(neutral, flying);
        assertNotEquals(flying, banking);
        assertAll("approved Owl poses",
            () -> assertEquals("4aa3f12cc5bf6f07eeb546e17580a02a0334b80dbf059a762823a1843a04a2d7", neutral),
            () -> assertEquals("43b71a84c351558ec124ebd9d5b2c36e8dab333a8b3d64333624926cb260c153", flying),
            () -> assertEquals("bd731f49fe8f1b2606293a121e561001e1c35127d3a0fec5b070eef94282d152", banking));
    }

    @Test
    void sourceOwnsTheOwlAndAtlasIsPinned() throws Exception {
        final String source = Files.readString(SOURCE);
        for (final String forbidden : List.of("ArcaneCreatureModel", "CreatureModelProfile", "FamiliarCatModel", "ToadModel", "HexBatModel", "ParasyticLouseModel", "ModelHelper", "GeometryHelper", "FamilyAnimator")) assertFalse(source.contains(forbidden), forbidden);
        assertTrue(source.contains("extends EntityModel<OwlModel.State>"));
        assertTrue(source.contains("extractRenderState(final ArcaneMob entity"));
        assertEquals("fb83c2770f19d63bb18c274f90c3f16c5ae7e336235dcadd11d0ce182e49c480", textureHash());
    }

    @Test
    void writesSoftwareContactSheet() throws Exception {
        writeSheet(List.of(
            view(OwlModel.createBodyLayer().bakeRoot(), CreatureModelTestSupport.Projection.FRONT),
            view(OwlModel.createBodyLayer().bakeRoot(), CreatureModelTestSupport.Projection.SIDE),
            threeQuarter(), posed(false, false), posed(true, false), posed(true, true)
        ), Path.of("build/reports/visual-audit/creatures/owl-software-contact-sheet.png"));
    }

    private static OwlModel.State motionState(final boolean flying, final boolean diving) {
        final OwlModel.State state = new OwlModel.State();
        state.yRot = 28.0F;
        state.xRot = -11.0F;
        state.walkAnimationPos = 2.1F;
        state.walkAnimationSpeed = 0.72F;
        state.ageInTicks = 44.25F;
        state.flying = flying;
        state.diving = diving;
        return state;
    }

    private static String poseHash(final boolean flying, final boolean diving) {
        final OwlModel model = new OwlModel(OwlModel.createBodyLayer().bakeRoot());
        model.setupAnim(motionState(flying, diving));
        return geometrySnapshot(model.root());
    }

    private static BufferedImage posed(final boolean flying, final boolean diving) {
        final OwlModel model = new OwlModel(OwlModel.createBodyLayer().bakeRoot());
        model.setupAnim(motionState(flying, diving));
        return view(model.root(), CreatureModelTestSupport.Projection.FRONT);
    }

    private static void assertViews(final ModelPart root, final String front, final String side, final String angled) {
        assertAll("approved Owl views",
            () -> assertEquals(front, imageSnapshot(view(root, CreatureModelTestSupport.Projection.FRONT))),
            () -> assertEquals(side, imageSnapshot(view(root, CreatureModelTestSupport.Projection.SIDE))),
            () -> assertEquals(angled, imageSnapshot(threeQuarter())));
    }

    private static BufferedImage threeQuarter() { final ModelPart root = OwlModel.createBodyLayer().bakeRoot(); root.yRot = 0.7853982F; return view(root, CreatureModelTestSupport.Projection.FRONT); }
    private static BufferedImage view(final ModelPart root, final CreatureModelTestSupport.Projection projection) { return softwareSnapshot(root, projection, 192, 10); }
    private static String textureHash() throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(TEXTURE))); }
    private static void writeSheet(final List<BufferedImage> views, final Path output) throws Exception {
        final BufferedImage sheet = new BufferedImage(576, 384, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = sheet.createGraphics();
        try { graphics.setColor(new Color(35, 31, 27)); graphics.fillRect(0, 0, 576, 384); for (int i = 0; i < views.size(); i++) graphics.drawImage(views.get(i), i % 3 * 192, i / 3 * 192, null); }
        finally { graphics.dispose(); }
        Files.createDirectories(output.getParent()); ImageIO.write(sheet, "PNG", output.toFile());
    }
}
