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

import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.Mode;
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

final class HobgoblinModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/HobgoblinModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/hobgoblin.png"
    );

    @Test
    void ownsStoutTravelerHierarchyAndPaintedAtlas() throws Exception {
        assertEquals(192, HobgoblinModel.TEXTURE_WIDTH);
        assertEquals(128, HobgoblinModel.TEXTURE_HEIGHT);
        final ModelPart root = HobgoblinModel.createBodyLayer().bakeRoot();
        final ModelPart torso = requiredChild(root, "torso");
        final ModelPart head = requiredChild(torso, "head");
        assertFalse(requiredChild(head, "beak").isEmpty());
        assertFalse(requiredChild(head, "hood_crown").isEmpty());
        assertFalse(requiredChild(torso, "belly_shield").isEmpty());
        assertFalse(requiredChild(torso, "hood_cape").isEmpty());
        assertFalse(requiredChild(torso, "tail_wedge").isEmpty());
        assertFalse(requiredChild(requiredChild(torso, "travel_pack"), "camp_roll").isEmpty());
        assertFalse(requiredChild(requiredChild(torso, "side_pack"), "lantern").isEmpty());
        assertFalse(requiredChild(requiredChild(torso, "tool_holster"), "prospecting_tool").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_flipper"), "right_flipper_tip").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_flipper"), "left_flipper_tip").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_leg"), "right_webbed_foot").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_leg"), "left_webbed_foot").isEmpty());
        assertTrue(CreatureModelTestSupport.solidPartCount(root) >= 23);
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        assertEquals(HobgoblinModel.TEXTURE_WIDTH, texture.getWidth());
        assertEquals(HobgoblinModel.TEXTURE_HEIGHT, texture.getHeight());
        assertTrue(hasTransparentPixel(texture));
        assertTrue(distinctOpaqueColors(texture) >= 10);
        CreatureModelTestSupport.assertUvsWithin(
            root, HobgoblinModel.TEXTURE_WIDTH, HobgoblinModel.TEXTURE_HEIGHT
        );
        CreatureModelTestSupport.assertOpaqueUvs(root, texture,
            cube -> !cube.path().endsWith("leg"));
    }

    @Test
    void restingBoundsAndTurnaroundsStayPenguinReadable() {
        final ModelPart root = HobgoblinModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        final float height = bounds.maxY() - bounds.minY();
        final float width = bounds.maxX() - bounds.minX();
        final float depth = bounds.maxZ() - bounds.minZ();
        final String front = view(root, 0.0F);
        final String side = view(root, -1.5707964F);
        final String back = view(root, 3.1415927F);
        final String threeQuarter = view(root, -0.7853982F);
        assertAll(
            () -> assertTrue(height >= 19.0F && height <= 25.0F, bounds.toString()),
            () -> assertTrue(width >= 15.0F && width <= 25.0F, bounds.toString()),
            () -> assertTrue(depth >= 11.0F && depth <= 23.0F, bounds.toString()),
            () -> assertTrue(bounds.maxY() >= 23.0F && bounds.maxY() <= 25.5F, bounds.toString()),
            () -> assertNotEquals(front, side),
            () -> assertNotEquals(front, back),
            () -> assertNotEquals(front, threeQuarter),
            () -> assertNotEquals(side, threeQuarter)
        );
    }

    @Test
    void waddleDefenseAndHeldItemPosesRemainDistinct() throws Exception {
        final HobgoblinModel neutralModel = new HobgoblinModel(HobgoblinModel.createBodyLayer().bakeRoot());
        neutralModel.setupAnim(new HobgoblinModel.State());
        final String neutral = geometrySnapshot(neutralModel.root());

        final HobgoblinModel movingModel = new HobgoblinModel(HobgoblinModel.createBodyLayer().bakeRoot());
        final HobgoblinModel.State moving = motionState();
        movingModel.setupAnim(moving);
        final String movement = geometrySnapshot(movingModel.root());

        final HobgoblinModel actionModel = new HobgoblinModel(HobgoblinModel.createBodyLayer().bakeRoot());
        final HobgoblinModel.State action = motionState();
        action.mode = Mode.DEFEND;
        actionModel.setupAnim(action);
        final String defense = geometrySnapshot(actionModel.root());
        final String rightHand = hand(actionModel, action, HumanoidArm.RIGHT);
        final String leftHand = hand(actionModel, action, HumanoidArm.LEFT);
        writeContactSheet(actionModel, action);
        assertAll(
            () -> assertNotEquals(neutral, movement),
            () -> assertNotEquals(neutral, defense),
            () -> assertNotEquals(movement, defense),
            () -> assertNotEquals(rightHand, leftHand)
        );
    }

    @Test
    void sourceIsDirectIndependentAndHobgoblinTyped() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<HobgoblinModel.State>"));
        assertTrue(source.contains("implements ArmedModel<HobgoblinModel.State>"));
        assertTrue(source.contains("public static void extractRenderState("));
        assertTrue(source.contains("final HobgoblinEntity entity"));
        for (final String forbidden : java.util.List.of(
            "ArcaneCreatureModel", "CreatureModelProfile", "PenguinRig", "GoblinRig",
            "GeometryHelper", "AnimationHelper", "ModelHelper", "RigCatalog",
            "GoblinModel", "StonebrokerModel", "ForgewardenModel", "extends Warlockery"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    private static HobgoblinModel.State motionState() {
        final HobgoblinModel.State state = new HobgoblinModel.State();
        state.yRot = -23.0F;
        state.xRot = 7.0F;
        state.walkAnimationPos = 2.65F;
        state.walkAnimationSpeed = 0.78F;
        state.ageInTicks = 36.0F;
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
        final HobgoblinModel model,
        final HobgoblinModel.State state,
        final HumanoidArm arm
    ) {
        final PoseStack stack = new PoseStack();
        model.translateToHand(state, arm, stack);
        return matrixSnapshot(stack);
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
        final HobgoblinModel model,
        final HobgoblinModel.State action
    ) throws Exception {
        final BufferedImage sheet = new BufferedImage(768, 160, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new Color(38, 36, 42));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        model.setupAnim(new HobgoblinModel.State());
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
            "build/reports/visual-audit/creatures/hobgoblin-software-contact-sheet.png"
        );
        Files.createDirectories(output.getParent());
        ImageIO.write(sheet, "png", output.toFile());
    }
}
