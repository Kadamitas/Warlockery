package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.geometrySnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.imageSnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.matrixSnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.softwareSnapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ImpLifeRules.Action;
import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

final class ImpModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/ImpModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/imp.png"
    );

    @Test
    void bakesAnIndependentInfernalAerialTricksterHierarchy() throws Exception {
        assertEquals(128, ImpModel.TEXTURE_WIDTH);
        assertEquals(128, ImpModel.TEXTURE_HEIGHT);
        final ModelPart root = ImpModel.createBodyLayer().bakeRoot();
        final ModelPart head = requiredChild(root, "head");
        for (final String feature : java.util.List.of(
            "muzzle", "left_horn", "right_horn", "left_ear", "right_ear"
        )) {
            assertFalse(requiredChild(head, feature).isEmpty(), feature);
        }
        assertFalse(requiredChild(root, "torso").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_wing"), "left_wing_tip").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_wing"), "right_wing_tip").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_arm"), "left_hand").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_arm"), "right_hand").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_leg"), "left_foot").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_leg"), "right_foot").isEmpty());
        final ModelPart tailBase = requiredChild(root, "tail_base");
        assertFalse(requiredChild(requiredChild(tailBase, "tail_mid"), "tail_tip").isEmpty());
        CreatureModelTestSupport.assertUvsWithin(root, ImpModel.TEXTURE_WIDTH, ImpModel.TEXTURE_HEIGHT);
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        CreatureModelTestSupport.assertOpaqueUvs(root, texture,
            cube -> !cube.path().endsWith("toe_middle"));
        assertEquals(new Color(255, 170, 75).getRGB(), texture.getRGB(16, 38),
            "bargaining hand carries an authored old-gold glint");
        assertEquals(0, texture.getRGB(127, 127) >>> 24, "unused atlas corner stays transparent");
    }

    @Test
    void transformedBoundsMeetTheGroundAndSilhouettesStayApproved() {
        final ModelPart root = ImpModel.createBodyLayer().bakeRoot();
        assertEquals("0b3ef2efd8f81f6e29b4f42ef87517c1d733933b9117af9f1c5eb6e3bf8f7aac",
            geometrySnapshot(root));
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        assertEquals(24.0F, bounds.maxY(), 0.001F);
        assertEquals(new CreatureModelTestSupport.Bounds(
            -16.486708F, -4.9645925F, -7.0777493F,
            16.486708F, 23.999231F, 15.932754F
        ), bounds);
        assertEquals("80cb393dfae6eb8b989ac78d5346307be844390be03ccc979450a040b00aca6a",
            imageSnapshot(softwareSnapshot(
            root, CreatureModelTestSupport.Projection.FRONT, 192, 6
        )));
        assertEquals("dfd9787331b4ef8a4dc78ec69602311b6ddff7ca90986e759d57d49acb1b1940",
            imageSnapshot(softwareSnapshot(
            root, CreatureModelTestSupport.Projection.SIDE, 192, 6
        )));
        final ModelPart threeQuarter = ImpModel.createBodyLayer().bakeRoot();
        threeQuarter.yRot = 0.7853982F;
        assertEquals("8ab38a2f01deb5368d75b2e127d828f418e1d954adc4ce6a7ffc275608d3e4a6",
            imageSnapshot(softwareSnapshot(
            threeQuarter, CreatureModelTestSupport.Projection.FRONT, 192, 6
        )));
    }

    @Test
    void neutralMovingAndEmberTrickPosesStayDistinctAndApproved() {
        final ImpModel neutral = new ImpModel(ImpModel.createBodyLayer().bakeRoot());
        neutral.setupAnim(new ImpModel.State());
        assertEquals("33544b9dc4f43222fd686ad5f3b611f030c800cb65f307f6e9369376a7a6bfd1",
            geometrySnapshot(neutral.root()));

        final ImpModel moving = new ImpModel(ImpModel.createBodyLayer().bakeRoot());
        final ImpModel.State movingState = motionState();
        moving.setupAnim(movingState);
        assertEquals("e14c6cc161f0157ab05d0d450872a2acdd25fc998ef245695ff375d63d9b0e65",
            geometrySnapshot(moving.root()));

        final ImpModel special = new ImpModel(ImpModel.createBodyLayer().bakeRoot());
        final ImpModel.State specialState = motionState();
        specialState.action = Action.RANGED_WINDUP;
        special.setupAnim(specialState);
        assertEquals("fb7fa6283d8be67b7300c06a3e6db473ae0d0ef8a784769d5d27648a31d0c92f",
            geometrySnapshot(special.root()));

        final PoseStack leftHand = new PoseStack();
        special.translateToHand(specialState, HumanoidArm.LEFT, leftHand);
        assertEquals("0da158feb73469ecc4cd0b8c9bcc2ff30c25bce5fe06ff971cbbad656e4922b3",
            matrixSnapshot(leftHand));
        final PoseStack rightHand = new PoseStack();
        special.translateToHand(specialState, HumanoidArm.RIGHT, rightHand);
        assertEquals("d9d6b31ac52ab6ced3a6a42ecd1303b7ca56d0c5d9a644d43df4d8979fe75dc1",
            matrixSnapshot(rightHand));
    }

    @Test
    void emberWindupReadsAsAnAsymmetricBargainingFlourish() {
        final ImpModel special = new ImpModel(ImpModel.createBodyLayer().bakeRoot());
        final ImpModel.State specialState = motionState();
        specialState.action = Action.RANGED_WINDUP;
        special.setupAnim(specialState);

        final ModelPart root = special.root();
        final ModelPart leftArm = requiredChild(root, "left_arm");
        final ModelPart rightArm = requiredChild(root, "right_arm");
        final ModelPart leftHand = requiredChild(leftArm, "left_hand");
        final ModelPart rightHand = requiredChild(rightArm, "right_hand");
        final ModelPart leftWing = requiredChild(root, "left_wing");
        final ModelPart rightWing = requiredChild(root, "right_wing");
        assertTrue(leftArm.xRot < rightArm.xRot - 0.4F,
            "ember palm must lift above the offered-glint palm");
        assertTrue(leftHand.xRot > rightHand.xRot + 0.2F,
            "the two presenting hands need independent gestures");
        assertTrue(Math.abs(Math.abs(leftWing.zRot) - Math.abs(rightWing.zRot)) > 0.2F,
            "cocked bargaining wings must not mirror the Storm Simian flight beat");
    }

    @Test
    void sourceOwnsItsRigAndAtlasIsPinned() throws Exception {
        final String source = Files.readString(SOURCE);
        for (final String forbidden : java.util.List.of(
            "ArcaneCreatureModel", "CreatureModelProfile", "WarlockeryModel",
            "AnimationHelper", "GeometryHelper", "ModelHelper", "StormSimianModel"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        assertFalse(source.contains("import com.kadamitas.warlockery.client"));
        assertFalse(source.contains("static void add"));
        assertEquals(true, source.contains("extractRenderState"));
        assertEquals(true, source.contains("entity.presentationAction()"));
        assertFalse(source.contains("entity.lifeState()"));
        assertEquals(128, ImageIO.read(TEXTURE.toFile()).getWidth());
        assertEquals(128, ImageIO.read(TEXTURE.toFile()).getHeight());
        assertEquals("7974a5a221e331f5635ae223cc281a6f418ae3d7de7537660bc30350512c4a04",
            textureHash());
    }

    @Test
    void writesApprovedSoftwareContactSheet() throws Exception {
        final ModelPart neutral = ImpModel.createBodyLayer().bakeRoot();
        final ModelPart threeQuarter = ImpModel.createBodyLayer().bakeRoot();
        threeQuarter.yRot = 0.7853982F;
        final ImpModel moving = new ImpModel(ImpModel.createBodyLayer().bakeRoot());
        moving.setupAnim(motionState());
        final ImpModel special = new ImpModel(ImpModel.createBodyLayer().bakeRoot());
        final ImpModel.State specialState = motionState();
        specialState.action = Action.RANGED_WINDUP;
        special.setupAnim(specialState);
        writeContactSheet(List.of(
            softwareSnapshot(neutral, CreatureModelTestSupport.Projection.FRONT, 192, 10),
            softwareSnapshot(neutral, CreatureModelTestSupport.Projection.SIDE, 192, 10),
            softwareSnapshot(threeQuarter, CreatureModelTestSupport.Projection.FRONT, 192, 10),
            softwareSnapshot(moving.root(), CreatureModelTestSupport.Projection.FRONT, 192, 10),
            softwareSnapshot(special.root(), CreatureModelTestSupport.Projection.FRONT, 192, 10),
            softwareSnapshot(special.root(), CreatureModelTestSupport.Projection.SIDE, 192, 10)
        ), Path.of("build/reports/visual-audit/creatures/imp-software-contact-sheet.png"));
    }

    private static ImpModel.State motionState() {
        final ImpModel.State state = new ImpModel.State();
        state.yRot = 31.0F;
        state.xRot = -12.0F;
        state.walkAnimationPos = 2.4F;
        state.walkAnimationSpeed = 0.78F;
        state.ageInTicks = 38.25F;
        state.airborne = true;
        return state;
    }

    private static String textureHash() throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(TEXTURE)));
    }

    private static void writeContactSheet(final List<BufferedImage> views, final Path output)
        throws Exception {
        final BufferedImage sheet = new BufferedImage(576, 384, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = sheet.createGraphics();
        try {
            graphics.setColor(new Color(31, 20, 25));
            graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            for (int index = 0; index < views.size(); index++) {
                graphics.drawImage(views.get(index), index % 3 * 192, index / 3 * 192, null);
            }
        } finally {
            graphics.dispose();
        }
        Files.createDirectories(output.getParent());
        ImageIO.write(sheet, "PNG", output.toFile());
    }
}
