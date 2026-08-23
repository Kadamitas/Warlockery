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

final class EmberhornArchfiendModelTest {
    private static final Path SOURCE = Path.of("src/main/java/com/kadamitas/warlockery/client/model/EmberhornArchfiendModel.java");
    private static final Path TEXTURE = Path.of("src/main/resources/assets/warlockery/textures/entity/emberhorn_archfiend.png");

    @Test
    void bakesFireDemonHeartGuardianHierarchyOnItsOwnAtlas() throws Exception {
        assertEquals(192, EmberhornArchfiendModel.TEXTURE_WIDTH);
        assertEquals(128, EmberhornArchfiendModel.TEXTURE_HEIGHT);
        final ModelPart root = EmberhornArchfiendModel.createBodyLayer().bakeRoot();
        final ModelPart chest = requiredChild(root, "guarded_heart_chest");
        final ModelPart heart = requiredChild(chest, "exposed_demon_heart");
        final ModelPart head = requiredChild(chest, "archfiend_head");
        assertFalse(requiredChild(heart, "heart_core").isEmpty());
        assertFalse(requiredChild(heart, "heart_crown").isEmpty());
        assertFalse(requiredChild(chest, "right_guard_rib").isEmpty());
        assertFalse(requiredChild(chest, "left_guard_rib").isEmpty());
        assertFalse(requiredChild(head, "right_branched_emberhorn").isEmpty());
        assertFalse(requiredChild(head, "left_branched_emberhorn").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_guard_arm"), "right_magma_fist").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_guard_arm"), "left_magma_fist").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_leg"), "right_obsidian_hoof").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_leg"), "left_obsidian_hoof").isEmpty());
        assertFalse(requiredChild(root, "back_rib_mantle").isEmpty());
        assertTrue(CreatureModelTestSupport.solidPartCount(root) >= 21);
        CreatureModelTestSupport.assertUvsWithin(root, 192, 128);
        CreatureModelTestSupport.assertOpaqueUvs(root, ImageIO.read(TEXTURE.toFile()), cube -> true);
    }

    @Test
    void pinsGroundingGeometryAndThreeReadableSilhouettes() {
        final ModelPart root = EmberhornArchfiendModel.createBodyLayer().bakeRoot();
        final String neutral = geometrySnapshot(root); final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        final String front = view(root, 0.0F); final String side = view(root, -1.5707964F); final String threeQuarter = view(root, -0.7853982F);
        assertAll(
            () -> assertEquals("333764b02ea967830c00d503f9ae94f4a871eefca44f236e608e3dd9604e9f61", neutral),
            () -> assertEquals(new CreatureModelTestSupport.Bounds(-17.632753F, -8.991124F, -6.53186F, 17.632753F, 24.198895F, 8.853881F), bounds),
            () -> assertEquals("8596b1dc1bee3021ebcbf1617fc04d9b2dd3e1c0393eebfe9b8c2014e2d27f8e", front),
            () -> assertEquals("6545b78b066c034f53b229452f4e3efeed8149fac951ee50b8a34db2096ba92f", side),
            () -> assertEquals("054c865e1c64aaa9623b708f93ba4febf41fe6af58df834fc33079cc3c28e6e2", threeQuarter)
        );
    }

    @Test
    void movementAndEruptionActionAreDistinctPinnedPoses() throws Exception {
        final EmberhornArchfiendModel model = new EmberhornArchfiendModel(EmberhornArchfiendModel.createBodyLayer().bakeRoot());
        final EmberhornArchfiendModel.State movement = motionState();
        model.setupAnim(movement);
        final String movementHash = geometrySnapshot(model.root());
        final EmberhornArchfiendModel.State action = motionState();
        action.eruptionProgress = 0.84F;
        action.aggressive = true;
        model.setupAnim(action);
        final String actionHash = geometrySnapshot(model.root());
        writeContactSheet(model, action);
        assertAll(
            () -> assertEquals("f161e2e338eb981b394d98bfe2963b8ad8759ffb23623d8d958f5bc91812b6e3", movementHash),
            () -> assertEquals("191ba3bbe6aeef21c1f7ea44f11bfbf989398d36b27348c6274fa9cd761796e1", actionHash),
            () -> assertNotEquals(movementHash, actionHash)
        );
    }

    @Test
    void sourceAndAtlasAreIndependentAndBytePinned() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<EmberhornArchfiendModel.State>"));
        assertTrue(source.contains("public static void extractRenderState(")); assertTrue(source.contains("final InfernalHierarchyEntity entity"));
        assertTrue(source.contains("\"exposed_demon_heart\""));
        assertTrue(source.contains("\"right_guard_rib\""));
        assertFalse(source.toLowerCase(java.util.Locale.ROOT).contains("furnace"));
        assertFalse(source.toLowerCase(java.util.Locale.ROOT).contains("chimney"));
        for (final String forbidden : java.util.List.of("ArcaneCreatureModel", "CreatureModelProfile", "GeometryHelper", "AnimationHelper", "ModelHelper", "RigCatalog", "extends Warlockery")) assertFalse(source.contains(forbidden), forbidden);
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        assertEquals(192, texture.getWidth());
        assertEquals(128, texture.getHeight());
        assertTrue(hasTransparentPixel(texture));
        assertEquals("4247860382f5fb001386510540f76a9e451dba693d11df23deb3daa3ed7bd8cd", hash(TEXTURE));
    }

    private static EmberhornArchfiendModel.State motionState() {
        final EmberhornArchfiendModel.State state = new EmberhornArchfiendModel.State();
        state.yRot = -31.0F; state.xRot = 7.0F; state.walkAnimationPos = 3.1F;
        state.walkAnimationSpeed = 0.68F; state.ageInTicks = 53.0F;
        return state;
    }

    private static String view(final ModelPart root, final float rotation) {
        root.yRot = rotation;
        final String result = imageSnapshot(softwareSnapshot(root, CreatureModelTestSupport.Projection.FRONT, 128, 5));
        root.yRot = 0.0F;
        return result;
    }

    private static String hash(final Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    private static boolean hasTransparentPixel(final BufferedImage image) { for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) if ((image.getRGB(x, y) >>> 24) == 0) return true; return false; }

    private static void writeContactSheet(final EmberhornArchfiendModel model, final EmberhornArchfiendModel.State action) throws Exception {
        final BufferedImage sheet = new BufferedImage(768, 160, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = sheet.createGraphics(); graphics.setColor(new Color(45, 34, 30)); graphics.fillRect(0, 0, 768, 160); model.setupAnim(new EmberhornArchfiendModel.State());
        final float[] turns = {0, 1.5707964F, 3.1415927F, -1.5707964F, -0.7853982F};
        for (int i = 0; i < turns.length; i++) { model.root().yRot = turns[i]; graphics.drawImage(softwareSnapshot(model.root(), CreatureModelTestSupport.Projection.FRONT, 128, 6), i * 128, 0, null); }
        model.setupAnim(action); graphics.drawImage(softwareSnapshot(model.root(), CreatureModelTestSupport.Projection.FRONT, 128, 6), 640, 0, null); graphics.dispose();
        final Path output = Path.of("build/reports/visual-audit/creatures/emberhorn_archfiend-software-contact-sheet.png"); Files.createDirectories(output.getParent()); ImageIO.write(sheet, "png", output.toFile());
    }
}
