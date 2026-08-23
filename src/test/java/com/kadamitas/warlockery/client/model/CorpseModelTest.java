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
import com.kadamitas.warlockery.entity.CorpseEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

final class CorpseModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/CorpseModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/corpse.png"
    );

    @Test
    void ownsAnAsymmetricReknittingBody() throws Exception {
        final ModelPart root = CorpseModel.createBodyLayer().bakeRoot();
        assertFalse(requiredChild(requiredChild(root, "head"), "dropped_jaw").isEmpty());
        final ModelPart torso = requiredChild(root, "reknit_torso");
        assertFalse(requiredChild(torso, "left_rib_cage").isEmpty());
        assertFalse(requiredChild(torso, "right_reknit_slab").isEmpty());
        assertFalse(requiredChild(torso, "stitch_bridge").isEmpty());
        final ModelPart bindings = requiredChild(torso, "grave_binding_bands");
        final ModelPart seams = requiredChild(torso, "teal_cohesion_seams");
        final ModelPart ballast = requiredChild(torso, "sternum_ballast");
        final ModelPart splint = requiredChild(torso, "timber_splint");
        assertFalse(bindings.isEmpty());
        assertFalse(seams.isEmpty());
        assertFalse(ballast.isEmpty());
        assertFalse(splint.isEmpty());
        assertTrue(CreatureModelTestSupport.bounds(bindings).maxX()
                - CreatureModelTestSupport.bounds(bindings).minX() >= 5.0F);
        assertTrue(CreatureModelTestSupport.bounds(ballast).maxY()
                - CreatureModelTestSupport.bounds(ballast).minY() >= 5.0F);
        assertTrue(CreatureModelTestSupport.bounds(splint).maxY()
                - CreatureModelTestSupport.bounds(splint).minY() >= 9.0F);
        final ModelPart dragArm = requiredChild(root, "drag_arm");
        final ModelPart braceArm = requiredChild(root, "brace_arm");
        assertFalse(requiredChild(dragArm, "drag_forearm").isEmpty());
        assertFalse(requiredChild(braceArm, "brace_forearm").isEmpty());
        final ModelPart stiffLeg = requiredChild(root, "stiff_leg");
        final ModelPart foldedLeg = requiredChild(root, "folded_leg");
        assertFalse(requiredChild(stiffLeg, "stiff_shin").isEmpty());
        assertFalse(requiredChild(foldedLeg, "folded_shin").isEmpty());
        assertTrue(torso.xRot >= 0.28F, "the reknit body must pitch forward at rest");
        assertTrue(dragArm.y < braceArm.y, "the drag shoulder must hang higher and extend longer");
        assertTrue(foldedLeg.y != stiffLeg.y || foldedLeg.z != stiffLeg.z,
            "dropped hip and folded leg must remain visibly uneven");
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        assertTrue(Math.abs(bounds.minX()) != Math.abs(bounds.maxX()), "silhouette must remain asymmetric");
        CreatureModelTestSupport.assertUvsWithin(root, CorpseModel.TEXTURE_WIDTH, CorpseModel.TEXTURE_HEIGHT);
        assertEquals(CorpseModel.TEXTURE_WIDTH, ImageIO.read(TEXTURE.toFile()).getWidth());
        assertEquals(CorpseModel.TEXTURE_HEIGHT, ImageIO.read(TEXTURE.toFile()).getHeight());
        CreatureModelTestSupport.assertOpaqueUvs(root, ImageIO.read(TEXTURE.toFile()), cube -> true);
    }

    @Test
    void conceptShapeIsSlenderForwardPitchedAndGroundedInsteadOfBroad() {
        final ModelPart root = CorpseModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        final float height = bounds.maxY() - bounds.minY();
        final float frontAspect = (bounds.maxX() - bounds.minX()) / height;
        final float sideAspect = (bounds.maxZ() - bounds.minZ()) / height;
        assertTrue(frontAspect >= 0.50F && frontAspect <= 0.68F,
            "bindings and slack limbs must frame a narrow corpse front: " + frontAspect);
        assertTrue(sideAspect >= 0.38F && sideAspect <= 0.51F,
            "forward pitch and uneven limbs must retain restrained profile depth: " + sideAspect);
    }

    @Test
    void synchronizedDormantStateCollapsesInsteadOfWalking() {
        final CorpseModel model = new CorpseModel(CorpseModel.createBodyLayer().bakeRoot());
        final CorpseModel.State state = new CorpseModel.State();
        state.walkAnimationPos = 2.0F;
        state.walkAnimationSpeed = 0.8F;
        model.setupAnim(state);
        final String moving = geometrySnapshot(model.root());
        state.dormant = true;
        state.walkAnimationSpeed = 0.0F;
        model.setupAnim(state);
        assertNotEquals(moving, geometrySnapshot(model.root()));
    }

    @Test
    void sourceContainsNoZombieOrSharedRigInheritance() throws Exception {
        final String source = Files.readString(SOURCE);
        for (final String forbidden : java.util.List.of(
            "ZombieModel", "HumanoidModel<", "ArcaneCreatureModel", "CreatureModelProfile",
            "AnimationHelper", "GeometryHelper", "ModelHelper"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    @Test
    void exposesConcreteDormancyExtractor() {
        assertDoesNotThrow(() -> CorpseModel.class.getDeclaredMethod(
            "extractRenderState", CorpseEntity.class, CorpseModel.State.class, float.class
        ));
    }

    @Test
    void writesSoftwareContactSheet() throws Exception {
        final CorpseModel model = new CorpseModel(CorpseModel.createBodyLayer().bakeRoot());
        final CorpseModel.State action = new CorpseModel.State();
        action.dormant = true;
        final Path output = Path.of("build/reports/visual-audit/creatures/corpse-software-contact-sheet.png");
        final java.awt.image.BufferedImage sheet = new java.awt.image.BufferedImage(
            768, 160, java.awt.image.BufferedImage.TYPE_INT_ARGB
        );
        final java.awt.Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new java.awt.Color(41, 34, 31));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        final float[] turns = {0.0F, Mth.HALF_PI, Mth.PI, -Mth.HALF_PI, -0.72F};
        for (int index = 0; index < turns.length; index++) {
            model.setupAnim(new CorpseModel.State());
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
