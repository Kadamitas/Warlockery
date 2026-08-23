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
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class IronboundSentinelModelTest {
    private static final Path SOURCE = Path.of("src/main/java/com/kadamitas/warlockery/client/model/IronboundSentinelModel.java");
    private static final Path TEXTURE = Path.of("src/main/resources/assets/warlockery/textures/entity/ironbound_sentinel.png");

    @Test
    void bakesIronGolemVillageSentinelFusionOnItsOwnAtlas() throws Exception {
        assertEquals(192, IronboundSentinelModel.TEXTURE_WIDTH); assertEquals(128, IronboundSentinelModel.TEXTURE_HEIGHT);
        final ModelPart root = IronboundSentinelModel.createBodyLayer().bakeRoot(); final ModelPart guardian = requiredChild(root, "village_guardian");
        assertFalse(requiredChild(requiredChild(guardian, "guardian_head"), "leather_guard_helmet").isEmpty());
        assertFalse(requiredChild(guardian, "leather_harness").isEmpty());
        assertFalse(requiredChild(guardian, "blue_tabard").isEmpty());
        assertFalse(requiredChild(guardian, "village_bell_core").isEmpty());
        assertFalse(requiredChild(guardian, "folded_defense_bow").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_guard_arm"), "right_hammer_clamp").isEmpty()); assertFalse(requiredChild(requiredChild(root, "left_guard_arm"), "left_hammer_clamp").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_masonry_leg"), "right_anchor_foot").isEmpty()); assertFalse(requiredChild(requiredChild(root, "left_masonry_leg"), "left_anchor_foot").isEmpty());
        assertTrue(CreatureModelTestSupport.solidPartCount(root) >= 22); CreatureModelTestSupport.assertUvsWithin(root, 192, 128); CreatureModelTestSupport.assertOpaqueUvs(root, ImageIO.read(TEXTURE.toFile()), cube -> !cube.path().contains("village_guardian"));
    }

    @Test
    void pinsGroundingGeometryAndThreeReadableSilhouettes() {
        final ModelPart root = IronboundSentinelModel.createBodyLayer().bakeRoot(); final String neutral = geometrySnapshot(root); final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        final String front = view(root, 0); final String side = view(root, -1.5707964F); final String threeQuarter = view(root, -0.7853982F);
        assertAll(() -> assertEquals("81791657779ca467d811f70549b6809e77c5278680aca5aa77a35d5e37cf256c", neutral), () -> assertEquals(new CreatureModelTestSupport.Bounds(-16.421728F, -4.0F, -7.8F, 16.421728F, 24.07692F, 5.5F), bounds), () -> assertEquals("98515148546bb59c29fd23ac8c6ed1b85ac40affdd63d84bf85e7b1bd7d87537", front), () -> assertEquals("0c0c07185278a82402669a78a2623c74e09e3cef9348893dd55866b8e1a76bd4", side), () -> assertEquals("b2034a051367c2909e6a47f153d9dbc60c4b58a50ae6843ab57480868a24bf9f", threeQuarter));
    }

    @Test
    void patrolAndRepelActionAreDistinctPinnedPoses() throws Exception {
        final IronboundSentinelModel model = new IronboundSentinelModel(IronboundSentinelModel.createBodyLayer().bakeRoot()); final IronboundSentinelModel.State movement = motionState(); model.setupAnim(movement);
        final String movementHash = geometrySnapshot(model.root());
        final IronboundSentinelModel.State action = motionState(); action.repelProgress = 0.88F; action.charged = true; model.setupAnim(action);
        final String actionHash = geometrySnapshot(model.root()); writeContactSheet(model, action);
        assertAll(() -> assertEquals("7fed4f4680bac78e05a37f442805362bdd98e4f7777c37ca0ada1aa717cc2260", movementHash), () -> assertEquals("5ca012c8d6baa8731c2d77fdc6ed76be96cbd1d14901c0b5b3ab8c72b285cc79", actionHash), () -> assertNotEquals(movementHash, actionHash));
    }

    @Test
    void sourceAndAtlasAreIndependentAndBytePinned() throws Exception {
        final String source = Files.readString(SOURCE); assertTrue(source.contains("extends EntityModel<IronboundSentinelModel.State>")); assertTrue(source.contains("public static void extractRenderState(")); assertTrue(source.contains("final IronboundSentinelEntity entity"));
        assertTrue(source.contains("\"blue_tabard\""));
        assertTrue(source.contains("\"village_bell_core\""));
        assertFalse(source.toLowerCase(java.util.Locale.ROOT).contains("ward_boiler"));
        assertFalse(source.toLowerCase(java.util.Locale.ROOT).contains("occult_lock"));
        assertFalse(source.toLowerCase(java.util.Locale.ROOT).contains("exhaust"));
        for (final String forbidden : java.util.List.of("ArcaneCreatureModel", "CreatureModelProfile", "GeometryHelper", "AnimationHelper", "ModelHelper", "RigCatalog", "extends Warlockery")) assertFalse(source.contains(forbidden), forbidden);
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile()); assertEquals(192, texture.getWidth()); assertEquals(128, texture.getHeight()); assertTrue(hasTransparentPixel(texture)); assertEquals("bceb509f708893b611273041c3bc285cb43495de91a38f21783a312a1c53b82c", hash(TEXTURE));
    }

    private static IronboundSentinelModel.State motionState() { final IronboundSentinelModel.State state = new IronboundSentinelModel.State(); state.yRot = 13; state.xRot = -3; state.walkAnimationPos = 2.15F; state.walkAnimationSpeed = 0.62F; state.ageInTicks = 80; return state; }
    private static String view(final ModelPart root, final float rotation) { root.yRot = rotation; final String result = imageSnapshot(softwareSnapshot(root, CreatureModelTestSupport.Projection.FRONT, 128, 5)); root.yRot = 0; return result; }
    private static String hash(final Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    private static boolean hasTransparentPixel(final BufferedImage image) { for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) if ((image.getRGB(x, y) >>> 24) == 0) return true; return false; }
    private static void writeContactSheet(final IronboundSentinelModel model, final IronboundSentinelModel.State action) throws Exception { final BufferedImage sheet = new BufferedImage(768, 160, BufferedImage.TYPE_INT_ARGB); final Graphics2D g = sheet.createGraphics(); g.setColor(new Color(37, 38, 43)); g.fillRect(0, 0, 768, 160); final IronboundSentinelModel.State idle = new IronboundSentinelModel.State(); idle.charged = true; model.setupAnim(idle); final float[] turns = {0, 1.5707964F, 3.1415927F, -1.5707964F, -0.7853982F}; for (int i = 0; i < turns.length; i++) { model.root().yRot = turns[i]; g.drawImage(softwareSnapshot(model.root(), CreatureModelTestSupport.Projection.FRONT, 128, 6), i * 128, 0, null); } model.setupAnim(action); g.drawImage(softwareSnapshot(model.root(), CreatureModelTestSupport.Projection.FRONT, 128, 6), 640, 0, null); g.dispose(); final Path output = Path.of("build/reports/visual-audit/creatures/ironbound_sentinel-software-contact-sheet.png"); Files.createDirectories(output.getParent()); ImageIO.write(sheet, "png", output.toFile()); }
}
