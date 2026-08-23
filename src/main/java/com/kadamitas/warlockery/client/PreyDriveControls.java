package com.kadamitas.warlockery.client;

import java.lang.reflect.Field;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

public final class PreyDriveControls {
    private static final Field MOVE_VECTOR = moveVectorField();

    private PreyDriveControls() {
    }

    public static void suppressMovement(final MovementInputUpdateEvent event) {
        if (shouldSuppressInput(ClientSupernaturalState.snapshot().preyTargetEntityId())) {
            suppress(event.getInput());
        }
    }

    static boolean shouldSuppressInput(final int preyTargetEntityId) {
        return preyTargetEntityId >= 0;
    }

    static void suppress(final ClientInput input) {
        input.keyPresses = Input.EMPTY;
        try {
            MOVE_VECTOR.set(input, Vec2.ZERO);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to suppress prey-drive movement", exception);
        }
    }

    private static Field moveVectorField() {
        try {
            final Field field = ClientInput.class.getDeclaredField("moveVector");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | RuntimeException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
