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

final class EntModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/EntModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/ent.png"
    );

    @Test
    void bakesAnIndependentAsymmetricSplitTrunkHierarchy() throws Exception {
        assertEquals(256, EntModel.TEXTURE_WIDTH);
        assertEquals(128, EntModel.TEXTURE_HEIGHT);
        final ModelPart root = EntModel.createBodyLayer().bakeRoot();
        final ModelPart trunk = requiredChild(root, "trunk_base");
        final ModelPart split = requiredChild(trunk, "split_trunk");
        assertFalse(requiredChild(split, "hollow_knot").isEmpty());
        assertFalse(requiredChild(requiredChild(split, "high_shoulder"), "right_branch_arm").isEmpty());
        assertFalse(requiredChild(requiredChild(split, "low_shoulder"), "left_branch_arm").isEmpty());
        final ModelPart crown = requiredChild(split, "branch_crown");
        assertFalse(requiredChild(crown, "crown_fork_left").isEmpty());
        assertFalse(requiredChild(crown, "crown_fork_right").isEmpty());
        final CreatureModelTestSupport.Bounds highCanopy = CreatureModelTestSupport.bounds(
            requiredChild(crown, "crown_canopy_high")
        );
        final CreatureModelTestSupport.Bounds leftCanopy = CreatureModelTestSupport.bounds(
            requiredChild(crown, "crown_canopy_left")
        );
        final CreatureModelTestSupport.Bounds rightCanopy = CreatureModelTestSupport.bounds(
            requiredChild(crown, "crown_canopy_right")
        );
        final ModelPart highCanopyPart = requiredChild(crown, "crown_canopy_high");
        final ModelPart leftCanopyPart = requiredChild(crown, "crown_canopy_left");
        final ModelPart rightCanopyPart = requiredChild(crown, "crown_canopy_right");
        final ModelPart rightRootLeg = requiredChild(root, "right_root_leg");
        final ModelPart leftRootLeg = requiredChild(root, "left_root_leg");
        assertAll(
            () -> assertTrue(width(highCanopy) >= 13.0F && height(highCanopy) >= 6.5F && depth(highCanopy) >= 11.0F),
            () -> assertTrue(width(leftCanopy) >= 12.0F && height(leftCanopy) >= 5.5F && depth(leftCanopy) >= 10.0F),
            () -> assertTrue(width(rightCanopy) >= 11.0F && height(rightCanopy) >= 5.5F && depth(rightCanopy) >= 9.0F),
            () -> assertTrue(maximum(highCanopyPart.z, leftCanopyPart.z, rightCanopyPart.z)
                - minimum(highCanopyPart.z, leftCanopyPart.z, rightCanopyPart.z) >= 1.5F,
                "the three canopy masses need separated profile depth"),
            () -> assertTrue(Math.abs(rightRootLeg.z - leftRootLeg.z) >= 4.0F,
                "the split rooted stride needs front-to-back profile separation")
        );
        assertFalse(requiredChild(requiredChild(root, "right_root_leg"), "right_root_foot").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_root_leg"), "left_root_foot").isEmpty());
        assertTrue(CreatureModelTestSupport.solidPartCount(root) >= 24);
        CreatureModelTestSupport.assertUvsWithin(root, EntModel.TEXTURE_WIDTH, EntModel.TEXTURE_HEIGHT);
        CreatureModelTestSupport.assertOpaqueUvs(root, ImageIO.read(TEXTURE.toFile()), cube -> true);
    }

    @Test
    void pinsGroundedToweringGeometryAndThreeReadableSilhouettes() {
        final ModelPart root = EntModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        final int frontCrownOccupancy = upperBandPixels(root, 0.0F);
        final int rearCrownOccupancy = upperBandPixels(root, 3.1415927F);
        final int threeQuarterCrownOccupancy = upperBandPixels(root, -0.7853982F);
        assertAll(
            () -> assertEquals("05aef542d28e5d86f8fe6b90094317cbe4114d98a1732bad17e2614a46df33a8", geometrySnapshot(root)),
            () -> assertEquals(23.597248F, bounds.maxY(), 0.001F),
            () -> assertEquals(new CreatureModelTestSupport.Bounds(-39.98724F, -41.090107F, -15.080273F, 39.67505F, 23.597248F, 12.856086F), bounds),
            () -> assertEquals("1d670472d5748b4d6e2616b25e54683f6c1b14ec33b960324a2622be6e1f42ea", view(root, 0.0F)),
            () -> assertEquals("f1a6f0675bd3ab4ad190f2d2eb25d8e9073958cc98664e5e953eff13211cd816", view(root, -1.5707964F)),
            () -> assertEquals("b42d3ffa61bee98b0810fb8c0713d95f1f62c0e766d176232bf44c5f7c4306e0", view(root, -0.7853982F)),
            () -> assertTrue(frontCrownOccupancy >= 1350, "front crown occupancy " + frontCrownOccupancy),
            () -> assertTrue(rearCrownOccupancy >= 1350, "rear crown occupancy " + rearCrownOccupancy),
            () -> assertTrue(threeQuarterCrownOccupancy >= 1400, "three-quarter crown occupancy " + threeQuarterCrownOccupancy)
        );
    }

    @Test
    void profileRetainsBroadCanopyAndSplitRootReadability() {
        final ModelPart root = EntModel.createBodyLayer().bakeRoot();
        final BufferedImage front = renderedView(root, 0.0F);
        final BufferedImage side = renderedView(root, -1.5707964F);
        assertTrue(
            occupiedWidth(side) >= occupiedWidth(front) * 0.42F,
            "side width " + occupiedWidth(side) + " must retain at least 42% of front width "
                + occupiedWidth(front)
        );
    }

    @Test
    void rootedStrideAndRousingSweepAreDistinctPinnedPoses() {
        final EntModel model = new EntModel(EntModel.createBodyLayer().bakeRoot());
        final EntModel.State movement = motionState();
        model.setupAnim(movement);
        final String movementHash = geometrySnapshot(model.root());
        final EntModel.State roused = motionState();
        roused.roused = true;
        roused.attackProgress = 0.82F;
        model.setupAnim(roused);
        final String actionHash = geometrySnapshot(model.root());
        final PoseStack branchTransform = new PoseStack();
        requiredChild(model.root(), "trunk_base").translateAndRotate(branchTransform);
        final ModelPart split = requiredChild(requiredChild(model.root(), "trunk_base"), "split_trunk");
        split.translateAndRotate(branchTransform);
        final ModelPart shoulder = requiredChild(split, "high_shoulder");
        shoulder.translateAndRotate(branchTransform);
        requiredChild(shoulder, "right_branch_arm").translateAndRotate(branchTransform);
        assertAll(
            () -> assertEquals("bc3b0f0b87741d6235ef8cb39e20a8da621f252a2279ed17d5dd5f1ecf4a24af", movementHash),
            () -> assertEquals("05a0239da2fd2e69644d86da5a8a2d202a569071b096017f125f32d9ef83e6f4", actionHash),
            () -> assertEquals("816dc6454e84e745dd6f239e58822275ae3369e09147662b788c738e294415ad", matrixSnapshot(branchTransform)),
            () -> assertNotEquals(movementHash, actionHash)
        );
    }

    @Test
    void sourceExtractsTheRealVariantAndOwnsItsEntireRig() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<EntModel.State>"));
        assertTrue(source.contains("public static void extractRenderState("));
        assertTrue(source.contains("final EntEntity entity"));
        assertTrue(source.contains("entity.variant().tint()"));
        for (final String forbidden : java.util.List.of(
            "ArcaneCreatureModel", "CreatureModelProfile", "GeometryHelper", "AnimationHelper",
            "ModelHelper", "RigCatalog", "ModelCatalog", "FamilyAnimator", "BrambleColossusModel",
            "ThornedPursuerModel"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        assertEquals(256, texture.getWidth());
        assertEquals(128, texture.getHeight());
        assertTrue(hasTransparentPixel(texture));
        assertEquals("007ef57ca9c9f907e79a7afae589680cde5fa1c0195965f1766ca7518c7215cc", hash(TEXTURE));
    }

    @Test
    void writesApprovedSixViewSoftwareContactSheet() throws Exception {
        final EntModel model = new EntModel(EntModel.createBodyLayer().bakeRoot());
        final EntModel.State action = motionState();
        action.roused = true;
        action.attackProgress = 0.82F;
        writeContactSheet(model, action);
    }

    private static EntModel.State motionState() {
        final EntModel.State state = new EntModel.State();
        state.yRot = 16.0F;
        state.xRot = -5.0F;
        state.walkAnimationPos = 2.35F;
        state.walkAnimationSpeed = 0.58F;
        state.ageInTicks = 64.0F;
        return state;
    }

    private static String view(final ModelPart root, final float rotation) {
        return imageSnapshot(renderedView(root, rotation));
    }

    private static BufferedImage renderedView(final ModelPart root, final float rotation) {
        root.yRot = rotation;
        final BufferedImage image = softwareSnapshot(root, CreatureModelTestSupport.Projection.FRONT, 160, 7);
        root.yRot = 0.0F;
        return image;
    }

    private static int occupiedWidth(final BufferedImage image) {
        int minimum = image.getWidth();
        int maximum = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    minimum = Math.min(minimum, x);
                    maximum = Math.max(maximum, x);
                }
            }
        }
        return maximum < minimum ? 0 : maximum - minimum + 1;
    }

    private static float minimum(final float first, final float second, final float third) {
        return Math.min(first, Math.min(second, third));
    }

    private static float maximum(final float first, final float second, final float third) {
        return Math.max(first, Math.max(second, third));
    }

    private static int upperBandPixels(final ModelPart root, final float rotation) {
        root.yRot = rotation;
        final BufferedImage image = softwareSnapshot(root, CreatureModelTestSupport.Projection.FRONT, 160, 7);
        root.yRot = 0.0F;
        int occupied = 0;
        for (int y = 0; y < 45; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) occupied++;
            }
        }
        return occupied;
    }

    private static float width(final CreatureModelTestSupport.Bounds bounds) {
        return bounds.maxX() - bounds.minX();
    }

    private static float height(final CreatureModelTestSupport.Bounds bounds) {
        return bounds.maxY() - bounds.minY();
    }

    private static float depth(final CreatureModelTestSupport.Bounds bounds) {
        return bounds.maxZ() - bounds.minZ();
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

    private static void writeContactSheet(final EntModel model, final EntModel.State action) throws Exception {
        final BufferedImage sheet = new BufferedImage(960, 176, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new Color(39, 46, 34));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        final float[] turns = {0.0F, 1.5707964F, 3.1415927F, -1.5707964F, -0.7853982F};
        for (int index = 0; index < turns.length; index++) {
            model.setupAnim(new EntModel.State());
            model.root().yRot = turns[index];
            graphics.drawImage(softwareSnapshot(model.root(), CreatureModelTestSupport.Projection.FRONT, 160, 8), index * 160, 0, null);
        }
        model.setupAnim(action);
        graphics.drawImage(softwareSnapshot(model.root(), CreatureModelTestSupport.Projection.FRONT, 160, 8), 800, 0, null);
        graphics.dispose();
        final Path output = Path.of("build/reports/visual-audit/creatures/ent-software-contact-sheet.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(sheet, "png", output.toFile());
    }
}
