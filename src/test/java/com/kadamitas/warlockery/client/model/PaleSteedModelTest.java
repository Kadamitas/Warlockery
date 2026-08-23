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
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class PaleSteedModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/PaleSteedModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/pale_steed.png"
    );

    @Test
    void bakesBonePaleSpectralMountHierarchyAgainstItsOwnAtlas() throws Exception {
        assertEquals(256, PaleSteedModel.TEXTURE_WIDTH);
        assertEquals(192, PaleSteedModel.TEXTURE_HEIGHT);
        final ModelPart root = PaleSteedModel.createBodyLayer().bakeRoot();
        assertFalse(requiredChild(root, "thorax").isEmpty());
        assertFalse(requiredChild(root, "croup").isEmpty());
        final ModelPart skull = requiredChild(
            requiredChild(requiredChild(root, "neck_base"), "neck_spire"), "coffin_skull"
        );
        for (final String feature : List.of("long_muzzle", "left_long_ear", "right_long_ear")) {
            assertFalse(requiredChild(skull, feature).isEmpty(), feature);
        }
        final ModelPart mane = requiredChild(root, "vertebral_mane");
        for (final String plate : List.of("mane_plate_one", "mane_plate_two", "mane_plate_three")) {
            assertFalse(requiredChild(mane, plate).isEmpty(), plate);
        }
        assertThreeStage(root, "left_front_strut", "left_front_cannon", "left_front_split_hoof");
        assertThreeStage(root, "right_front_strut", "right_front_cannon", "right_front_split_hoof");
        assertThreeStage(root, "left_hind_haunch", "left_hind_hock", "left_hind_split_hoof");
        assertThreeStage(root, "right_hind_haunch", "right_hind_hock", "right_hind_split_hoof");
        final ModelPart tail = requiredChild(root, "ribbon_tail_root");
        final ModelPart mid = requiredChild(tail, "ribbon_tail_mid");
        assertFalse(requiredChild(mid, "left_ribbon_fork").isEmpty());
        assertFalse(requiredChild(mid, "right_ribbon_fork").isEmpty());
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        CreatureModelTestSupport.assertUvsWithin(root, 256, 192);
        CreatureModelTestSupport.assertOpaqueUvs(root, texture, cube -> true);
        assertEquals(0, texture.getRGB(255, 191) >>> 24);
        assertEquals("06db3120fac9c7ca823e1cec00fe6bffd987c89b14af71b31021ce6a8e853a62",
            textureHash());
    }

    @Test
    void transformedBoundsMeetGroundAndHighSteppingSilhouettesStayApproved() {
        final ModelPart root = PaleSteedModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        assertEquals(26.309242F, bounds.maxY(), 0.001F);
        assertTrue(bounds.maxZ() - bounds.minZ() >= 26.0F);
        assertTrue(bounds.maxY() - bounds.minY() >= 24.0F);
        final String front = imageSnapshot(softwareSnapshot(
            root, CreatureModelTestSupport.Projection.FRONT, 192, 8
        ));
        final String side = imageSnapshot(softwareSnapshot(
            root, CreatureModelTestSupport.Projection.SIDE, 192, 8
        ));
        final ModelPart threeQuarter = PaleSteedModel.createBodyLayer().bakeRoot();
        threeQuarter.yRot = 0.7853982F;
        final String oblique = imageSnapshot(softwareSnapshot(
            threeQuarter, CreatureModelTestSupport.Projection.FRONT, 192, 8
        ));
        assertNotEquals(front, side);
        assertNotEquals(front, oblique);
        assertEquals("485eee39d4cd01dfd1fa3ee993796098fd54bf1b96d2fafb95713b701ed12208\n"
            + "Bounds[minX=-5.45, minY=-19.712479, minZ=-19.93517, maxX=5.45, maxY=26.309242, maxZ=9.775]\n"
            + "bef2436008bfad74d5fa7d4190dfba7a94b3c55b88079661ec5d7365357bdff6\n"
            + "9b6c607dbb02f38e4b56d6c65bc6b61baa8d9872e8a8d9aaf810d41589454ae7\n"
            + "0a39ae86ec87498d1e0277cd6dfc06cf0c139895367fe7b1f75503dc56840cc9", String.join("\n",
            geometrySnapshot(root), bounds.toString(), front, side, oblique));
    }

    @Test
    void transformedHeightReadsAsATallMountAgainstThePlayerBaseline() {
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(
            PaleSteedModel.createBodyLayer().bakeRoot()
        );
        final float height = bounds.maxY() - bounds.minY();
        assertEquals(26.309242F, bounds.maxY(), 0.001F);
        assertTrue(height >= 45.0F && height <= 47.0F,
            "Pale Steed must stand 1.40x..1.47x the 32-unit player baseline: " + bounds);
    }

    @Test
    void sideProjectionConnectsChestAndForelegsIntoOneCreature() {
        final BufferedImage side = softwareSnapshot(
            PaleSteedModel.createBodyLayer().bakeRoot(),
            CreatureModelTestSupport.Projection.SIDE,
            256,
            8
        );
        assertEquals(1, opaqueComponentCount(side),
            "Pale Steed chest and forelegs must be physically connected in side projection");
    }

    @Test
    void neutralTrotAndBondedWardingStepAreDistinctAndApproved() {
        final PaleSteedModel neutral = new PaleSteedModel(PaleSteedModel.createBodyLayer().bakeRoot());
        neutral.setupAnim(new PaleSteedModel.State());
        final PaleSteedModel trotting = new PaleSteedModel(PaleSteedModel.createBodyLayer().bakeRoot());
        trotting.setupAnim(motionState());
        final PaleSteedModel warding = new PaleSteedModel(PaleSteedModel.createBodyLayer().bakeRoot());
        final PaleSteedModel.State action = motionState();
        action.balking = true;
        action.bond = 800;
        warding.setupAnim(action);
        final String neutralHash = geometrySnapshot(neutral.root());
        final String movingHash = geometrySnapshot(trotting.root());
        final String actionHash = geometrySnapshot(warding.root());
        assertNotEquals(neutralHash, movingHash);
        assertNotEquals(movingHash, actionHash);
        assertEquals("485eee39d4cd01dfd1fa3ee993796098fd54bf1b96d2fafb95713b701ed12208\n"
            + "e2b199d9fe1a5e8839601047064c668fcf86285cd691b6f248f03bcd576f842b\n"
            + "8456320c86e08132c2060b4e908e7f4722dfe29684fda46d3b48e8797fbc9395",
            String.join("\n", neutralHash, movingHash, actionHash));
    }

    @Test
    void sourceOwnsRigStateExtractionAndConcreteSteedBinding() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<PaleSteedModel.State>"));
        assertTrue(source.contains("extractRenderState("));
        assertTrue(source.contains("final SpectralSteedEntity entity"));
        assertTrue(source.contains("entity.presentationGait()"));
        assertTrue(source.contains("entity.presentationBond()"));
        assertTrue(source.contains("entity.presentationFatigue()"));
        assertTrue(source.contains("entity.presentationBalking()"));
        assertTrue(source.contains("entity.presentationResting()"));
        assertFalse(source.contains("entity.steedState()"));
        assertFalse(source.contains("left_hand"));
        assertFalse(source.contains("right_hand"));
        for (final String forbidden : List.of(
            "ArcaneCreatureModel", "CreatureModelProfile", "WarlockeryModel", "AnimationHelper",
            "GeometryHelper", "ModelHelper", "WerewolfModel", "FeralLycanModel",
            "HellhoundModel", "NightmareModel", "horse", "Horse", " implements "
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        assertFalse(source.contains("static void add"));
    }

    @Test
    void writesSoftwareContactSheet() throws Exception {
        final ModelPart neutral = PaleSteedModel.createBodyLayer().bakeRoot();
        final ModelPart oblique = PaleSteedModel.createBodyLayer().bakeRoot();
        oblique.yRot = 0.7853982F;
        final PaleSteedModel moving = new PaleSteedModel(PaleSteedModel.createBodyLayer().bakeRoot());
        moving.setupAnim(motionState());
        final PaleSteedModel action = new PaleSteedModel(PaleSteedModel.createBodyLayer().bakeRoot());
        final PaleSteedModel.State actionState = motionState();
        actionState.balking = true;
        actionState.bond = 800;
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

    private static PaleSteedModel.State motionState() {
        final PaleSteedModel.State state = new PaleSteedModel.State();
        state.yRot = -20.0F;
        state.xRot = 5.0F;
        state.walkAnimationPos = 3.4F;
        state.walkAnimationSpeed = 0.86F;
        state.ageInTicks = 52.0F;
        state.gait = Gait.TROT;
        state.bond = 620;
        state.fatigue = 280;
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

    private static int opaqueComponentCount(final BufferedImage image) {
        final boolean[] visited = new boolean[image.getWidth() * image.getHeight()];
        int components = 0;
        for (int start = 0; start < visited.length; start++) {
            final int startX = start % image.getWidth();
            final int startY = start / image.getWidth();
            if (visited[start] || (image.getRGB(startX, startY) >>> 24) == 0) {
                continue;
            }
            components++;
            final ArrayDeque<Integer> pending = new ArrayDeque<>();
            pending.add(start);
            visited[start] = true;
            while (!pending.isEmpty()) {
                final int current = pending.removeFirst();
                final int x = current % image.getWidth();
                final int y = current / image.getWidth();
                for (int deltaY = -1; deltaY <= 1; deltaY++) {
                    for (int deltaX = -1; deltaX <= 1; deltaX++) {
                        final int nextX = x + deltaX;
                        final int nextY = y + deltaY;
                        if (nextX < 0 || nextY < 0
                            || nextX >= image.getWidth() || nextY >= image.getHeight()) {
                            continue;
                        }
                        final int next = nextY * image.getWidth() + nextX;
                        if (!visited[next] && (image.getRGB(nextX, nextY) >>> 24) != 0) {
                            visited[next] = true;
                            pending.addLast(next);
                        }
                    }
                }
            }
        }
        return components;
    }

    private static void writeContactSheet(final List<BufferedImage> views) throws Exception {
        final Path output = Path.of(
            "build/reports/visual-audit/creatures/pale_steed-software-contact-sheet.png"
        );
        final BufferedImage sheet = new BufferedImage(576, 384, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = sheet.createGraphics();
        try {
            graphics.setColor(new Color(57, 76, 81));
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
