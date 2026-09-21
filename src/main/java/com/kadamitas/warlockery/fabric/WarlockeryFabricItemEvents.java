package com.kadamitas.warlockery.fabric;

import com.kadamitas.warlockery.item.BlockBreakBehavior;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

public final class WarlockeryFabricItemEvents {
    private static boolean initialized;

    private WarlockeryFabricItemEvents() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        registerBlockAttacks();
    }

    private static void registerBlockAttacks() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, position, state, blockEntity) ->
            !handleBlockBreak(player, position));
    }

    public static boolean handleBlockBreak(final Player player, final BlockPos position) {
        final var stack = player.getMainHandItem();
        return stack.getItem() instanceof BlockBreakBehavior behavior
            && behavior.beforeBlockBreak(stack, position, player);
    }

}
