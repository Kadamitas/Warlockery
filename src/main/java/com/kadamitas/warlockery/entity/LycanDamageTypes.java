package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.Warlockery;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public final class LycanDamageTypes {
    public static final ResourceKey<DamageType> HARM_WEREWOLVES = ResourceKey.create(
        Registries.DAMAGE_TYPE,
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "harm_werewolves")
    );

    private LycanDamageTypes() {
    }

    public static DamageSource harmWerewolvesSource(
        final ServerLevel level,
        final @Nullable Entity directEntity,
        final @Nullable Entity causingEntity
    ) {
        return new DamageSource(
            level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(HARM_WEREWOLVES),
            directEntity,
            causingEntity
        );
    }

    public static boolean isHarmWerewolves(final DamageSource source) {
        return source.is(HARM_WEREWOLVES);
    }
}
