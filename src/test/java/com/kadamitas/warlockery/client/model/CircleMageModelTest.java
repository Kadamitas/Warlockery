package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.geometrySnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.softwareSnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.solidPartCount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import com.kadamitas.warlockery.entity.CircleMageEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

final class CircleMageModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/CircleMageModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/circle_mage.png"
    );
    private static final Path GENERATOR = Path.of("tools/creature_models/generate_occult_humanoids.ps1");

    @Test
    void ownsARestrainedStudyRigAndCircleFocus() throws Exception {
        final ModelPart root = CircleMageModel.createBodyLayer().bakeRoot();
        final ModelPart head = requiredChild(root, "head");
        final ModelPart body = requiredChild(root, "body");
        assertFalse(requiredChild(head, "study_visor").isEmpty());
        final ModelPart mantle = requiredChild(body, "layered_mantle");
        assertFalse(requiredChild(mantle, "right_broken_ring_shard").isEmpty());
        assertFalse(requiredChild(mantle, "left_broken_ring_shard").isEmpty());
        assertFalse(requiredChild(mantle, "rear_broken_ring_shard").isEmpty());
        final ModelPart focus = requiredChild(body, "circle_focus");
        assertFalse(requiredChild(focus, "separate_focus_ring").isEmpty());
        assertFalse(requiredChild(focus, "focus_core").isEmpty());
        final CreatureModelTestSupport.Bounds focusBounds = CreatureModelTestSupport.bounds(focus);
        assertTrue(focusBounds.maxX() - focusBounds.minX() >= 6.0F);
        assertTrue(focusBounds.maxZ() - focusBounds.minZ() >= 2.5F);
        final ModelPart slate = requiredChild(body, "script_panel");
        assertFalse(requiredChild(slate, "right_folding_slate_leaf").isEmpty());
        assertFalse(requiredChild(slate, "left_folding_slate_leaf").isEmpty());
        final CreatureModelTestSupport.Bounds slateBounds = CreatureModelTestSupport.bounds(slate);
        assertTrue(slateBounds.maxX() - slateBounds.minX() >= 6.0F);
        assertTrue(slateBounds.maxZ() - slateBounds.minZ() >= 2.0F);
        final ModelPart tunic = requiredChild(body, "split_knee_tunic");
        assertFalse(requiredChild(tunic, "right_tunic_panel").isEmpty());
        assertFalse(requiredChild(tunic, "left_tunic_panel").isEmpty());
        assertTrue(CreatureModelTestSupport.bounds(tunic).maxY()
                - CreatureModelTestSupport.bounds(tunic).minY() >= 8.0F);
        final ModelPart rightArm = requiredChild(root, "right_arm");
        final ModelPart leftArm = requiredChild(root, "left_arm");
        assertFalse(requiredChild(rightArm, "right_forearm").isEmpty());
        assertFalse(requiredChild(rightArm, "right_hand").isEmpty());
        assertFalse(requiredChild(leftArm, "left_forearm").isEmpty());
        assertFalse(requiredChild(leftArm, "left_hand").isEmpty());
        assertTrue(CreatureModelTestSupport.bounds(rightArm).maxY()
                - CreatureModelTestSupport.bounds(rightArm).minY() >= 10.0F);
        assertTrue(CreatureModelTestSupport.bounds(leftArm).maxY()
                - CreatureModelTestSupport.bounds(leftArm).minY() >= 10.0F);
        assertFalse(requiredChild(requiredChild(root, "right_leg"), "right_study_boot").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_leg"), "left_study_boot").isEmpty());
        assertTrue(solidPartCount(root) >= 28);
        CreatureModelTestSupport.assertUvsWithin(
            root, CircleMageModel.TEXTURE_WIDTH, CircleMageModel.TEXTURE_HEIGHT
        );
        assertEquals(CircleMageModel.TEXTURE_WIDTH, ImageIO.read(TEXTURE.toFile()).getWidth());
        assertEquals(CircleMageModel.TEXTURE_HEIGHT, ImageIO.read(TEXTURE.toFile()).getHeight());
        CreatureModelTestSupport.assertOpaqueUvs(root, ImageIO.read(TEXTURE.toFile()), cube -> true);
    }

    @Test
    void conceptShapeStaysNarrowWhileHeldRitualToolsBuildTheFrontAndLeftSilhouettes() {
        final ModelPart root = CircleMageModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        final float height = bounds.maxY() - bounds.minY();
        final float frontAspect = (bounds.maxX() - bounds.minX()) / height;
        final float sideAspect = (bounds.maxZ() - bounds.minZ()) / height;
        assertTrue(frontAspect >= 0.66F && frontAspect <= 0.86F,
            "broken ring, folded slate, arms, and tunic must build the concept front: " + frontAspect);
        assertTrue(sideAspect >= 0.47F && sideAspect <= 0.66F,
            "inward-held ring and slate need authored profile depth: " + sideAspect);
    }

    @Test
    void studyAndBoltPresentationChangeTheOwnedGeometry() {
        final CircleMageModel model = new CircleMageModel(CircleMageModel.createBodyLayer().bakeRoot());
        final String neutral = geometrySnapshot(model.root());
        final CircleMageModel.State state = new CircleMageModel.State();
        state.activity = CircleMageModel.Activity.STUDYING;
        state.focusPrepared = true;
        state.ageInTicks = 18.0F;
        model.setupAnim(state);
        final String studying = geometrySnapshot(model.root());
        assertNotEquals(neutral, studying);

        state.activity = CircleMageModel.Activity.DEFENDING;
        state.ageInTicks = 27.0F;
        model.setupAnim(state);
        assertNotEquals(studying, geometrySnapshot(model.root()));
    }

    @Test
    void sourceContainsNoSharedWarlockeryRigOrHelper() throws Exception {
        final String source = Files.readString(SOURCE);
        for (final String forbidden : java.util.List.of(
            "ArcaneCreatureModel", "CreatureModelProfile", "AnimationHelper", "GeometryHelper",
            "ModelHelper", "HedgeCroneModel", "VampireModel", "HumanoidModel<",
            "generic_book", "held_staff", "broom", "cape"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    @Test
    void exposesConcreteSynchronizedPresentationExtractor() {
        assertDoesNotThrow(() -> CircleMageModel.class.getDeclaredMethod(
            "extractRenderState", CircleMageEntity.class, CircleMageModel.State.class, float.class
        ));
    }

    @Test
    void packageGeneratorOwnsOnlyTheOccultAtlases() throws Exception {
        final String generator = Files.readString(GENERATOR);
        for (final String id : java.util.List.of(
            "circle_mage", "hedge_crone", "blood_thrall", "corpse", "werewolf_hunter",
            "lycan_villager"
        )) {
            assertTrue(generator.contains(id), id);
        }
        for (final String forbidden : java.util.List.of(
            "banshee", "vampire_masculine.png", "vampire_feminine.png", "werewolf.png",
            "imp.png", "goblin.png", "mandrake.png"
        )) {
            assertFalse(generator.contains(forbidden), forbidden);
        }
    }

    @Test
    void writesSoftwareContactSheet() throws Exception {
        final CircleMageModel model = new CircleMageModel(CircleMageModel.createBodyLayer().bakeRoot());
        final CircleMageModel.State action = new CircleMageModel.State();
        action.activity = CircleMageModel.Activity.DEFENDING;
        action.focusPrepared = true;
        action.ageInTicks = 27.0F;
        writeContactSheet(
            model,
            action,
            Path.of("build/reports/visual-audit/creatures/circle_mage-software-contact-sheet.png")
        );
    }

    private static void writeContactSheet(
        final CircleMageModel model,
        final CircleMageModel.State action,
        final Path output
    ) throws Exception {
        final java.awt.image.BufferedImage sheet = new java.awt.image.BufferedImage(
            768, 160, java.awt.image.BufferedImage.TYPE_INT_ARGB
        );
        final java.awt.Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new java.awt.Color(31, 38, 45));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        final float[] turns = {0.0F, Mth.HALF_PI, Mth.PI, -Mth.HALF_PI, -0.72F};
        for (int index = 0; index < turns.length; index++) {
            model.setupAnim(new CircleMageModel.State());
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
