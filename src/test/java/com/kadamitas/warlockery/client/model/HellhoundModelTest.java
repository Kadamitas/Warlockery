package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.geometrySnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.imageSnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.softwareSnapshot;
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

final class HellhoundModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/HellhoundModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/hellhound.png"
    );

    @Test
    void bakesLowInfernalQuadrupedCanidHierarchyAgainstItsOwnAtlas() throws Exception {
        assertEquals(256, HellhoundModel.TEXTURE_WIDTH);
        assertEquals(160, HellhoundModel.TEXTURE_HEIGHT);
        final ModelPart root = HellhoundModel.createBodyLayer().bakeRoot();
        assertFalse(requiredChild(root, "ember_chest").isEmpty());
        assertFalse(requiredChild(root, "long_spine").isEmpty());
        assertFalse(requiredChild(root, "cinder_hips").isEmpty());
        final ModelPart skull = requiredChild(requiredChild(root, "thick_neck"), "wedge_skull");
        for (final String feature : List.of(
            "long_muzzle", "lower_jaw", "left_ember_horn", "right_ember_horn"
        )) {
            assertFalse(requiredChild(skull, feature).isEmpty(), feature);
        }
        assertThreeStage(root, "left_foreleg", "left_fore_cannon", "left_fore_paw");
        assertThreeStage(root, "right_foreleg", "right_fore_cannon", "right_fore_paw");
        assertThreeStage(root, "left_hindleg", "left_hind_hock", "left_hind_paw");
        assertThreeStage(root, "right_hindleg", "right_hind_hock", "right_hind_paw");
        final ModelPart tail = requiredChild(root, "flame_tail_root");
        final ModelPart mid = requiredChild(tail, "flame_tail_mid");
        assertFalse(requiredChild(mid, "left_flame_fork").isEmpty());
        assertFalse(requiredChild(mid, "right_flame_fork").isEmpty());
        assertFalse(requiredChild(root, "dorsal_ridge").isEmpty());
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        CreatureModelTestSupport.assertUvsWithin(root, 256, 160);
        CreatureModelTestSupport.assertOpaqueUvs(root, texture,
            cube -> !cube.path().endsWith("long_muzzle"));
        assertEquals(0, texture.getRGB(255, 159) >>> 24);
        assertEquals("e8a646bd00b3fa446ff8004f402a9379fbd8f6ac28ac5220abaf9fe8e4e09f69",
            textureHash());
    }

    @Test
    void transformedBoundsMeetGroundAndQuadrupedSilhouettesStayApproved() {
        final ModelPart root = HellhoundModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        assertEquals(24.0F, bounds.maxY(), 0.001F);
        assertTrue(bounds.maxZ() - bounds.minZ() >= 28.0F);
        assertTrue(bounds.maxY() - bounds.minY() < 22.0F);
        final String front = imageSnapshot(softwareSnapshot(
            root, CreatureModelTestSupport.Projection.FRONT, 192, 8
        ));
        final String side = imageSnapshot(softwareSnapshot(
            root, CreatureModelTestSupport.Projection.SIDE, 192, 8
        ));
        final ModelPart threeQuarter = HellhoundModel.createBodyLayer().bakeRoot();
        threeQuarter.yRot = 0.7853982F;
        final String oblique = imageSnapshot(softwareSnapshot(
            threeQuarter, CreatureModelTestSupport.Projection.FRONT, 192, 8
        ));
        assertNotEquals(front, side);
        assertNotEquals(front, oblique);
        assertEquals("0846c47fbb298033759a8c60625e691d9e7cf51d84ec318f378f26c1d91f8a3d\n"
            + "Bounds[minX=-9.627122, minY=3.3609197, minZ=-18.20145, maxX=9.627122, maxY=24.0, maxZ=20.696093]\n"
            + "ba70c9388eb229c3eae1d7bf7ffd779cdfc17d9460d5fe4c8818438c655b61ee\n"
            + "b4030033edf900ef58d4d028cce635f1f283dde63d01fcc321946698cbea0aea\n"
            + "fdf8620a0cb75e4836797a76604ce1dc26031f0607259b9e5d130e1c676d3d5f", String.join("\n",
            geometrySnapshot(root), bounds.toString(), front, side, oblique));
    }

    @Test
    void neutralRunAndCommittedBiteAreDistinctAndApproved() {
        final HellhoundModel neutral = new HellhoundModel(HellhoundModel.createBodyLayer().bakeRoot());
        neutral.setupAnim(new HellhoundModel.State());
        final HellhoundModel running = new HellhoundModel(HellhoundModel.createBodyLayer().bakeRoot());
        running.setupAnim(motionState());
        final HellhoundModel biting = new HellhoundModel(HellhoundModel.createBodyLayer().bakeRoot());
        final HellhoundModel.State action = motionState();
        action.biting = true;
        action.warning = true;
        biting.setupAnim(action);
        final String neutralHash = geometrySnapshot(neutral.root());
        final String movingHash = geometrySnapshot(running.root());
        final String actionHash = geometrySnapshot(biting.root());
        assertNotEquals(neutralHash, movingHash);
        assertNotEquals(movingHash, actionHash);
        assertEquals("0846c47fbb298033759a8c60625e691d9e7cf51d84ec318f378f26c1d91f8a3d\n"
            + "fee45de010d5c14ad3c99ba6f50e0da6c495c322f560287dc6275e9897da29a3\n"
            + "9701f05f151f96ea584cd6a02c2bb7108815ebdd6b68bceab176a3571700691b",
            String.join("\n", neutralHash, movingHash, actionHash));
    }

    @Test
    void sourceOwnsRigStateExtractionAndConcreteHellhoundBinding() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<HellhoundModel.State>"));
        assertTrue(source.contains("extractRenderState("));
        assertTrue(source.contains("final HellhoundEntity entity"));
        assertTrue(source.contains("entity.presentationBound()"));
        assertTrue(source.contains("entity.presentationWarning()"));
        assertTrue(source.contains("entity.presentationBiting()"));
        assertTrue(source.contains("entity.presentationRetreating()"));
        assertFalse(source.contains("entity.lifeState()"));
        assertFalse(source.contains("left_hand"));
        assertFalse(source.contains("right_hand"));
        for (final String forbidden : List.of(
            "ArcaneCreatureModel", "CreatureModelProfile", "WarlockeryModel", "AnimationHelper",
            "GeometryHelper", "ModelHelper", "WerewolfModel", "FeralLycanModel",
            "PaleSteedModel", "NightmareModel", " implements "
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        assertFalse(source.contains("static void add"));
    }

    @Test
    void writesSoftwareContactSheet() throws Exception {
        final ModelPart neutral = HellhoundModel.createBodyLayer().bakeRoot();
        final ModelPart oblique = HellhoundModel.createBodyLayer().bakeRoot();
        oblique.yRot = 0.7853982F;
        final HellhoundModel moving = new HellhoundModel(HellhoundModel.createBodyLayer().bakeRoot());
        moving.setupAnim(motionState());
        final HellhoundModel action = new HellhoundModel(HellhoundModel.createBodyLayer().bakeRoot());
        final HellhoundModel.State actionState = motionState();
        actionState.biting = true;
        actionState.warning = true;
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

    private static HellhoundModel.State motionState() {
        final HellhoundModel.State state = new HellhoundModel.State();
        state.yRot = -18.0F;
        state.xRot = 6.0F;
        state.walkAnimationPos = 3.9F;
        state.walkAnimationSpeed = 0.96F;
        state.ageInTicks = 35.0F;
        state.bound = true;
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
            "build/reports/visual-audit/creatures/hellhound-software-contact-sheet.png"
        );
        final BufferedImage sheet = new BufferedImage(576, 384, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = sheet.createGraphics();
        try {
            graphics.setColor(new Color(35, 22, 21));
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
