package com.kadamitas.warlockery.brew;

import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import com.kadamitas.warlockery.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

public final class BodegaBrewingRules {
    private static final Identifier RECIPE = Identifier.fromNamespaceAndPath("warlockery", "kettle_brew_bodega");

    private BodegaBrewingRules() {
    }

    public static boolean allows(final ServerLevel level, final BlockPos pos, final Identifier recipe) {
        return !requiresFamiliar(recipe) || hasBoundOwl(level, pos);
    }

    public static boolean hasBoundOwl(final ServerLevel level, final BlockPos pos) {
        return !level.getEntities(
            ModEntities.ALL.get("owl").get(),
            new AABB(pos).inflate(16.0),
            owl -> owl.isAlive() && CreatureBehaviorState.owner(owl).isPresent()
        ).isEmpty();
    }

    static boolean requiresFamiliar(final Identifier recipe) {
        return RECIPE.equals(recipe);
    }

    static boolean ready(final boolean required, final boolean familiarPresent) {
        return !required || familiarPresent;
    }
}
