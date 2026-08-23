package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.geometrySnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.imageSnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.softwareSnapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.SpectralSteedRules.Gait;
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

final class NightmareModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/NightmareModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/nightmare.png"
    );

    @Test
    void bakesScorchedDreamDarkMountHierarchyAgainstItsOwnAtlas() throws Exception {
        assertEquals(256, NightmareModel.TEXTURE_WIDTH);
        assertEquals(224, NightmareModel.TEXTURE_HEIGHT);
        final ModelPart root = NightmareModel.createBodyLayer().bakeRoot();
        assertFalse(requiredChild(root, "barrel").isEmpty());
        assertFalse(requiredChild(root, "shoulder_yoke").isEmpty());
        assertFalse(requiredChild(root, "croup_block").isEmpty());
        final ModelPart skull = requiredChild(requiredChild(root, "arched_neck"), "war_skull");
        for (final String feature : List.of(
            "lower_jaw", "left_obsidian_horn", "right_obsidian_horn"
        )) {
            assertFalse(requiredChild(skull, feature).isEmpty(), feature);
        }
        final ModelPart crest = requiredChild(root, "broken_crest");
        for (final String shard : List.of("crest_shard_one", "crest_shard_two", "crest_shard_three")) {
            assertFalse(requiredChild(crest, shard).isEmpty(), shard);
        }
        assertThreeStage(root, "left_front_pillar", "left_front_fetlock", "left_front_cloven");
        assertThreeStage(root, "right_front_pillar", "right_front_fetlock", "right_front_cloven");
        assertThreeStage(root, "left_rear_drive", "left_rear_fetlock", "left_rear_cloven");
        assertThreeStage(root, "right_rear_drive", "right_rear_fetlock", "right_rear_cloven");
        final ModelPart tail = requiredChild(root, "chain_tail_anchor");
        final ModelPart second = requiredChild(requiredChild(tail, "chain_tail_link"), "chain_tail_end");
        for (final String barb : List.of("left_ember_barb", "center_ember_barb", "right_ember_barb")) {
            assertFalse(requiredChild(second, barb).isEmpty(), barb);
        }
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        CreatureModelTestSupport.assertUvsWithin(root, 256, 224);
        CreatureModelTestSupport.assertOpaqueUvs(root, texture,
            cube -> !cube.path().contains("_front_pillar")
                && !cube.path().contains("ember_barb"));
        assertEquals(0, texture.getRGB(255, 223) >>> 24);
        assertEquals("66873f8ab52ab77afe17006d18d6ecd9022c66559ee87621e785a9e1603e8302",
            textureHash());
    }

    @Test
    void conceptTopologyCarriesTallBrokenCrestBranchingTailAndAftershadowForks() {
        final ModelPart root = NightmareModel.createBodyLayer().bakeRoot();
        final ModelPart skull = requiredChild(requiredChild(root, "arched_neck"), "war_skull");
        for (final String side : List.of("left", "right")) {
            final ModelPart horn = requiredChild(skull, side + "_obsidian_horn");
            assertFalse(requiredChild(horn, side + "_obsidian_horn_tip").isEmpty());
        }
        final ModelPart crest = requiredChild(root, "broken_crest");
        for (final String number : List.of("one", "two", "three", "four", "five")) {
            final ModelPart shard = requiredChild(crest, "crest_shard_" + number);
            assertFalse(requiredChild(shard, "crest_tip_" + number).isEmpty(),
                "crest_tip_" + number);
        }
        final ModelPart tailEnd = requiredChild(
            requiredChild(requiredChild(root, "chain_tail_anchor"), "chain_tail_link"),
            "chain_tail_end"
        );
        for (final String branch : List.of("left", "center", "right")) {
            final ModelPart barb = requiredChild(tailEnd, branch + "_ember_barb");
            assertFalse(requiredChild(barb, branch + "_ember_branch_tip").isEmpty());
        }
        for (final String side : List.of("left", "right")) {
            final ModelPart aftershadow = requiredChild(root, side + "_after_shadow");
            assertFalse(requiredChild(aftershadow, side + "_after_shadow_tip").isEmpty());
            final ModelPart rear = requiredChild(root, side + "_rear_drive");
            final ModelPart fetlock = requiredChild(rear, side + "_rear_fetlock");
            assertFalse(requiredChild(fetlock, side + "_rear_hook").isEmpty());
        }
    }

    @Test
    void neutralSilhouetteTracksTheTallBranchingNightmareConceptEnvelope() {
        final ModelPart root = NightmareModel.createBodyLayer().bakeRoot();
        final double frontAspect = silhouetteAspect(
            root, CreatureModelTestSupport.Projection.FRONT
        );
        final double sideAspect = silhouetteAspect(
            root, CreatureModelTestSupport.Projection.SIDE
        );
        assertTrue(frontAspect >= 0.32 && frontAspect <= 0.40,
            "concept front aspect 0.357 requires 0.32..0.40, got " + frontAspect);
        assertTrue(sideAspect >= 0.84 && sideAspect <= 1.05,
            "concept left aspect 0.920 requires 0.84..1.05, got " + sideAspect);
    }

    @Test
    void transformedBoundsMeetGroundAndCrouchedSilhouettesStayApproved() {
        final ModelPart root = NightmareModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        assertEquals(24.0F, bounds.maxY(), 0.001F);
        assertTrue(bounds.maxZ() - bounds.minZ() >= 30.0F);
        assertTrue(bounds.maxY() - bounds.minY() < 40.0F, bounds.toString());
        final String front = imageSnapshot(softwareSnapshot(
            root, CreatureModelTestSupport.Projection.FRONT, 192, 8
        ));
        final String side = imageSnapshot(softwareSnapshot(
            root, CreatureModelTestSupport.Projection.SIDE, 192, 8
        ));
        final ModelPart threeQuarter = NightmareModel.createBodyLayer().bakeRoot();
        threeQuarter.yRot = 0.7853982F;
        final String oblique = imageSnapshot(softwareSnapshot(
            threeQuarter, CreatureModelTestSupport.Projection.FRONT, 192, 8
        ));
        assertNotEquals(front, side);
        assertNotEquals(front, oblique);
        assertEquals("4d026a41364322e7b97aafe88aa15916b6a2b4e66f34d55480819491763c9f1d\n"
            + "Bounds[minX=-6.1723466, minY=-13.310557, minZ=-17.5, maxX=6.1723466, maxY=24.0, maxZ=20.611937]\n"
            + "715e794d5f8244e48f4e7100980bf8ff3ecc750c9ef89acb4e9e5edc0de98e76\n"
            + "9bbf990365aa6dd3a8e23c0f6a6d9f2181b5aebc77d7ed658c544bb625433e43\n"
            + "b268302c8bd82c930cf70897ce0cbce11e2121cbc5c54e0614257ee93d37f243", String.join("\n",
            geometrySnapshot(root), bounds.toString(), front, side, oblique));
    }

    @Test
    void neutralGallopAndDreamChargeAreDistinctAndApproved() {
        final NightmareModel neutral = new NightmareModel(NightmareModel.createBodyLayer().bakeRoot());
        neutral.setupAnim(new NightmareModel.State());
        final NightmareModel galloping = new NightmareModel(NightmareModel.createBodyLayer().bakeRoot());
        galloping.setupAnim(motionState());
        final NightmareModel charging = new NightmareModel(NightmareModel.createBodyLayer().bakeRoot());
        final NightmareModel.State action = motionState();
        action.warning = true;
        action.gait = Gait.SPRINT;
        charging.setupAnim(action);
        final String neutralHash = geometrySnapshot(neutral.root());
        final String movingHash = geometrySnapshot(galloping.root());
        final String actionHash = geometrySnapshot(charging.root());
        assertNotEquals(neutralHash, movingHash);
        assertNotEquals(movingHash, actionHash);
        assertEquals("e02c3d8ce9b174e78b64e9d58d7e0cc94c7a50542ca167ff64cbedf0281a51e5\n"
            + "94293719e7dc44519857d28bbc52a4fc26bfec49934f62939bd7395e4dda2c80\n"
            + "e54821fe0d21337321c683bd84f5b22bb19766b9ebdb862ed59873eab5d3d509",
            String.join("\n", neutralHash, movingHash, actionHash));
    }

    @Test
    void sourceOwnsRigStateExtractionAndConcreteSteedBinding() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<NightmareModel.State>"));
        assertTrue(source.contains("extractRenderState("));
        assertTrue(source.contains("final SpectralSteedEntity entity"));
        assertTrue(source.contains("entity.presentationGait()"));
        assertTrue(source.contains("entity.presentationBond()"));
        assertTrue(source.contains("entity.presentationFatigue()"));
        assertTrue(source.contains("entity.presentationBalking()"));
        assertTrue(source.contains("entity.presentationResting()"));
        assertTrue(source.contains("entity.presentationWarning()"));
        assertFalse(source.contains("entity.steedState()"));
        assertFalse(source.contains("left_hand"));
        assertFalse(source.contains("right_hand"));
        for (final String forbidden : List.of(
            "ArcaneCreatureModel", "CreatureModelProfile", "WarlockeryModel", "AnimationHelper",
            "GeometryHelper", "ModelHelper", "WerewolfModel", "FeralLycanModel",
            "HellhoundModel", "PaleSteedModel", "horse", "Horse", " implements "
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        assertFalse(source.contains("static void add"));
    }

    @Test
    void writesSoftwareContactSheet() throws Exception {
        final ModelPart neutral = NightmareModel.createBodyLayer().bakeRoot();
        final ModelPart oblique = NightmareModel.createBodyLayer().bakeRoot();
        oblique.yRot = 0.7853982F;
        final NightmareModel moving = new NightmareModel(NightmareModel.createBodyLayer().bakeRoot());
        moving.setupAnim(motionState());
        final NightmareModel action = new NightmareModel(NightmareModel.createBodyLayer().bakeRoot());
        final NightmareModel.State actionState = motionState();
        actionState.warning = true;
        actionState.gait = Gait.SPRINT;
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

    private static NightmareModel.State motionState() {
        final NightmareModel.State state = new NightmareModel.State();
        state.yRot = 16.0F;
        state.xRot = -7.0F;
        state.walkAnimationPos = 4.1F;
        state.walkAnimationSpeed = 1.0F;
        state.ageInTicks = 39.0F;
        state.gait = Gait.CANTER;
        state.bond = 430;
        state.fatigue = 510;
        state.carrying = true;
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

    private static double silhouetteAspect(
        final ModelPart root,
        final CreatureModelTestSupport.Projection projection
    ) {
        final BufferedImage image = softwareSnapshot(root, projection, 256, 8);
        int minimumX = image.getWidth();
        int minimumY = image.getHeight();
        int maximumX = -1;
        int maximumY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    minimumX = Math.min(minimumX, x);
                    minimumY = Math.min(minimumY, y);
                    maximumX = Math.max(maximumX, x);
                    maximumY = Math.max(maximumY, y);
                }
            }
        }
        assertTrue(maximumX >= minimumX && maximumY >= minimumY, "empty silhouette");
        return (maximumX - minimumX + 1.0) / (maximumY - minimumY + 1.0);
    }

    private static void writeContactSheet(final List<BufferedImage> views) throws Exception {
        final Path output = Path.of(
            "build/reports/visual-audit/creatures/nightmare-software-contact-sheet.png"
        );
        final BufferedImage sheet = new BufferedImage(576, 384, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = sheet.createGraphics();
        try {
            graphics.setColor(new Color(28, 22, 36));
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
