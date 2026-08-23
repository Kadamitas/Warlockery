package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import com.kadamitas.warlockery.entity.ParasyticLouseTenancyRules.Phase;
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

final class ParasyticLouseModelTest {
    private static final Path SOURCE = Path.of("src/main/java/com/kadamitas/warlockery/client/model/ParasyticLouseModel.java");
    private static final Path TEXTURE = Path.of("src/main/resources/assets/warlockery/textures/entity/parasytic_louse.png");

    @Test
    void bakesIndependentSixLeggedSegmentedEctoparasite() throws Exception {
        assertEquals(128, ParasyticLouseModel.TEXTURE_WIDTH);
        assertEquals(128, ParasyticLouseModel.TEXTURE_HEIGHT);
        assertTrue(EntityModel.class.isAssignableFrom(ParasyticLouseModel.class));
        assertTrue(Modifier.isPublic(ParasyticLouseModel.State.class.getModifiers()));
        final ModelPart root = ParasyticLouseModel.createBodyLayer().bakeRoot();
        final ModelPart head = requiredChild(root, "head");
        assertFalse(requiredChild(head, "left_palp").isEmpty());
        assertFalse(requiredChild(head, "right_palp").isEmpty());
        assertFalse(requiredChild(root, "thorax").isEmpty());
        final ModelPart abdomen = requiredChild(root, "abdomen_front");
        assertFalse(requiredChild(requiredChild(abdomen, "abdomen_mid"), "abdomen_rear").isEmpty());
        assertFalse(requiredChild(root, "feeding_core").isEmpty());
        int legCount = 0;
        for (final String side : List.of("left", "right")) for (final String station : List.of("front", "mid", "rear")) {
            final ModelPart leg = requiredChild(root, side + "_" + station + "_leg");
            final ModelPart shin = requiredChild(leg, side + "_" + station + "_shin");
            assertFalse(requiredChild(shin, side + "_" + station + "_hook").isEmpty());
            legCount++;
        }
        assertEquals(6, legCount);
        CreatureModelTestSupport.assertUvsWithin(root, 128, 128);
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        CreatureModelTestSupport.assertOpaqueUvs(root, texture, cube -> true);
        assertEquals(0, texture.getRGB(127, 127) >>> 24);
    }

    @Test
    void flattenedGroundBoundsAndApprovedViewsArePinned() {
        final ModelPart root = ParasyticLouseModel.createBodyLayer().bakeRoot();
        final Bounds bounds = bounds(root);
        final float height = bounds.maxY() - bounds.minY();
        final float frontAspect = (bounds.maxX() - bounds.minX()) / height;
        final float sideAspect = (bounds.maxZ() - bounds.minZ()) / height;
        assertEquals(22.280638F, bounds.maxY(), 0.001F);
        assertTrue(bounds.maxZ() - bounds.minZ() > bounds.maxY() - bounds.minY());
        assertAll("domed Parasytic Louse concept proportions",
            () -> assertTrue(height >= 12.0F && height <= 13.0F,
                "armored carapace must remain low above the hooked legs: " + height),
            () -> assertTrue(bounds.minY() <= 11.0F, "carapace crown must reach y=11 or higher: " + bounds),
            () -> assertTrue(frontAspect >= 1.65F && frontAspect <= 1.85F,
                "front must preserve the broad low carapace: " + frontAspect),
            () -> assertTrue(sideAspect >= 2.10F && sideAspect <= 2.30F,
                "profile must preserve the elongated segmented abdomen: " + sideAspect)
        );
        assertAll("approved Parasytic Louse bake and views",
            () -> assertEquals("59c4786bf133ad07bdb98fe48a80d33da5ed85eb568814dd2e3b22ab54e0fb9e", geometrySnapshot(root)),
            () -> assertViews(root,
                "7856f6a1109111003f9dd80a9a94e30c5dfbf9cde1c885e0c2a0f3693cf64c50",
                "9bf57d23e5cd042f1c7851055ae8642a1513c1bb666e4b9d47def7f1f9a6388e",
                "6c8d07670cb1fea12d4ebb751be7b8770bde580d0c40c0e5b424b8f3fcf8766b"));
    }

    @Test
    void neutralScuttleAndFeedingPosesAreDistinctAndPinned() {
        final String neutral = poseHash(Phase.FREE, 0.0F);
        final String moving = poseHash(Phase.SEEK, 0.8F);
        final String feeding = poseHash(Phase.FEED, 0.2F);
        assertNotEquals(neutral, moving);
        assertNotEquals(moving, feeding);
        assertAll("approved Parasytic Louse poses",
            () -> assertEquals("9a81f64380e07b88357e542fb4354ae0438ec781cde4c34ff8acb1ada3e09088", neutral),
            () -> assertEquals("f539f9f24fd98c80cc4310ede6e8a68d7fd2f474a0efc4b13ef8cb3d4fae9f0d", moving),
            () -> assertEquals("9b0cc6c900afcb5520faaca88287ca0ed72397770441ccfc2a7caccf130faab8", feeding));
    }

    @Test
    void sourceExtractsSynchronizedTenancyPresentationAndAtlasIsPinned() throws Exception {
        final String source = Files.readString(SOURCE);
        for (final String forbidden : List.of("ArcaneCreatureModel", "CreatureModelProfile", "FamiliarCatModel", "OwlModel", "ToadModel", "HexBatModel", "ModelHelper", "GeometryHelper", "FamilyAnimator")) assertFalse(source.contains(forbidden), forbidden);
        assertTrue(source.contains("extends EntityModel<ParasyticLouseModel.State>"));
        assertTrue(source.contains("extractRenderState(final ParasyticLouseEntity entity"));
        assertTrue(source.contains("entity.presentationPhase()"));
        assertTrue(source.contains("entity.presentationNourishment()"));
        assertFalse(source.contains("entity.tenancy()"));
        assertFalse(source.contains("entity.louseState()"));
        assertEquals("dbcc114bf5c3573aee158e27816b73e091c2f60d59f09b19b83204916732ed70", textureHash());
    }

    @Test
    void writesSoftwareContactSheet() throws Exception {
        writeSheet(List.of(view(ParasyticLouseModel.createBodyLayer().bakeRoot(), Projection.FRONT), view(ParasyticLouseModel.createBodyLayer().bakeRoot(), Projection.SIDE), threeQuarter(), posed(Phase.FREE, 0.0F, Projection.FRONT), posed(Phase.SEEK, 0.8F, Projection.FRONT), posed(Phase.FEED, 0.2F, Projection.SIDE)), Path.of("build/reports/visual-audit/creatures/parasytic_louse-software-contact-sheet.png"));
    }

    private static ParasyticLouseModel.State state(final Phase phase, final float speed) { final ParasyticLouseModel.State state = new ParasyticLouseModel.State(); state.yRot = 12.0F; state.xRot = -6.0F; state.walkAnimationPos = 2.9F; state.walkAnimationSpeed = speed; state.ageInTicks = 29.5F; state.phase = phase; state.feeding = phase == Phase.FEED; state.nourishment = state.feeding ? 3 : 0; return state; }
    private static String poseHash(final Phase phase, final float speed) { final ParasyticLouseModel model = new ParasyticLouseModel(ParasyticLouseModel.createBodyLayer().bakeRoot()); model.setupAnim(state(phase, speed)); return geometrySnapshot(model.root()); }
    private static BufferedImage posed(final Phase phase, final float speed, final Projection projection) { final ParasyticLouseModel model = new ParasyticLouseModel(ParasyticLouseModel.createBodyLayer().bakeRoot()); model.setupAnim(state(phase, speed)); return view(model.root(), projection); }
    private static void assertViews(final ModelPart root, final String front, final String side, final String angled) { assertAll("approved Parasytic Louse views", () -> assertEquals(front, imageSnapshot(view(root, Projection.FRONT))), () -> assertEquals(side, imageSnapshot(view(root, Projection.SIDE))), () -> assertEquals(angled, imageSnapshot(threeQuarter()))); }
    private static BufferedImage threeQuarter() { final ModelPart root = ParasyticLouseModel.createBodyLayer().bakeRoot(); root.yRot = 0.7853982F; return view(root, Projection.FRONT); }
    private static BufferedImage view(final ModelPart root, final Projection projection) { return softwareSnapshot(root, projection, 192, 10); }
    private static String textureHash() throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(TEXTURE))); }
    private static void writeSheet(final List<BufferedImage> views, final Path output) throws Exception { final BufferedImage sheet = new BufferedImage(576, 384, BufferedImage.TYPE_INT_ARGB); final Graphics2D graphics = sheet.createGraphics(); try { graphics.setColor(new Color(34, 25, 29)); graphics.fillRect(0, 0, 576, 384); for (int i = 0; i < views.size(); i++) graphics.drawImage(views.get(i), i % 3 * 192, i / 3 * 192, null); } finally { graphics.dispose(); } Files.createDirectories(output.getParent()); ImageIO.write(sheet, "PNG", output.toFile()); }
}
