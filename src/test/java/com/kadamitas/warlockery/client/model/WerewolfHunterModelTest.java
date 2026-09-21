package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.bounds;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.geometrySnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.WerewolfHunterEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

final class WerewolfHunterModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/WerewolfHunterModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/werewolf_hunter.png"
    );

    @Test
    void humanHunterHasAFullBrimLongCoatAndGroundedBoots() throws Exception {
        final ModelPart root = WerewolfHunterModel.createBodyLayer().bakeRoot();
        final ModelPart head = requiredChild(root, "head");
        assertFalse(requiredChild(head, "right_eye").isEmpty());
        assertFalse(requiredChild(head, "left_eye").isEmpty());
        assertFalse(requiredChild(head, "nose").isEmpty());
        assertFalse(requiredChild(head, "mouth").isEmpty());
        assertFalse(requiredChild(head, "right_sideburn").isEmpty());
        assertFalse(requiredChild(head, "left_sideburn").isEmpty());
        final ModelPart hat = requiredChild(head, "broad_brimmed_hat");
        assertTrue(bounds(hat).maxX() - bounds(hat).minX() >= 12.0F);
        assertTrue(bounds(hat).maxZ() - bounds(hat).minZ() >= 12.0F);

        final ModelPart body = requiredChild(root, "body");
        final ModelPart coat = requiredChild(body, "long_coat");
        final ModelPart rightTail = requiredChild(coat, "right_coat_tail");
        final ModelPart leftTail = requiredChild(coat, "left_coat_tail");
        assertTrue(bounds(rightTail).maxY() - bounds(rightTail).minY() >= 10.0F);
        assertTrue(bounds(leftTail).maxY() - bounds(leftTail).minY() >= 10.0F);
        assertFalse(requiredChild(requiredChild(body, "silver_bolt_case"), "silver_bolt_fan").isEmpty());
        assertFalse(requiredChild(body, "crossbow_sling").isEmpty());

        final ModelPart rightBoot = requiredChild(requiredChild(root, "right_leg"), "right_hunter_boot");
        final ModelPart leftBoot = requiredChild(requiredChild(root, "left_leg"), "left_hunter_boot");
        assertEquals(
            bounds(rightBoot).maxY() - bounds(rightBoot).minY(),
            bounds(leftBoot).maxY() - bounds(leftBoot).minY(),
            0.001F
        );
        assertTrue(bounds(rightBoot).maxY() - bounds(rightBoot).minY() >= 7.0F);
        final CreatureModelTestSupport.Bounds silhouette = bounds(root);
        assertTrue(silhouette.maxY() >= 26.0F && silhouette.maxY() <= 28.0F,
            "boots and coat must finish at the expected ground plane: " + silhouette.maxY());

        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        assertEquals(WerewolfHunterModel.TEXTURE_WIDTH, texture.getWidth());
        assertEquals(WerewolfHunterModel.TEXTURE_HEIGHT, texture.getHeight());
        CreatureModelTestSupport.assertUvsWithin(
            root, WerewolfHunterModel.TEXTURE_WIDTH, WerewolfHunterModel.TEXTURE_HEIGHT
        );
        CreatureModelTestSupport.assertOpaqueUvs(root, texture, cube -> true);
        final int colors = opaqueColors(texture);
        assertTrue(colors >= 6 && colors <= 24, "restrained atlas palette: " + colors);
    }

    @Test
    void walkingWarningAndCrossbowDrawAreDifferentUsablePoses() {
        final WerewolfHunterModel model = new WerewolfHunterModel(
            WerewolfHunterModel.createBodyLayer().bakeRoot()
        );
        final WerewolfHunterModel.State walking = new WerewolfHunterModel.State();
        walking.walkAnimationPos = 2.0F;
        walking.walkAnimationSpeed = 1.0F;
        model.setupAnim(walking);
        final String walkingPose = geometrySnapshot(model.root());

        final WerewolfHunterModel.State warning = new WerewolfHunterModel.State();
        warning.activity = WerewolfHunterModel.Activity.WARNING;
        model.setupAnim(warning);
        final String warningPose = geometrySnapshot(model.root());

        final WerewolfHunterModel.State drawEarly = new WerewolfHunterModel.State();
        drawEarly.activity = WerewolfHunterModel.Activity.ENGAGING;
        drawEarly.swingAnimation = 0.1F;
        model.setupAnim(drawEarly);
        final String earlyPose = geometrySnapshot(model.root());
        drawEarly.chargingCrossbow = true;
        model.setupAnim(drawEarly);
        final String latePose = geometrySnapshot(model.root());

        assertNotEquals(walkingPose, warningPose);
        assertNotEquals(warningPose, earlyPose);
        assertNotEquals(earlyPose, latePose, "the actual crossbow draw must animate");
    }

    @Test
    void bothHandsExposeDifferentAttachmentTransforms() {
        final WerewolfHunterModel model = new WerewolfHunterModel(
            WerewolfHunterModel.createBodyLayer().bakeRoot()
        );
        final WerewolfHunterModel.State state = new WerewolfHunterModel.State();
        final PoseStack left = new PoseStack();
        final PoseStack right = new PoseStack();
        model.translateToHand(state, HumanoidArm.LEFT, left);
        model.translateToHand(state, HumanoidArm.RIGHT, right);
        assertNotEquals(
            CreatureModelTestSupport.matrixSnapshot(left),
            CreatureModelTestSupport.matrixSnapshot(right)
        );
    }

    @Test
    void sourceStaysHumanAndKeepsTheExistingStateAdapter() throws Exception {
        assertDoesNotThrow(() -> WerewolfHunterModel.class.getDeclaredMethod(
            "extractRenderState", WerewolfHunterEntity.class, WerewolfHunterModel.State.class, float.class
        ));
        final String source = Files.readString(SOURCE);
        for (final String forbidden : java.util.List.of(
            "snout", "muzzle", "wolf_ear", "fur_", "PillagerModel", "HumanoidModel<",
            "ArcaneCreatureModel", "CreatureModelProfile", "AnimationHelper", "GeometryHelper"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        assertTrue(source.contains("broad_brimmed_hat"));
        assertTrue(source.contains("long_coat"));
    }

    private static int opaqueColors(final BufferedImage image) {
        final Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int pixel = image.getRGB(x, y);
                if ((pixel >>> 24) == 255) {
                    colors.add(pixel & 0xFFFFFF);
                }
            }
        }
        return colors.size();
    }
}
