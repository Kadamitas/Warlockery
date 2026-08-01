package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.entity.BroomEntity;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class BroomControls {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "broom")
    );
    private static final KeyMapping GLIDE = new KeyMapping(
        "key.warlockery.broom_glide",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_LEFT_ALT,
        CATEGORY
    );
    private static ControlState lastState = ControlState.IDLE;
    private static int heartbeatTicks;

    private BroomControls() {
    }

    public static void register() {
        KeyMappingHelper.registerKeyMapping(GLIDE);
    }

    public static void tick(final Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getConnection() == null) {
            lastState = ControlState.IDLE;
            heartbeatTicks = 0;
            return;
        }
        final boolean mounted = minecraft.player.getVehicle() instanceof BroomEntity;
        if (!mounted) {
            lastState = ControlState.IDLE;
            heartbeatTicks = 0;
            return;
        }
        final boolean acceptingInput = minecraft.gui.screen() == null;
        final ControlState state = new ControlState(
            acceptingInput ? axis(minecraft.options.keyRight.isDown(), minecraft.options.keyLeft.isDown()) : 0,
            acceptingInput ? axis(minecraft.options.keyUp.isDown(), minecraft.options.keyDown.isDown()) : 0,
            acceptingInput && minecraft.options.keyJump.isDown(),
            acceptingInput && GLIDE.isDown()
        );
        if (!state.equals(lastState) || ++heartbeatTicks >= 5) {
            ModClientNetwork.requestBroomControl(state.strafe(), state.forward(), state.ascend(), state.gliding());
            lastState = state;
            heartbeatTicks = 0;
        }
    }

    private static int axis(final boolean positive, final boolean negative) {
        return (positive ? 1 : 0) - (negative ? 1 : 0);
    }

    private record ControlState(int strafe, int forward, boolean ascend, boolean gliding) {
        private static final ControlState IDLE = new ControlState(0, 0, false, false);
    }
}
