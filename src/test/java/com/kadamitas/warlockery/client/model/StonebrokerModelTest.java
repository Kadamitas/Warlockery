package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.geometrySnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.matrixSnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.GoblinPatronRules.Action;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

final class StonebrokerModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/StonebrokerModel.java"
    );
    private static final Path RENDERERS = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/DedicatedCreatureRenderers.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/stonebroker.png"
    );

    @Test
    void preservesTheBrokerBodyAndOpaqueAtlasContract() throws Exception {
        assertEquals(192, StonebrokerModel.TEXTURE_WIDTH);
        assertEquals(160, StonebrokerModel.TEXTURE_HEIGHT);
        final ModelPart root = StonebrokerModel.createBodyLayer().bakeRoot();
        final ModelPart body = requiredChild(root, "broker_body");
        assertFalse(requiredChild(body, "head").isEmpty());
        assertFalse(requiredChild(body, "ledger").isEmpty());
        assertFalse(requiredChild(body, "quiver").isEmpty());
        for (final String flipper : java.util.List.of("right_flipper", "left_flipper")) {
            assertFalse(requiredChild(root, flipper).isEmpty(), flipper);
        }
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        CreatureModelTestSupport.assertUvsWithin(root, texture.getWidth(), texture.getHeight());
        CreatureModelTestSupport.assertOpaqueUvs(root, texture, cube -> true);
    }

    @Test
    void volleyRaisesBothFlippersIntoABowAndDrawPose() {
        final StonebrokerModel neutral = model(new StonebrokerModel.State());
        final StonebrokerModel.State state = new StonebrokerModel.State();
        state.action = Action.LEDGER_VOLLEY;
        state.actionProgress = 1.0F;
        final StonebrokerModel volley = model(state);
        final ModelPart right = requiredChild(volley.root(), "right_flipper");
        final ModelPart left = requiredChild(volley.root(), "left_flipper");
        assertTrue(right.xRot < -1.3F, "bow hand must be raised");
        assertTrue(left.xRot < -1.2F, "draw hand must meet the bow string");
        assertNotEquals(geometrySnapshot(neutral.root()), geometrySnapshot(volley.root()));
        assertNotEquals(hand(volley, state, HumanoidArm.RIGHT), hand(volley, state, HumanoidArm.LEFT));
    }

    @Test
    void rendererSynthesizesANativeBowOnlyForTheRealVolleyAction() throws Exception {
        final String renderer = Files.readString(RENDERERS);
        assertTrue(renderer.contains("class StonebrokerRenderer"));
        assertTrue(renderer.contains("state.action == GoblinPatronRules.Action.LEDGER_VOLLEY"));
        assertTrue(renderer.contains("new ItemStack(Items.BOW)"));
        assertTrue(renderer.contains("ItemDisplayContext.THIRD_PERSON_RIGHT_HAND"));
        assertTrue(renderer.contains("ItemDisplayContext.THIRD_PERSON_LEFT_HAND"));
        assertTrue(renderer.contains("addLayer(new ItemInHandLayer<>(this))"));
        assertTrue(renderer.contains("\"spirit\", TranslucentSpiritRenderer::new"));
        assertTrue(renderer.contains("RenderTypes.entityTranslucent(getTextureLocation(state))"));
    }

    @Test
    void sourceRetainsTheArmedModelHandAttachmentContract() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("implements ArmedModel<StonebrokerModel.State>"));
        assertTrue(source.contains("public void translateToHand("));
        assertTrue(source.contains("final StonebrokerEntity entity"));
        assertFalse(source.contains("GoblinModel"));
    }

    private static StonebrokerModel model(final StonebrokerModel.State state) {
        final StonebrokerModel model = new StonebrokerModel(StonebrokerModel.createBodyLayer().bakeRoot());
        model.setupAnim(state);
        return model;
    }

    private static String hand(
        final StonebrokerModel model,
        final StonebrokerModel.State state,
        final HumanoidArm arm
    ) {
        final PoseStack stack = new PoseStack();
        model.translateToHand(state, arm, stack);
        return matrixSnapshot(stack);
    }
}
