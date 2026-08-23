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

final class DreamrootModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/DreamrootModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/dreamroot.png"
    );

    @Test
    void ownsItsAtlasLayerAndCompleteHierarchy() throws Exception {
        assertEquals(128, DreamrootModel.TEXTURE_WIDTH);
        assertEquals(64, DreamrootModel.TEXTURE_HEIGHT);
        final ModelPart root = DreamrootModel.createBodyLayer().bakeRoot();
        assertFalse(requiredChild(root, "head").isEmpty());
        assertFalse(requiredChild(root, "body").isEmpty());
        final ModelPart crown = requiredChild(root, "crown");
        final ModelPart inner = requiredChild(crown, "petal_tier_inner");
        final ModelPart middle = requiredChild(inner, "petal_tier_middle");
        assertFalse(inner.isEmpty());
        assertFalse(middle.isEmpty());
        assertFalse(requiredChild(middle, "petal_tier_outer").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_arm"), "right_outer_tassel").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_arm"), "left_outer_tassel").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_hind_leg"), "right_tassel_distal").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_hind_leg"), "left_tassel_distal").isEmpty());
        CreatureModelTestSupport.assertUvsWithin(root, DreamrootModel.TEXTURE_WIDTH, DreamrootModel.TEXTURE_HEIGHT);
        CreatureModelTestSupport.assertOpaqueUvs(
            root,
            ImageIO.read(TEXTURE.toFile()),
            cube -> cube.path().endsWith("body") && cube.index() == 1
        );
    }

    @Test
    void geometryBoundsAndSoftwareViewsMatchTheApprovedModel() throws Exception {
        final ModelPart dedicated = DreamrootModel.createBodyLayer().bakeRoot();
        assertEquals("5eb9466257800922839822ef723fd02908bff51e5b2ac5b0c10cd191d5089c71",
            geometrySnapshot(dedicated));
        assertEquals(new CreatureModelTestSupport.Bounds(
            -14.944803F, -6.7486386F, -7.3133597F, 15.318746F, 24.017035F, 7.348838F
        ), CreatureModelTestSupport.bounds(dedicated));
        final Map<CreatureModelTestSupport.Projection, String> approvedViews = Map.of(
            CreatureModelTestSupport.Projection.FRONT,
            "145fd7495e105b76d7ac41225d485fab254bc2c869933515a6f42bb4a588e6ba",
            CreatureModelTestSupport.Projection.SIDE,
            "e72646b7190c2d0ded0e75f04fc020b2638668b5f785cc591e650851b80e8465",
            CreatureModelTestSupport.Projection.TOP,
            "6fc6dfcfd88973be26c45069e36e72a54ad4d060ac00cf4d39a88036ef17fc89"
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
        final DreamrootModel dedicated = new DreamrootModel(DreamrootModel.createBodyLayer().bakeRoot());
        final DreamrootModel.State dedicatedState = new DreamrootModel.State();
        setMotion(dedicatedState);
        dedicated.setupAnim(dedicatedState);
        assertEquals("133ef43f72a15a1668bb4ec0c0bac65cd2ada637cb1f98afc2bbd2908a4ba237",
            geometrySnapshot(dedicated.root()));

        final Map<HumanoidArm, String> approvedHands = Map.of(
            HumanoidArm.LEFT, "8b120b7d943335f5b38c4d881ce28ccbe1e03b2d238e4602c7b233bed568a178",
            HumanoidArm.RIGHT, "c0e0bb1c4a93bf70a774a67c647660ea36e7cd0329ea9f822df8a8149d31e887"
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
        assertEquals("9fe9c3a32a2eaf77962a9105625692ff4f5138a0dc6e07714278d98b32563bde", textureHash());
    }

    private static void setMotion(final LivingEntityRenderState state) {
        state.yRot = -34.0F;
        state.xRot = 11.0F;
        state.walkAnimationPos = 3.5F;
        state.walkAnimationSpeed = 0.65F;
        state.ageInTicks = 44.25F;
    }

    private static String textureHash() throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(TEXTURE)));
    }

}
