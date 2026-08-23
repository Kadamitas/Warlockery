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
import org.junit.jupiter.api.Test;

final class ThornedPursuerModelTest {
    private static final Path SOURCE = Path.of("src/main/java/com/kadamitas/warlockery/client/model/ThornedPursuerModel.java");
    private static final Path TEXTURE = Path.of("src/main/resources/assets/warlockery/textures/entity/thorned_pursuer.png");

    @Test
    void bakesAnIndependentLeanRitualHunterHierarchy() throws Exception {
        assertEquals(192, ThornedPursuerModel.TEXTURE_WIDTH);
        assertEquals(128, ThornedPursuerModel.TEXTURE_HEIGHT);
        final ModelPart root = ThornedPursuerModel.createBodyLayer().bakeRoot();
        final ModelPart torso = requiredChild(root, "ritual_torso");
        assertFalse(requiredChild(torso, "seedpod_head").isEmpty());
        assertFalse(requiredChild(torso, "high_hooked_shoulders").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_sickle_arm"), "left_sickle_blade").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_sickle_arm"), "right_sickle_blade").isEmpty());
        assertFalse(requiredChild(requiredChild(requiredChild(root, "left_digitigrade_leg"), "left_recurved_shin"), "left_toe_cluster").isEmpty());
        assertFalse(requiredChild(requiredChild(requiredChild(root, "right_digitigrade_leg"), "right_recurved_shin"), "right_toe_cluster").isEmpty());
        final ModelPart tendrils = requiredChild(torso, "snare_tendrils");
        assertFalse(requiredChild(tendrils, "left_trailing_snare").isEmpty());
        assertFalse(requiredChild(tendrils, "right_trailing_snare").isEmpty());
        assertFalse(requiredChild(tendrils, "left_lower_snare").isEmpty());
        assertFalse(requiredChild(tendrils, "right_lower_snare").isEmpty());
        assertTrue(CreatureModelTestSupport.solidPartCount(root) >= 24);
        CreatureModelTestSupport.assertUvsWithin(root, 192, 128);
        CreatureModelTestSupport.assertOpaqueUvs(root, ImageIO.read(TEXTURE.toFile()), cube -> true);
    }

    @Test
    void pinsLeanStalkingGroundedGeometryAndThreeReadableSilhouettes() {
        final ModelPart root = ThornedPursuerModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        assertAll(
            () -> assertEquals("18125cfed1c14897151b088ff3dda0b8e0056a18611f6f71bdded046a4f0355b", geometrySnapshot(root)),
            () -> assertEquals(23.998596F, bounds.maxY(), 0.001F),
            () -> assertEquals(new CreatureModelTestSupport.Bounds(-23.29545F, -7.615847F, -14.676797F, 23.29545F, 23.998596F, 16.560318F), bounds),
            () -> assertEquals("3245b542b6db4e0d0c8503a86bb67f4df0364bd46453210ad23c7ee0510a04f6", view(root, 0.0F)),
            () -> assertEquals("612f9f7ea5f6ddac7576626569fc44711bb11e013dfad509c4f1c46fffe2e6fe", view(root, -1.5707964F)),
            () -> assertEquals("c2c85bb3715dc68ddf5b730f5ee7a66f4fafcdc83a3cc7d9d85ee672bea54fcd", view(root, -0.7853982F))
        );
    }

    @Test
    void stalkingGaitAndAnchoredSnareAreDistinctPinnedPoses() {
        final ThornedPursuerModel model = new ThornedPursuerModel(ThornedPursuerModel.createBodyLayer().bakeRoot());
        final ThornedPursuerModel.State movement = motionState();
        model.setupAnim(movement);
        final String movementHash = geometrySnapshot(model.root());
        final ThornedPursuerModel.State snare = motionState();
        snare.anchored = true;
        snare.snaring = true;
        snare.actionProgress = 0.84F;
        model.setupAnim(snare);
        final String actionHash = geometrySnapshot(model.root());
        final PoseStack snareTransform = new PoseStack();
        final ModelPart torso = requiredChild(model.root(), "ritual_torso");
        torso.translateAndRotate(snareTransform);
        final ModelPart tendrils = requiredChild(torso, "snare_tendrils");
        tendrils.translateAndRotate(snareTransform);
        requiredChild(tendrils, "left_trailing_snare").translateAndRotate(snareTransform);
        assertAll(
            () -> assertEquals("63b64ca36fa57da27d72d42708cc61fd920ee9984d9d988940b313d36d8ba775", movementHash),
            () -> assertEquals("68bab4e1b27abc2bcd8f8d8a551b9942f3979f1fc369f5280cfda9de7318c664", actionHash),
            () -> assertEquals("011cc3a4940fdc57df9986a97ed08414018eef3329bcdfc23bd1d52d7241f18b", matrixSnapshot(snareTransform)),
            () -> assertNotEquals(movementHash, actionHash)
        );
    }

    @Test
    void sourceExtractsSynchronizedPhaseAndSnareCooldownWithoutSharedRigCode() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<ThornedPursuerModel.State>"));
        assertTrue(source.contains("public static void extractRenderState("));
        assertTrue(source.contains("final ThornedPursuerEntity entity"));
        assertTrue(source.contains("entity.presentationPhase()"));
        assertTrue(source.contains("entity.presentationSnareCooldownRemaining()"));
        assertFalse(source.contains("entity.pursuerRuntime()"));
        assertFalse(source.contains("entity.pursuerState()"));
        for (final String forbidden : java.util.List.of(
            "ArcaneCreatureModel", "CreatureModelProfile", "GeometryHelper", "AnimationHelper",
            "ModelHelper", "RigCatalog", "ModelCatalog", "FamilyAnimator", "EntModel", "BrambleColossusModel"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        assertEquals(192, texture.getWidth());
        assertEquals(128, texture.getHeight());
        assertTrue(hasTransparentPixel(texture));
        assertEquals("600491d1cec4536687f6b8d27a907fe76d98f61f1033c5564d5d5e5201a5d1e2", hash(TEXTURE));
    }

    @Test
    void writesApprovedSixViewSoftwareContactSheet() throws Exception {
        final ThornedPursuerModel model = new ThornedPursuerModel(ThornedPursuerModel.createBodyLayer().bakeRoot());
        final ThornedPursuerModel.State action = motionState();
        action.anchored = true;
        action.snaring = true;
        action.actionProgress = 0.84F;
        writeContactSheet(model, action);
    }

    private static ThornedPursuerModel.State motionState() {
        final ThornedPursuerModel.State state = new ThornedPursuerModel.State();
        state.yRot = 24.0F;
        state.xRot = 7.0F;
        state.walkAnimationPos = 2.8F;
        state.walkAnimationSpeed = 0.78F;
        state.ageInTicks = 72.0F;
        state.recovering = false;
        return state;
    }

    private static String view(final ModelPart root, final float rotation) {
        root.yRot = rotation;
        final String snapshot = imageSnapshot(softwareSnapshot(root, CreatureModelTestSupport.Projection.FRONT, 160, 7));
        root.yRot = 0.0F;
        return snapshot;
    }

    private static String hash(final Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static boolean hasTransparentPixel(final BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) == 0) return true;
            }
        }
        return false;
    }

    private static void writeContactSheet(final ThornedPursuerModel model, final ThornedPursuerModel.State action) throws Exception {
        final BufferedImage sheet = new BufferedImage(960, 176, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new Color(37, 31, 43));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        final float[] turns = {0.0F, 1.5707964F, 3.1415927F, -1.5707964F, -0.7853982F};
        for (int index = 0; index < turns.length; index++) {
            model.setupAnim(new ThornedPursuerModel.State());
            model.root().yRot = turns[index];
            graphics.drawImage(softwareSnapshot(model.root(), CreatureModelTestSupport.Projection.FRONT, 160, 8), index * 160, 0, null);
        }
        model.setupAnim(action);
        graphics.drawImage(softwareSnapshot(model.root(), CreatureModelTestSupport.Projection.FRONT, 160, 8), 800, 0, null);
        graphics.dispose();
        final Path output = Path.of("build/reports/visual-audit/creatures/thorned_pursuer-software-contact-sheet.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(sheet, "png", output.toFile());
    }
}
