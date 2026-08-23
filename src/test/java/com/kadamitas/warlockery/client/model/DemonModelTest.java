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

final class DemonModelTest {
    private static final Path SOURCE = Path.of("src/main/java/com/kadamitas/warlockery/client/model/DemonModel.java");
    private static final Path TEXTURE = Path.of("src/main/resources/assets/warlockery/textures/entity/demon.png");

    @Test
    void bakesMappedRedHornedFootSoldierHierarchyOnItsOwnAtlas() throws Exception {
        assertEquals(128, DemonModel.TEXTURE_WIDTH);
        assertEquals(128, DemonModel.TEXTURE_HEIGHT);
        final ModelPart root = DemonModel.createBodyLayer().bakeRoot();
        final ModelPart torso = requiredChild(root, "infernal_torso");
        final ModelPart head = requiredChild(torso, "horned_head");
        assertFalse(requiredChild(head, "gold_eye_band").isEmpty());
        assertFalse(requiredChild(head, "right_upright_horn").isEmpty());
        assertFalse(requiredChild(head, "left_upright_horn").isEmpty());
        assertFalse(requiredChild(torso, "right_pointed_tasset").isEmpty());
        assertFalse(requiredChild(torso, "left_pointed_tasset").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_arm"), "right_ember_claw").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_arm"), "left_ember_claw").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_leg"), "right_cloven_hoof").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_leg"), "left_cloven_hoof").isEmpty());
        assertTrue(CreatureModelTestSupport.solidPartCount(root) >= 18);
        CreatureModelTestSupport.assertUvsWithin(root, DemonModel.TEXTURE_WIDTH, DemonModel.TEXTURE_HEIGHT);
        CreatureModelTestSupport.assertOpaqueUvs(root, ImageIO.read(TEXTURE.toFile()),
            cube -> !cube.path().endsWith("cloven_hoof")
                && !cube.path().endsWith("back_spines"));
    }

    @Test
    void pinsGroundingGeometryAndThreeReadableSilhouettes() {
        final ModelPart root = DemonModel.createBodyLayer().bakeRoot();
        final String neutral = geometrySnapshot(root);
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        final String front = view(root, 0.0F);
        final String side = view(root, -1.5707964F);
        final String threeQuarter = view(root, -0.7853982F);
        assertAll(
            () -> assertEquals("30185a4f35cf3648140f2fde2dd1ca8ff153f485de63722d8b424f46d0de6483", neutral),
            () -> assertEquals(new CreatureModelTestSupport.Bounds(-12.609278F, -8.793498F, -5.768502F, 12.609278F, 24.394323F, 6.7470293F), bounds),
            () -> assertEquals("582bde186013cdf64787a5bc391c1286ef5e3cf2d0db6e83cf96a8c5edbd203c", front),
            () -> assertEquals("abdfc95e68d4bb76bb6515ba5fbdd43093063135485e78846bd87f1c40b7ed66", side),
            () -> assertEquals("840de093f450d1b09236ffb68d7f6c12867defdbcf3cf7a0a95670df90b74f0e", threeQuarter)
        );
    }

    @Test
    void movementAndClawRushAreDistinctPinnedPoses() throws Exception {
        final DemonModel model = new DemonModel(DemonModel.createBodyLayer().bakeRoot());
        final DemonModel.State movement = motionState();
        model.setupAnim(movement);
        final String movementHash = geometrySnapshot(model.root());
        final DemonModel.State action = motionState();
        action.attackProgress = 0.78F;
        action.aggressive = true;
        model.setupAnim(action);
        final String actionHash = geometrySnapshot(model.root());
        writeContactSheet(model, action);
        assertAll(
            () -> assertEquals("755ed5e4c34461a52541ea12b051bea22e0c0ab9d307ce477452b3aa41638a2c", movementHash),
            () -> assertEquals("1f630d4197fd3211bb4b5089e419ad49efb2f49c852fc79dc930be771858155c", actionHash),
            () -> assertNotEquals(movementHash, actionHash)
        );
    }

    @Test
    void sourceAndAtlasAreIndependentAndBytePinned() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<DemonModel.State>"));
        assertTrue(source.contains("public static void extractRenderState("));
        assertTrue(source.contains("final InfernalHierarchyEntity entity"));
        assertTrue(source.contains("\"gold_eye_band\""));
        assertTrue(source.contains("\"right_upright_horn\""));
        assertFalse(source.contains("\"brow_blade\""));
        assertFalse(source.contains("\"weapon_arm\""));
        assertFalse(source.contains("ArmedModel"));
        assertFalse(source.contains("infernal_cleaver"));
        assertFalse(source.contains("translateToHand"));
        for (final String forbidden : java.util.List.of(
            "ArcaneCreatureModel", "CreatureModelProfile", "GeometryHelper", "AnimationHelper",
            "ModelHelper", "RigCatalog", "extends Warlockery", "DemonModel extends Demon"
        )) assertFalse(source.contains(forbidden), forbidden);
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        assertEquals(128, texture.getWidth());
        assertEquals(128, texture.getHeight());
        assertTrue(hasTransparentPixel(texture));
        assertEquals("56ba04bb13b02ac8f6f284769e374ddfac0eb09900b259c53349efa238e043ab", hash(TEXTURE));
    }

    private static DemonModel.State motionState() {
        final DemonModel.State state = new DemonModel.State();
        state.yRot = 24.0F;
        state.xRot = -9.0F;
        state.walkAnimationPos = 2.4F;
        state.walkAnimationSpeed = 0.82F;
        state.ageInTicks = 37.0F;
        return state;
    }

    private static String view(final ModelPart root, final float rotation) {
        root.yRot = rotation;
        final String result = imageSnapshot(softwareSnapshot(root, CreatureModelTestSupport.Projection.FRONT, 128, 5));
        root.yRot = 0.0F;
        return result;
    }

    private static String hash(final Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static boolean hasTransparentPixel(final BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            if ((image.getRGB(x, y) >>> 24) == 0) return true;
        return false;
    }

    private static void writeContactSheet(final DemonModel model, final DemonModel.State action) throws Exception {
        final BufferedImage sheet = new BufferedImage(768, 160, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new Color(38, 36, 42));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        model.setupAnim(new DemonModel.State());
        final float[] turns = {0.0F, 1.5707964F, 3.1415927F, -1.5707964F, -0.7853982F};
        for (int index = 0; index < turns.length; index++) {
            model.root().yRot = turns[index];
            graphics.drawImage(softwareSnapshot(model.root(), CreatureModelTestSupport.Projection.FRONT, 128, 6), index * 128, 0, null);
        }
        model.setupAnim(action);
        graphics.drawImage(softwareSnapshot(model.root(), CreatureModelTestSupport.Projection.FRONT, 128, 6), 640, 0, null);
        graphics.dispose();
        final Path output = Path.of("build/reports/visual-audit/creatures/demon-software-contact-sheet.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(sheet, "png", output.toFile());
    }
}
