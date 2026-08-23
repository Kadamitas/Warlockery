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

final class BrambleColossusModelTest {
    private static final Path SOURCE = Path.of("src/main/java/com/kadamitas/warlockery/client/model/BrambleColossusModel.java");
    private static final Path TEXTURE = Path.of("src/main/resources/assets/warlockery/textures/entity/bramble_colossus.png");

    @Test
    void bakesAnIndependentLowSiegeOrganismHierarchy() throws Exception {
        assertEquals(256, BrambleColossusModel.TEXTURE_WIDTH);
        assertEquals(128, BrambleColossusModel.TEXTURE_HEIGHT);
        final ModelPart root = BrambleColossusModel.createBodyLayer().bakeRoot();
        final ModelPart body = requiredChild(root, "siege_body");
        assertFalse(requiredChild(body, "vaulted_carapace").isEmpty());
        final ModelPart carapace = requiredChild(body, "vaulted_carapace");
        assertFalse(requiredChild(carapace, "high_bloom_bulb").isEmpty());
        assertFalse(requiredChild(carapace, "low_bloom_bulb").isEmpty());
        assertFalse(requiredChild(body, "wedge_head").isEmpty());
        assertFalse(requiredChild(body, "left_thorn_outrigger").isEmpty());
        assertFalse(requiredChild(body, "right_thorn_outrigger").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_fore_post"), "left_sweep_hook").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_fore_post"), "right_sweep_hook").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_rear_haunch"), "left_drag_foot").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_rear_haunch"), "right_drag_foot").isEmpty());
        assertTrue(CreatureModelTestSupport.solidPartCount(root) >= 22);
        CreatureModelTestSupport.assertUvsWithin(root, 256, 128);
        CreatureModelTestSupport.assertOpaqueUvs(root, ImageIO.read(TEXTURE.toFile()), cube -> true);
    }

    @Test
    void pinsBroadLowGroundedGeometryAndThreeReadableSilhouettes() {
        final ModelPart root = BrambleColossusModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        assertAll(
            () -> assertEquals("4c9f94906cdd96e538133b344f5db2e93d4bf609b8dac19bede46c03aaaf3dc2", geometrySnapshot(root)),
            () -> assertEquals(24.100096F, bounds.maxY(), 0.001F),
            () -> assertEquals(new CreatureModelTestSupport.Bounds(-21.981842F, -13.352722F, -22.982052F, 22.280518F, 24.100096F, 17.968143F), bounds),
            () -> assertEquals("e80d1711feacbf4fb46d77cee924b4577afa5f9e3c89c04430b1e4f8132d8659", view(root, 0.0F)),
            () -> assertEquals("431ae0d515dac650aff35158ddbcc6f6ed35fbc3d6151d8e4ae689ce251bd519", view(root, -1.5707964F)),
            () -> assertEquals("ba3c29c3100ab334d00f71bdf186fd3175fe73e4dd50494e4c15767475dc85fd", view(root, -0.7853982F))
        );
    }

    @Test
    void draggingGaitAndPostedDisplaySweepAreDistinctPinnedPoses() {
        final BrambleColossusModel neutralModel = new BrambleColossusModel(
            BrambleColossusModel.createBodyLayer().bakeRoot()
        );
        neutralModel.setupAnim(new BrambleColossusModel.State());
        final float neutralWidth = width(CreatureModelTestSupport.bounds(neutralModel.root()));
        final BrambleColossusModel model = new BrambleColossusModel(BrambleColossusModel.createBodyLayer().bakeRoot());
        final BrambleColossusModel.State movement = motionState();
        model.setupAnim(movement);
        final String movementHash = geometrySnapshot(model.root());
        final BrambleColossusModel.State display = motionState();
        display.posted = true;
        display.displaying = true;
        display.nerve = 92;
        display.actionProgress = 0.76F;
        model.setupAnim(display);
        final String actionHash = geometrySnapshot(model.root());
        final PoseStack sweepTransform = new PoseStack();
        final ModelPart forePost = requiredChild(model.root(), "left_fore_post");
        forePost.translateAndRotate(sweepTransform);
        requiredChild(forePost, "left_sweep_hook").translateAndRotate(sweepTransform);
        final ModelPart actionCarapace = requiredChild(requiredChild(model.root(), "siege_body"), "vaulted_carapace");
        final ModelPart highBulb = requiredChild(actionCarapace, "high_bloom_bulb");
        final ModelPart lowBulb = requiredChild(actionCarapace, "low_bloom_bulb");
        final float actionWidth = width(CreatureModelTestSupport.bounds(model.root()));
        assertAll(
            () -> assertEquals("6ef2cd4289bd2c3719c86712ed56d16c6426f4e8a31206b1bc81b85eb5d383b9", movementHash),
            () -> assertEquals("75b9b20dc4645986ed9c7a064e090de22c78019a8cecafe1625e7992557515d4", actionHash),
            () -> assertEquals("9170b074ccfc20a936cc59f35ec9f6a94cb84aac66b2bba038eb07533f009cee", matrixSnapshot(sweepTransform)),
            () -> assertNotEquals(movementHash, actionHash),
            () -> assertTrue(actionWidth >= neutralWidth * 1.12F,
                "display hook width " + actionWidth + " must exceed idle width " + neutralWidth),
            () -> assertTrue(highBulb.xScale >= 1.28F, "high bulb must flare during display"),
            () -> assertTrue(highBulb.xScale >= lowBulb.xScale + 0.16F,
                "the unequal display bulbs must not inflate into matching domes")
        );
    }

    @Test
    void sourceExtractsSynchronizedPostedNerveLegAndDisplayWithoutSharedRigCode() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<BrambleColossusModel.State>"));
        assertTrue(source.contains("public static void extractRenderState("));
        assertTrue(source.contains("final BrambleColossusEntity entity"));
        assertTrue(source.contains("entity.presentationPosted()"));
        assertTrue(source.contains("entity.presentationNerve()"));
        assertTrue(source.contains("entity.presentationLeg()"));
        assertTrue(source.contains("entity.presentationPhase()"));
        assertFalse(source.contains("entity.colossusState()"));
        assertFalse(source.contains("entity.colossusTransient()"));
        for (final String forbidden : java.util.List.of(
            "ArcaneCreatureModel", "CreatureModelProfile", "GeometryHelper", "AnimationHelper",
            "ModelHelper", "RigCatalog", "ModelCatalog", "FamilyAnimator", "EntModel", "ThornedPursuerModel"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        assertEquals(256, texture.getWidth());
        assertEquals(128, texture.getHeight());
        assertTrue(hasTransparentPixel(texture));
        assertEquals("664aea9044966289c1cdacc57d75e47605ebc0a731b805348a7a560e712155f2", hash(TEXTURE));
    }

    @Test
    void writesApprovedSixViewSoftwareContactSheet() throws Exception {
        final BrambleColossusModel model = new BrambleColossusModel(BrambleColossusModel.createBodyLayer().bakeRoot());
        final BrambleColossusModel.State action = motionState();
        action.posted = true;
        action.displaying = true;
        action.nerve = 92;
        action.actionProgress = 0.76F;
        writeContactSheet(model, action);
    }

    private static BrambleColossusModel.State motionState() {
        final BrambleColossusModel.State state = new BrambleColossusModel.State();
        state.yRot = -11.0F;
        state.xRot = 4.0F;
        state.walkAnimationPos = 3.1F;
        state.walkAnimationSpeed = 0.66F;
        state.ageInTicks = 58.0F;
        state.leg = 2;
        state.nerve = 74;
        return state;
    }

    private static String view(final ModelPart root, final float rotation) {
        root.yRot = rotation;
        final String snapshot = imageSnapshot(softwareSnapshot(root, CreatureModelTestSupport.Projection.FRONT, 160, 7));
        root.yRot = 0.0F;
        return snapshot;
    }

    private static float width(final CreatureModelTestSupport.Bounds bounds) {
        return bounds.maxX() - bounds.minX();
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

    private static void writeContactSheet(final BrambleColossusModel model, final BrambleColossusModel.State action) throws Exception {
        final BufferedImage sheet = new BufferedImage(960, 176, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new Color(49, 35, 38));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        final float[] turns = {0.0F, 1.5707964F, 3.1415927F, -1.5707964F, -0.7853982F};
        for (int index = 0; index < turns.length; index++) {
            model.setupAnim(new BrambleColossusModel.State());
            model.root().yRot = turns[index];
            graphics.drawImage(softwareSnapshot(model.root(), CreatureModelTestSupport.Projection.FRONT, 160, 8), index * 160, 0, null);
        }
        model.setupAnim(action);
        graphics.drawImage(softwareSnapshot(model.root(), CreatureModelTestSupport.Projection.FRONT, 160, 8), 800, 0, null);
        graphics.dispose();
        final Path output = Path.of("build/reports/visual-audit/creatures/bramble_colossus-software-contact-sheet.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(sheet, "png", output.toFile());
    }
}
