package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.effect.SoaringMobEffect;
import com.kadamitas.warlockery.effect.UndeadMendingMobEffect;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;

public final class ModEffects {
    public static final RegistrationHandle<MobEffect> SOARING = RegistrationHandle.create("soaring", SoaringMobEffect::new);
    public static final RegistrationHandle<MobEffect> UNDEAD_MENDING = RegistrationHandle.create(
        "undead_mending",
        UndeadMendingMobEffect::new
    );

    private ModEffects() {
    }

    public static void register() {
        SOARING.register(BuiltInRegistries.MOB_EFFECT);
        UNDEAD_MENDING.register(BuiltInRegistries.MOB_EFFECT);
    }
}
