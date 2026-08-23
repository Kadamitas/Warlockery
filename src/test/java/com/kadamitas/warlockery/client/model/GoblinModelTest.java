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

import com.kadamitas.warlockery.entity.GoblinEnclaveRules.Intent;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

final class GoblinModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/GoblinModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/goblin.png"
    );

    @Test
    void ownsAgileRockhopperHierarchyAndPaintedAtlas() throws Exception {
        assertEquals(128, GoblinModel.TEXTURE_WIDTH);
        assertEquals(128, GoblinModel.TEXTURE_HEIGHT);
        final ModelPart root = GoblinModel.createBodyLayer().bakeRoot();
        final ModelPart body = requiredChild(root, "body");
        final ModelPart head = requiredChild(body, "head");
        assertFalse(requiredChild(head, "beak").isEmpty());
        assertFalse(requiredChild(head, "right_crest").isEmpty());
        assertFalse(requiredChild(head, "left_crest").isEmpty());
        assertFalse(requiredChild(requiredChild(head, "lamp_cap"), "lamp").isEmpty());
        assertFalse(requiredChild(body, "belly_keel").isEmpty());
        assertFalse(requiredChild(body, "tail_wedge").isEmpty());
        assertFalse(requiredChild(requiredChild(body, "satchel"), "ore_cluster").isEmpty());
        assertFalse(requiredChild(requiredChild(body, "pick_harness"), "pick_head").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_flipper"), "right_flipper_tip").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_flipper"), "left_flipper_tip").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_leg"), "right_webbed_foot").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_leg"), "left_webbed_foot").isEmpty());
        assertTrue(CreatureModelTestSupport.solidPartCount(root) >= 22);
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        assertEquals(GoblinModel.TEXTURE_WIDTH, texture.getWidth());
        assertEquals(GoblinModel.TEXTURE_HEIGHT, texture.getHeight());
        assertTrue(hasTransparentPixel(texture));
        assertTrue(distinctOpaqueColors(texture) >= 10);
        CreatureModelTestSupport.assertUvsWithin(root, GoblinModel.TEXTURE_WIDTH, GoblinModel.TEXTURE_HEIGHT);
        CreatureModelTestSupport.assertOpaqueUvs(root, texture, cube -> true);
    }

    @Test
    void restingBoundsAndTurnaroundsStayPenguinReadable() {
        final ModelPart root = GoblinModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        final float height = bounds.maxY() - bounds.minY();
        final float width = bounds.maxX() - bounds.minX();
        final float depth = bounds.maxZ() - bounds.minZ();
        final String front = view(root, 0.0F);
        final String side = view(root, -1.5707964F);
        final String back = view(root, 3.1415927F);
        final String threeQuarter = view(root, -0.7853982F);
        assertAll(
            () -> assertTrue(height >= 17.0F && height <= 22.0F, bounds.toString()),
            () -> assertTrue(width >= 13.0F && width <= 28.0F, bounds.toString()),
            () -> assertTrue(width <= height * 1.35F, "lateral flippers must retain a compact penguin body: " + bounds),
            () -> assertTrue(depth >= 9.0F && depth <= 20.0F, bounds.toString()),
            () -> assertTrue(bounds.maxY() >= 23.0F && bounds.maxY() <= 25.5F, bounds.toString()),
            () -> assertNotEquals(front, side),
            () -> assertNotEquals(front, back),
            () -> assertNotEquals(front, threeQuarter),
            () -> assertNotEquals(side, threeQuarter)
        );
    }

    @Test
    void clanFrontsRemainFourRoleDistinctPenguinSilhouettes() {
        final BufferedImage goblin = frontSilhouette(GoblinModel.createBodyLayer().bakeRoot());
        final BufferedImage hobgoblin = frontSilhouette(HobgoblinModel.createBodyLayer().bakeRoot());
        final BufferedImage stonebroker = frontSilhouette(StonebrokerModel.createBodyLayer().bakeRoot());
        final BufferedImage forgewarden = frontSilhouette(ForgewardenModel.createBodyLayer().bakeRoot());
        assertAll("role silhouettes must not collapse into one A-line penguin shell",
            () -> assertDistinct("goblin/hobgoblin", goblin, hobgoblin),
            () -> assertDistinct("goblin/stonebroker", goblin, stonebroker),
            () -> assertDistinct("goblin/forgewarden", goblin, forgewarden),
            () -> assertDistinct("hobgoblin/stonebroker", hobgoblin, stonebroker),
            () -> assertDistinct("hobgoblin/forgewarden", hobgoblin, forgewarden),
            () -> assertDistinct("stonebroker/forgewarden", stonebroker, forgewarden)
        );
    }

    @Test
    void waddleRaidAndHeldItemPosesRemainDistinct() throws Exception {
        final GoblinModel neutralModel = new GoblinModel(GoblinModel.createBodyLayer().bakeRoot());
        neutralModel.setupAnim(new GoblinModel.State());
        final String neutral = geometrySnapshot(neutralModel.root());

        final GoblinModel movingModel = new GoblinModel(GoblinModel.createBodyLayer().bakeRoot());
        final GoblinModel.State moving = motionState();
        movingModel.setupAnim(moving);
        final String movement = geometrySnapshot(movingModel.root());

        final GoblinModel actionModel = new GoblinModel(GoblinModel.createBodyLayer().bakeRoot());
        final GoblinModel.State action = motionState();
        action.intent = Intent.ASSAULT;
        action.assaultMember = true;
        action.assaultLeader = true;
        action.assaultWave = 3;
        actionModel.setupAnim(action);
        final String raid = geometrySnapshot(actionModel.root());
        final String rightHand = hand(actionModel, action, HumanoidArm.RIGHT);
        final String leftHand = hand(actionModel, action, HumanoidArm.LEFT);
        writeContactSheet(actionModel, action);
        assertAll(
            () -> assertNotEquals(neutral, movement),
            () -> assertNotEquals(neutral, raid),
            () -> assertNotEquals(movement, raid),
            () -> assertNotEquals(rightHand, leftHand)
        );
    }

    @Test
    void sourceIsDirectIndependentAndGoblinTyped() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<GoblinModel.State>"));
        assertTrue(source.contains("implements ArmedModel<GoblinModel.State>"));
        assertTrue(source.contains("public static void extractRenderState("));
        assertTrue(source.contains("final GoblinEntity entity"));
        for (final String forbidden : java.util.List.of(
            "ArcaneCreatureModel", "CreatureModelProfile", "PenguinRig", "GoblinRig",
            "GeometryHelper", "AnimationHelper", "ModelHelper", "RigCatalog",
            "HobgoblinModel", "StonebrokerModel", "ForgewardenModel", "extends Warlockery"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    private static GoblinModel.State motionState() {
        final GoblinModel.State state = new GoblinModel.State();
        state.yRot = 25.0F;
        state.xRot = -8.0F;
        state.walkAnimationPos = 2.35F;
        state.walkAnimationSpeed = 0.86F;
        state.ageInTicks = 41.0F;
        return state;
    }

    private static String view(final ModelPart root, final float rotation) {
        root.yRot = rotation;
        final String result = imageSnapshot(
            softwareSnapshot(root, CreatureModelTestSupport.Projection.FRONT, 128, 6)
        );
        root.yRot = 0.0F;
        return result;
    }

    private static String hand(
        final GoblinModel model,
        final GoblinModel.State state,
        final HumanoidArm arm
    ) {
        final PoseStack stack = new PoseStack();
        model.translateToHand(state, arm, stack);
        return matrixSnapshot(stack);
    }

    private static BufferedImage frontSilhouette(final ModelPart root) {
        return softwareSnapshot(root, CreatureModelTestSupport.Projection.FRONT, 160, 8);
    }

    private static void assertDistinct(
        final String pair,
        final BufferedImage first,
        final BufferedImage second
    ) {
        final float dice = silhouetteDice(first, second);
        assertTrue(dice < 0.85F, pair + " silhouette Dice overlap " + dice);
    }

    private static float silhouetteDice(final BufferedImage first, final BufferedImage second) {
        int firstPixels = 0;
        int secondPixels = 0;
        int intersection = 0;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                final boolean inFirst = (first.getRGB(x, y) >>> 24) != 0;
                final boolean inSecond = (second.getRGB(x, y) >>> 24) != 0;
                if (inFirst) firstPixels++;
                if (inSecond) secondPixels++;
                if (inFirst && inSecond) intersection++;
            }
        }
        return 2.0F * intersection / (firstPixels + secondPixels);
    }

    private static boolean hasTransparentPixel(final BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int distinctOpaqueColors(final BufferedImage image) {
        final HashSet<Integer> colors = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int argb = image.getRGB(x, y);
                if ((argb >>> 24) != 0) {
                    colors.add(argb);
                }
            }
        }
        return colors.size();
    }

    private static void writeContactSheet(
        final GoblinModel model,
        final GoblinModel.State action
    ) throws Exception {
        final BufferedImage sheet = new BufferedImage(768, 160, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new Color(38, 36, 42));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        model.setupAnim(new GoblinModel.State());
        final float[] turns = {0.0F, 1.5707964F, 3.1415927F, -1.5707964F, -0.7853982F};
        for (int index = 0; index < turns.length; index++) {
            model.root().yRot = turns[index];
            graphics.drawImage(
                softwareSnapshot(model.root(), CreatureModelTestSupport.Projection.FRONT, 128, 6),
                index * 128,
                0,
                null
            );
        }
        model.root().yRot = 0.0F;
        model.setupAnim(action);
        graphics.drawImage(
            softwareSnapshot(model.root(), CreatureModelTestSupport.Projection.FRONT, 128, 6),
            640,
            0,
            null
        );
        graphics.dispose();
        final Path output = Path.of(
            "build/reports/visual-audit/creatures/goblin-software-contact-sheet.png"
        );
        Files.createDirectories(output.getParent());
        ImageIO.write(sheet, "png", output.toFile());
    }
}
