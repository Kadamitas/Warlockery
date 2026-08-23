package com.kadamitas.warlockery.transformation;

import com.kadamitas.warlockery.Warlockery;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

public final class VampireDamageTypes {
    public static final ResourceKey<DamageType> VAMPIRE_SUNLIGHT = ResourceKey.create(
        Registries.DAMAGE_TYPE,
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "vampire_sunlight")
    );

    private VampireDamageTypes() {
    }

    public static DamageSource sunlight(final ServerLevel level) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(VAMPIRE_SUNLIGHT));
    }
}
