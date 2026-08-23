package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.geometrySnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.imageSnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.matrixSnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.softwareSnapshot;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

final class DeathModelTest {
    private static final Path SOURCE = Path.of("src/main/java/com/kadamitas/warlockery/client/model/DeathModel.java");
    private static final Path TEXTURE = Path.of("src/main/resources/assets/warlockery/textures/entity/death.png");

    @Test
    void bakesBlueEyedAppointmentKeeperHierarchyOnItsOwnAtlas() throws Exception {
        assertEquals(128, DeathModel.TEXTURE_WIDTH); assertEquals(128, DeathModel.TEXTURE_HEIGHT);
        final ModelPart root = DeathModel.createBodyLayer().bakeRoot();
        final ModelPart shroud = requiredChild(root, "appointment_shroud"); final ModelPart hood = requiredChild(shroud, "void_hood");
        assertFalse(requiredChild(hood, "right_blue_eye").isEmpty());
        assertFalse(requiredChild(hood, "left_blue_eye").isEmpty());
        assertFalse(requiredChild(hood, "hourglass_crown").isEmpty());
        assertFalse(requiredChild(requiredChild(shroud, "right_sleeve"), "iron_scythe").isEmpty());
        assertFalse(requiredChild(requiredChild(requiredChild(shroud, "right_sleeve"), "iron_scythe"), "crescent_blade").isEmpty());
        assertFalse(requiredChild(requiredChild(shroud, "left_sleeve"), "appointment_lantern").isEmpty());
        assertFalse(requiredChild(requiredChild(shroud, "split_robe"), "right_robe_tail").isEmpty());
        assertFalse(requiredChild(requiredChild(shroud, "split_robe"), "left_robe_tail").isEmpty());
        assertTrue(CreatureModelTestSupport.solidPartCount(root) >= 15);
        CreatureModelTestSupport.assertUvsWithin(root, 128, 128);
        CreatureModelTestSupport.assertOpaqueUvs(root, ImageIO.read(TEXTURE.toFile()), cube -> true);
    }

    @Test
    void pinsGroundingGeometryAndThreeReadableSilhouettes() {
        final ModelPart root = DeathModel.createBodyLayer().bakeRoot(); final String neutral = geometrySnapshot(root); final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        final String front = view(root, 0); final String side = view(root, -1.5707964F); final String threeQuarter = view(root, -0.7853982F);
        assertAll(() -> assertEquals("1218dbb63861807af0466f565f060f98a1f1454bc299e252e0ddd6631ab1f194", neutral), () -> assertEquals(new CreatureModelTestSupport.Bounds(-19.31066F, -4.2F, -4.7999997F, 9.690853F, 32.04995F, 3.5F), bounds), () -> assertEquals("77dcdab995db385010bc1dff701272d9d13d27b56c2bb55ceb920e0f5b0d79b5", front), () -> assertEquals("a1a3bcebeabe8e3c65ae79058fae86d8c4e2e34780e9e78bde9d4f7d99613656", side), () -> assertEquals("2379e736fa4dd0f50432a1f80a8e52697cdfb83ab81540c162a634bb06bfd237", threeQuarter));
    }

    @Test
    void stalkingAndReapActionAreDistinctPinnedPoses() throws Exception {
        final DeathModel model = new DeathModel(DeathModel.createBodyLayer().bakeRoot()); final DeathModel.State movement = motionState(); model.setupAnim(movement);
        final String movementHash = geometrySnapshot(model.root());
        final DeathModel.State action = motionState(); action.reapProgress = 0.92F; action.telegraph = true; model.setupAnim(action);
        final String actionHash = geometrySnapshot(model.root()); final String rightHand = hand(model, action, HumanoidArm.RIGHT); final String leftHand = hand(model, action, HumanoidArm.LEFT);
        writeContactSheet(model, action);
        assertAll(() -> assertEquals("3653a3ef8f80db8316b9e7a9668d64f409fa24a0988fe925a6bb933044e2736b", movementHash), () -> assertEquals("7328397f84c26be1c37419688be78fb3a1d3db645c68e3e26813f72fdc6cad4e", actionHash), () -> assertNotEquals(movementHash, actionHash), () -> assertEquals("af88f645e55c130bfdf7fb1f552c103f8ab82a11004e713965c52fd6a2600c8c", rightHand), () -> assertEquals("e77ef1218815d7a3138d8f57103d37d980e9eb03d3f2ba2270d97a6ee0884353", leftHand));
    }

    @Test
    void sourceAndAtlasAreIndependentAndBytePinned() throws Exception {
        final String source = Files.readString(SOURCE); assertTrue(source.contains("extends EntityModel<DeathModel.State>")); assertTrue(source.contains("public static void extractRenderState(")); assertTrue(source.contains("final DeathEntity entity"));
        assertTrue(source.contains("\"right_blue_eye\""));
        assertTrue(source.contains("\"hourglass_crown\""));
        assertFalse(source.toLowerCase(java.util.Locale.ROOT).contains("bone_mask"));
        assertTrue(source.contains("\"skull_face\""));
        for (final String forbidden : java.util.List.of("ArcaneCreatureModel", "CreatureModelProfile", "GeometryHelper", "AnimationHelper", "ModelHelper", "RigCatalog", "extends Warlockery")) assertFalse(source.contains(forbidden), forbidden);
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile()); assertEquals(128, texture.getWidth()); assertEquals(128, texture.getHeight()); assertTrue(hasTransparentPixel(texture)); assertEquals("2128dda8ca936447d4d90b76354e1f58f5024066d104262aa37b617fc4a176a5", hash(TEXTURE));
    }

    private static DeathModel.State motionState() { final DeathModel.State state = new DeathModel.State(); state.yRot = -26; state.xRot = 12; state.walkAnimationPos = 2.8F; state.walkAnimationSpeed = 0.48F; state.ageInTicks = 61; return state; }
    private static String view(final ModelPart root, final float rotation) { root.yRot = rotation; final String result = imageSnapshot(softwareSnapshot(root, CreatureModelTestSupport.Projection.FRONT, 128, 5)); root.yRot = 0; return result; }
    private static String hand(final DeathModel model, final DeathModel.State state, final HumanoidArm arm) { final PoseStack stack = new PoseStack(); model.translateToHand(state, arm, stack); return matrixSnapshot(stack); }
    private static String hash(final Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    private static boolean hasTransparentPixel(final BufferedImage image) { for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) if ((image.getRGB(x, y) >>> 24) == 0) return true; return false; }
    private static void writeContactSheet(final DeathModel model, final DeathModel.State action) throws Exception { final BufferedImage sheet = new BufferedImage(768, 160, BufferedImage.TYPE_INT_ARGB); final Graphics2D g = sheet.createGraphics(); g.setColor(new Color(33, 38, 39)); g.fillRect(0, 0, 768, 160); model.setupAnim(new DeathModel.State()); final float[] turns = {0, 1.5707964F, 3.1415927F, -1.5707964F, -0.7853982F}; for (int i = 0; i < turns.length; i++) { model.root().yRot = turns[i]; g.drawImage(softwareSnapshot(model.root(), CreatureModelTestSupport.Projection.FRONT, 128, 6), i * 128, 0, null); } model.setupAnim(action); g.drawImage(softwareSnapshot(model.root(), CreatureModelTestSupport.Projection.FRONT, 128, 6), 640, 0, null); g.dispose(); final Path output = Path.of("build/reports/visual-audit/creatures/death-software-contact-sheet.png"); Files.createDirectories(output.getParent()); ImageIO.write(sheet, "png", output.toFile()); }
}
