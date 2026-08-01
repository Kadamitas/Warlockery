package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.network.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class SupernaturalControls {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "supernatural")
    );
    private static final KeyMapping CYCLE_POWER = new KeyMapping(
        "key.warlockery.cycle_power",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_V,
        CATEGORY
    );
    private static final KeyMapping ACTIVATE_POWER = new KeyMapping(
        "key.warlockery.activate_power",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_G,
        CATEGORY
    );

    private SupernaturalControls() {
    }

    public static void register() {
        KeyMappingHelper.registerKeyMapping(CYCLE_POWER);
        KeyMappingHelper.registerKeyMapping(ACTIVATE_POWER);
    }

    public static void tick(final Minecraft minecraft) {
        if (minecraft.player == null || minecraft.gui.screen() != null) {
            return;
        }
        while (CYCLE_POWER.consumeClick()) {
            ModClientNetwork.requestSupernaturalAction(ModNetwork.SupernaturalAction.CYCLE);
        }
        while (ACTIVATE_POWER.consumeClick()) {
            ModClientNetwork.requestSupernaturalAction(ModNetwork.SupernaturalAction.ACTIVATE);
        }
    }
}
