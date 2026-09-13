package com.kadamitas.warlockery.gametest;

import com.kadamitas.warlockery.registry.ModGameTestEnvironments;
import com.kadamitas.warlockery.registry.ModGameTests;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;

/** Development-only registration; this source set is never part of the production artifact. */
@Mod("warlockery_gametests")
public final class WarlockeryGameTestMod {
    public WarlockeryGameTestMod(final IEventBus modBus) {
        ModGameTestEnvironments.REGISTRY.register(modBus);
        ModGameTests.REGISTRY.register(modBus);
    }
}
