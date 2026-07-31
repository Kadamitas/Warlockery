package com.kadamitas.warlockery.brew;

import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import com.kadamitas.warlockery.registry.ModEntities;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

public final class BodegaBrewingRules {
    private static final Map<Identifier, String> REQUIRED_FAMILIARS = Map.of(
        Identifier.fromNamespaceAndPath("warlockery", "kettle_brew_bodega"), "owl",
        Identifier.fromNamespaceAndPath("warlockery", "kettle_brew_cursed_leaping"), "familiar_cat",
        Identifier.fromNamespaceAndPath("warlockery", "kettle_brew_frogs_tongue"), "toad"
    );

    private BodegaBrewingRules() {
    }

    public static boolean allows(final ServerLevel level, final BlockPos pos, final Identifier recipe) {
        return requiredFamiliar(recipe).map(id -> hasBoundFamiliar(level, pos, id)).orElse(true);
    }

    public static boolean hasBoundOwl(final ServerLevel level, final BlockPos pos) {
        return hasBoundFamiliar(level, pos, "owl");
    }

    public static boolean hasBoundFamiliar(final ServerLevel level, final BlockPos pos, final String id) {
        return !level.getEntities(
            ModEntities.ALL.get(id).get(),
            new AABB(pos).inflate(16.0),
            familiar -> familiar.isAlive() && CreatureBehaviorState.owner(familiar).isPresent()
        ).isEmpty();
    }

    static boolean requiresFamiliar(final Identifier recipe) {
        return REQUIRED_FAMILIARS.containsKey(recipe);
    }

    static Optional<String> requiredFamiliar(final Identifier recipe) {
        return Optional.ofNullable(REQUIRED_FAMILIARS.get(recipe));
    }

    static boolean ready(final boolean required, final boolean familiarPresent) {
        return !required || familiarPresent;
    }
}
