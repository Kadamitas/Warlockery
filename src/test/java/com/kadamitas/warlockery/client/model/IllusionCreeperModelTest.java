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

import com.kadamitas.warlockery.entity.MimicryRules.Phase;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class IllusionCreeperModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/IllusionCreeperModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/illusion_creeper.png"
    );
    private static final Path CONTACT_SHEET = Path.of(
        "build/reports/visual-audit/creatures/illusion_creeper-software-contact-sheet.png"
    );

    @Test
    void bakesTheIndependentHollowFuseTotemAndPaintsEveryFace() throws Exception {
        assertEquals(64, IllusionCreeperModel.TEXTURE_WIDTH);
        assertEquals(64, IllusionCreeperModel.TEXTURE_HEIGHT);
        final IllusionCreeperModel model = model(new IllusionCreeperModel.State());
        final ModelPart root = model.root();
        final ModelPart mask = requiredChild(root, "mask_head");
        for (final String part : List.of(
            "crown_left", "crown_right", "left_void_shutter", "center_void_shutter",
            "right_void_shutter"
        )) {
            assertFalse(requiredChild(mask, part).isEmpty(), part);
        }
        final ModelPart neck = requiredChild(root, "neck_stack");
        assertFalse(requiredChild(requiredChild(neck, "neck_middle"), "neck_lower").isEmpty());
        final ModelPart trunk = requiredChild(root, "tapered_trunk");
        assertFalse(requiredChild(trunk, "false_fuse_seam").isEmpty());
        assertFalse(requiredChild(trunk, "rear_ridge").isEmpty());
        assertFalse(requiredChild(trunk, "trunk_keystone").isEmpty());
        for (final String foot : List.of(
            "front_left_foot", "front_right_foot", "back_left_foot", "back_right_foot"
        )) {
            assertFalse(requiredChild(root, foot).isEmpty(), foot);
        }
        assertTrue(CreatureModelTestSupport.solidPartCount(root) >= 17);
        CreatureModelTestSupport.assertUvsWithin(
            root, IllusionCreeperModel.TEXTURE_WIDTH, IllusionCreeperModel.TEXTURE_HEIGHT
        );
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        CreatureModelTestSupport.assertOpaqueUvs(root, texture, cube -> true);
        assertEquals(0, texture.getRGB(63, 63) >>> 24, "unused atlas remains transparent");
    }

    @Test
    void pinsGroundContactGeometryAndFourTurnaroundSilhouettes() {
        final IllusionCreeperModel model = model(new IllusionCreeperModel.State());
        final ModelPart root = model.root();
        assertAll(
            () -> assertEquals("ea36c11b1ec2bdc1e992276c740b7448dbae732e33c749e4ee8590257d95d851", geometrySnapshot(root)),
            () -> assertEquals(new CreatureModelTestSupport.Bounds(
                -8.849378F, -0.96768737F, -8.756117F, 8.756117F, 24.0F, 8.702466F
            ),
                CreatureModelTestSupport.bounds(root)),
            () -> assertEquals(24.0F, CreatureModelTestSupport.bounds(root).maxY(), 0.001F),
            () -> assertEquals("eadfbaaefd47ac88170e78576ab0085fb38ee93e4062c2e223b40bde4c50d7b0", viewHash(root, 0.0F)),
            () -> assertEquals("298fb2eeb8c72ed442e1c2fe631ba6231caf9ca21eee1f8837299f494e95a577", viewHash(root, -1.5707964F)),
            () -> assertEquals("5fedffc2cff1a91635868de6ff93090f000fa93039fabe24408dfd882b80218c", viewHash(root, 3.1415927F)),
            () -> assertEquals("9f56e04d8f59746c232e3190c14e4f130aa6d5532539632cc625ecf972ae9d42", viewHash(root, -0.7853982F))
        );
    }

    @Test
    void frontAndSideGroundBandsKeepFourIndependentWedgeFeet() {
        final ModelPart frontRoot = model(new IllusionCreeperModel.State()).root();
        final BufferedImage front = softwareSnapshot(
            frontRoot, CreatureModelTestSupport.Projection.FRONT, 160, 6
        );
        final ModelPart sideRoot = model(new IllusionCreeperModel.State()).root();
        sideRoot.yRot = -1.5707964F;
        final BufferedImage side = softwareSnapshot(
            sideRoot, CreatureModelTestSupport.Projection.FRONT, 160, 6
        );
        assertAll(
            () -> assertTrue(maximumGroundRuns(front) >= 4,
                "front ground band must expose four separated feet"),
            () -> assertTrue(maximumGroundRuns(side) >= 4,
                "side ground band must expose four separated feet")
        );
    }

    @Test
    void neutralAdvanceAndQuietCollapseAreDistinctPinnedPoses() {
        final IllusionCreeperModel neutral = model(new IllusionCreeperModel.State());
        final IllusionCreeperModel moving = model(motionState());
        final IllusionCreeperModel.State collapseState = motionState();
        collapseState.walkAnimationSpeed = 0.0F;
        collapseState.phase = Phase.COLLAPSE;
        final IllusionCreeperModel collapse = model(collapseState);
        assertAll(
            () -> assertEquals("ea36c11b1ec2bdc1e992276c740b7448dbae732e33c749e4ee8590257d95d851", geometrySnapshot(neutral.root())),
            () -> assertEquals("a9c9ff69408d6c345f3a85cd9a7f974be34dae3e83301fb2c2e874dc70778819", geometrySnapshot(moving.root())),
            () -> assertEquals("9cf87ba611907aa9c1b81da86900bd3b5900ee6abc0fbbb842b1eeb8905e96e3", geometrySnapshot(collapse.root())),
            () -> assertNotEquals(geometrySnapshot(neutral.root()), geometrySnapshot(moving.root())),
            () -> assertNotEquals(geometrySnapshot(moving.root()), geometrySnapshot(collapse.root()))
        );
        final ModelPart neutralHead = requiredChild(neutral.root(), "mask_head");
        final ModelPart collapseHead = requiredChild(collapse.root(), "mask_head");
        assertTrue(collapseHead.y > neutralHead.y + 2.0F, "collapse must compress the mask downward");
        assertTrue(Math.abs(requiredChild(collapse.root(), "front_left_foot").zRot)
            > Math.abs(requiredChild(neutral.root(), "front_left_foot").zRot) + 0.2F,
            "collapse must brace its independent feet");
    }

    @Test
    void sourceOwnsItsEntityStateRigAnimationAndAtlas() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<IllusionCreeperModel.State>"));
        assertTrue(source.contains("new MeshDefinition()"));
        assertTrue(source.contains("CubeListBuilder"));
        assertTrue(source.contains("PartPose"));
        assertTrue(source.contains("public static void extractRenderState("));
        assertTrue(source.contains("final IllusionCreeperEntity entity"));
        assertTrue(source.contains("entity.presentationPhase()"));
        assertFalse(source.contains("entity.mimicCore()"));
        for (final String forbidden : List.of(
            "ArcaneCreatureModel", "CreatureModelProfile", "WarlockeryModel", "GeometryHelper",
            "AnimationHelper", "ModelHelper", "RigCatalog", "IllusionSpiderModel",
            "IllusionZombieModel", "extends CreeperModel", "net.minecraft.client.model.CreeperModel"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        assertEquals(64, texture.getWidth());
        assertEquals(64, texture.getHeight());
        assertTrue(hasTransparentPixel(texture));
        assertEquals("c195a13ca5622c640554c33aa5655bceeadd40ac69faa8271257e99873b6015c", hash(TEXTURE));
    }

    @Test
    void writesFrontSideRearThreeQuarterAdvanceAndCollapseSheet() throws Exception {
        final IllusionCreeperModel.State idle = new IllusionCreeperModel.State();
        final IllusionCreeperModel.State collapse = motionState();
        collapse.walkAnimationSpeed = 0.0F;
        collapse.phase = Phase.COLLAPSE;
        writeContactSheet(List.of(
            view(idle, 0.0F),
            view(idle, -1.5707964F),
            view(idle, 3.1415927F),
            view(idle, -0.7853982F),
            view(motionState(), 0.0F),
            view(collapse, 0.0F)
        ));
        final BufferedImage sheet = ImageIO.read(CONTACT_SHEET.toFile());
        assertEquals(768, sheet.getWidth());
        assertEquals(160, sheet.getHeight());
    }

    private static IllusionCreeperModel model(final IllusionCreeperModel.State state) {
        final IllusionCreeperModel model = new IllusionCreeperModel(
            IllusionCreeperModel.createBodyLayer().bakeRoot()
        );
        model.setupAnim(state);
        return model;
    }

    private static IllusionCreeperModel.State motionState() {
        final IllusionCreeperModel.State state = new IllusionCreeperModel.State();
        state.yRot = 14.0F;
        state.xRot = -4.0F;
        state.walkAnimationPos = 2.35F;
        state.walkAnimationSpeed = 0.76F;
        state.ageInTicks = 41.0F;
        state.phase = Phase.APPROACH;
        return state;
    }

    private static String viewHash(final ModelPart root, final float rotation) {
        root.yRot = rotation;
        final String hash = imageSnapshot(softwareSnapshot(
            root, CreatureModelTestSupport.Projection.FRONT, 160, 6
        ));
        root.yRot = 0.0F;
        return hash;
    }

    private static int maximumGroundRuns(final BufferedImage image) {
        int bottom = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) bottom = y;
            }
        }
        int maximum = 0;
        for (int y = Math.max(0, bottom - 10); y <= bottom; y++) {
            int runs = 0;
            boolean occupied = false;
            for (int x = 0; x < image.getWidth(); x++) {
                final boolean next = (image.getRGB(x, y) >>> 24) != 0;
                if (next && !occupied) runs++;
                occupied = next;
            }
            maximum = Math.max(maximum, runs);
        }
        return maximum;
    }

    private static BufferedImage view(final IllusionCreeperModel.State state, final float rotation) {
        final ModelPart root = model(state).root();
        root.yRot = rotation;
        return softwareSnapshot(root, CreatureModelTestSupport.Projection.FRONT, 128, 7);
    }

    private static void writeContactSheet(final List<BufferedImage> views) throws Exception {
        final BufferedImage sheet = new BufferedImage(768, 160, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = sheet.createGraphics();
        try {
            graphics.setColor(new Color(24, 31, 23));
            graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            for (int index = 0; index < views.size(); index++) {
                graphics.drawImage(views.get(index), index * 128, 16, null);
            }
        } finally {
            graphics.dispose();
        }
        Files.createDirectories(CONTACT_SHEET.getParent());
        ImageIO.write(sheet, "png", CONTACT_SHEET.toFile());
    }

    private static boolean hasTransparentPixel(final BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) == 0) return true;
            }
        }
        return false;
    }

    private static String hash(final Path path) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
        );
    }
}
