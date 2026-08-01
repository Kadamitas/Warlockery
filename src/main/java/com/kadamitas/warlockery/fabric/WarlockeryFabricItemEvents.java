package com.kadamitas.warlockery.fabric;

import com.kadamitas.warlockery.brew.BrewItem;
import com.kadamitas.warlockery.item.BlockBreakBehavior;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.registry.FuelValueEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
        registerBrewFuels();
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

    private static void registerBrewFuels() {
        FuelValueEvents.BUILD.register((builder, context) -> BuiltInRegistries.ITEM.stream()
            .filter(BrewItem.class::isInstance)
            .map(BrewItem.class::cast)
            .filter(item -> item.kind().fuelBurnTime() > 0)
            .forEach(item -> builder.add(item, item.kind().fuelBurnTime())));
    }
}
