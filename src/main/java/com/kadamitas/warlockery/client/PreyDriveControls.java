package com.kadamitas.warlockery.client;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraftforge.client.event.MovementInputUpdateEvent;

public final class PreyDriveControls {
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
        input.moveVector = Vec2.ZERO;
    }
}
