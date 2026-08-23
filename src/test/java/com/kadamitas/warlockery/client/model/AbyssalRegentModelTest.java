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

final class AbyssalRegentModelTest {
    private static final Path SOURCE = Path.of("src/main/java/com/kadamitas/warlockery/client/model/AbyssalRegentModel.java");
    private static final Path TEXTURE = Path.of("src/main/resources/assets/warlockery/textures/entity/abyssal_regent.png");

    @Test
    void bakesLordOfTormentVoidSovereignHierarchyOnItsOwnAtlas() throws Exception {
        assertEquals(256, AbyssalRegentModel.TEXTURE_WIDTH);
        assertEquals(128, AbyssalRegentModel.TEXTURE_HEIGHT);
        final ModelPart root = AbyssalRegentModel.createBodyLayer().bakeRoot();
        final ModelPart body = requiredChild(root, "torment_body");
        final ModelPart cage = requiredChild(body, "void_cage");
        final ModelPart head = requiredChild(body, "torment_head");
        assertFalse(requiredChild(cage, "internal_fire").isEmpty());
        assertFalse(requiredChild(head, "right_narrow_antenna").isEmpty());
        assertFalse(requiredChild(head, "left_narrow_antenna").isEmpty());
        for (final String wing : java.util.List.of(
            "right_upper_wing", "left_upper_wing", "right_middle_wing",
            "left_middle_wing", "right_lower_wing", "left_lower_wing"
        )) assertFalse(requiredChild(body, wing).isEmpty(), wing);
        final ModelPart rightUpperWing = requiredChild(body, "right_upper_wing");
        final ModelPart rightMiddleWing = requiredChild(body, "right_middle_wing");
        final ModelPart rightLowerWing = requiredChild(body, "right_lower_wing");
        final ModelPart leftUpperWing = requiredChild(body, "left_upper_wing");
        final ModelPart leftMiddleWing = requiredChild(body, "left_middle_wing");
        final ModelPart leftLowerWing = requiredChild(body, "left_lower_wing");
        assertAll("three wing pairs descend with visible vertical and angular separation",
            () -> assertTrue(rightMiddleWing.y - rightUpperWing.y >= 4.5F),
            () -> assertTrue(rightLowerWing.y - rightMiddleWing.y >= 4.5F),
            () -> assertTrue(leftMiddleWing.y - leftUpperWing.y >= 4.5F),
            () -> assertTrue(leftLowerWing.y - leftMiddleWing.y >= 4.5F),
            () -> assertTrue(Math.abs(rightMiddleWing.zRot) >= Math.abs(rightUpperWing.zRot) + 0.12F),
            () -> assertTrue(Math.abs(rightLowerWing.zRot) >= Math.abs(rightMiddleWing.zRot) + 0.12F),
            () -> assertTrue(Math.abs(leftMiddleWing.zRot) >= Math.abs(leftUpperWing.zRot) + 0.12F),
            () -> assertTrue(Math.abs(leftLowerWing.zRot) >= Math.abs(leftMiddleWing.zRot) + 0.12F)
        );
        final ModelPart splitShroud = requiredChild(body, "split_void_shroud");
        assertFalse(requiredChild(splitShroud, "right_void_tail").isEmpty());
        assertFalse(requiredChild(splitShroud, "left_void_tail").isEmpty());
        assertFalse(requiredChild(body, "broken_halo_cage").isEmpty());
        assertTrue(CreatureModelTestSupport.solidPartCount(root) >= 27);
        CreatureModelTestSupport.assertUvsWithin(root, 256, 128);
        CreatureModelTestSupport.assertOpaqueUvs(root, ImageIO.read(TEXTURE.toFile()),
            cube -> cube.path().equals("/torment_body")
                || cube.path().equals("/torment_body/void_cage"));
    }

    @Test
    void pinsIntentionalHoverGeometryAndThreeReadableSilhouettes() {
        final ModelPart root = AbyssalRegentModel.createBodyLayer().bakeRoot();
        final String neutral = geometrySnapshot(root); final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        final String front = view(root, 0.0F); final String side = view(root, -1.5707964F); final String threeQuarter = view(root, -0.7853982F);
        assertAll(
            () -> assertEquals("5d0f2045369b041100425f03ad5ec9b1577ada547b42d596c0301876014b2534", neutral),
            () -> assertEquals(new CreatureModelTestSupport.Bounds(-26.7606F, -17.506638F, -8.779413F, 26.7606F, 22.029028F, 7.411766F), bounds),
            () -> assertEquals("9df8f3dba5d411edda24ae4723a42693cd540c7025a4c197fa4ebcd2d469f62a", front),
            () -> assertEquals("93ebb998acf066a4fd42d9026c0572f5276d9e358d1cb04a91f421f1d161ee8a", side),
            () -> assertEquals("55d95028cfbcfa6a38aabe0c1d108d2dab997e5ca7dc117f38904729ee60f771", threeQuarter)
        );
    }

    @Test
    void driftAndCourtCommandAreDistinctPinnedPoses() throws Exception {
        final AbyssalRegentModel model = new AbyssalRegentModel(AbyssalRegentModel.createBodyLayer().bakeRoot());
        final AbyssalRegentModel.State movement = motionState(); model.setupAnim(movement);
        final String movementHash = geometrySnapshot(model.root());
        final AbyssalRegentModel.State action = motionState(); action.commandProgress = 0.9F; action.phasePulse = true; model.setupAnim(action);
        final String actionHash = geometrySnapshot(model.root());
        writeContactSheet(model, action);
        assertAll(() -> assertEquals("a59c7901b27b5fd3af3311d578ee4c8a8c7820b900a390975347f2c96e7ee41a", movementHash), () -> assertEquals("4d40211334231d8f56baecad43ba8978318198cc103695c3bdf7bd13d9c1bdda", actionHash), () -> assertNotEquals(movementHash, actionHash));
    }

    @Test
    void sourceAndAtlasAreIndependentAndBytePinned() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<AbyssalRegentModel.State>"));
        assertTrue(source.contains("public static void extractRenderState(")); assertTrue(source.contains("final InfernalHierarchyEntity entity"));
        assertTrue(source.contains("\"right_upper_wing\""));
        assertTrue(source.contains("\"right_middle_wing\""));
        assertTrue(source.contains("\"right_lower_wing\""));
        assertFalse(source.toLowerCase(java.util.Locale.ROOT).contains("tendril"));
        assertFalse(source.toLowerCase(java.util.Locale.ROOT).contains("cephalopod"));
        assertFalse(source.contains("ArmedModel"));
        assertFalse(source.contains("translateToHand"));
        assertFalse(source.toLowerCase(java.util.Locale.ROOT).contains("held_weapon"));
        for (final String forbidden : java.util.List.of("ArcaneCreatureModel", "CreatureModelProfile", "GeometryHelper", "AnimationHelper", "ModelHelper", "RigCatalog", "extends Warlockery")) assertFalse(source.contains(forbidden), forbidden);
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile()); assertEquals(256, texture.getWidth()); assertEquals(128, texture.getHeight()); assertTrue(hasTransparentPixel(texture));
        assertEquals("7e05add5e2fc870948ea354f8316584fb28c3bfc1d7409cbe20a786ce18775af", hash(TEXTURE));
    }

    private static AbyssalRegentModel.State motionState() { final AbyssalRegentModel.State state = new AbyssalRegentModel.State(); state.yRot = 18; state.xRot = -6; state.walkAnimationPos = 4.2F; state.walkAnimationSpeed = 0.55F; state.ageInTicks = 71; return state; }
    private static String view(final ModelPart root, final float rotation) { root.yRot = rotation; final String result = imageSnapshot(softwareSnapshot(root, CreatureModelTestSupport.Projection.FRONT, 128, 5)); root.yRot = 0; return result; }
    private static String hash(final Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    private static boolean hasTransparentPixel(final BufferedImage image) { for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) if ((image.getRGB(x, y) >>> 24) == 0) return true; return false; }
    private static void writeContactSheet(final AbyssalRegentModel model, final AbyssalRegentModel.State action) throws Exception { final BufferedImage sheet = new BufferedImage(768, 160, BufferedImage.TYPE_INT_ARGB); final Graphics2D g = sheet.createGraphics(); g.setColor(new Color(24, 37, 46)); g.fillRect(0, 0, 768, 160); model.setupAnim(new AbyssalRegentModel.State()); final float[] turns = {0, 1.5707964F, 3.1415927F, -1.5707964F, -0.7853982F}; for (int i = 0; i < turns.length; i++) { model.root().yRot = turns[i]; g.drawImage(softwareSnapshot(model.root(), CreatureModelTestSupport.Projection.FRONT, 128, 6), i * 128, 0, null); } model.setupAnim(action); g.drawImage(softwareSnapshot(model.root(), CreatureModelTestSupport.Projection.FRONT, 128, 6), 640, 0, null); g.dispose(); final Path output = Path.of("build/reports/visual-audit/creatures/abyssal_regent-software-contact-sheet.png"); Files.createDirectories(output.getParent()); ImageIO.write(sheet, "png", output.toFile()); }
}
