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

final class IllusionZombieModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/IllusionZombieModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/illusion_zombie.png"
    );
    private static final Path CONTACT_SHEET = Path.of(
        "build/reports/visual-audit/creatures/illusion_zombie-software-contact-sheet.png"
    );

    @Test
    void bakesTheIndependentHollowDecoyMannequinAndPaintsEveryFace() throws Exception {
        assertEquals(64, IllusionZombieModel.TEXTURE_WIDTH);
        assertEquals(64, IllusionZombieModel.TEXTURE_HEIGHT);
        final IllusionZombieModel model = model(new IllusionZombieModel.State());
        final ModelPart root = model.root();
        final ModelPart head = requiredChild(root, "stepped_mask_head");
        assertFalse(requiredChild(head, "left_face_plate").isEmpty());
        assertFalse(requiredChild(head, "right_face_plate").isEmpty());
        assertFalse(requiredChild(head, "crown_fragment").isEmpty());
        final ModelPart shell = requiredChild(root, "torso_shell");
        assertFalse(requiredChild(shell, "left_torso_slab").isEmpty());
        assertFalse(requiredChild(shell, "right_torso_slab").isEmpty());
        assertFalse(requiredChild(shell, "left_broken_hem").isEmpty());
        assertFalse(requiredChild(shell, "right_broken_hem").isEmpty());
        assertThreeStageArm(root, "left_shoulder", "left_upper_arm", "left_forearm");
        assertThreeStageArm(root, "right_shoulder", "right_upper_arm", "right_forearm");
        assertFalse(requiredChild(requiredChild(root, "left_leg"), "left_foot").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_leg"), "right_foot").isEmpty());
        assertTrue(CreatureModelTestSupport.solidPartCount(root) >= 19);
        CreatureModelTestSupport.assertUvsWithin(
            root, IllusionZombieModel.TEXTURE_WIDTH, IllusionZombieModel.TEXTURE_HEIGHT
        );
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        CreatureModelTestSupport.assertOpaqueUvs(root, texture, cube -> true);
        assertEquals(0, texture.getRGB(63, 63) >>> 24, "unused atlas remains transparent");
    }

    @Test
    void pinsGroundContactGeometryAndFourTurnaroundSilhouettes() {
        final IllusionZombieModel model = model(new IllusionZombieModel.State());
        final ModelPart root = model.root();
        assertAll(
            () -> assertEquals("af9fd2500ebdba31bc44b4694c3a9cffd9cae89fe6aff9c93b7d5af9b80d6c08", geometrySnapshot(root)),
            () -> assertEquals(new CreatureModelTestSupport.Bounds(
                -8.14965F, -2.4008877F, -6.812672F, 8.14965F, 24.0F, 2.9948902F
            ),
                CreatureModelTestSupport.bounds(root)),
            () -> assertEquals(24.0F, CreatureModelTestSupport.bounds(root).maxY(), 0.001F),
            () -> assertEquals("fdf99e0a278f77959bcff599c0d9fe65165bb738b09722404ee1c40250452125", viewHash(root, 0.0F)),
            () -> assertEquals("8c7caaf042d4e96ef999ae6463541f7b802b24a2833a7b18053bbb065c7a82c4", viewHash(root, -1.5707964F)),
            () -> assertEquals("c4f63faa7697ff15a3db6f69653f433befdf77eacec7271a95728ca813c22061", viewHash(root, 3.1415927F)),
            () -> assertEquals("9a0277b32a34cf766ed6d6212687fdac72feff1577fdb9c9e0d3eea62fb46788", viewHash(root, -0.7853982F))
        );
    }

    @Test
    void neutralStaggerAndHollowUnmaskAreDistinctPinnedPoses() {
        final IllusionZombieModel neutral = model(new IllusionZombieModel.State());
        final IllusionZombieModel moving = model(motionState());
        final IllusionZombieModel.State unmaskState = new IllusionZombieModel.State();
        unmaskState.ageInTicks = 34.0F;
        unmaskState.phase = Phase.UNMASK;
        unmaskState.acceptedHits = 2;
        final IllusionZombieModel unmask = model(unmaskState);
        assertAll(
            () -> assertEquals("af9fd2500ebdba31bc44b4694c3a9cffd9cae89fe6aff9c93b7d5af9b80d6c08", geometrySnapshot(neutral.root())),
            () -> assertEquals("ab7c8a6b61a1afca4bd6ea87a51ac125354f6a26d32b788e53b0249f527b5042", geometrySnapshot(moving.root())),
            () -> assertEquals("1f3e1c7876faa01eb8ec5df04d1c195b29df5e9417b0f657d02fa9b496d2e8ef", geometrySnapshot(unmask.root())),
            () -> assertNotEquals(geometrySnapshot(neutral.root()), geometrySnapshot(moving.root())),
            () -> assertNotEquals(geometrySnapshot(moving.root()), geometrySnapshot(unmask.root()))
        );
        final ModelPart neutralShell = requiredChild(neutral.root(), "torso_shell");
        final ModelPart unmaskShell = requiredChild(unmask.root(), "torso_shell");
        assertTrue(requiredChild(unmaskShell, "left_torso_slab").x
            < requiredChild(neutralShell, "left_torso_slab").x - 1.0F);
        assertTrue(requiredChild(unmaskShell, "right_torso_slab").x
            > requiredChild(neutralShell, "right_torso_slab").x + 1.0F);
        assertEquals(requiredChild(neutral.root(), "left_shoulder").xRot,
            requiredChild(unmask.root(), "left_shoulder").xRot, 0.0001F,
            "unmask keeps the non-retaliating arm inert");
        assertEquals(requiredChild(neutral.root(), "right_shoulder").xRot,
            requiredChild(unmask.root(), "right_shoulder").xRot, 0.0001F,
            "unmask keeps the non-retaliating arm inert");
    }

    @Test
    void unmaskExposesARealHollowGapBetweenTheTwoTorsoSlabs() {
        final IllusionZombieModel.State neutralState = new IllusionZombieModel.State();
        final IllusionZombieModel neutral = model(neutralState);
        assertTrue(requiredChild(neutral.root(), "torso_shell").isEmpty(),
            "the torso parent must be a pivot, not a solid block behind the hollow seam");

        final IllusionZombieModel.State unmaskState = new IllusionZombieModel.State();
        unmaskState.phase = Phase.UNMASK;
        unmaskState.acceptedHits = 2;
        final ModelPart shell = requiredChild(model(unmaskState).root(), "torso_shell");
        final ModelPart left = requiredChild(shell, "left_torso_slab");
        final ModelPart right = requiredChild(shell, "right_torso_slab");
        assertTrue(right.x - left.x > 4.0F, "unmask must open a visible central void");
    }

    @Test
    void sourceOwnsItsEntityStateRigAnimationAndAtlas() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<IllusionZombieModel.State>"));
        assertTrue(source.contains("new MeshDefinition()"));
        assertTrue(source.contains("CubeListBuilder"));
        assertTrue(source.contains("PartPose"));
        assertTrue(source.contains("public static void extractRenderState("));
        assertTrue(source.contains("final IllusionZombieEntity entity"));
        assertTrue(source.contains("entity.presentationPhase()"));
        assertTrue(source.contains("entity.presentationAcceptedHits()"));
        assertFalse(source.contains("entity.mimicCore()"));
        for (final String forbidden : List.of(
            "ArcaneCreatureModel", "CreatureModelProfile", "WarlockeryModel", "GeometryHelper",
            "AnimationHelper", "ModelHelper", "RigCatalog", "IllusionCreeperModel",
            "IllusionSpiderModel", "extends ZombieModel", "net.minecraft.client.model.ZombieModel",
            "HumanoidModel"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        assertEquals(64, texture.getWidth());
        assertEquals(64, texture.getHeight());
        assertTrue(hasTransparentPixel(texture));
        assertEquals("d77fbfdb9fd14d2b128abaabadb3e71b545a4308e7b34c0255ca8ac98c54607f", hash(TEXTURE));
    }

    @Test
    void writesFrontSideRearThreeQuarterStaggerAndUnmaskSheet() throws Exception {
        final IllusionZombieModel.State idle = new IllusionZombieModel.State();
        final IllusionZombieModel.State unmask = new IllusionZombieModel.State();
        unmask.ageInTicks = 34.0F;
        unmask.phase = Phase.UNMASK;
        unmask.acceptedHits = 2;
        writeContactSheet(List.of(
            view(idle, 0.0F),
            view(idle, -1.5707964F),
            view(idle, 3.1415927F),
            view(idle, -0.7853982F),
            view(motionState(), 0.0F),
            view(unmask, 0.0F)
        ));
        final BufferedImage sheet = ImageIO.read(CONTACT_SHEET.toFile());
        assertEquals(768, sheet.getWidth());
        assertEquals(160, sheet.getHeight());
    }

    private static void assertThreeStageArm(
        final ModelPart root,
        final String shoulderName,
        final String upperName,
        final String forearmName
    ) {
        final ModelPart shoulder = requiredChild(root, shoulderName);
        final ModelPart upper = requiredChild(shoulder, upperName);
        assertFalse(shoulder.isEmpty(), shoulderName);
        assertFalse(upper.isEmpty(), upperName);
        assertFalse(requiredChild(upper, forearmName).isEmpty(), forearmName);
    }

    private static IllusionZombieModel model(final IllusionZombieModel.State state) {
        final IllusionZombieModel model = new IllusionZombieModel(
            IllusionZombieModel.createBodyLayer().bakeRoot()
        );
        model.setupAnim(state);
        return model;
    }

    private static IllusionZombieModel.State motionState() {
        final IllusionZombieModel.State state = new IllusionZombieModel.State();
        state.yRot = 11.0F;
        state.xRot = -5.0F;
        state.walkAnimationPos = 2.6F;
        state.walkAnimationSpeed = 0.74F;
        state.ageInTicks = 52.0F;
        state.phase = Phase.STATION;
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

    private static BufferedImage view(final IllusionZombieModel.State state, final float rotation) {
        final ModelPart root = model(state).root();
        root.yRot = rotation;
        return softwareSnapshot(root, CreatureModelTestSupport.Projection.FRONT, 128, 7);
    }

    private static void writeContactSheet(final List<BufferedImage> views) throws Exception {
        final BufferedImage sheet = new BufferedImage(768, 160, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = sheet.createGraphics();
        try {
            graphics.setColor(new Color(27, 32, 31));
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
