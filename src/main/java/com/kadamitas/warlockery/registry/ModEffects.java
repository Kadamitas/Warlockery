package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.effect.SoaringMobEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> REGISTRY = DeferredRegister.create(
        Registries.MOB_EFFECT,
        Warlockery.MOD_ID
    );
    public static final DeferredHolder<MobEffect, MobEffect> SOARING = REGISTRY.register(
        "soaring",
        SoaringMobEffect::new
    );

    private ModEffects() {
    }
}
