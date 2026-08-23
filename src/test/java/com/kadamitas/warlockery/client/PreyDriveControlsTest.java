package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.junit.jupiter.api.Test;

final class PreyDriveControlsTest {
    @Test
    void activeServerEpisodeSuppressesEveryOrdinaryMovementInput() {
        final ClientInput input = new ClientInput();
        input.keyPresses = new Input(true, false, true, false, true, true, true);
        input.moveVector = new Vec2(1.0F, 1.0F);

        assertFalse(PreyDriveControls.shouldSuppressInput(-1));
        assertTrue(PreyDriveControls.shouldSuppressInput(42));
        PreyDriveControls.suppress(input);

        assertEquals(Input.EMPTY, input.keyPresses);
        assertEquals(Vec2.ZERO, input.moveVector);
    }
}
