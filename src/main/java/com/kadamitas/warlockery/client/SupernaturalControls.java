package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.network.ModNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

public final class SupernaturalControls {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "supernatural")
    );
    private static final KeyMapping CYCLE_POWER = new KeyMapping(
        "key.warlockery.cycle_power",
        GLFW.GLFW_KEY_V,
        CATEGORY
    );
    private static final KeyMapping ACTIVATE_POWER = new KeyMapping(
        "key.warlockery.activate_power",
        GLFW.GLFW_KEY_G,
        CATEGORY
    );

    private SupernaturalControls() {
    }

    public static void register(final RegisterKeyMappingsEvent event) {
        event.register(CYCLE_POWER);
        event.register(ACTIVATE_POWER);
    }

    public static void tick(final TickEvent.ClientTickEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.screen() != null) {
            return;
        }
        while (CYCLE_POWER.consumeClick()) {
            ModNetwork.requestSupernaturalAction(ModNetwork.SupernaturalAction.CYCLE);
        }
        while (ACTIVATE_POWER.consumeClick()) {
            ModNetwork.requestSupernaturalAction(ModNetwork.SupernaturalAction.ACTIVATE);
        }
    }
}
