package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.bounds;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.geometrySnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.DeathEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

final class DeathModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/DeathModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/death.png"
    );

    @Test
    void leanHoodedSkeletonCarriesAFullLengthScythe() throws Exception {
        final ModelPart root = DeathModel.createBodyLayer().bakeRoot();
        final ModelPart reaper = requiredChild(root, "reaper");
        final ModelPart hood = requiredChild(reaper, "hood");
        assertFalse(requiredChild(hood, "skull").isEmpty());
        assertFalse(requiredChild(requiredChild(hood, "right_eye"), "pupil").isEmpty());
        assertFalse(requiredChild(requiredChild(hood, "left_eye"), "pupil").isEmpty());
        assertFalse(requiredChild(hood, "nasal_cavity").isEmpty());
        assertFalse(requiredChild(requiredChild(hood, "jaw"), "teeth").isEmpty());

        final ModelPart rightArm = requiredChild(reaper, "right_arm");
        final ModelPart scythe = requiredChild(rightArm, "scythe");
        final ModelPart blade = requiredChild(scythe, "blade");
        assertTrue(bounds(scythe).maxY() - bounds(scythe).minY() >= 25.0F);
        assertTrue(bounds(blade).maxX() - bounds(blade).minX() >= 12.0F);

        final CreatureModelTestSupport.Bounds silhouette = bounds(root);
        final float height = silhouette.maxY() - silhouette.minY();
        final float hoodWidth = bounds(hood).maxX() - bounds(hood).minX();
        assertTrue(hoodWidth / height < 0.55F, "the hood and body core must stay lean");
        CreatureModelTestSupport.assertUvsWithin(
            root, DeathModel.TEXTURE_WIDTH, DeathModel.TEXTURE_HEIGHT
        );
        assertEquals(DeathModel.TEXTURE_WIDTH, ImageIO.read(TEXTURE.toFile()).getWidth());
        assertEquals(DeathModel.TEXTURE_HEIGHT, ImageIO.read(TEXTURE.toFile()).getHeight());
        CreatureModelTestSupport.assertOpaqueUvs(root, ImageIO.read(TEXTURE.toFile()), cube -> true);
    }

    @Test
    void driftTelegraphAndReapProduceSeparateReadablePoses() {
        final DeathModel model = new DeathModel(DeathModel.createBodyLayer().bakeRoot());
        final DeathModel.State idle = new DeathModel.State();
        idle.ageInTicks = 4.0F;
        model.setupAnim(idle);
        final String idlePose = geometrySnapshot(model.root());

        final DeathModel.State telegraph = new DeathModel.State();
        telegraph.ageInTicks = 4.0F;
        telegraph.telegraph = true;
        model.setupAnim(telegraph);
        final String telegraphPose = geometrySnapshot(model.root());

        final DeathModel.State reapEarly = new DeathModel.State();
        reapEarly.attackTime = 0.15F;
        model.setupAnim(reapEarly);
        final String earlyPose = geometrySnapshot(model.root());
        reapEarly.attackTime = 0.75F;
        model.setupAnim(reapEarly);
        final String latePose = geometrySnapshot(model.root());

        assertNotEquals(idlePose, telegraphPose);
        assertNotEquals(telegraphPose, earlyPose);
        assertNotEquals(earlyPose, latePose, "the scythe must follow the actual attack swing");
    }

    @Test
    void bothHandsExposeDifferentAttachmentTransforms() {
        final DeathModel model = new DeathModel(DeathModel.createBodyLayer().bakeRoot());
        final DeathModel.State state = new DeathModel.State();
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
    void modelKeepsDedicatedStateAdapterAndNoSharedRig() throws Exception {
        assertDoesNotThrow(() -> DeathModel.class.getDeclaredMethod(
            "extractRenderState", DeathEntity.class, DeathModel.State.class, float.class
        ));
        final String source = Files.readString(SOURCE);
        for (final String forbidden : java.util.List.of(
            "HumanoidModel<", "ArcaneCreatureModel", "CreatureModelProfile",
            "AnimationHelper", "GeometryHelper", "ModelHelper"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }
}
