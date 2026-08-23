package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.*;
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
import net.minecraft.client.model.animal.frog.FrogModel;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class ToadModelTest {
    private static final Path SOURCE = Path.of("src/main/java/com/kadamitas/warlockery/client/model/ToadModel.java");
    private static final Path TEXTURE = Path.of("src/main/resources/assets/warlockery/textures/entity/toad.png");

    @Test
    void bakesIndependentSquatAuthoredAmphibian() throws Exception {
        assertEquals(48, ToadModel.TEXTURE_WIDTH);
        assertEquals(48, ToadModel.TEXTURE_HEIGHT);
        assertTrue(EntityModel.class.isAssignableFrom(ToadModel.class));
        assertEquals(FrogModel.class, ToadModel.class.getSuperclass());
        assertTrue(Modifier.isPublic(ToadModel.State.class.getModifiers()));
        final ModelPart root = ToadModel.createBodyLayer().bakeRoot();
        final ModelPart frogRoot = requiredChild(root, "root");
        final ModelPart body = requiredChild(frogRoot, "body");
        assertFalse(requiredChild(body, "left_arm").isEmpty());
        assertFalse(requiredChild(body, "right_arm").isEmpty());
        assertFalse(requiredChild(frogRoot, "left_leg").isEmpty());
        assertFalse(requiredChild(frogRoot, "right_leg").isEmpty());
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        assertEquals(ToadModel.TEXTURE_WIDTH, texture.getWidth());
        assertEquals(ToadModel.TEXTURE_HEIGHT, texture.getHeight());
        assertEquals(0, texture.getRGB(47, 47) >>> 24);
    }

    @Test
    void neutralConceptPoseIsTallEnoughForFoldedHindLegsAndToeGaps() {
        final ModelPart root = ToadModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = bounds(root);
        final float height = bounds.maxY() - bounds.minY();
        final float frontAspect = (bounds.maxX() - bounds.minX()) / height;
        final float sideAspect = (bounds.maxZ() - bounds.minZ()) / height;
        assertAll("squat Toad concept proportions",
            () -> assertTrue(height >= 8.0F && height <= 9.0F,
                "the compact vanilla-frog chassis must remain squat: " + height),
            () -> assertTrue(frontAspect >= 2.20F && frontAspect <= 2.50F,
                "front must preserve the broad amphibian stance: " + frontAspect),
            () -> assertTrue(sideAspect >= 1.80F && sideAspect <= 2.10F,
                "profile must preserve the folded-leg depth: " + sideAspect),
            () -> assertEquals(24.01F, bounds.maxY(), 0.001F)
        );
    }

    @Test
    void squatBoundsAndApprovedViewsArePinned() {
        final ModelPart root = ToadModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = bounds(root);
        assertEquals(24.01F, bounds.maxY(), 0.001F);
        assertTrue(bounds.maxX() - bounds.minX() > bounds.maxY() - bounds.minY());
        assertAll("approved Toad bake and views",
            () -> assertEquals("9ab6a14e04fec6ced3b50362d2ebb14312835e903243af7366a0f3a139dc3ae1", geometrySnapshot(root)),
            () -> assertViews(root,
                "2106ae07806612bb61a219c37fdd34134d352563b6ba64f1dd4143578c302b5c",
                "cf5d2d3d0b1404e7c3d92181943bbb61f0d87490692267cb490b3dd9749f2420",
                "1ee2fac9ea236f862cf7fd7fdb9c6289d54e3cbf95f8f9b2261cc1bccfbbeb4b"));
    }

    @Test
    void neutralScuttleAndHopPosesAreDistinctAndPinned() {
        final String neutral = poseHash(false, false, 0.0F);
        final String moving = poseHash(false, false, 0.8F);
        final String leap = poseHash(true, true, 0.8F);
        assertNotEquals(neutral, moving);
        assertNotEquals(moving, leap);
        assertAll("approved Toad poses",
            () -> assertEquals("7a60147573baa4560f68ef21f893a5bccca664110d4047be7086666f2acf86ea", neutral),
            () -> assertEquals("028508826f72948f71a36ef6fff029336d02671e6c7e5dd9cd47e164bc088e86", moving),
            () -> assertEquals("c0c7666033f92d579f2ac29bb904dfc9ed6ceb2e7f2498d1a3a5944f2150265f", leap));
    }

    @Test
    void sourceOwnsTheToadAndAtlasIsPinned() throws Exception {
        final String source = Files.readString(SOURCE);
        for (final String forbidden : List.of("ArcaneCreatureModel", "CreatureModelProfile", "FamiliarCatModel", "OwlModel", "HexBatModel", "ParasyticLouseModel", "ModelHelper", "GeometryHelper", "FamilyAnimator")) assertFalse(source.contains(forbidden), forbidden);
        assertTrue(source.contains("extends FrogModel"));
        assertTrue(source.contains("extractRenderState(final ArcaneMob entity"));
        assertEquals("f7f52e6e9e59583c5cc0719b433eca3ebd1517cd2e2d510aeb780a29ef89977d", textureHash());
    }

    @Test
    void writesSoftwareContactSheet() throws Exception {
        writeSheet(List.of(view(ToadModel.createBodyLayer().bakeRoot(), CreatureModelTestSupport.Projection.FRONT), view(ToadModel.createBodyLayer().bakeRoot(), CreatureModelTestSupport.Projection.SIDE), threeQuarter(), posed(false, false, 0.7F, CreatureModelTestSupport.Projection.FRONT), posed(true, false, 0.8F, CreatureModelTestSupport.Projection.FRONT), posed(true, true, 0.8F, CreatureModelTestSupport.Projection.SIDE)), Path.of("build/reports/visual-audit/creatures/toad-software-contact-sheet.png"));
    }

    private static ToadModel.State state(final boolean hopping, final boolean leaping, final float speed) { final ToadModel.State state = new ToadModel.State(); state.yRot = 15.0F; state.xRot = -5.0F; state.walkAnimationPos = 2.8F; state.walkAnimationSpeed = speed; state.ageInTicks = 37.0F; state.hopping = hopping; state.leaping = leaping; return state; }
    private static String poseHash(final boolean hopping, final boolean leaping, final float speed) { final ToadModel model = new ToadModel(ToadModel.createBodyLayer().bakeRoot()); model.setupAnim(state(hopping, leaping, speed)); return geometrySnapshot(model.root()); }
    private static BufferedImage posed(final boolean hopping, final boolean leaping, final float speed, final CreatureModelTestSupport.Projection projection) { final ToadModel model = new ToadModel(ToadModel.createBodyLayer().bakeRoot()); model.setupAnim(state(hopping, leaping, speed)); return view(model.root(), projection); }
    private static void assertViews(final ModelPart root, final String front, final String side, final String angled) { assertAll("approved Toad views", () -> assertEquals(front, imageSnapshot(view(root, CreatureModelTestSupport.Projection.FRONT))), () -> assertEquals(side, imageSnapshot(view(root, CreatureModelTestSupport.Projection.SIDE))), () -> assertEquals(angled, imageSnapshot(threeQuarter()))); }
    private static BufferedImage threeQuarter() { final ModelPart root = ToadModel.createBodyLayer().bakeRoot(); root.yRot = 0.7853982F; return view(root, CreatureModelTestSupport.Projection.FRONT); }
    private static BufferedImage view(final ModelPart root, final CreatureModelTestSupport.Projection projection) { return softwareSnapshot(root, projection, 192, 10); }
    private static String textureHash() throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(TEXTURE))); }
    private static void writeSheet(final List<BufferedImage> views, final Path output) throws Exception { final BufferedImage sheet = new BufferedImage(576, 384, BufferedImage.TYPE_INT_ARGB); final Graphics2D graphics = sheet.createGraphics(); try { graphics.setColor(new Color(28, 32, 19)); graphics.fillRect(0, 0, 576, 384); for (int i = 0; i < views.size(); i++) graphics.drawImage(views.get(i), i % 3 * 192, i / 3 * 192, null); } finally { graphics.dispose(); } Files.createDirectories(output.getParent()); ImageIO.write(sheet, "PNG", output.toFile()); }
}
