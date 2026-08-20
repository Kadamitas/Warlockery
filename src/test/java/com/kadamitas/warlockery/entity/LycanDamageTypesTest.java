package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class LycanDamageTypesTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static MappedRegistry<DamageType> registryWithLycanTypes() {
        final MappedRegistry<DamageType> registry = new MappedRegistry<>(Registries.DAMAGE_TYPE, Lifecycle.stable());
        registry.register(
            LycanDamageTypes.HARM_WEREWOLVES,
            new DamageType("magic", DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.0F),
            RegistrationInfo.BUILT_IN
        );
        registry.register(
            DamageTypes.MAGIC,
            new DamageType("magic", DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.0F),
            RegistrationInfo.BUILT_IN
        );
        registry.register(
            DamageTypes.INDIRECT_MAGIC,
            new DamageType("indirectMagic", DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.0F),
            RegistrationInfo.BUILT_IN
        );
        registry.freeze();
        return registry;
    }

    @Test
    void resourceKeyIsTheExactWarlockeryHarmWerewolvesIdentifier() {
        assertEquals("warlockery", LycanDamageTypes.HARM_WEREWOLVES.identifier().getNamespace());
        assertEquals("harm_werewolves", LycanDamageTypes.HARM_WEREWOLVES.identifier().getPath());
        assertEquals(Registries.DAMAGE_TYPE, LycanDamageTypes.HARM_WEREWOLVES.registryKey());
    }

    @Test
    void recognizerMatchesOnlyTheTypedSourceAndPreservesAttribution() {
        final MappedRegistry<DamageType> registry = registryWithLycanTypes();
        final Holder<DamageType> typed = registry.getOrThrow(LycanDamageTypes.HARM_WEREWOLVES);
        final DamageSource attributed = new DamageSource(typed, null, null);
        assertTrue(LycanDamageTypes.isHarmWerewolves(attributed));
        assertEquals(null, attributed.getDirectEntity());
        assertEquals(null, attributed.getEntity());
        final DamageSource ordinaryMagic = new DamageSource(registry.getOrThrow(DamageTypes.MAGIC));
        final DamageSource indirectMagic = new DamageSource(registry.getOrThrow(DamageTypes.INDIRECT_MAGIC));
        assertFalse(LycanDamageTypes.isHarmWerewolves(ordinaryMagic),
            "ordinary magic must not be conflated with the typed anti-werewolf source");
        assertFalse(LycanDamageTypes.isHarmWerewolves(indirectMagic),
            "indirect magic must not be conflated with the typed anti-werewolf source");
    }

    @Test
    void typedSourceIsNeverClassifiedAsSilver() {
        final MappedRegistry<DamageType> registry = registryWithLycanTypes();
        final DamageSource typed = new DamageSource(registry.getOrThrow(LycanDamageTypes.HARM_WEREWOLVES));
        assertTrue(typed.getWeaponItem() == null,
            "the typed brew source carries no weapon item, so silver-weapon classification cannot apply");
    }
}
