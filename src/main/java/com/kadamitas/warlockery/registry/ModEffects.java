package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.effect.SoaringMobEffect;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;

public final class ModEffects {
    public static final RegistrationHandle<MobEffect> SOARING = RegistrationHandle.create("soaring", SoaringMobEffect::new);

    private ModEffects() {
    }

    public static void register() {
        SOARING.register(BuiltInRegistries.MOB_EFFECT);
    }
}
