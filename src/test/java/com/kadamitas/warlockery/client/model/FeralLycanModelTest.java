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
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class FeralLycanModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/FeralLycanModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/feral_lycan.png"
    );

    @Test
    void bakesRangyUntamedLycanHierarchyAgainstItsOwnAtlas() throws Exception {
        assertEquals(192, FeralLycanModel.TEXTURE_WIDTH);
        assertEquals(160, FeralLycanModel.TEXTURE_HEIGHT);
        final ModelPart root = FeralLycanModel.createBodyLayer().bakeRoot();
        final ModelPart ribcage = requiredChild(root, "ribcage");
        assertFalse(requiredChild(ribcage, "abdomen").isEmpty());
        final ModelPart skull = requiredChild(requiredChild(ribcage, "throat"), "skull");
        for (final String feature : List.of("long_muzzle", "torn_left_ear", "torn_right_ear")) {
            assertFalse(requiredChild(skull, feature).isEmpty(), feature);
        }
        final ModelPart ruff = requiredChild(root, "wild_ruff");
        for (final String tuft : List.of("left_ruff", "right_ruff")) {
            assertFalse(requiredChild(ruff, tuft).isEmpty(), tuft);
        }
        assertThreeStage(root, "left_reach", "left_carpal", "left_rake");
        assertThreeStage(root, "right_reach", "right_carpal", "right_rake");
        assertThreeStage(root, "left_haunch", "left_hock", "left_paw");
        assertThreeStage(root, "right_haunch", "right_hock", "right_paw");
        assertAll("feral anatomy stays hunched, long-reaching, and digitigrade",
            () -> assertTrue(ribcage.xRot >= 0.25F, "ribcage must pitch into a feral hunch"),
            () -> assertTrue(Math.abs(requiredChild(requiredChild(root, "left_haunch"), "left_hock").xRot) >= 0.4F,
                "left hock must break the upright humanoid leg line"),
            () -> assertTrue(Math.abs(requiredChild(requiredChild(root, "right_haunch"), "right_hock").xRot) >= 0.4F,
                "right hock must break the upright humanoid leg line")
        );
        final ModelPart tail = requiredChild(root, "crooked_tail");
        assertFalse(requiredChild(requiredChild(tail, "crooked_mid"), "crooked_tip").isEmpty());
        assertTrue(CreatureModelTestSupport.solidPartCount(root) >= 25);
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        CreatureModelTestSupport.assertUvsWithin(root, 192, 160);
        CreatureModelTestSupport.assertOpaqueUvs(root, texture,
            cube -> !cube.path().endsWith("toe_middle"));
        assertEquals(0, texture.getRGB(191, 159) >>> 24);
        assertEquals("cbe889f7aa66724a13b571944a45c0991bc8dc2ab8286d25aa4bb6681add88f2",
            textureHash());
    }

    @Test
    void transformedBoundsMeetGroundAndNarrowSilhouettesStayApproved() {
        final ModelPart root = FeralLycanModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        assertEquals(24.0F, bounds.maxY(), 0.001F);
        assertTrue(bounds.maxY() - bounds.minY() >= 28.0F);
        assertTrue(bounds.maxX() - bounds.minX() < 30.0F);
        final String front = imageSnapshot(softwareSnapshot(
            root, CreatureModelTestSupport.Projection.FRONT, 192, 8
        ));
        final String side = imageSnapshot(softwareSnapshot(
            root, CreatureModelTestSupport.Projection.SIDE, 192, 8
        ));
        final ModelPart threeQuarter = FeralLycanModel.createBodyLayer().bakeRoot();
        threeQuarter.yRot = 0.7853982F;
        final String oblique = imageSnapshot(softwareSnapshot(
            threeQuarter, CreatureModelTestSupport.Projection.FRONT, 192, 8
        ));
        assertNotEquals(front, side);
        assertNotEquals(front, oblique);
        assertEquals("40f76ad439f44f560043d3063d0a1adf8b2d90cec50e1809184847136f8fc1a7\n"
            + "Bounds[minX=-12.512741, minY=-6.6001654, minZ=-10.949134, maxX=12.512741, maxY=24.000002, maxZ=9.302788]\n"
            + "819e19d8df98894a41490ae008da3f607ca9effe2ee9567250a78be333270277\n"
            + "d582e83962cf03f0625b997440c874eee6127fef9aa2639268f5c38e185f9077\n"
            + "e8f1a67b5d80098532ce3962161b71b799f8cdfac7833a58f863ac4185ca4255", String.join("\n",
            geometrySnapshot(root), bounds.toString(), front, side, oblique));
    }

    @Test
    void neutralScuttleAndLowBoundingPounceAreDistinctAndApproved() {
        final FeralLycanModel neutral = new FeralLycanModel(FeralLycanModel.createBodyLayer().bakeRoot());
        neutral.setupAnim(new FeralLycanModel.State());
        final FeralLycanModel moving = new FeralLycanModel(FeralLycanModel.createBodyLayer().bakeRoot());
        moving.setupAnim(motionState());
        final FeralLycanModel bounding = new FeralLycanModel(FeralLycanModel.createBodyLayer().bakeRoot());
        final FeralLycanModel.State action = motionState();
        action.bounding = true;
        action.panicked = true;
        bounding.setupAnim(action);
        final String neutralHash = geometrySnapshot(neutral.root());
        final String movingHash = geometrySnapshot(moving.root());
        final String actionHash = geometrySnapshot(bounding.root());
        assertNotEquals(neutralHash, movingHash);
        assertNotEquals(movingHash, actionHash);
        assertEquals("3c45ee11f65a066f56abe2b7a7f4cb1b055e5579a00f110da05ee9b82db1711e\n"
            + "7b3fb61ea9bf425cb09f4fd6b7f3f9a2c423eafe158baba3d5136aca7a57abf5\n"
            + "97432bfc82139841822928e987e175f28d2aa8c4c7d0561c28655cb34c2d2ecb",
            String.join("\n", neutralHash, movingHash, actionHash));
    }

    @Test
    void sourceOwnsRigStateExtractionAndConcreteFeralBinding() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<FeralLycanModel.State>"));
        assertTrue(source.contains("extractRenderState("));
        assertTrue(source.contains("final FeralLycanEntity entity"));
        assertTrue(source.contains("entity.presentationHunger()"));
        assertTrue(source.contains("entity.presentationFear()"));
        assertTrue(source.contains("entity.presentationAction()"));
        assertFalse(source.contains("entity.packState()"));
        for (final String forbidden : List.of(
            "ArcaneCreatureModel", "CreatureModelProfile", "WarlockeryModel", "AnimationHelper",
            "GeometryHelper", "ModelHelper", "WerewolfModel", "HellhoundModel",
            "PaleSteedModel", "NightmareModel", " implements "
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        assertFalse(source.contains("static void add"));
    }

    @Test
    void writesSoftwareContactSheet() throws Exception {
        final ModelPart neutral = FeralLycanModel.createBodyLayer().bakeRoot();
        final ModelPart oblique = FeralLycanModel.createBodyLayer().bakeRoot();
        oblique.yRot = 0.7853982F;
        final FeralLycanModel moving = new FeralLycanModel(FeralLycanModel.createBodyLayer().bakeRoot());
        moving.setupAnim(motionState());
        final FeralLycanModel action = new FeralLycanModel(FeralLycanModel.createBodyLayer().bakeRoot());
        final FeralLycanModel.State actionState = motionState();
        actionState.bounding = true;
        actionState.panicked = true;
        action.setupAnim(actionState);
        writeContactSheet(List.of(
            softwareSnapshot(neutral, CreatureModelTestSupport.Projection.FRONT, 192, 10),
            softwareSnapshot(neutral, CreatureModelTestSupport.Projection.SIDE, 192, 10),
            softwareSnapshot(oblique, CreatureModelTestSupport.Projection.FRONT, 192, 10),
            softwareSnapshot(moving.root(), CreatureModelTestSupport.Projection.FRONT, 192, 10),
            softwareSnapshot(action.root(), CreatureModelTestSupport.Projection.FRONT, 192, 10),
            softwareSnapshot(action.root(), CreatureModelTestSupport.Projection.SIDE, 192, 10)
        ));
    }

    private static FeralLycanModel.State motionState() {
        final FeralLycanModel.State state = new FeralLycanModel.State();
        state.yRot = 31.0F;
        state.xRot = -5.0F;
        state.walkAnimationPos = 2.7F;
        state.walkAnimationSpeed = 0.94F;
        state.ageInTicks = 61.0F;
        state.hunger = 840;
        state.fear = 710;
        return state;
    }

    private static void assertThreeStage(
        final ModelPart root,
        final String proximal,
        final String middle,
        final String distal
    ) {
        final ModelPart first = requiredChild(root, proximal);
        final ModelPart second = requiredChild(first, middle);
        assertFalse(first.isEmpty());
        assertFalse(second.isEmpty());
        assertFalse(requiredChild(second, distal).isEmpty());
    }

    private static String textureHash() throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(TEXTURE)));
    }

    private static void writeContactSheet(final List<BufferedImage> views) throws Exception {
        final Path output = Path.of(
            "build/reports/visual-audit/creatures/feral_lycan-software-contact-sheet.png"
        );
        final BufferedImage sheet = new BufferedImage(576, 384, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = sheet.createGraphics();
        try {
            graphics.setColor(new Color(54, 47, 43));
            graphics.fillRect(0, 0, 576, 384);
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
