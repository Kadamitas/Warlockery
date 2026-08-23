package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.geometrySnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.matrixSnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.softwareSnapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import com.kadamitas.warlockery.entity.WerewolfHunterEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

final class WerewolfHunterModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/WerewolfHunterModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/werewolf_hunter.png"
    );

    @Test
    void codeSpellingOwnsTheWarlockHunterFieldSilhouette() throws Exception {
        final ModelPart root = WerewolfHunterModel.createBodyLayer().bakeRoot();
        assertFalse(requiredChild(requiredChild(root, "head"), "half_brim_hood").isEmpty());
        final ModelPart body = requiredChild(root, "body");
        final ModelPart coat = requiredChild(body, "split_field_coat");
        final ModelPart rightPanel = requiredChild(coat, "right_rear_coat_panel");
        final ModelPart centerPanel = requiredChild(coat, "center_rear_coat_panel");
        final ModelPart leftPanel = requiredChild(coat, "left_rear_coat_panel");
        assertFalse(rightPanel.isEmpty());
        assertFalse(centerPanel.isEmpty());
        assertFalse(leftPanel.isEmpty());
        assertTrue(CreatureModelTestSupport.bounds(rightPanel).maxY()
                - CreatureModelTestSupport.bounds(rightPanel).minY() >= 8.0F);
        assertTrue(CreatureModelTestSupport.bounds(centerPanel).maxY()
                - CreatureModelTestSupport.bounds(centerPanel).minY() >= 8.0F);
        assertTrue(CreatureModelTestSupport.bounds(leftPanel).maxY()
                - CreatureModelTestSupport.bounds(leftPanel).minY() >= 8.0F);
        final float minimumPanelZ = Math.min(rightPanel.z, Math.min(centerPanel.z, leftPanel.z));
        final float maximumPanelZ = Math.max(rightPanel.z, Math.max(centerPanel.z, leftPanel.z));
        assertTrue(maximumPanelZ - minimumPanelZ >= 2.0F,
            "three rear coat panels must be staggered in profile");
        final ModelPart boltCase = requiredChild(body, "silver_bolt_case");
        final ModelPart boltFan = requiredChild(boltCase, "silver_bolt_fan");
        final ModelPart satchel = requiredChild(body, "field_satchel");
        assertFalse(boltFan.isEmpty());
        assertFalse(satchel.isEmpty());
        assertTrue(CreatureModelTestSupport.bounds(satchel).maxZ()
                - CreatureModelTestSupport.bounds(satchel).minZ() >= 4.0F);
        assertFalse(requiredChild(body, "raised_shoulder_guard").isEmpty());
        assertFalse(requiredChild(body, "crossbow_sling").isEmpty());
        assertFalse(requiredChild(root, "right_arm").isEmpty());
        assertFalse(requiredChild(root, "left_arm").isEmpty());
        final ModelPart rightBoot = requiredChild(requiredChild(root, "right_leg"), "right_hunter_boot");
        final ModelPart leftBoot = requiredChild(requiredChild(root, "left_leg"), "left_hunter_boot");
        assertFalse(rightBoot.isEmpty());
        assertFalse(leftBoot.isEmpty());
        assertTrue(CreatureModelTestSupport.bounds(rightBoot).maxZ()
                - CreatureModelTestSupport.bounds(rightBoot).minZ() >= 5.0F);
        assertTrue(CreatureModelTestSupport.bounds(leftBoot).maxZ()
                - CreatureModelTestSupport.bounds(leftBoot).minZ() >= 5.0F);
        CreatureModelTestSupport.assertUvsWithin(
            root, WerewolfHunterModel.TEXTURE_WIDTH, WerewolfHunterModel.TEXTURE_HEIGHT
        );
        assertEquals(WerewolfHunterModel.TEXTURE_WIDTH, ImageIO.read(TEXTURE.toFile()).getWidth());
        assertEquals(WerewolfHunterModel.TEXTURE_HEIGHT, ImageIO.read(TEXTURE.toFile()).getHeight());
        CreatureModelTestSupport.assertOpaqueUvs(root, ImageIO.read(TEXTURE.toFile()), cube -> true);
    }

    @Test
    void conceptShapePreservesThePassingFrontWhileThreeCoatTailsBuildProfileDepth() {
        final ModelPart root = WerewolfHunterModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        final float height = bounds.maxY() - bounds.minY();
        final float frontAspect = (bounds.maxX() - bounds.minX()) / height;
        final float sideAspect = (bounds.maxZ() - bounds.minZ()) / height;
        assertTrue(frontAspect >= 0.50F && frontAspect <= 0.64F,
            "the already-passing lean hunter front must remain bounded: " + frontAspect);
        assertTrue(sideAspect >= 0.43F && sideAspect <= 0.54F,
            "three rear panels, satchel, bolt fan, and boots must carry the left profile: " + sideAspect);
    }

    @Test
    void warningStepAndCrossbowEngagementAreDifferentUsablePoses() {
        final WerewolfHunterModel model = new WerewolfHunterModel(
            WerewolfHunterModel.createBodyLayer().bakeRoot()
        );
        final WerewolfHunterModel.State state = new WerewolfHunterModel.State();
        state.activity = WerewolfHunterModel.Activity.WARNING;
        model.setupAnim(state);
        final String warning = geometrySnapshot(model.root());
        state.activity = WerewolfHunterModel.Activity.ENGAGING;
        state.attackTime = 0.7F;
        model.setupAnim(state);
        assertNotEquals(warning, geometrySnapshot(model.root()));

        final PoseStack left = new PoseStack();
        final PoseStack right = new PoseStack();
        model.translateToHand(state, HumanoidArm.LEFT, left);
        model.translateToHand(state, HumanoidArm.RIGHT, right);
        assertNotEquals(matrixSnapshot(left), matrixSnapshot(right));
    }

    @Test
    void sourceContainsNoPillagerModelOrSharedRig() throws Exception {
        final String source = Files.readString(SOURCE);
        for (final String forbidden : java.util.List.of(
            "PillagerModel", "HumanoidModel<", "ArcaneCreatureModel", "CreatureModelProfile",
            "AnimationHelper", "GeometryHelper", "ModelHelper", "held_crossbow", "baked_crossbow"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        assertTrue(source.contains("implements ArmedModel<WerewolfHunterModel.State>"));
        assertTrue(source.contains("translateToHand"));
    }

    @Test
    void exposesConcreteCrossbowPresentationExtractor() {
        assertDoesNotThrow(() -> WerewolfHunterModel.class.getDeclaredMethod(
            "extractRenderState", WerewolfHunterEntity.class, WerewolfHunterModel.State.class, float.class
        ));
    }

    @Test
    void writesSoftwareContactSheet() throws Exception {
        final WerewolfHunterModel model = new WerewolfHunterModel(
            WerewolfHunterModel.createBodyLayer().bakeRoot()
        );
        final WerewolfHunterModel.State action = new WerewolfHunterModel.State();
        action.activity = WerewolfHunterModel.Activity.ENGAGING;
        action.attackTime = 0.75F;
        final Path output = Path.of(
            "build/reports/visual-audit/creatures/werewolf_hunter-software-contact-sheet.png"
        );
        final java.awt.image.BufferedImage sheet = new java.awt.image.BufferedImage(
            768, 160, java.awt.image.BufferedImage.TYPE_INT_ARGB
        );
        final java.awt.Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new java.awt.Color(40, 37, 33));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        final float[] turns = {0.0F, Mth.HALF_PI, Mth.PI, -Mth.HALF_PI, -0.72F};
        for (int index = 0; index < turns.length; index++) {
            model.setupAnim(new WerewolfHunterModel.State());
            model.root().yRot = turns[index];
            graphics.drawImage(softwareSnapshot(
                model.root(), CreatureModelTestSupport.Projection.FRONT, 128, 5
            ), index * 128, 0, null);
        }
        model.setupAnim(action);
        graphics.drawImage(softwareSnapshot(
            model.root(), CreatureModelTestSupport.Projection.FRONT, 128, 5
        ), 640, 0, null);
        graphics.dispose();
        Files.createDirectories(output.getParent());
        ImageIO.write(sheet, "PNG", output.toFile());
    }
}
