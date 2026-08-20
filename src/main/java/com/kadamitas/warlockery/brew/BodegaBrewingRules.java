package com.kadamitas.warlockery.brew;

import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import com.kadamitas.warlockery.registry.ModEntities;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

    public static boolean allows(
        final ServerLevel level,
        final BlockPos pos,
        final Identifier recipe,
        final Optional<UUID> brewer
    ) {
        return requiredFamiliar(recipe)
            .map(id -> brewer.filter(owner -> hasBoundFamiliar(level, pos, id, owner)).isPresent())
            .orElse(true);
    }

    public static boolean hasBoundFamiliar(
        final ServerLevel level,
        final BlockPos pos,
        final String id,
        final UUID brewer
    ) {
        return !level.getEntities(
            ModEntities.ALL.get(id).get(),
            new AABB(pos).inflate(16.0),
            familiar -> ownedByBrewer(familiar.isAlive(), CreatureBehaviorState.owner(familiar), brewer)
        ).isEmpty();
    }

    static boolean ownedByBrewer(final boolean alive, final Optional<UUID> owner, final UUID brewer) {
        return alive && owner.filter(brewer::equals).isPresent();
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
