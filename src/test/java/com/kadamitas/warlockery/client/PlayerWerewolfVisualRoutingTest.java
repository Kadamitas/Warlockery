package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.network.ModNetwork;
import com.kadamitas.warlockery.transformation.WerewolfShape;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;

final class PlayerWerewolfVisualRoutingTest {
    @AfterEach
    void clearState() {
        PlayerWolfVisualState.clear();
    }

    @Test
    void synchronizedAvatarStateDistinguishesWolfWolfmanAndHuman() throws Exception {
        final RecordComponent[] components = ModNetwork.PlayerWolfVisualPayload.class.getRecordComponents();
        assertTrue(Arrays.stream(components).anyMatch(component ->
            component.getName().equals("shape") && component.getType() == WerewolfShape.class
        ));

        final Constructor<ModNetwork.PlayerWolfVisualPayload> constructor =
            ModNetwork.PlayerWolfVisualPayload.class.getConstructor(UUID.class, WerewolfShape.class);
        final Method shape = PlayerWolfVisualState.class.getMethod("shape", UUID.class);
        final UUID wolf = UUID.randomUUID();
        final UUID wolfman = UUID.randomUUID();
        final UUID human = UUID.randomUUID();

        PlayerWolfVisualState.update(constructor.newInstance(wolf, WerewolfShape.WOLF));
        PlayerWolfVisualState.update(constructor.newInstance(wolfman, WerewolfShape.WOLFMAN));
        PlayerWolfVisualState.update(constructor.newInstance(human, WerewolfShape.HUMAN));

        assertEquals(WerewolfShape.WOLF, shape.invoke(null, wolf));
        assertEquals(WerewolfShape.WOLFMAN, shape.invoke(null, wolfman));
        assertEquals(WerewolfShape.HUMAN, shape.invoke(null, human));
        assertTrue(PlayerWolfVisualState.isWolf(wolf));
        assertFalse(PlayerWolfVisualState.isWolf(wolfman));
    }

    @Test
    void bothTransformedShapesOwnAFirstPersonForelimbRenderer() throws Exception {
        final Class<?> wolfmanRenderer = Class.forName(
            "com.kadamitas.warlockery.client.WerewolfFormAvatarRenderer"
        );
        for (final Class<?> renderer : new Class<?>[]{WolfFormAvatarRenderer.class, wolfmanRenderer}) {
            assertEquals(void.class, renderer.getDeclaredMethod(
                "submitFirstPersonArm",
                PoseStack.class,
                SubmitNodeCollector.class,
                int.class,
                HumanoidArm.class
            ).getReturnType());
        }
    }
}
