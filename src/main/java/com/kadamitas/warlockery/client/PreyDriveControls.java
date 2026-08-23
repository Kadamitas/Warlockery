package com.kadamitas.warlockery.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

public final class PreyDriveControls {
    private static boolean suppressing;

    private PreyDriveControls() {
    }

    public static void tick(final Minecraft minecraft) {
        final boolean active = shouldSuppressInput(ClientSupernaturalState.snapshot().preyTargetEntityId());
        if (active) {
            suppress(minecraft.options);
        } else if (suppressing) {
            KeyMapping.setAll();
        }
        suppressing = active;
    }

    public static void disconnect() {
        suppressing = false;
        KeyMapping.setAll();
    }

    static boolean shouldSuppressInput(final int preyTargetEntityId) {
        return preyTargetEntityId >= 0;
    }

    private static void suppress(final Options options) {
        options.keyUp.setDown(false);
        options.keyDown.setDown(false);
        options.keyLeft.setDown(false);
        options.keyRight.setDown(false);
        options.keyJump.setDown(false);
        options.keyShift.setDown(false);
        options.keySprint.setDown(false);
    }
}
