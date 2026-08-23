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
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class HexBatModelTest {
    private static final Path SOURCE = Path.of("src/main/java/com/kadamitas/warlockery/client/model/HexBatModel.java");
    private static final Path TEXTURE = Path.of("src/main/resources/assets/warlockery/textures/entity/hex_bat.png");

    @Test
    void bakesIndependentOccultBatWithoutHumanoidAnatomy() throws Exception {
        assertEquals(128, HexBatModel.TEXTURE_WIDTH);
        assertEquals(128, HexBatModel.TEXTURE_HEIGHT);
        assertTrue(EntityModel.class.isAssignableFrom(HexBatModel.class));
        assertTrue(Modifier.isPublic(HexBatModel.State.class.getModifiers()));
        final ModelPart root = HexBatModel.createBodyLayer().bakeRoot();
        final ModelPart body = requiredChild(root, "body");
        final ModelPart head = requiredChild(body, "head");
        for (final String feature : List.of("muzzle", "left_ear", "right_ear")) assertFalse(requiredChild(head, feature).isEmpty());
        for (final String side : List.of("left", "right")) {
            final ModelPart wing = requiredChild(body, side + "_wing_root");
            final ModelPart forearm = requiredChild(wing, side + "_wing_forearm");
            assertFalse(requiredChild(forearm, side + "_wing_tip").isEmpty());
            assertFalse(requiredChild(wing, side + "_inner_membrane").isEmpty());
            assertFalse(requiredChild(forearm, side + "_outer_membrane").isEmpty());
            assertFalse(requiredChild(requiredChild(body, side + "_leg"), side + "_claw").isEmpty());
        }
        final ModelPart leftWing = requiredChild(body, "left_wing_root");
        final ModelPart rightWing = requiredChild(body, "right_wing_root");
        assertAll("paired wings retain a mirrored two-plane structure around the body",
            () -> assertEquals(leftWing.z, rightWing.z, 0.001F),
            () -> assertEquals(leftWing.y, rightWing.y, 0.001F),
            () -> assertEquals(leftWing.x, -rightWing.x, 0.001F)
        );
        assertFalse(requiredChild(body, "tail_membrane").isEmpty());
        CreatureModelTestSupport.assertUvsWithin(root, 128, 128);
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        CreatureModelTestSupport.assertOpaqueUvs(root, texture, cube -> true);
        assertEquals(0, texture.getRGB(127, 127) >>> 24);
    }

    @Test
    void lowFoldedBoundsAndApprovedViewsArePinned() {
        final ModelPart root = HexBatModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = bounds(root);
        assertEquals(24.0F, bounds.maxY(), 0.001F);
        assertTrue(bounds.maxX() - bounds.minX() > 12.0F);
        assertAll("approved Hex Bat bake and views",
            () -> assertEquals("fa9d07d489f56b4163a45d1f15dc25fe1c6292ba289a803f3917aa11ba85522d", geometrySnapshot(root)),
            () -> assertViews(root,
                "557ab49e0d732ffb4772ce3e886b06c170899cb85107ec778d01a777c90d328e",
                "6e6f354c4279e9f84c0d7cf0dd20791d7328b658bc21e78f70bcddf1100ade05",
                "976dbce98c1bf5c778494bdf472c68904aca4c7ee3b1eb704cb8b3012ba55f88"));
    }

    @Test
    void foldedRoostAndSwoopPosesAreDistinctAndPinned() {
        final HexBatModel neutralModel = posedModel(false, false);
        final HexBatModel roostModel = posedModel(true, false);
        final HexBatModel swoopModel = posedModel(false, true);
        final String neutral = geometrySnapshot(neutralModel.root());
        final String roost = geometrySnapshot(roostModel.root());
        final String swoop = geometrySnapshot(swoopModel.root());
        final ModelPart swoopBody = requiredChild(swoopModel.root(), "body");
        final ModelPart swoopLeftWing = requiredChild(swoopBody, "left_wing_root");
        final ModelPart swoopRightWing = requiredChild(swoopBody, "right_wing_root");
        assertNotEquals(neutral, roost);
        assertNotEquals(roost, swoop);
        assertTrue(bounds(roostModel.root()).maxY() < bounds(neutralModel.root()).maxY());
        assertAll("approved Hex Bat poses",
            () -> assertEquals("a6718d7e11a2fa70497640ef88385e734db47a424b56528557f95968dab87a4d", neutral),
            () -> assertEquals("64039f469ac8a0ed44377fe4a60571dcff47bc60f3c8361245dc0bd6dda6e506", roost),
            () -> assertEquals("d346552837e873d45e403ae954e07995e6f7ee4846d53005215c8bf50b161ab9", swoop));
        assertTrue(Math.abs(swoopLeftWing.yRot - swoopRightWing.yRot) >= 0.5F,
            "swoop must preserve two wing planes around the body");
    }

    @Test
    void sourceExtractsActualRoostAndSwoopAndAtlasIsPinned() throws Exception {
        final String source = Files.readString(SOURCE);
        for (final String forbidden : List.of("ArcaneCreatureModel", "CreatureModelProfile", "FamiliarCatModel", "OwlModel", "ToadModel", "ParasyticLouseModel", "ModelHelper", "GeometryHelper", "FamilyAnimator")) assertFalse(source.contains(forbidden), forbidden);
        assertTrue(source.contains("extends EntityModel<HexBatModel.State>"));
        assertTrue(source.contains("extractRenderState(final HexBatEntity entity"));
        assertTrue(source.contains("entity.isRoosting()"));
        assertTrue(source.contains("entity.isSwooping()"));
        assertEquals("96c69abf9ffc53a66683279022c78469ee5f3272fbb29c5ff41c025335ff160c", textureHash());
    }

    @Test
    void writesSoftwareContactSheet() throws Exception {
        writeSheet(List.of(view(HexBatModel.createBodyLayer().bakeRoot(), Projection.FRONT), view(HexBatModel.createBodyLayer().bakeRoot(), Projection.SIDE), threeQuarter(), view(posedModel(false, false).root(), Projection.FRONT), view(posedModel(true, false).root(), Projection.FRONT), view(posedModel(false, true).root(), Projection.SIDE)), Path.of("build/reports/visual-audit/creatures/hex_bat-software-contact-sheet.png"));
    }

    private static HexBatModel posedModel(final boolean roosting, final boolean swooping) { final HexBatModel model = new HexBatModel(HexBatModel.createBodyLayer().bakeRoot()); final HexBatModel.State state = new HexBatModel.State(); state.yRot = 22.0F; state.xRot = -14.0F; state.walkAnimationPos = 2.25F; state.walkAnimationSpeed = 0.8F; state.ageInTicks = 48.0F; state.roosting = roosting; state.swooping = swooping; model.setupAnim(state); return model; }
    private static void assertViews(final ModelPart root, final String front, final String side, final String angled) { assertAll("approved Hex Bat views", () -> assertEquals(front, imageSnapshot(view(root, Projection.FRONT))), () -> assertEquals(side, imageSnapshot(view(root, Projection.SIDE))), () -> assertEquals(angled, imageSnapshot(threeQuarter()))); }
    private static BufferedImage threeQuarter() { final ModelPart root = HexBatModel.createBodyLayer().bakeRoot(); root.yRot = 0.7853982F; return view(root, Projection.FRONT); }
    private static BufferedImage view(final ModelPart root, final Projection projection) { return softwareSnapshot(root, projection, 192, 10); }
    private static String textureHash() throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(TEXTURE))); }
    private static void writeSheet(final List<BufferedImage> views, final Path output) throws Exception { final BufferedImage sheet = new BufferedImage(576, 384, BufferedImage.TYPE_INT_ARGB); final Graphics2D graphics = sheet.createGraphics(); try { graphics.setColor(new Color(25, 22, 32)); graphics.fillRect(0, 0, 576, 384); for (int i = 0; i < views.size(); i++) graphics.drawImage(views.get(i), i % 3 * 192, i / 3 * 192, null); } finally { graphics.dispose(); } Files.createDirectories(output.getParent()); ImageIO.write(sheet, "PNG", output.toFile()); }
}
