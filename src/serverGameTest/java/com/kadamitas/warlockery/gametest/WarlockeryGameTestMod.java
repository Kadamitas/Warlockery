package com.kadamitas.warlockery.gametest;

import com.kadamitas.warlockery.registry.ModGameTestEnvironments;
import com.kadamitas.warlockery.registry.ModGameTests;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Development-only registration; this source set is never part of the production artifact. */
@Mod("warlockery_gametests")
public final class WarlockeryGameTestMod {
    public WarlockeryGameTestMod(final FMLJavaModLoadingContext context) {
        final var modBus = context.getModBusGroup();
        ModGameTestEnvironments.REGISTRY.register(modBus);
        ModGameTests.REGISTRY.register(modBus);
    }
}
