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

import com.kadamitas.warlockery.entity.GoblinPatronRules.Action;
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

final class ForgewardenModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/ForgewardenModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/forgewarden.png"
    );

    @Test
    void ownsEmperorPenguinForgeHierarchyAndPaintedAtlas() throws Exception {
        assertEquals(192, ForgewardenModel.TEXTURE_WIDTH);
        assertEquals(160, ForgewardenModel.TEXTURE_HEIGHT);
        final ModelPart root = ForgewardenModel.createBodyLayer().bakeRoot();
        final ModelPart body = requiredChild(root, "furnace_body");
        final ModelPart head = requiredChild(body, "head");
        assertFalse(requiredChild(head, "beak").isEmpty());
        assertFalse(requiredChild(head, "furnace_brow").isEmpty());
        assertFalse(requiredChild(body, "heated_belly_plate").isEmpty());
        assertFalse(requiredChild(body, "tail_wedge").isEmpty());
        final ModelPart yoke = requiredChild(body, "shoulder_yoke");
        assertFalse(requiredChild(yoke, "right_yoke_plate").isEmpty());
        assertFalse(requiredChild(yoke, "left_yoke_plate").isEmpty());
        assertFalse(requiredChild(requiredChild(body, "back_bellows"), "chimney_bank").isEmpty());
        assertFalse(requiredChild(requiredChild(body, "tool_belt"), "hanging_tongs").isEmpty());
        final ModelPart hammerFlipper = requiredChild(root, "right_hammer_flipper");
        assertFalse(requiredChild(requiredChild(hammerFlipper, "hammer_gauntlet"), "hammer_head").isEmpty());
        final ModelPart wardFlipper = requiredChild(root, "left_ward_flipper");
        assertFalse(requiredChild(wardFlipper, "ward_plate").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_leg"), "right_webbed_foot").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_leg"), "left_webbed_foot").isEmpty());
        assertTrue(CreatureModelTestSupport.solidPartCount(root) >= 25);
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        assertEquals(ForgewardenModel.TEXTURE_WIDTH, texture.getWidth());
        assertEquals(ForgewardenModel.TEXTURE_HEIGHT, texture.getHeight());
        assertTrue(hasTransparentPixel(texture));
        assertTrue(distinctOpaqueColors(texture) >= 12);
        CreatureModelTestSupport.assertUvsWithin(
            root, ForgewardenModel.TEXTURE_WIDTH, ForgewardenModel.TEXTURE_HEIGHT
        );
        CreatureModelTestSupport.assertOpaqueUvs(root, texture,
            cube -> !cube.path().endsWith("leg"));
    }

    @Test
    void restingBoundsAndTurnaroundsStayPenguinReadable() {
        final ModelPart root = ForgewardenModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        final float height = bounds.maxY() - bounds.minY();
        final float width = bounds.maxX() - bounds.minX();
        final float depth = bounds.maxZ() - bounds.minZ();
        final String front = view(root, 0.0F);
        final String side = view(root, -1.5707964F);
        final String back = view(root, 3.1415927F);
        final String threeQuarter = view(root, -0.7853982F);
        assertAll(
            () -> assertTrue(height >= 29.0F && height <= 39.0F, bounds.toString()),
            () -> assertTrue(width >= 24.0F && width <= 44.0F, bounds.toString()),
            () -> assertTrue(depth >= 14.0F && depth <= 30.0F, bounds.toString()),
            () -> assertTrue(bounds.maxY() >= 22.5F && bounds.maxY() <= 25.5F, bounds.toString()),
            () -> assertNotEquals(front, side),
            () -> assertNotEquals(front, back),
            () -> assertNotEquals(front, threeQuarter),
            () -> assertNotEquals(side, threeQuarter)
        );
    }

    @Test
    void waddleHammerCommitAndHeldItemPosesRemainDistinct() throws Exception {
        final ForgewardenModel neutralModel = new ForgewardenModel(
            ForgewardenModel.createBodyLayer().bakeRoot()
        );
        neutralModel.setupAnim(new ForgewardenModel.State());
        final String neutral = geometrySnapshot(neutralModel.root());

        final ForgewardenModel movingModel = new ForgewardenModel(
            ForgewardenModel.createBodyLayer().bakeRoot()
        );
        final ForgewardenModel.State moving = motionState();
        movingModel.setupAnim(moving);
        final String movement = geometrySnapshot(movingModel.root());

        final ForgewardenModel actionModel = new ForgewardenModel(
            ForgewardenModel.createBodyLayer().bakeRoot()
        );
        final ForgewardenModel.State action = motionState();
        action.action = Action.HAMMER_COMMIT;
        action.actionProgress = 0.84F;
        actionModel.setupAnim(action);
        final String hammer = geometrySnapshot(actionModel.root());
        final String rightHand = hand(actionModel, action, HumanoidArm.RIGHT);
        final String leftHand = hand(actionModel, action, HumanoidArm.LEFT);
        writeContactSheet(actionModel, action);
        assertAll(
            () -> assertNotEquals(neutral, movement),
            () -> assertNotEquals(neutral, hammer),
            () -> assertNotEquals(movement, hammer),
            () -> assertNotEquals(rightHand, leftHand)
        );
    }

    @Test
    void sourceIsDirectIndependentAndForgewardenTyped() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<ForgewardenModel.State>"));
        assertTrue(source.contains("implements ArmedModel<ForgewardenModel.State>"));
        assertTrue(source.contains("public static void extractRenderState("));
        assertTrue(source.contains("final ForgewardenEntity entity"));
        for (final String forbidden : java.util.List.of(
            "ArcaneCreatureModel", "CreatureModelProfile", "PenguinRig", "GoblinRig",
            "GeometryHelper", "AnimationHelper", "ModelHelper", "RigCatalog",
            "GoblinModel", "HobgoblinModel", "StonebrokerModel", "extends Warlockery"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    private static ForgewardenModel.State motionState() {
        final ForgewardenModel.State state = new ForgewardenModel.State();
        state.yRot = -18.0F;
        state.xRot = 5.0F;
        state.walkAnimationPos = 2.55F;
        state.walkAnimationSpeed = 0.7F;
        state.ageInTicks = 52.0F;
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
        final ForgewardenModel model,
        final ForgewardenModel.State state,
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
        final ForgewardenModel model,
        final ForgewardenModel.State action
    ) throws Exception {
        final BufferedImage sheet = new BufferedImage(768, 160, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new Color(38, 36, 42));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        model.setupAnim(new ForgewardenModel.State());
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
            "build/reports/visual-audit/creatures/forgewarden-software-contact-sheet.png"
        );
        Files.createDirectories(output.getParent());
        ImageIO.write(sheet, "png", output.toFile());
    }
}
