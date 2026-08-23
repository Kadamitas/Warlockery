package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.geometrySnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.imageSnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.matrixSnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.softwareSnapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

final class MandrakeModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/MandrakeModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/mandrake.png"
    );

    @Test
    void ownsItsAtlasLayerAndCompleteHierarchy() throws Exception {
        assertEquals(128, MandrakeModel.TEXTURE_WIDTH);
        assertEquals(64, MandrakeModel.TEXTURE_HEIGHT);
        final ModelPart root = MandrakeModel.createBodyLayer().bakeRoot();
        assertFalse(requiredChild(root, "head").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "head"), "recessed_mouth").isEmpty());
        assertFalse(requiredChild(root, "body").isEmpty());
        final ModelPart crown = requiredChild(root, "crown");
        for (final String leaf : java.util.List.of(
            "mandrake_leaf_north", "mandrake_leaf_south", "mandrake_leaf_west",
            "mandrake_leaf_east", "mandrake_leaf_high"
        )) {
            assertFalse(requiredChild(crown, leaf).isEmpty(), leaf);
        }
        assertFalse(requiredChild(requiredChild(root, "right_arm"), "right_arm_distal").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_arm"), "left_arm_distal").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_hind_leg"), "right_root_distal").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_hind_leg"), "left_root_distal").isEmpty());
        CreatureModelTestSupport.assertUvsWithin(root, MandrakeModel.TEXTURE_WIDTH, MandrakeModel.TEXTURE_HEIGHT);
    }

    @Test
    void geometryBoundsAndSoftwareViewsMatchTheApprovedModel() throws Exception {
        final ModelPart dedicated = MandrakeModel.createBodyLayer().bakeRoot();
        assertEquals("9ba5317e4a36e57233a50292fff8aac9caf21e9e3283f3e22af46140b952fec8",
            geometrySnapshot(dedicated));
        assertEquals(new CreatureModelTestSupport.Bounds(
            -11.628106F, -1.5537078F, -3.3999999F, 11.809366F, 23.98103F, 6.853289F
        ), CreatureModelTestSupport.bounds(dedicated));
        final Map<CreatureModelTestSupport.Projection, String> approvedViews = Map.of(
            CreatureModelTestSupport.Projection.FRONT,
            "887370efda50c9eddb3c5f52ed552de7aafac17809458daf0fbc391d86f9becf",
            CreatureModelTestSupport.Projection.SIDE,
            "adaa4e04872dc12fde6a4aedce4831ae502063197fa92c949ca88327d1f9922a",
            CreatureModelTestSupport.Projection.TOP,
            "5849987e021bed76876670c56c31a274082cc0973836fee5735693765cd77536"
        );
        for (final CreatureModelTestSupport.Projection projection
            : CreatureModelTestSupport.Projection.values()) {
            assertEquals(
                approvedViews.get(projection),
                imageSnapshot(softwareSnapshot(dedicated, projection, 128, 4)),
                projection.name()
            );
        }
    }

    @Test
    void animationAndHandTransformsMatchTheApprovedModel() throws Exception {
        final MandrakeModel dedicated = new MandrakeModel(MandrakeModel.createBodyLayer().bakeRoot());
        final MandrakeModel.State dedicatedState = new MandrakeModel.State();
        setMotion(dedicatedState);
        dedicated.setupAnim(dedicatedState);
        assertEquals("8c62489cb2a21cb9421b1828e3b4167bc5deccf57c187f6673bc4b9e686f9d08",
            geometrySnapshot(dedicated.root()));

        final Map<HumanoidArm, String> approvedHands = Map.of(
            HumanoidArm.LEFT, "baedcb8e9ff7e4e98956a8863adc4158c6ca21279f300c78e7fe800c7e1ae6d4",
            HumanoidArm.RIGHT, "58881cca3a1fa0331fc9bf1a5086e8c17d9a869866783e9a114040a8de83a3e0"
        );
        for (final HumanoidArm arm : HumanoidArm.values()) {
            final PoseStack dedicatedHand = new PoseStack();
            dedicated.translateToHand(dedicatedState, arm, dedicatedHand);
            assertEquals(approvedHands.get(arm), matrixSnapshot(dedicatedHand), arm.name());
        }
    }

    @Test
    void sourceOwnsItsRigAndTextureRemainsByteIdentical() throws Exception {
        final String source = Files.readString(SOURCE);
        assertFalse(source.contains("ArcaneCreatureModel"));
        assertFalse(source.contains("CreatureModelProfile"));
        assertFalse(source.contains("extends ") && source.contains("WarlockeryModel"));
        assertFalse(source.lines().anyMatch(line -> line.startsWith("import com.kadamitas.warlockery")));
        assertFalse(source.contains("AnimationHelper"));
        assertFalse(source.contains("GeometryHelper"));
        assertFalse(source.contains("ModelHelper"));
        assertEquals(128, ImageIO.read(TEXTURE.toFile()).getWidth());
        assertEquals(64, ImageIO.read(TEXTURE.toFile()).getHeight());
        assertEquals("7039324a1c721f726c56c9dd5b2ed13ebac5bcaf35b8bc6342a2b356352cea2f", textureHash());
    }

    private static void setMotion(final LivingEntityRenderState state) {
        state.yRot = 27.0F;
        state.xRot = -13.0F;
        state.walkAnimationPos = 2.25F;
        state.walkAnimationSpeed = 0.8F;
        state.ageInTicks = 31.5F;
    }

    private static String textureHash() throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(TEXTURE)));
    }

}
