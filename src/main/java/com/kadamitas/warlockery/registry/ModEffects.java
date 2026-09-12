package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.effect.SoaringMobEffect;
import com.kadamitas.warlockery.effect.UndeadMendingMobEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> REGISTRY = DeferredRegister.create(
        ForgeRegistries.MOB_EFFECTS,
        Warlockery.MOD_ID
    );
    public static final RegistryObject<MobEffect> SOARING = REGISTRY.register("soaring", SoaringMobEffect::new);
    public static final RegistryObject<MobEffect> UNDEAD_MENDING = REGISTRY.register(
        "undead_mending",
        UndeadMendingMobEffect::new
    );

    private ModEffects() {
    }
}
