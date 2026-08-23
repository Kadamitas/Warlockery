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
import com.kadamitas.warlockery.entity.VampireCourtEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

final class BloodThrallModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/BloodThrallModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/blood_thrall.png"
    );

    @Test
    void ownsASeparateRestrainedGuardRig() throws Exception {
        final ModelPart root = BloodThrallModel.createBodyLayer().bakeRoot();
        final ModelPart body = requiredChild(root, "narrow_ribcage");
        assertFalse(requiredChild(body, "shell_restraint").isEmpty());
        assertFalse(requiredChild(body, "pearl_cage").isEmpty());
        assertFalse(requiredChild(body, "coral_seal").isEmpty());
        final ModelPart rightArm = requiredChild(root, "right_long_arm");
        final ModelPart leftArm = requiredChild(root, "left_long_arm");
        assertFalse(rightArm.isEmpty());
        assertFalse(leftArm.isEmpty());
        assertTrue(Math.abs(rightArm.x - leftArm.x) >= 9.0F,
            "long arms must spread into a guarding stance");
        final ModelPart rightLeg = requiredChild(root, "right_crouched_leg");
        final ModelPart leftLeg = requiredChild(root, "left_crouched_leg");
        assertFalse(rightLeg.isEmpty());
        assertFalse(leftLeg.isEmpty());
        assertTrue(Math.abs(rightLeg.x - leftLeg.x) >= 3.0F,
            "separate legs must retain a stable guard stance");
        final ModelPart head = requiredChild(root, "head");
        assertTrue(head.z < 0.0F, "the restraint must pull the head forward");
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        final CreatureModelTestSupport.Bounds torsoBounds = CreatureModelTestSupport.bounds(body);
        final float height = bounds.maxY() - bounds.minY();
        final float width = bounds.maxX() - bounds.minX();
        final float depth = bounds.maxZ() - bounds.minZ();
        final float torsoWidth = torsoBounds.maxX() - torsoBounds.minX();
        assertTrue(torsoWidth <= width * 0.75F,
            "the ribcage must remain lean inside the guarding arms");
        assertTrue(width / height >= 0.35F && width / height <= 0.50F,
            "the current thrall stays lean rather than broad: " + width / height);
        assertTrue(depth / height >= 0.20F && depth / height <= 0.30F,
            "the current guard keeps a narrow profile: " + depth / height);
        CreatureModelTestSupport.assertUvsWithin(
            root, BloodThrallModel.TEXTURE_WIDTH, BloodThrallModel.TEXTURE_HEIGHT
        );
        assertEquals(BloodThrallModel.TEXTURE_WIDTH, ImageIO.read(TEXTURE.toFile()).getWidth());
        assertEquals(BloodThrallModel.TEXTURE_HEIGHT, ImageIO.read(TEXTURE.toFile()).getHeight());
        CreatureModelTestSupport.assertOpaqueUvs(root, ImageIO.read(TEXTURE.toFile()), cube -> true);
    }

    @Test
    void boundGuardAndWaveringStatesHaveDifferentLowPoses() {
        final BloodThrallModel model = new BloodThrallModel(BloodThrallModel.createBodyLayer().bakeRoot());
        final BloodThrallModel.State state = new BloodThrallModel.State();
        state.activity = BloodThrallModel.Activity.BOUND_GUARD;
        state.ageInTicks = 16.0F;
        model.setupAnim(state);
        final String guard = geometrySnapshot(model.root());
        state.activity = BloodThrallModel.Activity.WAVERING;
        state.ageInTicks = 29.0F;
        model.setupAnim(state);
        assertNotEquals(guard, geometrySnapshot(model.root()));
    }

    @Test
    void sourceNeverScalesExtendsOrReferencesVampireModel() throws Exception {
        final String source = Files.readString(SOURCE);
        for (final String forbidden : java.util.List.of(
            "VampireModel", "PlayerModel", "HumanoidModel<", "ArcaneCreatureModel",
            "CreatureModelProfile", "AnimationHelper", "GeometryHelper", "ModelHelper"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    @Test
    void exposesItsOwnConcreteCourtStateExtractor() {
        assertDoesNotThrow(() -> BloodThrallModel.class.getDeclaredMethod(
            "extractRenderState", VampireCourtEntity.class, BloodThrallModel.State.class, float.class
        ));
    }

    @Test
    void writesSoftwareContactSheet() throws Exception {
        final BloodThrallModel model = new BloodThrallModel(BloodThrallModel.createBodyLayer().bakeRoot());
        final BloodThrallModel.State action = new BloodThrallModel.State();
        action.activity = BloodThrallModel.Activity.WAVERING;
        action.ageInTicks = 29.0F;
        final Path output = Path.of(
            "build/reports/visual-audit/creatures/blood_thrall-software-contact-sheet.png"
        );
        final java.awt.image.BufferedImage sheet = new java.awt.image.BufferedImage(
            768, 160, java.awt.image.BufferedImage.TYPE_INT_ARGB
        );
        final java.awt.Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new java.awt.Color(17, 34, 43));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        final float[] turns = {0.0F, Mth.HALF_PI, Mth.PI, -Mth.HALF_PI, -0.72F};
        for (int index = 0; index < turns.length; index++) {
            model.setupAnim(new BloodThrallModel.State());
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
