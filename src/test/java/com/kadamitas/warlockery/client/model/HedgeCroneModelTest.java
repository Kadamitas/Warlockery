package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.geometrySnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.softwareSnapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import com.kadamitas.warlockery.entity.HedgeCroneEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

final class HedgeCroneModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/HedgeCroneModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/hedge_crone.png"
    );

    @Test
    void ownsItsDeepHunchRootArchBowlAndPestle() throws Exception {
        final ModelPart root = HedgeCroneModel.createBodyLayer().bakeRoot();
        final ModelPart pelvis = requiredChild(root, "hunched_pelvis");
        final ModelPart body = requiredChild(pelvis, "body");
        final ModelPart head = requiredChild(body, "head");
        assertFalse(requiredChild(head, "gray_hair_cap").isEmpty());
        assertFalse(requiredChild(head, "gray_back_hair").isEmpty());
        assertFalse(requiredChild(head, "crooked_nose").isEmpty());
        final ModelPart rootArch = requiredChild(body, "one_sided_root_arch");
        assertFalse(rootArch.isEmpty());
        assertTrue(CreatureModelTestSupport.bounds(rootArch).maxY()
                - CreatureModelTestSupport.bounds(rootArch).minY() >= 6.0F);
        assertFalse(requiredChild(body, "shawl").isEmpty());
        final ModelPart bowl = requiredChild(body, "shallow_stone_bowl");
        assertFalse(bowl.isEmpty());
        assertTrue(CreatureModelTestSupport.bounds(bowl).maxX()
                - CreatureModelTestSupport.bounds(bowl).minX() >= 4.0F);
        assertFalse(requiredChild(body, "ward_bundle").isEmpty());
        final ModelPart rightArm = requiredChild(pelvis, "right_arm");
        final ModelPart leftArm = requiredChild(pelvis, "left_arm");
        assertFalse(rightArm.isEmpty());
        assertFalse(leftArm.isEmpty());
        final ModelPart pestle = requiredChild(rightArm, "short_ward_pestle");
        assertFalse(pestle.isEmpty());
        assertTrue(CreatureModelTestSupport.bounds(pestle).maxY()
                - CreatureModelTestSupport.bounds(pestle).minY() >= 6.0F);
        assertTrue(bowl.x * rightArm.x < 0.0F, "bowl and ward pestle must read on opposite sides");
        final ModelPart rightLeg = requiredChild(pelvis, "right_bent_leg");
        final ModelPart leftLeg = requiredChild(pelvis, "left_bent_leg");
        assertFalse(rightLeg.isEmpty());
        assertFalse(leftLeg.isEmpty());
        assertTrue(pelvis.xRot >= 0.04F, "the pelvis must begin the hunch");
        assertTrue(body.xRot >= 0.17F, "the spine must continue the hunch");
        assertTrue(head.xRot >= 0.11F, "the head must pitch forward from the bent spine");
        assertTrue(head.z < 0.0F, "the head must project ahead of the hips");
        CreatureModelTestSupport.assertUvsWithin(
            root, HedgeCroneModel.TEXTURE_WIDTH, HedgeCroneModel.TEXTURE_HEIGHT
        );
        assertEquals(HedgeCroneModel.TEXTURE_WIDTH, ImageIO.read(TEXTURE.toFile()).getWidth());
        assertEquals(HedgeCroneModel.TEXTURE_HEIGHT, ImageIO.read(TEXTURE.toFile()).getHeight());
        CreatureModelTestSupport.assertOpaqueUvs(root, ImageIO.read(TEXTURE.toFile()), cube -> true);
    }

    @Test
    void conceptShapeIsDeeplyHunchedWithWideLowerBandsAndAReadableProfile() {
        final ModelPart root = HedgeCroneModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        final float height = bounds.maxY() - bounds.minY();
        final float frontAspect = (bounds.maxX() - bounds.minX()) / height;
        final float sideAspect = (bounds.maxZ() - bounds.minZ()) / height;
        assertTrue(frontAspect >= 0.50F && frontAspect <= 0.60F,
            "root arch, bowl, and arms need a readable front: " + frontAspect);
        assertTrue(sideAspect >= 0.35F && sideAspect <= 0.70F,
            "forward head, bowl, and burden frame need authored profile depth: " + sideAspect);
    }

    @Test
    void preparedWardAndCastingPoseRemainSpeciesOwned() {
        final HedgeCroneModel model = new HedgeCroneModel(HedgeCroneModel.createBodyLayer().bakeRoot());
        final HedgeCroneModel.State state = new HedgeCroneModel.State();
        final String neutral = geometrySnapshot(model.root());
        state.activity = HedgeCroneModel.Activity.PREPARING;
        state.wardPrepared = true;
        state.ageInTicks = 11.0F;
        model.setupAnim(state);
        final String preparing = geometrySnapshot(model.root());
        assertNotEquals(neutral, preparing);
        state.activity = HedgeCroneModel.Activity.CASTING;
        state.ageInTicks = 35.0F;
        model.setupAnim(state);
        assertNotEquals(preparing, geometrySnapshot(model.root()));
    }

    @Test
    void sourceContainsNoSharedWarlockeryRigOrHelper() throws Exception {
        final String source = Files.readString(SOURCE);
        for (final String forbidden : java.util.List.of(
            "ArcaneCreatureModel", "CreatureModelProfile", "AnimationHelper", "GeometryHelper",
            "ModelHelper", "CircleMageModel", "VampireModel", "HumanoidModel<"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    @Test
    void exposesConcreteSynchronizedPresentationExtractor() {
        assertDoesNotThrow(() -> HedgeCroneModel.class.getDeclaredMethod(
            "extractRenderState", HedgeCroneEntity.class, HedgeCroneModel.State.class, float.class
        ));
    }

    @Test
    void writesSoftwareContactSheet() throws Exception {
        final HedgeCroneModel model = new HedgeCroneModel(HedgeCroneModel.createBodyLayer().bakeRoot());
        final HedgeCroneModel.State action = new HedgeCroneModel.State();
        action.activity = HedgeCroneModel.Activity.CASTING;
        action.wardPrepared = true;
        action.ageInTicks = 35.0F;
        final Path output = Path.of(
            "build/reports/visual-audit/creatures/hedge_crone-software-contact-sheet.png"
        );
        final java.awt.image.BufferedImage sheet = new java.awt.image.BufferedImage(
            768, 160, java.awt.image.BufferedImage.TYPE_INT_ARGB
        );
        final java.awt.Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new java.awt.Color(40, 35, 29));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        final float[] turns = {0.0F, Mth.HALF_PI, Mth.PI, -Mth.HALF_PI, -0.72F};
        for (int index = 0; index < turns.length; index++) {
            model.setupAnim(new HedgeCroneModel.State());
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
